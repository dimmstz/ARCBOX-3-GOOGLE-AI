package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.FileType
import com.example.data.repository.FileRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScopedCategoryAndSearchTest {

    private lateinit var context: Context
    private lateinit var repository: FileRepository
    private lateinit var testRoot: File
    private lateinit var downloadDir: File
    private lateinit var dcimDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FileRepository(context)
        testRoot = File(context.cacheDir, "scoped_search_test_root").apply {
            deleteRecursively()
            mkdirs()
        }
        downloadDir = File(testRoot, "Download").apply { mkdirs() }
        val downloadSubDir = File(downloadDir, "Telegram").apply { mkdirs() }
        dcimDir = File(testRoot, "DCIM").apply { mkdirs() }

        File(downloadDir, "foto_download.jpg").writeText("img1")
        File(downloadSubDir, "sub_download.png").writeText("img2")
        File(downloadDir, "clip_download.mp4").writeText("vid1")
        File(downloadDir, ".oculta.jpg").writeText("hidden")

        File(dcimDir, "foto_dcim.jpg").writeText("img3")
        File(dcimDir, "clip_dcim.mp4").writeText("vid2")
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun `category filter searches only inside currently open folder`() = runBlocking {
        val downloadImages = repository.listFiles(
            directoryPath = downloadDir.absolutePath,
            filterCategory = FileType.IMAGE,
            showHiddenFiles = false
        )
        val names = downloadImages.map { it.name }.toSet()
        assertEquals(setOf("foto_download.jpg", "sub_download.png"), names)
        assertFalse("Must not leak DCIM images into Download filter", names.contains("foto_dcim.jpg"))
        assertFalse("Must hide dot files when showHiddenFiles is false", names.contains(".oculta.jpg"))

        val downloadVideos = repository.listFiles(
            directoryPath = downloadDir.absolutePath,
            filterCategory = FileType.VIDEO
        )
        assertEquals(listOf("clip_download.mp4"), downloadVideos.map { it.name })
    }

    @Test
    fun `text search scopes results strictly to currently open folder`() = runBlocking {
        val results = repository.listFiles(
            directoryPath = downloadDir.absolutePath,
            searchQuery = "foto",
            showHiddenFiles = false
        )
        val names = results.map { it.name }
        assertTrue(names.contains("foto_download.jpg"))
        assertFalse("Must not return matches outside open directory", names.contains("foto_dcim.jpg"))
    }
}
