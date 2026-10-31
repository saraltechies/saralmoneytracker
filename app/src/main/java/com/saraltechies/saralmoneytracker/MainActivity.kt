package com.saraltechies.saralmoneytracker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.ui.AppNav
import com.saraltechies.saralmoneytracker.ui.lock.LockScreen
import com.saraltechies.saralmoneytracker.ui.theme.SaralMoneyTrackerTheme
import com.saraltechies.saralmoneytracker.util.AppLock
import com.saraltechies.saralmoneytracker.util.AppShortcuts
import com.saraltechies.saralmoneytracker.util.AppUpdates
import com.saraltechies.saralmoneytracker.util.PlayStore
import kotlinx.coroutines.launch

// FragmentActivity (a ComponentActivity) because the fingerprint prompt needs it.
class MainActivity : FragmentActivity() {

    /** From the launcher shortcuts and the reminder notification. */
    private var launchShortcut by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(false)
    private var ready = false
    private var backgroundedAt = 0L
    private lateinit var appUpdates: AppUpdates

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        // androidx.core's version rather than androidx.activity's, which still called deprecated system-bar
        // color APIs on Android 15+ and gets flagged in Play Console.
        WindowCompat.enableEdgeToEdge(window)
        val app = application as SaralMoneyTrackerApp
        appUpdates = AppUpdates(this, app.settings)
        AppShortcuts.publish(this)
        if (savedInstanceState == null) readShortcut(intent)

        lifecycleScope.launch {
            val prefs = app.settings.current()
            // Lock before the first frame so nothing shows behind the prompt.
            locked = !unlockedInProcess && prefs.appLock && AppLock.available(this@MainActivity)
            ready = true
            if (locked) unlock()
        }

        setContent {
            val prefs by app.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
            val current: AppPrefs = prefs ?: return@setContent
            LaunchedEffect(current.hideInRecents) {
                if (current.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            SaralMoneyTrackerTheme(current.theme) {
                Box {
                    AppNav(
                        repository = app.repository,
                        settings = app.settings,
                        prefs = current,
                        launchShortcut = launchShortcut,
                        onShortcutConsumed = { launchShortcut = null },
                        updateReady = appUpdates.updateReady,
                        onRestartForUpdate = appUpdates::completeUpdate,
                        onReviewMoment = { lifecycleScope.launch { PlayStore.requestReview(this@MainActivity) } },
                    )
                    if (locked) LockScreen(onUnlock = ::unlock)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val app = application as SaralMoneyTrackerApp
        lifecycleScope.launch {
            // Entries that came due while the app was closed (salary, rent, EMIs).
            app.repository.processRecurring()
            if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > AppLock.RELOCK_AFTER_MS &&
                app.settings.current().appLock && AppLock.available(this@MainActivity)
            ) {
                unlockedInProcess = false
                locked = true
                unlock()
            }
            backgroundedAt = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun unlock() {
        AppLock.authenticate(this, onSuccess = {
            locked = false
            unlockedInProcess = true
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShortcut(intent)
    }

    private fun readShortcut(intent: Intent) {
        if (intent.action != ACTION_SHORTCUT) return
        val id = intent.getStringExtra(EXTRA_SHORTCUT) ?: return
        launchShortcut = id
        ShortcutManagerCompat.reportShortcutUsed(this, id)
    }

    companion object {
        const val ACTION_SHORTCUT = "com.saraltechies.saralmoneytracker.action.SHORTCUT"
        const val EXTRA_SHORTCUT = "shortcut"

        /** Survives rotation but not the process, so a restored app asks again. */
        private var unlockedInProcess = false
    }
}
