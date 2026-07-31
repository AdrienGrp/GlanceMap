package com.glancemap.glancemapwearos.data.repository

import java.io.File
import java.io.InputStream

interface MapRepository {
    suspend fun listMapFiles(): List<File>

    suspend fun saveMapFileAtomic(
        fileName: String,
        inputStream: InputStream,
        onProgress: (bytesCopied: Long) -> Unit,
        expectedSize: Long? = null,
        resumeOffset: Long = 0L,
        computeSha256: Boolean = true,
    ): String?

    suspend fun deleteMapFile(path: String): Boolean

    suspend fun fileExists(fileName: String): Boolean

    suspend fun renameMapFile(
        path: String,
        newName: String,
    ): File
}
