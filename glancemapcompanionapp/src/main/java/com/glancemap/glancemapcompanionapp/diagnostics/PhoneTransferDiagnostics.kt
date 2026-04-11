package com.glancemap.glancemapcompanionapp.diagnostics

object PhoneTransferDiagnostics {
    private const val TAG = "PhoneTransfer"

    fun log(component: String, message: String) {
        PhoneDebugCapture.log(TAG, "[$component] $message")
    }

    fun warn(component: String, message: String) {
        log(component, "WARN $message")
    }

    fun error(component: String, message: String, throwable: Throwable? = null) {
        val detail = throwable?.message?.takeIf { it.isNotBlank() }?.let { " error=$it" }.orEmpty()
        log(component, "ERROR $message$detail")
    }
}
