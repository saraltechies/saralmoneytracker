package com.saraltechies.saralmoneytracker

import android.app.Application
import com.saraltechies.saralmoneytracker.data.MoneyRepository
import com.saraltechies.saralmoneytracker.data.SettingsRepository
import com.saraltechies.saralmoneytracker.util.Reminders

/** Holds the app-wide singletons; small enough that a DI framework isn't worth it. */
class SaralMoneyTrackerApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { MoneyRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
    }
}
