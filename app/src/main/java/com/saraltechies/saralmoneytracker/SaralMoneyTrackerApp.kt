package com.saraltechies.saralmoneytracker

import android.app.Application
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.util.Reminders
import kotlinx.coroutines.launch
import com.saraltechies.saralmoneytracker.util.StatementFiles

/** Holds the app-wide singletons; small enough that a DI framework isn't worth it. */
class SaralMoneyTrackerApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { MoneyRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Thread { StatementFiles.clearOld(this) }.start()
        // A backup the nightly alarm missed (some phones hold alarms back) runs now.
        com.saraltechies.saralmoneytracker.util.AutoBackup.catchUp(this)
        com.saraltechies.saralmoneytracker.util.Alerts.createChannels(this)
        // Morning alerts (bills coming up, dues today, the monthly summary) are on unless turned off.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val p = settings.current()
            com.saraltechies.saralmoneytracker.util.Alerts.schedule(this@SaralMoneyTrackerApp, p.alertsOn || p.monthlySummaryOn)
        }
    }
}
