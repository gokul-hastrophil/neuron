package ai.neuron.brain.model

sealed class NeuronResult<out T> {
    data class Success<T>(val data: T) : NeuronResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NeuronResult<Nothing>()
}
