package ai.neuron.sdk.appfunctions

import java.lang.reflect.Method

/**
 * Scans NeuronAppFunctionsProvider subclasses for @NeuronCapability
 * annotated methods and extracts their metadata.
 */
class CapabilityScanner {
    /**
     * Scan a provider class for @NeuronCapability annotated methods.
     * @return List of discovered capabilities
     */
    fun scan(provider: NeuronAppFunctionsProvider): List<CapabilityMetadata> {
        val capabilities = mutableListOf<CapabilityMetadata>()

        for (method in provider.javaClass.declaredMethods) {
            val annotation = method.getAnnotation(NeuronCapability::class.java) ?: continue
            capabilities.add(
                CapabilityMetadata(
                    name = annotation.name,
                    description = annotation.description,
                    parameterNames = getParameterNames(method),
                    providerClass = provider.javaClass.name,
                    methodName = method.name,
                ),
            )
        }

        return capabilities
    }

    /**
     * Scan multiple providers.
     */
    fun scanAll(providers: List<NeuronAppFunctionsProvider>): Map<String, List<CapabilityMetadata>> {
        return providers.associate { provider ->
            provider.providerId to scan(provider)
        }
    }

    private fun getParameterNames(method: Method): List<String> {
        // Use Java reflection parameter names (requires -parameters compiler flag)
        // Fallback to generic names if not available
        return method.parameters.mapIndexed { index, param ->
            if (param.isNamePresent) param.name else "param$index"
        }
    }
}
