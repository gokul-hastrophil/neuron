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
-keep class ai.neuron.character.model.CharacterState { *; }
-keep class ai.neuron.character.dao.CharacterDao { *; }

# Character model serialization
-keep class ai.neuron.character.model.** { *; }

# Live2D SDK (when integrated)
-keep class com.live2d.sdk.** { *; }
-dontwarn com.live2d.sdk.**

# SDK public API (developer-facing)
-keep class ai.neuron.sdk.NeuronSDK { *; }
-keep class ai.neuron.sdk.NeuronTool { *; }
-keep class ai.neuron.sdk.ToolRegistry { *; }

# SECURITY: Strip verbose and debug log calls from release builds.
# This prevents accidental leakage of UI tree data, commands, or actions
# to logcat in production APKs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Google Tink / EncryptedSharedPreferences
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.Instant
-keep class com.google.crypto.tink.** { *; }

# OkHttp + Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
