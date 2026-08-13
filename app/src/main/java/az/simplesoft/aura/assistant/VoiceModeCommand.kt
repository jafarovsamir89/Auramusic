package az.simplesoft.aura.assistant

/** Explicit phrases that stop the always-on Plan B voice session. */
object VoiceModeCommand {
    private val shutdown = Regex(
        "^\\s*(?:аура\\s*[,.:!?\\-]?\\s*)?(?:отключись|выключись|замолчи|останови\\s+голос(?:овой\\s+режим)?|выключи\\s+голос(?:овой\\s+режим)?|выйди\\s+из\\s+голос(?:ового\\s+режима)?|прекрати\\s+слушать|не\\s+слушай)\\s*[.!?]*\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun isDisable(text: String): Boolean = shutdown.matches(text.trim())
}
