package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.CloudStorageService
import com.example.data.cloud.provider.MediaFireProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaFireProviderTest {

    private lateinit var context: Context
    private lateinit var cloudService: CloudStorageService
    private lateinit var mediaFireProvider: MediaFireProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cloudService = CloudStorageService.getInstance(context)
        mediaFireProvider = cloudService.mediaFireProvider
    }

    @Test
    fun testMediaFireFullConnectionLifecycle() = runBlocking {
        // 1. Authenticate / Connect
        val authResult = mediaFireProvider.authenticate(
            email = "user@mediafire.com",
            serverUrl = "https://www.mediafire.com/api",
            tokenOrPass = "test_token_123",
            isTemporary = false
        )

        assertTrue("Authentication should succeed", authResult.success)
        assertTrue("MediaFire should be connected", mediaFireProvider.isConnected)
        assertEquals("user@mediafire.com", mediaFireProvider.accountEmail)

        // 2. List Files at Root
        val rootFiles = mediaFireProvider.listFiles("/cloud/mediafire")
        assertNotNull("Root files should not be null", rootFiles)
        assertTrue("Root should contain initial files or workspace", rootFiles.isNotEmpty())
        val welcomeFile = rootFiles.find { it.name == "Bem-vindo ao MediaFire.txt" }
        assertNotNull("Welcome file should exist in root", welcomeFile)

        // 3. Create Folder
        val created = mediaFireProvider.createFolder("/cloud/mediafire", "Documentos")
        assertTrue("Folder creation should succeed", created)

        val updatedRoot = mediaFireProvider.listFiles("/cloud/mediafire")
        val docsFolder = updatedRoot.find { it.name == "Documentos" }
        assertNotNull("Documentos folder should appear in root", docsFolder)
        assertTrue("Documentos should be a directory", docsFolder!!.isDirectory)

        // 4. Upload File to Subfolder
        val localFile = File(context.cacheDir, "test_upload.txt").apply { writeText("Conteúdo de teste para upload no MediaFire") }
        val uploaded = mediaFireProvider.uploadFile(localFile, "/cloud/mediafire/Documentos")
        assertTrue("Upload file should succeed", uploaded)

        val subFiles = mediaFireProvider.listFiles("/cloud/mediafire/Documentos")
        val uploadedRemote = subFiles.find { it.name == "test_upload.txt" }
        assertNotNull("Uploaded file should exist in Documentos subfolder", uploadedRemote)

        // 5. Download File
        val downloadDest = File(context.cacheDir, "downloaded_test.txt")
        var progressReported = false
        val downloaded = mediaFireProvider.downloadFile(
            remoteFilePath = "/cloud/mediafire/Documentos/test_upload.txt",
            destinationFile = downloadDest,
            onProgress = { p -> progressReported = p > 0f }
        )
        assertTrue("Download should succeed", downloaded)
        assertTrue("Downloaded file should exist locally", downloadDest.exists())
        assertEquals("Conteúdo de teste para upload no MediaFire", downloadDest.readText())

        // 6. Rename File
        val renamed = mediaFireProvider.renameFile("/cloud/mediafire/Documentos/test_upload.txt", "novo_nome.txt")
        assertTrue("Rename should succeed", renamed)

        val subFilesAfterRename = mediaFireProvider.listFiles("/cloud/mediafire/Documentos")
        assertNull("Old filename should not exist", subFilesAfterRename.find { it.name == "test_upload.txt" })
        assertNotNull("New filename should exist", subFilesAfterRename.find { it.name == "novo_nome.txt" })

        // 7. Copy/Move File
        val moved = mediaFireProvider.copyOrMoveFile(
            sourceRemotePath = "/cloud/mediafire/Documentos/novo_nome.txt",
            destRemotePath = "/cloud/mediafire/novo_nome_raiz.txt",
            isMove = true
        )
        assertTrue("Move should succeed", moved)

        val rootAfterMove = mediaFireProvider.listFiles("/cloud/mediafire")
        assertNotNull("Moved file should exist at root", rootAfterMove.find { it.name == "novo_nome_raiz.txt" })

        // 8. Delete File
        val deleted = mediaFireProvider.deleteFile("/cloud/mediafire/novo_nome_raiz.txt")
        assertTrue("Delete should succeed", deleted)

        val rootAfterDelete = mediaFireProvider.listFiles("/cloud/mediafire")
        assertNull("Deleted file should no longer exist", rootAfterDelete.find { it.name == "novo_nome_raiz.txt" })

        // 9. Disconnect
        mediaFireProvider.disconnect()
        assertFalse("MediaFire should no longer be connected after disconnect", mediaFireProvider.isConnected)
    }
}
