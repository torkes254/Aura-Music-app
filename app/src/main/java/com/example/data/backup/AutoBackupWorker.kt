package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = AutoBackupPreferences.getInstance(applicationContext)
        val state = prefs.state.value

        if (!state.isEnabled || state.folderUri.isNullOrBlank()) {
            Log.i(TAG, "Auto backup is disabled or folder is not set.")
            return@withContext Result.success()
        }

        val treeUri = Uri.parse(state.folderUri)
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetFileName = "AuraMusic_AutoBackup_$timeStamp.zip"

            val newDocUri = DocumentsContract.createDocument(
                applicationContext.contentResolver,
                parentDocUri,
                "application/zip",
                targetFileName
            ) ?: return@withContext Result.failure()

            val musicRepo = MusicRepository.getInstance(applicationContext)
            val allSongs = musicRepo.queryAllSongs()

            val backupManager = BackupManager.getInstance(applicationContext)
            applicationContext.contentResolver.openOutputStream(newDocUri)?.use { out ->
                backupManager.exportBackup(
                    outputStream = out,
                    includeMedia = state.includeMedia,
                    allDiscoveredSongs = allSongs
                )
            } ?: return@withContext Result.failure()

            prefs.setLastBackupTime(System.currentTimeMillis())
            Log.i(TAG, "Auto backup successfully created: $targetFileName")

            // Enforce retention: keep only the last 3 backups
            enforceRetention(treeUri, docId)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating auto backup", e)
            Result.failure()
        }
    }

    private fun enforceRetention(treeUri: Uri, parentDocId: String) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            data class BackupDoc(val id: String, val name: String, val lastModified: Long)
            val autoBackups = mutableListOf<BackupDoc>()

            applicationContext.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val lastModified = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                    if (id != null && name != null && name.startsWith("AuraMusic_AutoBackup_") && name.endsWith(".zip")) {
                        autoBackups.add(BackupDoc(id, name, lastModified))
                    }
                }
            }

            // Sort newest first
            autoBackups.sortByDescending { it.lastModified }

            // Keep the last 3, delete older ones
            if (autoBackups.size > 3) {
                for (i in 3 until autoBackups.size) {
                    val docToDelete = autoBackups[i]
                    val deleteDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docToDelete.id)
                    DocumentsContract.deleteDocument(applicationContext.contentResolver, deleteDocUri)
                    Log.i(TAG, "Deleted old auto backup: ${docToDelete.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enforcing auto backup retention", e)
        }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val WORK_NAME = "aura_music_auto_backup_periodic"
        private const val ONE_TIME_WORK_NAME = "aura_music_auto_backup_onetime"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.i(TAG, "Scheduled periodic auto backup work (every 7 days)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled periodic auto backup work")
        }

        fun runOnceNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
