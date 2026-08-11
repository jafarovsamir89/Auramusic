package az.simplesoft.aura.assistant

/**
 * Compile-safe extension point for a future remote provider.
 * AURA 0.5 deliberately has no API client, token, or network runtime here.
 */
interface RemoteReasoningProvider : ReasoningProvider

class DisabledRemoteReasoningProvider : RemoteReasoningProvider {
    override val id: String = "remote-disabled"
    override val isAvailable: Boolean = false

    override suspend fun reason(request: AssistantRequest, context: AssistantContext): AssistantDecision =
        throw IllegalStateException("Remote reasoning is disabled in local-only mode")
}
