package com.glancemap.glancemapwearos.data.repository

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream

interface GpxRepository {
    suspend fun listGpxFiles(): List<File>
    fun getActiveGpxFiles(): Flow<Set<String>>
    suspend fun setActiveGpxFiles(paths: Set<String>)
    suspend fun deleteGpxFile(path: String)

    suspend fun saveGpxFileAtomic(
        fileName: String,
        inputStream: InputStream,
        onProgress: (bytesCopied: Long) -> Unit,
        expectedSize: Long? = null,
        resumeOffset: Long = 0L
    ): String?

    suspend fun fileExists(fileName: String): Boolean
}
