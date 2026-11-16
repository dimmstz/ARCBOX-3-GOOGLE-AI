package com.example

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.example.ui.ArcboxApp
import com.example.ui.viewmodel.FileViewModel

class MainActivity : FragmentActivity() {
    private val viewModel: FileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Failed to enable edge to edge", e)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null && downloadsDir.exists()) {
                    val legacyMockPhoto = File(downloadsDir, "mock_photo.jpg")
                    val legacyMockVideo = File(downloadsDir, "mock_video.mp4")
                    if (legacyMockPhoto.exists()) legacyMockPhoto.delete()
                    if (legacyMockVideo.exists()) legacyMockVideo.delete()
                }
                val megaCache = File(cacheDir, "cloud_storage/mega")
                if (megaCache.exists()) {
                    val noteFile = File(megaCache, "ArcBox_MEGA_Note.txt")
                    if (noteFile.exists()) noteFile.delete()
                    val docDir = File(megaCache, "Documentos")
                    if (docDir.exists() && docDir.listFiles().isNullOrEmpty()) docDir.delete()
                    val imgDir = File(megaCache, "Imagens")
                    if (imgDir.exists() && imgDir.listFiles().isNullOrEmpty()) imgDir.delete()
                    val downDir = File(megaCache, "Downloads")
                    if (downDir.exists() && downDir.listFiles().isNullOrEmpty()) downDir.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        intent?.let { viewModel.handleIncomingIntent(it, this) }

        setContent {
            ArcboxApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent, this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFilesOnResume()
    }
}


