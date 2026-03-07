# Neuron ProGuard Rules

# Keep AccessibilityService
-keep class ai.neuron.accessibility.NeuronAccessibilityService { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ai.neuron.**$$serializer { *; }
-keepclassmembers class ai.neuron.** { *** Companion; }
-keepclasseswithmembers class ai.neuron.** { kotlinx.serialization.KSerializer serializer(...); }
