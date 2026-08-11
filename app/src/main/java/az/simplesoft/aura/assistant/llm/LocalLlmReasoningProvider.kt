package az.simplesoft.aura.assistant.llm

import android.content.Context
import android.app.ActivityManager
import az.simplesoft.aura.assistant.AssistantContext
import az.simplesoft.aura.assistant.AssistantDecision
import az.simplesoft.aura.assistant.AssistantEntity
import az.simplesoft.aura.assistant.AssistantEntityType
import az.simplesoft.aura.assistant.AssistantLanguage
import az.simplesoft.aura.assistant.AssistantRequest
import az.simplesoft.aura.assistant.DecisionDiagnostics
import az.simplesoft.aura.assistant.ReasoningProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Second-level local reasoning. Fast deterministic commands never reach this provider. */
class LocalLlmReasoningProvider(
    context: Context,
    private val models: LocalLlmModelManager = LocalLlmModelManager(context),
    private val prompts: LocalLlmPromptBuilder = LocalLlmPromptBuilder(),
    private val parser: LocalLlmDecisionParser = LocalLlmDecisionParser(),
    private val enabled: () -> Boolean = { true }
) : ReasoningProvider, AutoCloseable {
    private val appContext = context.applicationContext
    private val engine = LocalLlmNativeEngine(context)
    private val loadMutex = Mutex()
    private var loadedModelId: String? = null
    private val mutableDiagnostics = MutableStateFlow<LocalLlmDiagnostics?>(null)

    override val id: String = "local-llama.cpp-qwen3"
    override val isAvailable: Boolean
        get() = enabled() && models.isReady()

    val modelManager: LocalLlmModelManager get() = models
    val diagnostics: StateFlow<LocalLlmDiagnostics?> = mutableDiagnostics.asStateFlow()

    override suspend fun reason(request: AssistantRequest, context: AssistantContext): AssistantDecision {
        if (!isAvailable) return unresolved(request, "brain-pack-missing")
        val metadata = models.models().first { it.id == LocalLlmModelManager.DEFAULT_MODEL_ID }
        val ramBefore = usedRamMb()
        var loadMs = 0L
        val systemPrompt = prompts.systemPrompt(request.language)
        val userPrompt = prompts.userPrompt(request, context, request.recentTurns)
        val inference = loadMutex.withLock {
            if (loadedModelId != metadata.id) {
                loadMs = engine.load(models.modelFile(metadata.id))
                loadedModelId = metadata.id
            }
            engine.complete(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                maxTokens = 160
            )
        }
        val parsed = parser.parse(inference.text)
        val generationMs = (inference.totalInferenceMs - inference.timeToFirstTokenMs).coerceAtLeast(1L)
        mutableDiagnostics.value = LocalLlmDiagnostics(
            model = metadata.parameters,
            quantization = metadata.quantization,
            ramBeforeMb = ramBefore,
            ramAfterMb = usedRamMb(),
            loadMs = loadMs,
            promptTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt),
            outputTokens = inference.outputTokens,
            tokensPerSecond = inference.outputTokens * 1000.0 / generationMs,
            timeToFirstTokenMs = inference.timeToFirstTokenMs,
            totalMs = inference.totalInferenceMs,
            route = parsed.routeName()
        )
        val diagnostics = DecisionDiagnostics(
            originalText = request.originalText,
            normalizedText = request.normalizedText,
            language = request.language,
            topIntents = listOf("LOCAL_LLM 1.00"),
            selectedIntent = parsed.intentName(),
            confidence = if (parsed is LocalLlmDecision.Unresolved) 0.0 else 0.78,
            entities = parsed.entities(),
            contextReferences = listOf("llama.cpp", metadata.id, "${inference.totalInferenceMs}ms"),
            reason = "local-llm-${parsed.routeName()}",
            processingTimeMs = inference.totalInferenceMs
        )
        return when (parsed) {
            is LocalLlmDecision.Action -> AssistantDecision(
                intentId = parsed.action::class.simpleName ?: "LOCAL_LLM_ACTION",
                confidence = 0.78,
                entities = parsed.entities(),
                language = request.language,
                reply = parsed.reply ?: defaultReply(parsed.action, request.language),
                action = parsed.action.toMusicIntent(),
                diagnostics = diagnostics
            )
            is LocalLlmDecision.Conversation -> AssistantDecision(
                intentId = "LOCAL_LLM_CONVERSATION",
                confidence = 0.72,
                entities = emptyList(),
                language = request.language,
                reply = parsed.reply,
                action = null,
                diagnostics = diagnostics.copy(reason = "local-conversation")
            )
            is LocalLlmDecision.Clarification -> AssistantDecision(
                intentId = "LOCAL_LLM_CLARIFICATION",
                confidence = 0.7,
                entities = emptyList(),
                language = request.language,
                reply = parsed.question,
                action = null,
                needsClarification = true,
                clarification = parsed.question,
                diagnostics = diagnostics
            )
            LocalLlmDecision.Unresolved -> unresolved(request, "local-llm-invalid-output", diagnostics)
        }
    }

    fun unload() {
        engine.unload()
        loadedModelId = null
    }

    override fun close() = engine.close()

    private fun usedRamMb(): Long {
        val info = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.getMemoryInfo(info)
        return ((info.totalMem - info.availMem).coerceAtLeast(0L)) / (1024L * 1024L)
    }

    private fun estimateTokens(text: String): Int =
        (text.length / 3).coerceAtLeast(text.split(Regex("\\s+")).count { it.isNotBlank() })

    private fun unresolved(
        request: AssistantRequest,
        reason: String,
        diagnostics: DecisionDiagnostics = DecisionDiagnostics(
            request.originalText,
            request.normalizedText,
            request.language,
            reason = reason
        )
    ) = AssistantDecision(
        intentId = "UNRESOLVED",
        confidence = 0.0,
        entities = emptyList(),
        language = request.language,
        reply = "",
        action = null,
        diagnostics = diagnostics.copy(reason = reason)
    )

    private fun defaultReply(action: LocalLlmAction, language: AssistantLanguage): String = when (language) {
        AssistantLanguage.RUSSIAN -> when (action) {
            is LocalLlmAction.SearchMusic -> "Хорошо, поищу что-нибудь подходящее."
            LocalLlmAction.PlaySimilar -> "Хорошо, найду что-нибудь похожее."
            else -> "Хорошо."
        }
        AssistantLanguage.AZERBAIJANI -> "Oldu."
        AssistantLanguage.ENGLISH -> "Okay."
    }
}

