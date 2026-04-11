package com.glancemap.glancemapwearos.presentation.features.navigate

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel

class NavigateViewModelFactory(
    private val application: Application,
    private val locationViewModel: LocationViewModel,
    private val compassViewModel: CompassViewModel
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NavigateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NavigateViewModel(application, locationViewModel, compassViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
