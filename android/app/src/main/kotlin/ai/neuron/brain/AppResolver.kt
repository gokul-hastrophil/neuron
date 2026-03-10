package ai.neuron.brain

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppResolver @Inject constructor() {

    companion object {
        private const val TAG = "NeuronAppResolver"

        val KNOWN_APPS = mapOf(
            "settings" to "com.android.settings",
            "chrome" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "messages" to "com.google.android.apps.messaging",
            "phone" to "com.android.phone",
            "dialer" to "com.android.phone",
            "phone dialer" to "com.android.phone",
            "camera" to "com.android.camera",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.android.deskclock",
            "alarm" to "com.android.deskclock",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "youtube" to "com.google.android.youtube",
            "play store" to "com.android.vending",
            "files" to "com.google.android.apps.nbu.files",
            "photos" to "com.google.android.apps.photos",
            "contacts" to "com.android.contacts",
            "calendar" to "com.google.android.calendar",
        )
    }

    fun resolve(value: String, pm: PackageManager): String? {
        val lower = value.lowercase().trim()

        // 1. Known apps map — but verify the package is actually installed
        KNOWN_APPS[lower]?.let { knownPkg ->
            if (pm.getLaunchIntentForPackage(knownPkg) != null) return knownPkg
            Log.w(TAG, "Known package '$knownPkg' not on this device, falling through to fuzzy match")
        }

        // 2. Package name validation (contains dot)
        if ('.' in value) {
            if (pm.getLaunchIntentForPackage(value) != null) return value
            Log.w(TAG, "Package '$value' not launchable, trying label lookup...")
        }

        // 3. Fuzzy match: search installed apps by label (only launchable apps)
        val searchTerm = value.replace("com.android.", "").replace("com.google.", "")
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        // Exact label match
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.equals(lower, ignoreCase = true) || label.equals(searchTerm, ignoreCase = true)) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) return app.packageName
                Log.d(TAG, "Fuzzy exact match '${app.packageName}' not launchable, skipping")
            }
        }
        // Partial label match
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(lower) || lower.contains(label)) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) return app.packageName
                Log.d(TAG, "Fuzzy partial match '${app.packageName}' not launchable, skipping")
            }
        }

        // 4. Intent-based resolution fallback (for OEM devices)
        return resolveViaIntent(lower, pm)
    }

    private fun resolveViaIntent(query: String, pm: PackageManager): String? {
        val intent = matchIntentForQuery(query) ?: return null
        val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = resolved?.activityInfo?.packageName
        if (pkg != null) {
            Log.i(TAG, "Intent-based resolution: '$query' → $pkg via ${intent.action}")
        }
        return pkg
    }

    internal fun matchIntentForQuery(query: String): Intent? {
        // Map natural language queries to standard Android intent actions
        val dialerKeywords = listOf("telephone", "call", "dial", "ring", "phone", "dialer")
        val cameraKeywords = listOf("photo", "picture", "snap", "take a photo", "shoot")
        val alarmKeywords = listOf("alarm", "timer", "reminder", "wake")
        val browserKeywords = listOf("browser", "web", "internet", "surf")
        val emailKeywords = listOf("email", "mail", "inbox")
        val smsKeywords = listOf("sms", "text message", "texting", "mms")

        return when {
            dialerKeywords.any { query.contains(it) } ->
                Intent(Intent.ACTION_DIAL)
            cameraKeywords.any { query.contains(it) } ->
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            alarmKeywords.any { query.contains(it) } ->
                Intent(AlarmClock.ACTION_SET_ALARM)
            browserKeywords.any { query.contains(it) } ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            emailKeywords.any { query.contains(it) } ->
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            smsKeywords.any { query.contains(it) } ->
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            else -> null
        }
    }
}
