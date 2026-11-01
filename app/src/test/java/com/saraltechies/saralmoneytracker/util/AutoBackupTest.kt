package com.saraltechies.saralmoneytracker.util

import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.BackupFrequency
import com.saraltechies.saralmoneytracker.data.ThemeMode
import com.saraltechies.saralmoneytracker.model.Currencies
import com.saraltechies.saralmoneytracker.model.PeriodPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val now = 1_000 * day

    private fun prefs(frequency: BackupFrequency, last: Long, folder: String? = "content://tree/backups") = AppPrefs(
        currency = Currencies.default, period = PeriodPrefs(), theme = ThemeMode.SYSTEM, appLock = false, hideInRecents = false,
        reminderOn = false, reminderMinute = 0, welcomeDismissed = false,
        backupFolder = folder, backupFrequency = frequency, lastBackupAt = last,
    )

    @Test
    fun weeklyIsDueAfterAWeek() {
        assertFalse(AutoBackup.isDue(prefs(BackupFrequency.WEEKLY, now - 3 * day), now))
        assertTrue(AutoBackup.isDue(prefs(BackupFrequency.WEEKLY, now - 7 * day), now))
        // An alarm that fires a little early still counts.
        assertTrue(AutoBackup.isDue(prefs(BackupFrequency.WEEKLY, now - 7 * day + 30 * 60 * 1000), now))
    }

    @Test
    fun dailyAndFirstBackup() {
        assertTrue(AutoBackup.isDue(prefs(BackupFrequency.DAILY, now - day), now))
        assertFalse(AutoBackup.isDue(prefs(BackupFrequency.DAILY, now - 10 * hour), now))
        assertTrue(AutoBackup.isDue(prefs(BackupFrequency.WEEKLY, 0), now))
    }

    @Test
    fun offOrNoFolderNeverRuns() {
        assertFalse(AutoBackup.isDue(prefs(BackupFrequency.OFF, 0), now))
        assertFalse(AutoBackup.isDue(prefs(BackupFrequency.DAILY, 0, folder = null), now))
    }
}
