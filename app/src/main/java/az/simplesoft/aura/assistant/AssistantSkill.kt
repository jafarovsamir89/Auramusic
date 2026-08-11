package az.simplesoft.aura.assistant

enum class AssistantActionSafety {
    SAFE,
    CONFIRMATION_REQUIRED,
    DENIED
}

data class SkillResult(
    val success: Boolean,
    val message: String,
    val safety: AssistantActionSafety = AssistantActionSafety.SAFE
)

/** Future tool seam. Existing AuraViewModel executor remains the compatibility path for now. */
interface AssistantSkill {
    val id: String
    fun canHandle(decision: AssistantDecision): Boolean
    suspend fun execute(decision: AssistantDecision, context: AssistantContext): SkillResult
}

class AssistantSkillRegistry(
    private val skills: List<AssistantSkill> = emptyList()
) {
    suspend fun execute(decision: AssistantDecision, context: AssistantContext): SkillResult? =
        skills.firstOrNull { it.canHandle(decision) }?.execute(decision, context)
}
