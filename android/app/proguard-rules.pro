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

# Room DB entities
-keep class ai.neuron.memory.entity.** { *; }
-keep class ai.neuron.memory.dao.** { *; }

# SDK public API (developer-facing)
-keep class ai.neuron.sdk.NeuronSDK { *; }
-keep class ai.neuron.sdk.NeuronTool { *; }
-keep class ai.neuron.sdk.ToolRegistry { *; }

# OkHttp + Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
