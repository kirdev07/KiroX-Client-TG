package com.kirox.app

import androidx.core.content.edit
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local crash journal for testing builds: Kotlin/Java crashes are written with their stack trace,
 * and on the next start Android's record of native crashes, ANRs and system kills is added
 * (API 30+). Nothing leaves the device unless the user shares the journal from Settings.
 * Only stack traces and technical reasons are kept — no messages, names or phone numbers.
 */
object CrashLog {
    private const val MAX_FILES = 10
    private const val PREFS = "kirox_crashlog"
    private const val KEY_LAST_EXIT = "last_exit_ts"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, "Падение (${thread.name})", stackTrace(error)) }
            previous?.uncaughtException(thread, error)
        }
        // Reading exit reasons touches disk: off the main thread.
        Thread { runCatching { recordExitReasons(app) } }.start()
    }

    /** A caught error that the app survived, written like a crash so it can be reported. */
    fun record(context: Context, title: String, error: Throwable) {
        runCatching { write(context.applicationContext, title, stackTrace(error)) }
    }

    /** Native crashes, ANRs and low-memory kills since the last start, as Android reports them. */
    private fun recordExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val reported = setOf(
            ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_EXIT, 0)
        val reasons = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 10).orEmpty()
            .filter { it.timestamp > last }
        reasons.filter { it.reason in reported }.forEach { exit ->
            val title = when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Падение в нативном коде"
                ApplicationExitInfo.REASON_ANR -> "Приложение не отвечало (ANR)"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "Закрыто системой: мало памяти"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Закрыто системой: слишком много ресурсов"
                else -> "Падение"
            }
            // For ANRs Android keeps the thread dump; the beginning is enough to see where it hung.
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR) runCatching {
                exit.traceInputStream?.bufferedReader()?.use { it.readText().take(12_000) }
            }.getOrNull() else null
            write(context, title, listOfNotNull("Описание: ${exit.description.orEmpty()}", "Время: ${format(exit.timestamp)}", trace).joinToString("\n"), exit.timestamp)
        }
        reasons.maxOfOrNull { it.timestamp }?.let { prefs.edit { putLong(KEY_LAST_EXIT, it) } }
    }

    private fun dir(context: Context) = File(context.filesDir, "crashes").apply { mkdirs() }

    private fun write(context: Context, title: String, body: String, time: Long = System.currentTimeMillis()) {
        val header = "$title\nKiroX ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n${format(time)}\n"
        File(dir(context), "crash_$time.txt").writeText(header + "\n" + body)
        dir(context).listFiles()?.sortedByDescending { it.name }?.drop(MAX_FILES)?.forEach { it.delete() }
    }

    fun count(context: Context): Int = dir(context).listFiles()?.size ?: 0

    /** All entries, newest first, joined for sharing. */
    fun read(context: Context): String = dir(context).listFiles().orEmpty().sortedByDescending { it.name }
        .joinToString("\n\n──────────\n\n") { runCatching { it.readText() }.getOrDefault("") }

    fun clear(context: Context) { dir(context).listFiles()?.forEach { it.delete() } }

    /** Share sheet with the journal as text (Telegram, mail…). */
    fun share(context: Context) {
        val text = read(context).ifBlank { return }
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Журнал ошибок KiroX ${BuildConfig.VERSION_NAME}")
            .putExtra(Intent.EXTRA_TEXT, text.take(90_000))
        context.startActivity(Intent.createChooser(intent, "Отправить журнал").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Exception types and stack frames only. Messages are left out: they may carry message text,
     * file paths or ids, and the journal can be shared.
     */
    private fun stackTrace(error: Throwable): String = buildString {
        var current: Throwable? = error
        var depth = 0
        val seen = HashSet<Throwable>()
        while (current != null && depth < 8 && seen.add(current)) {
            append(if (depth == 0) "" else "Caused by: ").append(current.javaClass.name).append('\n')
            current.stackTrace.take(40).forEach { append("    at ").append(it.toString()).append('\n') }
            current = current.cause
            depth++
        }
    }

    private fun format(time: Long) = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(time))
}
