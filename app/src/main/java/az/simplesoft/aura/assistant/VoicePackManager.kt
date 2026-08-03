package az.simplesoft.aura.assistant

import android.content.Context
import java.io.File

enum class VoicePackStatus { NOT_INSTALLED, READY, CORRUPT }

data class VoicePackMetadata(
    val id: String,
    val language: AssistantLanguage,
    val displayName: String,
    val sizeBytes: Long,
    val version: String,
    val license: String,
    val status: VoicePackStatus
)

interface VoiceEngine {
    fun speak(text: String, language: AssistantLanguage)
    fun stop()
    fun shutdown()
}

object VoiceSelectionPolicy {
    fun preferLocal(language: AssistantLanguage): Boolean = language != AssistantLanguage.ENGLISH
}

/** Reports and deletes voice resources independently; it does not download on inspection. */
class VoicePackManager(context: Context) {
    private val root = File(context.applicationContext.filesDir, "voice")

    fun packs(): List<VoicePackMetadata> = listOf(
        pack(
            id = "silero-ru-kseniya",
            language = AssistantLanguage.RUSSIAN,
            displayName = "Kseniya",
            sizeBytes = 142_264_026L,
            version = "v1",
            license = "Silero model license",
            directory = "silero-ru-kseniya",
            fileName = "v1_kseniya_16000.jit"
        ),
        pack(
            id = "silero-az-local",
            language = AssistantLanguage.AZERBAIJANI,
            displayName = "Silero Azerbaijani",
            sizeBytes = 91_695_221L,
            version = "v5",
            license = "Silero model license",
            directory = "silero-v5-cis-base",
            fileName = "v5_cis_base_nostress.jit"
        )
    )

    fun delete(id: String): Boolean {
        val target = packs().firstOrNull { it.id == id } ?: return false
        val directory = when (target.language) {
            AssistantLanguage.RUSSIAN -> File(root, "silero-ru-kseniya")
            AssistantLanguage.AZERBAIJANI -> File(root, "silero-v5-cis-base")
            AssistantLanguage.ENGLISH -> return false
        }
        return directory.deleteRecursively()
    }

    private fun pack(
        id: String,
        language: AssistantLanguage,
        displayName: String,
        sizeBytes: Long,
        version: String,
        license: String,
        directory: String,
        fileName: String
    ): VoicePackMetadata {
        val file = File(File(root, directory), fileName)
        val checksum = File(file.parentFile, "$fileName.sha256")
        val status = when {
            !file.exists() && !checksum.exists() -> VoicePackStatus.NOT_INSTALLED
            file.length() == sizeBytes && checksum.exists() -> VoicePackStatus.READY
            else -> VoicePackStatus.CORRUPT
        }
        return VoicePackMetadata(id, language, displayName, sizeBytes, version, license, status)
    }
}
