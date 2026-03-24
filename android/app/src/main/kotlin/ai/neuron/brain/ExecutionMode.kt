package ai.neuron.brain

/**
 * Controls how PlanAndExecuteEngine dispatches actions.
 *
 * - AUTONOMOUS: Execute all actions without user confirmation (sideload only).
 * - SUPERVISED: Show each action step and wait for user approval before executing.
 * - PLAN_APPROVE: Show the full plan up-front; once approved, execute all steps automatically.
 *
 * Play Store builds default to SUPERVISED to comply with Google Play's
 * AccessibilityService policy (Jan 2026) which prohibits autonomous AI-driven actions.
 */
enum class ExecutionMode {
    AUTONOMOUS,
    SUPERVISED,
    PLAN_APPROVE,
}
