package ai.neuron.sdk.appfunctions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Sample provider for testing. */
class SampleAppFunctionsProvider : NeuronAppFunctionsProvider() {
    override val providerId = "com.example.foodapp"
    override val displayName = "Food Ordering App"
    override val version = "2.0.0"

    var registered = false
    var unregistered = false

    override fun onRegister() {
        registered = true
    }

    override fun onUnregister() {
        unregistered = true
    }

    @NeuronCapability(
        name = "order_food",
        description = "Order food from the app",
    )
    fun orderFood(
        item: String,
        quantity: Int,
    ): String {
        return "Ordered $quantity x $item"
    }

    @NeuronCapability(
        name = "get_menu",
        description = "Get the current menu",
    )
    fun getMenu(): String {
        return "Pizza, Burger, Salad"
    }

    // Method without annotation — should NOT be discovered
    fun internalMethod(): String = "internal"
}

/** Provider with no capabilities for edge case testing. */
class EmptyProvider : NeuronAppFunctionsProvider() {
    override val providerId = "com.example.empty"
    override val displayName = "Empty App"
}

class AppFunctionsProviderTest {
    @Nested
    @DisplayName("NeuronCapability annotation")
    inner class AnnotationTests {
        @Test
        fun should_extractAnnotationMetadata() {
            val provider = SampleAppFunctionsProvider()
            val scanner = CapabilityScanner()
            val capabilities = scanner.scan(provider)

            assertTrue(capabilities.isNotEmpty())
            val orderFood = capabilities.find { it.name == "order_food" }
            assertTrue(orderFood != null)
            assertEquals("Order food from the app", orderFood!!.description)
        }

        @Test
        fun should_extractMethodName() {
            val scanner = CapabilityScanner()
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            val orderFood = capabilities.find { it.name == "order_food" }
            assertEquals("orderFood", orderFood!!.methodName)
        }

        @Test
        fun should_extractProviderClass() {
            val scanner = CapabilityScanner()
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            val orderFood = capabilities.find { it.name == "order_food" }
            assertTrue(orderFood!!.providerClass.contains("SampleAppFunctionsProvider"))
        }
    }

    @Nested
    @DisplayName("NeuronAppFunctionsProvider")
    inner class ProviderTests {
        @Test
        fun should_haveProviderIdAndName() {
            val provider = SampleAppFunctionsProvider()
            assertEquals("com.example.foodapp", provider.providerId)
            assertEquals("Food Ordering App", provider.displayName)
            assertEquals("2.0.0", provider.version)
        }

        @Test
        fun should_callLifecycleCallbacks() {
            val provider = SampleAppFunctionsProvider()
            provider.onRegister()
            assertTrue(provider.registered)
            provider.onUnregister()
            assertTrue(provider.unregistered)
        }

        @Test
        fun should_haveDefaultVersion() {
            val provider = EmptyProvider()
            assertEquals("1.0.0", provider.version)
        }
    }

    @Nested
    @DisplayName("CapabilityScanner")
    inner class ScannerTests {
        private lateinit var scanner: CapabilityScanner

        @BeforeEach
        fun setup() {
            scanner = CapabilityScanner()
        }

        @Test
        fun should_discoverAnnotatedMethods() {
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            assertEquals(2, capabilities.size)
        }

        @Test
        fun should_notDiscoverUnannotatedMethods() {
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            assertTrue(capabilities.none { it.methodName == "internalMethod" })
        }

        @Test
        fun should_returnEmptyList_when_noAnnotatedMethods() {
            val capabilities = scanner.scan(EmptyProvider())
            assertTrue(capabilities.isEmpty())
        }

        @Test
        fun should_scanMultipleProviders() {
            val results =
                scanner.scanAll(
                    listOf(
                        SampleAppFunctionsProvider(),
                        EmptyProvider(),
                    ),
                )
            assertEquals(2, results.size)
            assertEquals(2, results["com.example.foodapp"]!!.size)
            assertEquals(0, results["com.example.empty"]!!.size)
        }

        @Test
        fun should_extractParameterNames() {
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            val orderFood = capabilities.find { it.name == "order_food" }
            // Parameters: item (String), quantity (Int)
            assertEquals(2, orderFood!!.parameterNames.size)
        }

        @Test
        fun should_includeAllCapabilityFields() {
            val capabilities = scanner.scan(SampleAppFunctionsProvider())
            for (cap in capabilities) {
                assertTrue(cap.name.isNotBlank())
                assertTrue(cap.description.isNotBlank())
                assertTrue(cap.providerClass.isNotBlank())
                assertTrue(cap.methodName.isNotBlank())
            }
        }
    }
}
