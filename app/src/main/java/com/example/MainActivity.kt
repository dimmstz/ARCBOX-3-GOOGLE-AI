package com.example

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
}


