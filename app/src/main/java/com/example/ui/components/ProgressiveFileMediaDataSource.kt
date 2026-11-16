package com.example.ui.components

import android.media.MediaDataSource
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Job

@RequiresApi(Build.VERSION_CODES.M)
class ProgressiveFileMediaDataSource(
    private val file: File,
    private val expectedSize: Long,
    private val downloadJob: Job?
) : MediaDataSource() {
    
    private var randomAccessFile: RandomAccessFile? = null
    
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= expectedSize) return -1
        
        var availableBytes = file.length() - position
        // Wait if data is not yet available and still downloading
        while (availableBytes < size && downloadJob?.isActive == true && availableBytes < expectedSize - position) {
            Thread.sleep(50)
            availableBytes = file.length() - position
        }
        
        if (availableBytes <= 0 && (!file.exists() || position >= file.length())) return -1
        
        val bytesToRead = minOf(size.toLong(), if (availableBytes > 0) availableBytes else expectedSize - position).toInt()
        
        if (randomAccessFile == null) {
            randomAccessFile = RandomAccessFile(file, "r")
        }
        randomAccessFile?.seek(position)
        return randomAccessFile?.read(buffer, offset, bytesToRead) ?: -1
    }

    override fun getSize(): Long = expectedSize

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
    }
}
