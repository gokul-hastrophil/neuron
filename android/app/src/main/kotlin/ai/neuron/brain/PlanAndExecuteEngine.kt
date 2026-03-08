package ai.neuron.brain

import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.EngineState
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.NeuronResult
import ai.neuron.brain.model.StepLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanAndExecuteEngine @Inject constructor(
    private val router: LLMRouter,
    private val classifier: IntentClassifier,
    private val uiProvider: UIProvider,
    private val actionDispatcher: ActionDispatcher,
) {
    companion object {
        const val MAX_STEPS = 20
        const val TOTAL_TIMEOUT_MS = 60_000L
        const val CONFIDENCE_THRESHOLD = 0.7
        const val MAX_REPEATED_FAILURES = 3
    }

    interface UIProvider {
        suspend fun getCurrentUITree(): UITree
    }

    interface ActionDispatcher {
        suspend fun dispatch(action: LLMAction): Boolean
    }

    private val _stepLogs = mutableListOf<StepLog>()
    val stepLogs: List<StepLog> get() = _stepLogs.toList()

    fun execute(command: String): Flow<EngineState> = flow {
        _stepLogs.clear()
        emit(EngineState.Planning(command))

        val classification = classifier.classify(command)
        val startTime = System.currentTimeMillis()
        var stepIndex = 0
        var consecutiveFailures = 0
        var lastActionKey = ""

        while (stepIndex < MAX_STEPS) {
            // Abort: total timeout
            if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                emit(EngineState.Error("Total timeout (${TOTAL_TIMEOUT_MS}ms) exceeded"))
                return@flow
            }

            // Replanning: re-read UI tree each iteration for fresh state
            val uiTree = uiProvider.getCurrentUITree()
            val stepStart = System.currentTimeMillis()

            val routeResult = router.route(command, uiTree, classification)

            when (routeResult) {
                is NeuronResult.Error -> {
                    emit(EngineState.Error(routeResult.message))
                    return@flow
                }
                is NeuronResult.Success -> {
                    val action = routeResult.data.action
                        ?: run {
                            emit(EngineState.Error("LLM returned no action"))
                            return@flow
                        }

                    when (action.actionType) {
                        ActionType.DONE -> {
                            logStep(stepIndex, uiTree, routeResult.data.tier, action, true, stepStart)
                            emit(EngineState.Done(action.reasoning ?: "Task completed"))
                            return@flow
                        }
                        ActionType.ERROR -> {
                            logStep(stepIndex, uiTree, routeResult.data.tier, action, false, stepStart)
                            emit(EngineState.Error(action.reasoning ?: "LLM reported error"))
                            return@flow
                        }
                        ActionType.CONFIRM -> {
                            logStep(stepIndex, uiTree, routeResult.data.tier, action, true, stepStart)
                            emit(EngineState.WaitingForUser(action.reasoning ?: "Confirmation needed"))
                            return@flow
                        }
                        else -> {
                            if (action.confidence < CONFIDENCE_THRESHOLD) {
                                logStep(stepIndex, uiTree, routeResult.data.tier, action, false, stepStart)
                                emit(EngineState.WaitingForUser("Low confidence: ${action.reasoning}"))
                                return@flow
                            }

                            emit(EngineState.Executing(stepIndex, action))

                            val success = actionDispatcher.dispatch(action)
                            logStep(stepIndex, uiTree, routeResult.data.tier, action, success, stepStart)

                            if (!success) {
                                // Abort: repeated failures on same action
                                val actionKey = "${action.actionType}:${action.targetId}"
                                if (actionKey == lastActionKey) {
                                    consecutiveFailures++
                                } else {
                                    consecutiveFailures = 1
                                    lastActionKey = actionKey
                                }

                                if (consecutiveFailures >= MAX_REPEATED_FAILURES) {
                                    emit(EngineState.Error("Repeated failures ($MAX_REPEATED_FAILURES) on same action"))
                                    return@flow
                                }

                                emit(EngineState.Error("Action dispatch failed at step $stepIndex"))
                                return@flow
                            } else {
                                consecutiveFailures = 0
                                lastActionKey = ""
                            }

                            emit(EngineState.Verifying(stepIndex))
                            stepIndex++
                        }
                    }
                }
            }
        }

        emit(EngineState.Error("Max steps ($MAX_STEPS) exceeded"))
    }

    private fun logStep(
        stepIndex: Int,
        uiTree: UITree,
        tier: String?,
        action: LLMAction,
        success: Boolean,
        stepStartTime: Long,
    ) {
        _stepLogs.add(
            StepLog(
                stepIndex = stepIndex,
                uiTreeHash = uiTree.hashCode(),
                llmTier = tier,
                action = action,
                success = success,
                durationMs = System.currentTimeMillis() - stepStartTime,
            ),
        )
    }
}
