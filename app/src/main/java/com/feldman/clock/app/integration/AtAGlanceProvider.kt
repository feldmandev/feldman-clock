package com.feldman.clock.app.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.feldman.clock.core.data.DataModel
import java.util.concurrent.FutureTask

/** Read-only, signature-protected state shared with Feldman Launcher. */
class AtAGlanceProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(uri == CONTENT_URI) { "Unsupported URI: $uri" }
        val cursor = MatrixCursor(COLUMNS)
        readTimers().forEach { timer ->
            cursor.addRow(arrayOf<Any?>(timer.id, timer.label, timer.state, timer.remainingMillis))
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, CONTENT_URI) }
        return cursor
    }

    private fun readTimers(): List<TimerSnapshot> {
        if (Looper.myLooper() == Looper.getMainLooper()) return snapshotTimers()
        val task = FutureTask(::snapshotTimers)
        Handler(Looper.getMainLooper()).post(task)
        return task.get()
    }

    private fun snapshotTimers(): List<TimerSnapshot> {
        val dataModel = DataModel.dataModel
        return dataModel.timers
            .asSequence()
            .filter {
                it.isRunning || (it.isPaused && dataModel.timerModel.isPausedLiveUpdateActive(it.id))
            }
            .map {
                TimerSnapshot(
                    id = it.id,
                    label = it.label,
                    state = if (it.isRunning) STATE_RUNNING else STATE_PAUSED,
                    remainingMillis = it.remainingTime.coerceAtLeast(0L),
                )
            }
            .sortedWith(compareBy<TimerSnapshot> { it.state != STATE_RUNNING }.thenBy { it.remainingMillis })
            .toList()
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.com.feldman.clock.timer"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = unsupported()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = unsupported<Int>()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = unsupported<Int>()

    private fun <T> unsupported(): T = throw UnsupportedOperationException("Read-only provider")

    private data class TimerSnapshot(
        val id: Int,
        val label: String?,
        val state: String,
        val remainingMillis: Long,
    )

    companion object {
        private const val AUTHORITY = "com.feldman.clock.at-a-glance"
        @JvmField val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/timers")
        private val COLUMNS = arrayOf("_id", "label", "state", "remaining_millis")
        private const val STATE_RUNNING = "running"
        private const val STATE_PAUSED = "paused"

        @JvmStatic
        fun notifyChanged(context: android.content.Context) {
            context.contentResolver.notifyChange(CONTENT_URI, null)
        }
    }
}
