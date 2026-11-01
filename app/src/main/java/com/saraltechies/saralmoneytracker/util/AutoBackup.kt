package com.saraltechies.saralmoneytracker.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.saraltechies.saralmoneytracker.BuildConfig
import com.saraltechies.saralmoneytracker.SaralMoneyTrackerApp
import com.saraltechies.saralmoneytracker.data.AppPrefs
import com.saraltechies.saralmoneytracker.data.BackupFrequency
import com.saraltechies.saralmoneytracker.xlsx.BackupFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Automatic backup: the full Excel backup, saved daily or weekly into a folder the person picked with Android's
 * folder picker (on the phone, or a Google Drive folder, whose app then uploads it). The newest [KEEP] copies are
 * kept. The app itself needs no internet or storage permission for this.
 *
 * It runs from an inexact daily alarm, and also when the app opens if a backup is overdue, since some phones hold
 * alarms back.
 */
object AutoBackup {

    const val KEEP = 7
    const val PREFIX = "Saral Money Tracker backup "
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val REQUEST = 20
    private val lock = Mutex()

    sealed interface Result {
        data object Done : Result
        data object NotDue : Result
        data class Failed(val reason: String) : Result
    }

    fun isDue(prefs: AppPrefs, nowMs: Long = System.currentTimeMillis()): Boolean =
        prefs.backupFrequency != BackupFrequency.OFF && prefs.backupFolder != null &&
            nowMs - prefs.lastBackupAt >= prefs.backupFrequency.days * 24L * 60 * 60 * 1000 - 60 * 60 * 1000

    /** Sets (or clears) the daily check. The check itself decides whether a daily or weekly backup is due. */
    fun schedule(context: Context, on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, BackupReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.cancel(pending)
        if (!on) return
        // Around 2:30 at night, when the phone is usually idle and charging.
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(2, 30)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val trigger = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC, trigger, AlarmManager.INTERVAL_DAY, pending)
    }

    /** Backs up now if due (or always, with [force]), and records the result in the settings. */
    suspend fun run(context: Context, force: Boolean = false): Result = lock.withLock {
        val app = context.applicationContext as SaralMoneyTrackerApp
        val prefs = app.settings.current()
        val folder = prefs.backupFolder ?: return Result.Failed("no folder")
        if (!force && !isDue(prefs)) return Result.NotDue
        val result = runCatching { write(app, Uri.parse(folder)) }
        val error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
        app.settings.recordBackup(System.currentTimeMillis(), error)
        if (error == null) Result.Done else Result.Failed(error)
    }

    private fun write(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")) + ".xlsx"
        val doc = DocumentsContract.createDocument(resolver, parent, MIME, name) ?: error("couldn't create the file")
        val app = context.applicationContext as SaralMoneyTrackerApp
        resolver.openOutputStream(doc, "w")?.use { out ->
            BackupFormat.writeXlsx(app.repository.exportData(), out, LocalDate.now(), BuildConfig.VERSION_NAME)
        } ?: error("couldn't open the file")
        prune(context, tree)
    }

    /** Deletes older automatic backups beyond the newest [KEEP]. Files you put there yourself are left alone. */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = mutableListOf<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".xlsx")) ours += c.getString(0) to name
            }
        }
        // Names carry the date and time, so sorting by name sorts by age.
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    /** The picked folder's name, to show in Settings. */
    fun folderName(context: Context, folder: String?): String? {
        folder ?: return null
        return runCatching {
            val tree = Uri.parse(folder)
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /** When the app opens: catch up on a backup that the alarm missed. */
    fun catchUp(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { run(context) } }
    }
}

/** The daily alarm: backs up if a daily or weekly backup is due. */
class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { AutoBackup.run(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
