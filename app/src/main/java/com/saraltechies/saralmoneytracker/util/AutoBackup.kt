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
 * The picker often can't make a new folder (Google Drive hides "New folder" on many phones), so people pick
 * "My Drive" or the phone's main storage. Unless the picked folder is clearly meant for backups, the app makes its
 * own [FOLDER] inside it and saves there.
 *
 * It runs from an inexact daily alarm, and also when the app opens if a backup is overdue, since some phones hold
 * alarms back.
 */
object AutoBackup {

    const val KEEP = 7
    const val PREFIX = "Saral Money Tracker backup "
    const val FOLDER = "Saral Money Tracker backups"
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
        val dir = target(context, tree, create = true).id
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, dir)
        val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")) + ".xlsx"
        val doc = DocumentsContract.createDocument(resolver, parent, MIME, name) ?: error("couldn't create the file")
        val app = context.applicationContext as SaralMoneyTrackerApp
        resolver.openOutputStream(doc, "w")?.use { out ->
            BackupFormat.writeXlsx(app.repository.exportData(), out, LocalDate.now(), BuildConfig.VERSION_NAME)
        } ?: error("couldn't open the file")
        prune(context, tree, dir)
    }

    private data class Child(val id: String, val name: String, val isDir: Boolean)

    /** Where the backups go: the picked folder, or the app's own [FOLDER] inside it. */
    private data class Target(val id: String, val path: String)

    private fun children(context: Context, tree: Uri, dir: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return context.contentResolver.query(uri, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    add(Child(c.getString(0), name, c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR))
                }
            }
        }.orEmpty()
    }

    private fun displayName(context: Context, tree: Uri, id: String): String? =
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, id), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** True if [name] is an automatic backup file. */
    fun isBackupFile(name: String) = name.startsWith(PREFIX) && name.endsWith(".xlsx")

    /**
     * Whether to save straight into the picked folder: when it's already meant for backups (its name says so,
     * it is our own [FOLDER], or our backups are already in it). Otherwise, like "My Drive" or the phone's main
     * storage, the app keeps its files in its own folder.
     */
    fun usePickedFolder(name: String?, hasOurBackups: Boolean): Boolean {
        if (hasOurBackups) return true
        val n = name?.lowercase() ?: return false
        return "backup" in n || "saral" in n || "money" in n
    }

    /** Finds (and with [create], makes) the folder the backups go in. Falls back to the picked folder. */
    private fun target(context: Context, tree: Uri, create: Boolean): Target {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val rootName = displayName(context, tree, root)
        val inRoot = children(context, tree, root)
        val picked = Target(root, rootName.orEmpty())
        if (usePickedFolder(rootName, inRoot.any { !it.isDir && isBackupFile(it.name) })) return picked
        val path = listOfNotNull(rootName, FOLDER).joinToString(" › ")
        inRoot.firstOrNull { it.isDir && it.name == FOLDER }?.let { return Target(it.id, path) }
        if (!create) return Target(root, path) // Made on the first backup.
        val made = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, root), DocumentsContract.Document.MIME_TYPE_DIR, FOLDER,
            )
        }.getOrNull() ?: return picked // This provider can't make folders: save in the picked one.
        return Target(DocumentsContract.getDocumentId(made), path)
    }

    /** Deletes older automatic backups beyond the newest [KEEP]. Files you put there yourself are left alone. */
    private fun prune(context: Context, tree: Uri, dir: String) {
        val resolver = context.contentResolver
        // Names carry the date and time, so sorting by name sorts by age.
        children(context, tree, dir).filter { !it.isDir && isBackupFile(it.name) }.sortedByDescending { it.name }.drop(KEEP).forEach {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.id)) }
        }
    }

    /** Where backups are saved, like "My Drive › Saral Money Tracker backups", to show in Settings. */
    fun folderName(context: Context, folder: String?): String? {
        folder ?: return null
        return runCatching { target(context, Uri.parse(folder), create = false).path.ifEmpty { null } }.getOrNull()
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
