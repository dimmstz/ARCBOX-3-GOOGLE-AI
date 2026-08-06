package com.example.data.models

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, APK, ARCHIVE, CODE, OTHER;

    @Composable
    fun getCategoryColor(): Color = when (this) {
        FOLDER -> MaterialTheme.colorScheme.primary
        IMAGE -> FileColorImage
        VIDEO -> FileColorVideo
        AUDIO -> FileColorAudio
        DOCUMENT -> FileColorDocument
        APK -> FileColorApk
        ARCHIVE -> FileColorArchive
        CODE -> FileColorCode
        OTHER -> FileColorTrash
    }
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val safUriString: String? = null,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val fileType: FileType = FileType.OTHER,
    val extension: String = "",
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val childCount: Int = 0,
    val mimeType: String = "*/*",
    val packageName: String? = null,
    val appCategory: String? = null, // "USER", "SYSTEM", "APK_FILES"
    val versionName: String? = null
)

data class StorageVolume(
    val id: String,
    val name: String,
    val path: String,
    val isSaf: Boolean = false,
    val safUriString: String? = null,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val typeKey: String = "INTERNAL" // INTERNAL, SDCARD, USB, DOWNLOADS, DOCUMENTS, PICTURES
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedRatio: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class TabItem(
    val id: String,
    val title: String,
    val currentPath: String,
    val safUriString: String? = null,
    val history: List<String> = listOf(currentPath),
    val historyIndex: Int = 0
)

enum class SortOption { NAME, DATE, SIZE, TYPE }
enum class SortOrder { ASCENDING, DESCENDING }
enum class ViewMode { GRID, LIST }
enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class ClipboardMode { COPY, CUT }

data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val appName: String,
    val permissions: List<String>,
    val abis: List<String>,
    val apkFilePath: String
)

data class ZipEntryItem(
    val name: String,
    val fullPath: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val time: Long
)

data class StorageCategoryStats(
    val fileType: FileType,
    val name: String,
    val bytes: Long,
    val fileCount: Int
)

data class DuplicateGroup(
    val key: String,
    val size: Long,
    val files: List<FileItem>
)

data class CategoryFolderInfo(
    val folderName: String,
    val folderPath: String,
    val fileCount: Int,
    val totalSize: Long,
    val files: List<FileItem>
)

data class CategoryDetailInfo(
    val fileType: FileType,
    val categoryName: String,
    val totalSize: Long,
    val totalFiles: Int,
    val folders: List<CategoryFolderInfo>
)
