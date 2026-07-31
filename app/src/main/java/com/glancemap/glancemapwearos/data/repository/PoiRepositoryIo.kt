package com.glancemap.glancemapwearos.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun <T> poiIo(block: () -> T): T = withContext(Dispatchers.IO) { block() }
