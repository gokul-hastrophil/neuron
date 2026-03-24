package ai.neuron.brain.model

sealed class EngineState {
    data object Idle : EngineState()

    data class Planning(val command: String) : EngineState()

    data class Executing(val stepIndex: Int, val action: LLMAction) : EngineState()

    data class Verifying(val stepIndex: Int) : EngineState()

    data class WaitingForUser(val reason: String) : EngineState()

    /** HITL: awaiting user confirmation for a single action step (SUPERVISED mode). */
    data class ConfirmingAction(val stepIndex: Int, val action: LLMAction) : EngineState()

    /** HITL: showing full plan for user approval (PLAN_APPROVE mode). */
    data class AwaitingPlanApproval(val actions: List<LLMAction>) : EngineState()

    data class Done(val message: String = "Task completed") : EngineState()

    data class Error(val message: String, val recoverable: Boolean = false) : EngineState()
}
