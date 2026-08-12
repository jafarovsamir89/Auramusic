package az.simplesoft.aura.assistant.chatscript

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class ChatScriptVariant(val id: String, val label: String, val botName: String) {
    ENGLISH("en", "English demo", "harry"),
    RUSSIAN("ru", "Русский AURA", "aura_ru_lab"),
    AZERBAIJANI("az", "Azərbaycan AURA", "aura_az_lab")
}

data class BrainResult(
    val text: String,
    val latencyMs: Long,
    val variant: ChatScriptVariant,
    val action: String? = null,
    val error: String? = null
)

/** A small, serialized adapter for the isolated ChatScript Lab engine. */
class ChatScriptBrain(private val context: Context) : AutoCloseable {
    private val lock = Mutex()
    private var variant = ChatScriptVariant.ENGLISH
    private var userId = "lab_user"
    private var preparedRoot: File? = null

    suspend fun startSession(selected: ChatScriptVariant, user: String = "lab_user"): BrainResult = lock.withLock {
        variant = selected
        userId = user.ifBlank { "lab_user" }
        val startedAt = System.nanoTime()
        try {
            val root = withContext(Dispatchers.IO) { prepareRuntime(selected) }
            preparedRoot = root
            val raw = ChatScriptNative.nativeStart(root.absolutePath, selected.botName, userId)
            result(raw, elapsedMs(startedAt))
        } catch (error: Throwable) {
            BrainResult("", elapsedMs(startedAt), selected, error = error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun send(text: String): BrainResult = lock.withLock {
        val startedAt = System.nanoTime()
        try {
            val raw = withContext(Dispatchers.Default) {
                ChatScriptNative.nativeSend(variant.botName, userId, text)
            }
            result(raw, elapsedMs(startedAt))
        } catch (error: Throwable) {
            BrainResult("", elapsedMs(startedAt), variant, error = error.message ?: error.javaClass.simpleName)
        }
    }

    suspend fun reset(): BrainResult = lock.withLock {
        val startedAt = System.nanoTime()
        val raw = runCatching { ChatScriptNative.nativeReset(variant.botName, userId) }
            .getOrElse { "__ERROR__:${it.message ?: it.javaClass.simpleName}" }
        result(raw, elapsedMs(startedAt))
    }

    fun runtimeSizeBytes(): Long = preparedRoot?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    override fun close() {
        ChatScriptNative.nativeClose()
    }

    private fun result(raw: String, latencyMs: Long): BrainResult {
        val action = Regex("__ACTION__:([A-Za-z0-9_\\-]+)").find(raw)?.groupValues?.getOrNull(1)
        val cleaned = raw.replace(Regex("__ACTION__:[A-Za-z0-9_\\-]+\\s*"), "").trim()
        val error = cleaned.takeIf { it.startsWith("__ERROR__:") }?.removePrefix("__ERROR__:")
        return BrainResult(if (error == null) cleaned else "", latencyMs, variant, action, error)
    }

    private fun prepareRuntime(selected: ChatScriptVariant): File {
        val root = File(context.filesDir, "chatscript-lab/${selected.id}")
        val marker = File(root, ".prepared-v1")
        if (marker.exists()) return root
        root.deleteRecursively()
        root.mkdirs()
        val variantAsset = "chatscript/variants/${selected.id}"
        if (context.assets.list(variantAsset)?.isNotEmpty() == true) {
            copyAssetTree(variantAsset, root)
        } else {
            copyAssetTree("chatscript", root)
        }
        File(root, "LOGS").mkdirs()
        File(root, "USERS").mkdirs()
        File(root, "TMP").mkdirs()
        marker.writeText("ChatScript Lab runtime ${Locale.US}")
        return root
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> destination.outputStream().use { input.copyTo(it) } }
            return
        }
        destination.mkdirs()
        children.forEach { child -> copyAssetTree("$assetPath/$child", File(destination, child)) }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L
}

private object ChatScriptNative {
    init { System.loadLibrary("aura_chatscript") }

    external fun nativeStart(root: String, bot: String, user: String): String
    external fun nativeSend(bot: String, user: String, text: String): String
    external fun nativeReset(bot: String, user: String): String
    external fun nativeClose()
}
