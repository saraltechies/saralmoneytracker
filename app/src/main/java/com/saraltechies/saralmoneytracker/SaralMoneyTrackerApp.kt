package com.saraltechies.saralmoneytracker

import android.app.Application
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.util.Reminders
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
    }
}
