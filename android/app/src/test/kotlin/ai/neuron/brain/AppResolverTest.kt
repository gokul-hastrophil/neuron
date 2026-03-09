package ai.neuron.brain

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AppResolver")
class AppResolverTest {

    private lateinit var pm: PackageManager
    private lateinit var resolver: AppResolver

    @BeforeEach
    fun setup() {
        pm = mockk(relaxed = true)
        resolver = AppResolver()
    }

    @Nested
    @DisplayName("Known apps map")
    inner class KnownApps {

        @Test
        fun should_resolveSettings_when_knownAppName() {
            assertEquals("com.android.settings", resolver.resolve("settings", pm))
        }

        @Test
        fun should_resolveChrome_when_caseInsensitive() {
            assertEquals("com.android.chrome", resolver.resolve("Chrome", pm))
        }

        @Test
        fun should_resolveDialer_when_phoneDialer() {
            assertEquals("com.google.android.dialer", resolver.resolve("phone dialer", pm))
        }

        @Test
        fun should_resolveDialer_when_dialer() {
            assertEquals("com.google.android.dialer", resolver.resolve("dialer", pm))
        }

        @Test
        fun should_resolveWhatsApp_when_trimmedInput() {
            assertEquals("com.whatsapp", resolver.resolve("  whatsapp  ", pm))
        }
    }

    @Nested
    @DisplayName("Package name validation")
    inner class PackageNameValidation {

        @Test
        fun should_returnPackage_when_launchIntentExists() {
            every { pm.getLaunchIntentForPackage("com.example.app") } returns mockk()
            assertEquals("com.example.app", resolver.resolve("com.example.app", pm))
        }

        @Test
        fun should_fallThrough_when_packageNotLaunchable() {
            every { pm.getLaunchIntentForPackage("com.nonexistent.app") } returns null
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            assertNull(resolver.resolve("com.nonexistent.app", pm))
        }
    }

    @Nested
    @DisplayName("Fuzzy label matching")
    inner class FuzzyLabelMatching {

        @Test
        fun should_resolveByExactLabel_when_installedAppMatchesLabel() {
            val appInfo = ApplicationInfo().apply { packageName = "org.telegram.messenger" }
            every { pm.getInstalledApplications(any<Int>()) } returns listOf(appInfo)
            every { pm.getApplicationLabel(appInfo) } returns "Telegram"
            assertEquals("org.telegram.messenger", resolver.resolve("telegram", pm))
        }

        @Test
        fun should_resolveByPartialLabel_when_partialMatchExists() {
            val appInfo = ApplicationInfo().apply { packageName = "com.spotify.music" }
            every { pm.getInstalledApplications(any<Int>()) } returns listOf(appInfo)
            every { pm.getApplicationLabel(appInfo) } returns "Spotify Music"
            assertEquals("com.spotify.music", resolver.resolve("spotify", pm))
        }
    }

    @Nested
    @DisplayName("Intent-based resolution fallback")
    inner class IntentBasedResolution {

        @Test
        fun should_resolveDialerViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.contacts", ".DialtactsActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("telephone", pm)
            assertEquals("com.hihonor.contacts", result)
        }

        @Test
        fun should_resolveCameraViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.camera", ".CameraActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("take a photo", pm)
            assertEquals("com.hihonor.camera", result)
        }

        @Test
        fun should_resolveAlarmViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.deskclock", ".AlarmActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("alarm", pm)
            assertEquals("com.hihonor.deskclock", result)
        }

        @Test
        fun should_resolveBrowserViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.browser", ".BrowserActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("browser", pm)
            assertEquals("com.hihonor.browser", result)
        }

        @Test
        fun should_resolveEmailViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.email", ".EmailActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("email", pm)
            assertEquals("com.hihonor.email", result)
        }

        @Test
        fun should_resolveSmsViaIntent_when_packageResolutionFails() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            val resolveInfo = createResolveInfo("com.hihonor.mms", ".SmsActivity")
            every { pm.resolveActivity(any(), any<Int>()) } returns resolveInfo

            val result = resolver.resolve("sms", pm)
            assertEquals("com.hihonor.mms", result)
        }

        @Test
        fun should_notResolveViaIntent_when_noKeywordMatch() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            every { pm.resolveActivity(any(), any<Int>()) } returns null

            assertNull(resolver.resolve("random_gibberish_xyz", pm))
        }

        @Test
        fun should_callResolveActivity_when_intentKeywordMatches() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            every { pm.resolveActivity(any(), any<Int>()) } returns null

            resolver.resolve("telephone", pm)
            verify(atLeast = 1) { pm.resolveActivity(any(), any<Int>()) }
        }

        @Test
        fun should_notCallResolveActivity_when_noIntentKeywordMatches() {
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()

            resolver.resolve("random_gibberish_xyz", pm)
            verify(exactly = 0) { pm.resolveActivity(any(), any<Int>()) }
        }
    }

    @Nested
    @DisplayName("Intent keyword matching")
    inner class IntentKeywordMatching {

        @Test
        fun should_matchDialerKeywords() {
            val keywords = listOf("telephone", "call", "dial", "ring")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_matchCameraKeywords() {
            val keywords = listOf("photo", "picture", "snap", "take a photo", "shoot")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_matchAlarmKeywords() {
            val keywords = listOf("alarm", "timer", "wake")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_matchBrowserKeywords() {
            val keywords = listOf("browser", "web", "internet", "surf")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_matchEmailKeywords() {
            val keywords = listOf("email", "mail", "inbox")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_matchSmsKeywords() {
            val keywords = listOf("sms", "text message", "mms")
            for (kw in keywords) {
                assertNotNull(
                    resolver.matchIntentForQuery(kw),
                    "Expected intent match for '$kw'",
                )
            }
        }

        @Test
        fun should_returnNull_when_noKeywordMatch() {
            assertNull(resolver.matchIntentForQuery("random_gibberish"))
            assertNull(resolver.matchIntentForQuery("spotify"))
            assertNull(resolver.matchIntentForQuery("game"))
        }
    }

    @Nested
    @DisplayName("Resolution priority")
    inner class ResolutionPriority {

        @Test
        fun should_preferKnownApp_when_bothKnownAndIntentMatch() {
            assertEquals("com.android.settings", resolver.resolve("settings", pm))
        }

        @Test
        fun should_preferFuzzyMatch_when_noKnownAppButLabelMatches() {
            val appInfo = ApplicationInfo().apply { packageName = "com.custom.myapp" }
            every { pm.getInstalledApplications(any<Int>()) } returns listOf(appInfo)
            every { pm.getApplicationLabel(appInfo) } returns "MyApp"

            assertEquals("com.custom.myapp", resolver.resolve("myapp", pm))
        }

        @Test
        fun should_returnNull_when_noResolutionPossible() {
            every { pm.getLaunchIntentForPackage(any()) } returns null
            every { pm.getInstalledApplications(any<Int>()) } returns emptyList()
            every { pm.resolveActivity(any(), any<Int>()) } returns null

            assertNull(resolver.resolve("nonexistent_random_app_xyz", pm))
        }
    }

    private fun createResolveInfo(packageName: String, activityName: String): ResolveInfo {
        val actInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = activityName
        }
        return ResolveInfo().apply {
            this.activityInfo = actInfo
        }
    }
}
