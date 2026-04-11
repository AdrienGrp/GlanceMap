package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

internal object CrashDiagnosticsStore {
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "last_crash.txt"
    private val installed = AtomicBoolean(false)
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = crashFile(context)
        if (!file.exists() || !file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { printWriter ->
                throwable.printStackTrace(printWriter)
            }
        }.toString()

        val payload = buildString {
            appendLine("Last Fatal Crash")
            appendLine("Generated: ${timestampFormatter.format(Instant.now())}")
            appendLine()
            appendLine("Thread: ${thread.name}")
            appendLine("Exception: ${throwable::class.java.name}")
            appendLine("Message: ${throwable.message ?: "n/a"}")
            appendLine()
            appendLine("App")
            appendLine("Package: ${context.packageName}")
            appendLine("VersionName: ${packageInfo?.versionName ?: "unknown"}")
            appendLine("VersionCode: ${packageInfo?.longVersionCode ?: -1L}")
            appendLine()
            appendLine("Device")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine()
            appendLine("Stacktrace")
            append(stack)
        }

        file.writeText(payload)
    }

    private fun crashFile(context: Context): File {
        return File(File(context.filesDir, DIR_NAME), FILE_NAME)
    }
}
