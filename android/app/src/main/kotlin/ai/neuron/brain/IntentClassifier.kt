package ai.neuron.brain

import ai.neuron.brain.model.Complexity
import ai.neuron.brain.model.IntentClassification
import ai.neuron.brain.model.LLMTier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentClassifier
    @Inject
    constructor() {
        companion object {
            private val SIMPLE_PATTERNS =
                listOf(
                    Regex("^go\\s+(home|back)$", RegexOption.IGNORE_CASE),
                    Regex("^(open|show)\\s+(recents|notifications|settings)$", RegexOption.IGNORE_CASE),
                    Regex("^scroll\\s+(up|down|left|right)$", RegexOption.IGNORE_CASE),
                    Regex("^(tap|click|press)\\s+", RegexOption.IGNORE_CASE),
                    Regex("^open\\s+\\w+$", RegexOption.IGNORE_CASE),
                )

            private val COMPLEX_INDICATORS =
                listOf(
                    Regex("\\b(cheapest|best|compare|across|between)\\b", RegexOption.IGNORE_CASE),
                    Regex("\\b(and then|after that|also|plus)\\b", RegexOption.IGNORE_CASE),
                    Regex("\\b(book|reserve|order|schedule)\\b.*\\b(and|then)\\b", RegexOption.IGNORE_CASE),
                    Regex("\\b(find|search)\\b.*\\b(cheapest|best|lowest|highest)\\b", RegexOption.IGNORE_CASE),
                )

            private val AMBIGUOUS_PATTERNS =
                listOf(
                    Regex("^(yes|no|ok|sure|do it|okay|yep|nope|fine)$", RegexOption.IGNORE_CASE),
                    Regex("^\\w{1,4}$"),
                )

            private val DOMAIN_PATTERNS =
                mapOf(
                    "messaging" to Regex("\\b(message|text|whatsapp|telegram|sms|chat|send.*message)\\b", RegexOption.IGNORE_CASE),
                    "navigation" to Regex("\\b(go\\s+home|go\\s+back|recents|navigate|open\\s+recents)\\b", RegexOption.IGNORE_CASE),
                    "settings" to Regex("\\b(settings|dark\\s+mode|wifi|bluetooth|brightness|volume|toggle)\\b", RegexOption.IGNORE_CASE),
                    "search" to Regex("\\b(search|google|browse|look\\s+up|find.*in.*chrome)\\b", RegexOption.IGNORE_CASE),
                    "email" to Regex("\\b(email|gmail|mail|inbox)\\b", RegexOption.IGNORE_CASE),
                    "calling" to Regex("\\b(call|dial|phone)\\b", RegexOption.IGNORE_CASE),
                )
        }

        fun classify(command: String): IntentClassification {
            val trimmed = command.trim()

            val complexity = classifyComplexity(trimmed)
            val domain = classifyDomain(trimmed)
            val estimatedSteps = estimateSteps(complexity)
            val suggestedTier = tierForComplexity(complexity)

            return IntentClassification(
                complexity = complexity,
                domain = domain,
                estimatedSteps = estimatedSteps,
                suggestedTier = suggestedTier,
            )
        }

        private fun classifyComplexity(command: String): Complexity {
            if (AMBIGUOUS_PATTERNS.any { it.matches(command) }) return Complexity.ASK_USER
            if (SIMPLE_PATTERNS.any { it.matches(command) }) return Complexity.SIMPLE
            if (COMPLEX_INDICATORS.any { it.containsMatchIn(command) }) return Complexity.COMPLEX
            return Complexity.MODERATE
        }

        private fun classifyDomain(command: String): String {
            for ((domain, pattern) in DOMAIN_PATTERNS) {
                if (pattern.containsMatchIn(command)) return domain
            }
            return "general"
        }

        private fun estimateSteps(complexity: Complexity): Int =
            when (complexity) {
                Complexity.SIMPLE -> 1
                Complexity.MODERATE -> 3
                Complexity.COMPLEX -> 7
                Complexity.ASK_USER -> 0
            }

        private fun tierForComplexity(complexity: Complexity): LLMTier =
            when (complexity) {
                Complexity.SIMPLE -> LLMTier.T1
                Complexity.MODERATE -> LLMTier.T2
                Complexity.COMPLEX -> LLMTier.T3
                Complexity.ASK_USER -> LLMTier.T1
            }
    }
