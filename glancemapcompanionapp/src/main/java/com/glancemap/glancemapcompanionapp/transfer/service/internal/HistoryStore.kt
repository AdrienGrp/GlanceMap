package com.glancemap.glancemapcompanionapp.transfer.service.internal

import android.content.Context
import com.glancemap.glancemapcompanionapp.FileTransferHistoryItem
import com.glancemap.glancemapcompanionapp.FileTransferUiState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class HistoryStore(
    context: Context,
    private val uiState: MutableStateFlow<FileTransferUiState>,
) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("transfer_history", Context.MODE_PRIVATE)

    fun save() {
        val json = gson.toJson(uiState.value.history)
        prefs.edit().putString("history_json", json).apply()
    }

    fun load() {
        val json = prefs.getString("history_json", null) ?: return
        val type = object : TypeToken<List<FileTransferHistoryItem>>() {}.type
        val history = gson.fromJson<List<FileTransferHistoryItem>>(json, type)
        uiState.update { it.copy(history = history) }
    }
}
