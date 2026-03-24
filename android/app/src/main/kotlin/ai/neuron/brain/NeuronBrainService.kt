package ai.neuron.brain

import ai.neuron.BuildConfig
import ai.neuron.brain.model.EngineState
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NeuronBrainService : Service() {
    companion object {
        private const val TAG = "NeuronBrain"
        private const val CHANNEL_ID = "neuron_brain"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_TEXT_COMMAND = "ai.neuron.ACTION_TEXT_COMMAND"
        const val EXTRA_COMMAND = "command"
    }

    @Inject lateinit var engine: PlanAndExecuteEngine

    @Inject lateinit var workingMemory: WorkingMemory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _engineState = MutableStateFlow<EngineState>(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val commandReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                val command = intent?.getStringExtra(EXTRA_COMMAND) ?: return
                Log.d(TAG, "Received command via broadcast: $command")
                executeCommand(command)
            }
        }

    inner class BrainBinder : Binder() {
        fun getService(): NeuronBrainService = this@NeuronBrainService
    }

    private val binder = BrainBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // RECEIVER_EXPORTED is required for ADB broadcasts (debug only).
            // Release builds keep RECEIVER_NOT_EXPORTED so only in-process callers can reach the receiver.
            val flag = if (BuildConfig.DEBUG) RECEIVER_EXPORTED else RECEIVER_NOT_EXPORTED
            registerReceiver(commandReceiver, IntentFilter(ACTION_TEXT_COMMAND), flag)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(commandReceiver, IntentFilter(ACTION_TEXT_COMMAND))
        }

        Log.d(TAG, "NeuronBrainService started")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val command = intent?.getStringExtra(EXTRA_COMMAND)
        if (command != null) {
            Log.d(TAG, "onStartCommand: received command via intent extra: \"$command\"")
            executeCommand(command)
        } else {
            Log.d(TAG, "onStartCommand: no command extra present, ignoring")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        serviceScope.cancel()
        Log.d(TAG, "NeuronBrainService destroyed")
    }

    fun executeCommand(command: String) {
        Log.i(TAG, "executeCommand: starting — \"$command\"")
        workingMemory.clear()
        workingMemory.setCurrentTask(command)

        serviceScope.launch {
            engine.execute(command).collect { state ->
                _engineState.value = state
                logEngineState(command, state)
            }
        }
    }

    private fun logEngineState(
        command: String,
        state: EngineState,
    ) {
        when (state) {
            is EngineState.Idle -> Log.d(TAG, "[$command] state=Idle")
            is EngineState.Planning -> Log.i(TAG, "[$command] state=Planning command=\"${state.command}\"")
            is EngineState.Executing ->
                Log.i(
                    TAG,
                    "[$command] state=Executing step=${state.stepIndex} " +
                        "actionType=${state.action.actionType} target=\"${state.action.targetId}\"",
                )
            is EngineState.Verifying -> Log.d(TAG, "[$command] state=Verifying step=${state.stepIndex}")
            is EngineState.WaitingForUser -> Log.w(TAG, "[$command] state=WaitingForUser reason=\"${state.reason}\"")
            is EngineState.ConfirmingAction ->
                Log.i(
                    TAG,
                    "[$command] state=ConfirmingAction step=${state.stepIndex} " +
                        "actionType=${state.action.actionType} target=\"${state.action.targetId}\"",
                )
            is EngineState.AwaitingPlanApproval ->
                Log.i(
                    TAG,
                    "[$command] state=AwaitingPlanApproval steps=${state.actions.size}",
                )
            is EngineState.Done -> Log.i(TAG, "[$command] state=Done result=\"${state.message}\"")
            is EngineState.Error ->
                Log.e(
                    TAG,
                    "[$command] state=Error recoverable=${state.recoverable} message=\"${state.message}\"",
                )
        }
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Neuron Brain",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Neuron AI brain service"
            }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Neuron is active")
            .setContentText("AI brain ready for commands")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }
}