private fun LocalLlmDecision.intentName(): String? = when (this) {
    is LocalLlmDecision.Action -> action::class.simpleName
    is LocalLlmDecision.Conversation -> "CONVERSATION"
    is LocalLlmDecision.Clarification -> "CLARIFICATION"
    LocalLlmDecision.Unresolved -> null
}

private fun LocalLlmDecision.routeName(): String = when (this) {
    is LocalLlmDecision.Action -> "action"
    is LocalLlmDecision.Conversation -> "conversation"
    is LocalLlmDecision.Clarification -> "clarification"
    LocalLlmDecision.Unresolved -> "unresolved"
}

private fun LocalLlmDecision.entities(): List<AssistantEntity> = when (this) {
    is LocalLlmDecision.Action -> when (val value = action) {
        is LocalLlmAction.SearchMusic -> buildList {
            value.artist?.let { add(AssistantEntity(AssistantEntityType.ARTIST, it)) }
            value.mood?.let { add(AssistantEntity(AssistantEntityType.MOOD, it.name)) }
            add(AssistantEntity(AssistantEntityType.TRACK, value.query))
        }
        is LocalLlmAction.PlayPlaylist -> listOf(AssistantEntity(AssistantEntityType.PLAYLIST, value.name))
        is LocalLlmAction.CreatePlaylist -> listOf(AssistantEntity(AssistantEntityType.PLAYLIST, value.name))
        else -> emptyList()
    }
    else -> emptyList()
}
