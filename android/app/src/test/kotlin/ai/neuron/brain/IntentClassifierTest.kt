package ai.neuron.brain

import ai.neuron.brain.model.Complexity
import ai.neuron.brain.model.LLMTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IntentClassifierTest {
    private lateinit var classifier: IntentClassifier

    @BeforeEach
    fun setup() {
        classifier = IntentClassifier()
    }

    @Nested
    @DisplayName("Simple commands → SIMPLE")
    inner class SimpleCommands {
        @Test
        fun should_classifySimple_when_goHome() {
            val result = classifier.classify("go home")
            assertEquals(Complexity.SIMPLE, result.complexity)
            assertEquals(LLMTier.T1, result.suggestedTier)
        }

        @Test
        fun should_classifySimple_when_goBack() {
            val result = classifier.classify("go back")
            assertEquals(Complexity.SIMPLE, result.complexity)
        }

        @Test
        fun should_classifySimple_when_openRecents() {
            val result = classifier.classify("open recents")
            assertEquals(Complexity.SIMPLE, result.complexity)
        }

        @Test
        fun should_classifySimple_when_scrollDown() {
            val result = classifier.classify("scroll down")
            assertEquals(Complexity.SIMPLE, result.complexity)
        }

        @Test
        fun should_classifySimple_when_openSettings() {
            val result = classifier.classify("open settings")
            assertEquals(Complexity.SIMPLE, result.complexity)
        }

        @Test
        fun should_estimateOneStep_when_simpleCommand() {
            val result = classifier.classify("go home")
            assertEquals(1, result.estimatedSteps)
        }
    }

    @Nested
    @DisplayName("Moderate commands → MODERATE")
    inner class ModerateCommands {
        @Test
        fun should_classifyModerate_when_messageOnWhatsApp() {
            val result = classifier.classify("message Mom on WhatsApp")
            assertEquals(Complexity.MODERATE, result.complexity)
            assertEquals(LLMTier.T2, result.suggestedTier)
        }

        @Test
        fun should_classifyModerate_when_sendEmailTo() {
            val result = classifier.classify("send an email to John")
            assertEquals(Complexity.MODERATE, result.complexity)
        }

        @Test
        fun should_classifyModerate_when_searchAndOpen() {
            val result = classifier.classify("search for weather in Chrome")
            assertEquals(Complexity.MODERATE, result.complexity)
        }

        @Test
        fun should_classifyModerate_when_turnOnDarkMode() {
            val result = classifier.classify("turn on dark mode in settings")
            assertEquals(Complexity.MODERATE, result.complexity)
        }

        @Test
        fun should_estimateMultipleSteps_when_moderateCommand() {
            val result = classifier.classify("message Mom on WhatsApp")
            assert(result.estimatedSteps in 2..6)
        }
    }

    @Nested
    @DisplayName("Complex commands → COMPLEX")
    inner class ComplexCommands {
        @Test
        fun should_classifyComplex_when_findCheapestFlight() {
            val result = classifier.classify("find the cheapest flight to NYC next weekend")
            assertEquals(Complexity.COMPLEX, result.complexity)
            assertEquals(LLMTier.T3, result.suggestedTier)
        }

        @Test
        fun should_classifyComplex_when_compareProducts() {
            val result = classifier.classify("compare prices of iPhone 16 across Amazon, Flipkart, and Croma")
            assertEquals(Complexity.COMPLEX, result.complexity)
        }

        @Test
        fun should_classifyComplex_when_multiAppWorkflow() {
            val result = classifier.classify("book an Uber to the airport and then text my wife the ETA")
            assertEquals(Complexity.COMPLEX, result.complexity)
        }

        @Test
        fun should_estimateManySteps_when_complexCommand() {
            val result = classifier.classify("find the cheapest flight to NYC next weekend")
            assert(result.estimatedSteps >= 5)
        }
    }

    @Nested
    @DisplayName("Ambiguous commands → ASK_USER")
    inner class AmbiguousCommands {
        @Test
        fun should_classifyAskUser_when_veryShortAmbiguous() {
            val result = classifier.classify("do it")
            assertEquals(Complexity.ASK_USER, result.complexity)
        }

        @Test
        fun should_classifyAskUser_when_singleWord() {
            val result = classifier.classify("yes")
            assertEquals(Complexity.ASK_USER, result.complexity)
        }
    }

    @Nested
    @DisplayName("Domain classification")
    inner class DomainClassification {
        @Test
        fun should_classifyMessagingDomain_when_whatsAppMention() {
            val result = classifier.classify("message Mom on WhatsApp")
            assertEquals("messaging", result.domain)
        }

        @Test
        fun should_classifyNavigationDomain_when_goHome() {
            val result = classifier.classify("go home")
            assertEquals("navigation", result.domain)
        }

        @Test
        fun should_classifySettingsDomain_when_settingsMentioned() {
            val result = classifier.classify("turn on dark mode in settings")
            assertEquals("settings", result.domain)
        }

        @Test
        fun should_classifySearchDomain_when_searchMentioned() {
            val result = classifier.classify("search for weather in Chrome")
            assertEquals("search", result.domain)
        }
    }
}
