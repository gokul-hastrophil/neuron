package ai.neuron.accessibility

import ai.neuron.brain.NeuronBrainService
import ai.neuron.brain.model.EngineState
import ai.neuron.input.SpeechRecognitionManager
import ai.neuron.input.VoiceInputController
import ai.neuron.input.WakeWordService
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NeuronAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var overlayManager: OverlayManager? = null
    private var speechManager: SpeechRecognitionManager? = null
    private var voiceController: VoiceInputController? = null
    private var wakeWordService: WakeWordService? = null
    private var brainService: NeuronBrainService? = null
    private var brainBound = false

    private val brainConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val service = (binder as? NeuronBrainService.BrainBinder)?.getService() ?: return
                brainService = service
                brainBound = true
                Log.i(TAG, "Bound to NeuronBrainService")
                observeBrainState(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brainService = null
                brainBound = false
                Log.i(TAG, "Disconnected from NeuronBrainService")
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "NeuronAccessibilityService connected")

        // Create overlay
        val overlay = OverlayManager(this)
        overlayManager = overlay

        // Create speech + voice controller
        val speech = SpeechRecognitionManager()
        speechManager = speech
        val voice = VoiceInputController(speech, overlay)
        voiceController = voice

        // Wire hold-to-speak gesture from overlay → voice controller
        overlay.onHoldStart = { voice.onHoldStart(this) }
        overlay.onHoldRelease = { voice.onHoldRelease() }
        overlay.onClose = {
            Log.i(TAG, "Overlay closed by user")
            overlay.hide()
        }

        // Wire final voice command → brain service
        voice.setOnCommandReady { command ->
            Log.i(TAG, "Voice command received: $command")
            overlay.statusText.value = command
            sendCommandToBrain(command)
        }

        // Observe speech state changes → overlay state updates + partial transcript + errors
        voice.startObserving(serviceScope)
        serviceScope.launch(Dispatchers.Main) {
            speech.partialText.collect { text ->
                overlay.partialTranscript.value = text
            }
        }
        serviceScope.launch(Dispatchers.Main) {
            speech.errorMessage.collect { msg ->
                if (msg != null) {
                    overlay.errorMessage.value = msg
                }
            }
        }

        // Start wake word detection (hands-free activation)
        startWakeWordDetection(voice)

        // Show the floating bubble
        overlay.show()

        // Start and bind to the brain service
        startBrainService(null)
        bindToBrainService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (debugEventLogging) {
            Log.v(
                TAG_EVENTS,
                "event=${event.eventType} pkg=${event.packageName} " +
                    "class=${event.className} text=${event.text}",
            )
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "NeuronAccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "NeuronAccessibilityService destroyed")
        wakeWordService?.stop()
        voiceController?.stopObserving()
        overlayManager?.hide()
        if (brainBound) {
            unbindService(brainConnection)
            brainBound = false
        }
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startWakeWordDetection(voice: VoiceInputController) {
        val prefs = getSharedPreferences("neuron_prefs", Context.MODE_PRIVATE)
        val accessKey = prefs.getString("picovoice_access_key", "") ?: ""
        val keywordName = prefs.getString("wake_word_keyword", "JARVIS") ?: "JARVIS"

        if (accessKey.isBlank()) {
            Log.w(TAG, "Picovoice access key not set — wake word disabled")
            return
        }

        val keyword =
            try {
                ai.picovoice.porcupine.Porcupine.BuiltInKeyword.valueOf(keywordName)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid wake word keyword: $keywordName, defaulting to JARVIS")
                ai.picovoice.porcupine.Porcupine.BuiltInKeyword.JARVIS
            }

        val wakeWord = WakeWordService(this, accessKey, keyword)
        wakeWord.onWakeWordDetected = {
            Log.i(TAG, "Wake word detected! Starting voice input...")
            serviceScope.launch(Dispatchers.Main) {
                val overlay = overlayManager ?: return@launch
                // Only activate if not already busy
                if (overlay.state.value == OverlayManager.OverlayState.IDLE ||
                    overlay.state.value == OverlayManager.OverlayState.HIDDEN
                ) {
                    voice.onHoldStart(this@NeuronAccessibilityService)
                    overlay.updateState(OverlayManager.OverlayState.LISTENING)
                    overlay.statusText.value = "Listening..."
                    // Auto-stop after 5 seconds if user doesn't speak
                    serviceScope.launch(Dispatchers.Main) {
                        delay(5000)
                        if (overlay.state.value == OverlayManager.OverlayState.LISTENING) {
                            voice.onHoldRelease()
                        }
                    }
                }
            }
        }

        if (wakeWord.start()) {
            wakeWordService = wakeWord
            Log.i(TAG, "Wake word detection active (keyword=$keywordName)")
        } else {
            Log.e(TAG, "Failed to start wake word detection")
        }
    }

    private fun observeBrainState(service: NeuronBrainService) {
        serviceScope.launch(Dispatchers.Main) {
            service.engineState.collect { state ->
                val overlay = overlayManager ?: return@collect
                when (state) {
                    is EngineState.Idle -> {
                        // Don't reset to IDLE if speech is active
                        val speechState = speechManager?.state?.value
                        if (speechState == SpeechRecognitionManager.State.IDLE) {
                            // Only return to IDLE if not already showing a transient state
                        }
                    }
                    is EngineState.Planning -> {
                        overlay.updateState(OverlayManager.OverlayState.THINKING)
                        overlay.statusText.value = state.command
                    }
                    is EngineState.Executing -> {
                        overlay.updateState(OverlayManager.OverlayState.EXECUTING)
                        overlay.statusText.value = "${state.action.actionType}: ${state.action.targetText ?: state.action.targetId ?: ""}"
                    }
                    is EngineState.Verifying -> {
                        overlay.updateState(OverlayManager.OverlayState.EXECUTING)
                        overlay.statusText.value = "Verifying step ${state.stepIndex + 1}"
                    }
                    is EngineState.WaitingForUser -> {
                        overlay.updateState(OverlayManager.OverlayState.THINKING)
                        overlay.statusText.value = state.reason
                    }
                    is EngineState.ConfirmingAction -> {
                        overlay.updateState(OverlayManager.OverlayState.CONFIRMING)
                        val desc = "${state.action.actionType}: ${state.action.targetText ?: state.action.targetId ?: ""}"
                        overlay.confirmationPrompt.value = "Step ${state.stepIndex + 1}: $desc"
                    }
                    is EngineState.AwaitingPlanApproval -> {
                        overlay.updateState(OverlayManager.OverlayState.CONFIRMING)
                        overlay.confirmationPrompt.value = "Approve plan with ${state.actions.size} steps?"
                    }
                    is EngineState.Done -> {
                        overlay.updateState(OverlayManager.OverlayState.DONE)
                        overlay.statusText.value = state.message
                        // Auto-return to IDLE after 3 seconds
                        serviceScope.launch(Dispatchers.Main) {
                            delay(3000)
                            if (overlay.state.value == OverlayManager.OverlayState.DONE) {
                                overlay.updateState(OverlayManager.OverlayState.IDLE)
                                overlay.statusText.value = ""
                            }
                        }
                    }
                    is EngineState.Error -> {
                        overlay.updateState(OverlayManager.OverlayState.ERROR)
                        overlay.errorMessage.value = state.message
                        overlay.statusText.value = ""
                        // Auto-return to IDLE after 5 seconds
                        serviceScope.launch(Dispatchers.Main) {
                            delay(5000)
                            if (overlay.state.value == OverlayManager.OverlayState.ERROR) {
                                overlay.updateState(OverlayManager.OverlayState.IDLE)
                                overlay.errorMessage.value = ""
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendCommandToBrain(command: String) {
        Log.i(TAG, "Sending command to brain: $command")
        val service = brainService
        if (service != null) {
            service.executeCommand(command)
        } else {
            startBrainService(command)
        }
    }

    private fun startBrainService(command: String?) {
        val intent = Intent(this, NeuronBrainService::class.java)
        if (command != null) {
            intent.putExtra(NeuronBrainService.EXTRA_COMMAND, command)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun bindToBrainService() {
        val intent = Intent(this, NeuronBrainService::class.java)
        bindService(intent, brainConnection, Context.BIND_AUTO_CREATE)
    }

    companion object {
        private const val TAG = "NeuronAS"
        private const val TAG_EVENTS = "NeuronEvents"

        @Volatile
        var instance: NeuronAccessibilityService? = null
            private set

        var debugEventLogging: Boolean = false
    }
}
