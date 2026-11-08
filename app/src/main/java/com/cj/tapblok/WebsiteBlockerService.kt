package com.cj.tapblok

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import com.cj.tapblok.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads the address bar of supported browsers while a session is active and sends the
 * user back + shows the block screen when the URL matches [WebsiteFilter].
 * Nothing is read or stored outside an active session.
 */
class WebsiteBlockerService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var userPatterns: List<String> = emptyList()
    private var lastBlockedUrl: String? = null
    private var lastBlockedAt = 0L

    companion object {
        private const val TAG = "WebsiteBlockerService"
        private const val REBLOCK_DEBOUNCE_MS = 1_500L

        /** Browser package -> view id of its address bar (without the "pkg:id/" prefix). */
        val URL_BAR_IDS: Map<String, String> = mapOf(
            "com.android.chrome" to "url_bar",
            "com.chrome.beta" to "url_bar",
            "com.chrome.dev" to "url_bar",
            "com.brave.browser" to "url_bar",
            "com.microsoft.emmx" to "url_bar",
            "com.kiwibrowser.browser" to "url_bar",
            "com.vivaldi.browser" to "url_bar",
            "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
            "org.mozilla.firefox_beta" to "mozac_browser_toolbar_url_view",
            "org.mozilla.fenix" to "mozac_browser_toolbar_url_view",
            "org.mozilla.focus" to "mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser" to "location_bar_edit_text",
            "com.opera.browser" to "url_field",
            "com.opera.mini.native" to "url_field",
            "com.duckduckgo.mobile.android" to "omnibarTextInput"
        )

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, WebsiteBlockerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Only listen to browsers so other apps' content is never delivered to us
        serviceInfo = serviceInfo.apply { packageNames = URL_BAR_IDS.keys.toTypedArray() }

        serviceScope.launch {
            AppDatabase.getDatabase(this@WebsiteBlockerService).blockedSiteDao()
                .getAllBlockedSites().collect { list -> userPatterns = list.map { it.pattern } }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !AppMonitoringService.isRunning) return
        val browser = event.packageName?.toString() ?: return
        val urlBarId = URL_BAR_IDS[browser] ?: return
        val root = rootInActiveWindow ?: return

        val url = readUrl(root, "$browser:id/$urlBarId") ?: return
        val prefs = AppSettings.prefs(this)
        val adultFilter = prefs.getBoolean(AppSettings.KEY_ADULT_FILTER_ENABLED, true)
        if (!WebsiteFilter.matches(url, adultFilter, userPatterns)) return

        val now = System.currentTimeMillis()
        if (url == lastBlockedUrl && now - lastBlockedAt < REBLOCK_DEBOUNCE_MS) return
        lastBlockedUrl = url
        lastBlockedAt = now

        if (BuildConfig.DEBUG) Log.d(TAG, "Blocked website in $browser: $url")
        performGlobalAction(GLOBAL_ACTION_BACK)
        startActivity(Intent(this, BlockingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockingActivity.EXTRA_BLOCKED_URL, WebsiteFilter.hostOf(WebsiteFilter.normalize(url)))
        })
        prefs.edit { putInt("blocked_app_attempts", prefs.getInt("blocked_app_attempts", 0) + 1) }
    }

    private fun readUrl(root: AccessibilityNodeInfo, viewId: String): String? =
        root.findAccessibilityNodeInfosByViewId(viewId)
            .firstNotNullOfOrNull { node -> node.text?.toString()?.takeIf { it.isNotBlank() } }

    override fun onInterrupt() {}

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
