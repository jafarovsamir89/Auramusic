package az.simplesoft.aura.assistant.gemini

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

class GeminiLiveSession(
    private val authProvider: GeminiAuthProvider,
    private val toolExecutor: GeminiToolExecutor,
    private val audioOutput: GeminiAudioOutput,
    private val scope: CoroutineScope,
    voice: String = GeminiLiveConfig.DEFAULT_VOICE,
    private val onInputTranscription: (String) -> Unit = {},
    private val onOutputTranscription: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    @Volatile private var voiceName: String = voice
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val mutableState = MutableStateFlow(GeminiSessionState.DISCONNECTED)
    val state: StateFlow<GeminiSessionState> = mutableState.asStateFlow()
    private var socket: WebSocket? = null
    private var resumeHandle: String? = null
    private var setupStartedAt = 0L
    private var connectedAt = 0L
    private var speechEndAt = 0L
    private var reconnects = 0
    private var closedByUser = false
    private var sessionId = UUID.randomUUID().toString()
    private val _diagnostics = MutableStateFlow(GeminiDiagnostics(voice = voiceName, sessionId = sessionId))
    val diagnostics: StateFlow<GeminiDiagnostics> = _diagnostics.asStateFlow()

    fun connect() {
        if (mutableState.value == GeminiSessionState.CONNECTING || mutableState.value == GeminiSessionState.READY) return
        closedByUser = false
        mutableState.value = if (reconnects == 0) GeminiSessionState.CONNECTING else GeminiSessionState.RECONNECTING
        _diagnostics.value = _diagnostics.value.copy(state = mutableState.value, lastError = null)
        scope.launch(Dispatchers.IO) {
            runCatching { authProvider.getCredential() }
                .onSuccess { credential -> openSocket(credential) }
                .onFailure { fail(it.message ?: "Gemini authentication failed") }
        }
    }

    fun setVoice(value: String) {
        if (value !in GeminiLiveConfig.VOICES || value == voiceName) return
        voiceName = value
        _diagnostics.value = _diagnostics.value.copy(voice = value)
        if (mutableState.value != GeminiSessionState.DISCONNECTED) {
            close()
            connect()
        }
    }

    private fun openSocket(credential: GeminiCredential) {
        val url = GeminiLiveConfig.WS_URL.toHttpUrl().newBuilder()
            .addQueryParameter(if (credential.ephemeral) "access_token" else "key", credential.value)
            .build()
        setupStartedAt = System.currentTimeMillis()
        socket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connectedAt = System.currentTimeMillis()
                    _diagnostics.value = _diagnostics.value.copy(
                        connected = true,
                        connectMs = connectedAt - setupStartedAt,
                        bytesSent = 0,
                        bytesReceived = 0
                    )
                    webSocket.send(setupMessage().toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    _diagnostics.value = _diagnostics.value.copy(bytesReceived = _diagnostics.value.bytesReceived + text.toByteArray().size)
                    handleServerMessage(webSocket, JSONObject(text))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!closedByUser && socket === webSocket) fail(t.message ?: "Gemini WebSocket error")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closedByUser && socket === webSocket && mutableState.value != GeminiSessionState.ERROR) {
                        fail("Gemini connection closed ($code): $reason")
                    }
                }
            }
        )
    }

    private fun setupMessage(): JSONObject = JSONObject().apply {
        put("setup", JSONObject().apply {
            put("model", "models/${GeminiLiveConfig.MODEL}")
            put("responseModalities", JSONArray().put("AUDIO"))
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", GeminiSystemPrompt.VALUE))))
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", GeminiToolRegistry.declarations())))
            put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })
            put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            put("inputAudioTranscription", JSONObject())
            put("outputAudioTranscription", JSONObject())
            put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingLevel", "minimal")).put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)))))
        })
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        mutableState.value = GeminiSessionState.MODEL_THINKING
        send(JSONObject().put("realtimeInput", JSONObject().put("text", text)))
    }

    fun sendAudio(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        if (mutableState.value == GeminiSessionState.READY) mutableState.value = GeminiSessionState.USER_SPEAKING
        send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject().apply {
            put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
            put("mimeType", "audio/pcm;rate=${GeminiLiveConfig.INPUT_RATE}")
        })))
    }

    fun markSpeechEnded() {
        speechEndAt = System.currentTimeMillis()
        _diagnostics.value = _diagnostics.value.copy(firstAudioMs = null, speechEndToFirstAudioMs = null)
    }

    fun interrupt() {
        audioOutput.clear()
        _diagnostics.value = _diagnostics.value.copy(interruptionCount = _diagnostics.value.interruptionCount + 1)
    }

    private fun send(message: JSONObject) {
        val bytes = message.toString().toByteArray()
        if (socket?.send(message.toString()) == true) {
            _diagnostics.value = _diagnostics.value.copy(bytesSent = _diagnostics.value.bytesSent + bytes.size)
        }
    }

    private fun handleServerMessage(webSocket: WebSocket, message: JSONObject) {
        when {
            message.has("setupComplete") -> {
                mutableState.value = GeminiSessionState.READY
                _diagnostics.value = _diagnostics.value.copy(
                    state = GeminiSessionState.READY,
                    connected = true,
                    setupMs = System.currentTimeMillis() - setupStartedAt
                )
            }
            message.has("sessionResumptionUpdate") -> {
                val update = message.getJSONObject("sessionResumptionUpdate")
                if (update.optBoolean("resumable")) {
                    resumeHandle = update.optString("newHandle").takeIf(String::isNotBlank)
                    _diagnostics.value = _diagnostics.value.copy(resumeAvailable = resumeHandle != null)
                }
            }
            message.has("goAway") -> {
                reconnects++
                _diagnostics.value = _diagnostics.value.copy(reconnectCount = reconnects)
                if (!closedByUser) {
                    val oldSocket = socket
                    socket = null
                    mutableState.value = GeminiSessionState.RECONNECTING
                    oldSocket?.close(1000, "reconnecting before GoAway")
                    connect()
                }
            }
        }
        message.optJSONObject("serverContent")?.let { content ->
            if (content.optBoolean("interrupted")) {
                interrupt()
                mutableState.value = GeminiSessionState.USER_SPEAKING
            }
            content.optJSONObject("inputTranscription")?.optString("text")?.takeIf(String::isNotBlank)?.let {
                _diagnostics.value = _diagnostics.value.copy(inputTranscription = it)
                onInputTranscription(it)
            }
            content.optJSONObject("outputTranscription")?.optString("text")?.takeIf(String::isNotBlank)?.let {
                _diagnostics.value = _diagnostics.value.copy(outputTranscription = it)
                onOutputTranscription(it)
            }
            content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (index in 0 until parts.length()) {
                    val part = parts.optJSONObject(index) ?: continue
                    val inline = part.optJSONObject("inlineData") ?: continue
                    val encoded = inline.optString("data")
                    if (encoded.isNotBlank()) {
                        val firstAudio = _diagnostics.value.firstAudioMs ?: System.currentTimeMillis()
                        val latency = if (speechEndAt > 0) max(0L, firstAudio - speechEndAt) else null
                        _diagnostics.value = _diagnostics.value.copy(
                            state = GeminiSessionState.MODEL_SPEAKING,
                            firstAudioMs = firstAudio,
                            speechEndToFirstAudioMs = latency
                        )
                        audioOutput.write(Base64.decode(encoded, Base64.DEFAULT))
                    }
                }
            }
            if (content.optBoolean("turnComplete")) {
                mutableState.value = GeminiSessionState.READY
                _diagnostics.value = _diagnostics.value.copy(state = GeminiSessionState.READY)
            }
        }
        message.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            mutableState.value = GeminiSessionState.TOOL_EXECUTING
            scope.launch {
                val responses = JSONArray()
                for (index in 0 until calls.length()) {
                    val call = calls.getJSONObject(index)
                    val name = call.optString("name")
                    val id = call.optString("id")
                    val result = runCatching { toolExecutor.execute(name, call.optJSONObject("args") ?: JSONObject()) }
                        .getOrElse { GeminiToolResult("error", it.message ?: "Tool failed") }
                    _diagnostics.value = _diagnostics.value.copy(lastTool = name, lastToolResult = result.message)
                    responses.put(JSONObject().apply {
                        put("name", name)
                        put("id", id)
                        put("response", JSONObject().apply {
                            put("result", JSONObject().apply {
                                put("status", result.status)
                                put("message", result.message)
                                put("data", result.data)
                            })
                        })
                    })
                }
                send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", responses)))
                mutableState.value = GeminiSessionState.MODEL_THINKING
            }
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        mutableState.value = GeminiSessionState.ERROR
        _diagnostics.value = _diagnostics.value.copy(state = GeminiSessionState.ERROR, connected = false, lastError = message)
        onError(message)
    }

    fun close() {
        closedByUser = true
        socket?.close(1000, "client closed")
        socket = null
        audioOutput.stop()
        mutableState.value = GeminiSessionState.DISCONNECTED
        _diagnostics.value = _diagnostics.value.copy(state = GeminiSessionState.DISCONNECTED, connected = false)
    }

    private companion object { const val TAG = "GeminiLive" }
}
