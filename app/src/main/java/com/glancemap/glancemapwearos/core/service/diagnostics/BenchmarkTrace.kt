package com.glancemap.glancemapwearos.core.service.diagnostics

import android.os.Trace

internal object BenchmarkTrace {
    private const val MAX_SECTION_NAME_LENGTH = 127

    fun mark(sectionName: String) {
        begin(sectionName)
        end()
    }

    fun begin(sectionName: String) {
        Trace.beginSection(sectionName.safeTraceName())
    }

    fun end() {
        Trace.endSection()
    }

    inline fun <T> section(
        sectionName: String,
        block: () -> T,
    ): T {
        begin(sectionName)
        return try {
            block()
        } finally {
            end()
        }
    }

    private fun String.safeTraceName(): String =
        if (length <= MAX_SECTION_NAME_LENGTH) {
            this
        } else {
            take(MAX_SECTION_NAME_LENGTH)
        }
}
