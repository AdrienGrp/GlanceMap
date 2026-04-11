package com.glancemap.glancemapwearos.core.service.diagnostics

internal object TransferDiagnostics {
    private const val TAG = "TransferFlow"

    fun log(component: String, message: String) {
        DebugTelemetry.log(TAG, "[$component] $message")
    }

    fun warn(component: String, message: String) {
        log(component, "WARN $message")
    }

    fun error(component: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.message?.takeIf { it.isNotBlank() }?.let { " error=$it" }.orEmpty()
        log(component, "ERROR $message$detail")
    }
}
