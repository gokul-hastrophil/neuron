package ai.neuron.brain

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
import ai.neuron.brain.model.EngineState
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

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
            registerReceiver(
                commandReceiver,
                IntentFilter(ACTION_TEXT_COMMAND),
                RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(commandReceiver, IntentFilter(ACTION_TEXT_COMMAND))
        }

        Log.d(TAG, "NeuronBrainService started")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(commandReceiver)
        serviceScope.cancel()
        Log.d(TAG, "NeuronBrainService destroyed")
    }

    fun executeCommand(command: String) {
        workingMemory.clear()
        workingMemory.setCurrentTask(command)

        serviceScope.launch {
            engine.execute(command).collect { state ->
                _engineState.value = state
                Log.d(TAG, "Engine state: $state")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
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
