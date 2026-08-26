package com.example.util

import com.example.data.models.FileItem
import com.example.data.models.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSuccess: Boolean = exitCode == 0
)

data class RootStatus(
    val isAvailable: Boolean,
    val isGranted: Boolean,
    val suPath: String?,
    val suVersion: String?,
    val details: String
)

object RootHelper {

    private var cachedIsAvailable: Boolean? = null
    private var cachedIsGranted: Boolean? = null
    private var cachedSuPath: String? = null
    private var cachedSuVersion: String? = null

    private val KNOWN_SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/sbin/su",
        "/system/xbin/daemonsu",
        "/apex/com.android.runtime/bin/su",
        "/data/adb/magisk/busybox",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/usr/bin/su",
        "/bin/su"
    )

    /**
     * Checks if the SU binary or root capability exists on the device.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh && cachedIsAvailable != null) {
            return cachedIsAvailable ?: false
        }

        // 1. Check known file paths
        for (path in KNOWN_SU_PATHS) {
            try {
                val f = File(path)
                if (f.exists()) {
                    cachedSuPath = path
                    cachedIsAvailable = true
                    return true
                }
            } catch (_: Exception) {}
        }

        // 2. Check PATH environment variable
        try {
            val envPath = System.getenv("PATH") ?: ""
            for (dir in envPath.split(":")) {
                val suFile = File(dir, "su")
                if (suFile.exists()) {
                    cachedSuPath = suFile.absolutePath
                    cachedIsAvailable = true
                    return true
                }
            }
        } catch (_: Exception) {}

        // 3. Test execution of which su
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            if (process.exitValue() == 0 && !line.isNullOrBlank()) {
                cachedSuPath = line.trim()
                cachedIsAvailable = true
                return true
            }
        } catch (_: Exception) {}

        // 4. Test quick su version check
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-v"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val version = reader.readLine()
            process.waitFor()
            if (process.exitValue() == 0 && !version.isNullOrBlank()) {
                cachedSuVersion = version.trim()
                cachedIsAvailable = true
                return true
            }
        } catch (_: Exception) {}

        cachedIsAvailable = false
        return false
    }

    /**
     * Checks or requests Superuser (root) permission by running a test command with su.
     */
    suspend fun requestRootPermission(): Boolean = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            cachedIsGranted = false
            return@withContext false
        }
        try {
            val result = executeCommand("id")
            val granted = result.isSuccess && (result.stdout.contains("uid=0") || result.stdout.contains("root"))
            cachedIsGranted = granted
            if (granted) {
                cachedIsAvailable = true
            }
            granted
        } catch (_: Exception) {
            cachedIsGranted = false
            false
        }
    }

    suspend fun getRootStatus(forceRefresh: Boolean = false): RootStatus = withContext(Dispatchers.IO) {
        val available = isRootAvailable(forceRefresh)
        val granted = if (available) requestRootPermission() else false

        val details = when {
            granted -> "Acesso Root (Superusuário) ativo e concedido com privilégios de sistema (UID 0)."
            available -> "Binário SU encontrado (${cachedSuPath ?: "disponível"}), permissão de superusuário pendente de confirmação."
            else -> "Nenhum binário de superusuário (su) detectado. Modo padrão de armazenamento ativado."
        }

        RootStatus(
            isAvailable = available,
            isGranted = granted,
            suPath = cachedSuPath,
            suVersion = cachedSuVersion,
            details = details
        )
    }

    /**
     * Executes a shell command with superuser privileges.
     */
    suspend fun executeCommand(command: String): RootCommandResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            RootCommandResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            RootCommandResult(-1, "", e.localizedMessage ?: "Erro ao executar comando root")
        }
    }

    /**
     * Lists directory contents with root privileges, parsing metadata (permissions, owner, size, timestamp).
     */
    suspend fun listDirectory(
        dirPath: String,
        favoritePaths: Set<String> = emptySet()
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val cleanPath = if (dirPath.isEmpty()) "/" else dirPath.removeSuffix("/")
        val targetPath = if (cleanPath.isEmpty()) "/" else cleanPath

        // Try standard list first if directory is readable
        val directFile = File(targetPath)
        if (directFile.exists() && directFile.canRead()) {
            val directFiles = directFile.listFiles()
            if (directFiles != null) {
                val items = mutableListOf<FileItem>()
                for (file in directFiles) {
                    val isDir = file.isDirectory
                    val name = file.name
                    val ext = file.extension.lowercase()
                    val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext)
                    val size = if (isDir) 0L else file.length()
                    val count = if (isDir) (file.list()?.size ?: 0) else 0

                    items.add(
                        FileItem(
                            id = file.absolutePath,
                            name = name,
                            path = file.absolutePath,
                            size = size,
                            lastModified = file.lastModified(),
                            isDirectory = isDir,
                            fileType = type,
                            extension = ext,
                            isFavorite = favoritePaths.contains(file.absolutePath),
                            childCount = count,
                            mimeType = if (isDir) "resource/folder" else "*/*"
                        )
                    )
                }
                return@withContext items
            }
        }

        // Use root 'ls -la' parsing
        val result = executeCommand("ls -la '$targetPath'")
        if (!result.isSuccess || result.stdout.isBlank()) {
            return@withContext emptyList()
        }

        val items = mutableListOf<FileItem>()
        val lines = result.stdout.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total ")) continue

            val parsed = parseLsLine(trimmed, targetPath, favoritePaths)
            if (parsed != null) {
                items.add(parsed)
            }
        }

        items
    }

    private fun parseLsLine(
        line: String,
        parentPath: String,
        favoritePaths: Set<String>
    ): FileItem? {
        try {
            // Examples:
            // drwxr-xr-x  25 root root  4096 2026-08-14 12:00 system
            // -rw-r--r--   1 root root  1234 2026-08-14 12:00 build.prop
            // lrwxrwxrwx   1 root root    21 2026-08-14 12:00 sdcard -> /storage/self/primary
            // drwxr-xr-x 2 root root 4096 Aug 14 12:00 system
            val tokens = line.split("\\s+".toRegex())
            if (tokens.size < 6) return null

            val permissions = tokens[0]
            val isDirectory = permissions.startsWith("d")
            val isSymlink = permissions.startsWith("l")

            // Find name index (everything after date/time)
            // Look for date pattern like YYYY-MM-DD or Month Day
            var nameStartIndex = -1
            for (i in 4 until tokens.size) {
                val token = tokens[i]
                if (token.contains(":") && i < tokens.size - 1) {
                    nameStartIndex = i + 1
                    break
                }
            }

            if (nameStartIndex == -1 || nameStartIndex >= tokens.size) {
                nameStartIndex = tokens.size - 1
            }

            val rawName = tokens.subList(nameStartIndex, tokens.size).joinToString(" ")
            val name = if (isSymlink && rawName.contains(" -> ")) {
                rawName.substringBefore(" -> ").trim()
            } else {
                rawName.trim()
            }

            if (name == "." || name == "..") return null

            val size = tokens.getOrNull(3)?.toLongOrNull()
                ?: tokens.getOrNull(4)?.toLongOrNull()
                ?: 0L

            val itemPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
            val ext = name.substringAfterLast('.', "").lowercase()
            val effectiveIsDir = isDirectory || (isSymlink && (name.contains("sdcard") || name.contains("storage") || name.contains("data") || name.contains("system") || name.contains("etc") || name.contains("mnt")))
            val fileType = if (effectiveIsDir) FileType.FOLDER else getFileTypeFromExtension(ext)

            return FileItem(
                id = itemPath,
                name = name,
                path = itemPath,
                size = if (effectiveIsDir) 0L else size,
                lastModified = System.currentTimeMillis(),
                isDirectory = effectiveIsDir,
                fileType = fileType,
                extension = ext,
                isFavorite = favoritePaths.contains(itemPath),
                childCount = 0,
                mimeType = if (effectiveIsDir) "resource/folder" else "*/*"
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun getFileTypeFromExtension(ext: String): FileType {
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic" -> FileType.IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts" -> FileType.VIDEO
            "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "wma" -> FileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "log", "prop", "conf", "xml", "json", "rc", "sh", "cfg" -> FileType.DOCUMENT
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "zst" -> FileType.ARCHIVE
            "apk", "apks", "xapk", "apkm" -> FileType.APK
            else -> FileType.OTHER
        }
    }

    suspend fun readText(filePath: String): String = withContext(Dispatchers.IO) {
        val result = executeCommand("cat '$filePath'")
        if (result.isSuccess) {
            result.stdout
        } else {
            "Erro ao ler arquivo com privilégios root: ${result.stderr}"
        }
    }

    suspend fun writeText(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Remount rw if system file
            if (filePath.startsWith("/system") || filePath.startsWith("/vendor") || filePath.startsWith("/etc")) {
                executeCommand("mount -o remount,rw / && mount -o remount,rw /system")
            }
            val process = ProcessBuilder("su", "-c", "cat > '$filePath'").start()
            process.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("rm -rf '$filePath'")
        result.isSuccess
    }

    suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val target = if (parentPath == "/") "/$name" else "$parentPath/$name"
        val result = executeCommand("mkdir -p '$target'")
        result.isSuccess
    }

    suspend fun createFile(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val target = if (parentPath == "/") "/$name" else "$parentPath/$name"
        val result = executeCommand("touch '$target'")
        result.isSuccess
    }

    suspend fun rename(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val parent = oldPath.substringBeforeLast('/', "")
        val target = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
        val result = executeCommand("mv '$oldPath' '$target'")
        result.isSuccess
    }

    suspend fun copy(source: String, targetDir: String, customFileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val name = customFileName ?: source.substringAfterLast('/')
        val dest = if (targetDir == "/") "/$name" else "$targetDir/$name"
        val result = executeCommand("cp -r '$source' '$dest'")
        result.isSuccess
    }

    suspend fun move(source: String, targetDir: String, customFileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val name = customFileName ?: source.substringAfterLast('/')
        val dest = if (targetDir == "/") "/$name" else "$targetDir/$name"
        val result = executeCommand("mv '$source' '$dest'")
        result.isSuccess
    }

    suspend fun changePermissions(path: String, octalMode: String): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("chmod $octalMode '$path'")
        result.isSuccess
    }

    suspend fun remountSystemRw(): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("mount -o remount,rw / && mount -o remount,rw /system")
        result.isSuccess
    }
}
