package com.example.realtime_object

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class VideoActivity : AppCompatActivity() {

    lateinit var videoView: VideoView
    lateinit var statusText: TextView

    private val videoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            loadVideo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        videoView = findViewById(R.id.videoView)
        statusText = findViewById(R.id.videoStatusText)

        // Select video button
        findViewById<LinearLayout>(R.id.selectVideoBtn).setOnClickListener {
            videoLauncher.launch("video/*")
        }

        // Back button
        findViewById<LinearLayout>(R.id.backBtn).setOnClickListener {
            finish()
        }

        videoView.setOnPreparedListener { mp ->
            statusText.text = "Video ready: ${mp.duration / 1000} seconds"
        }
    }

    private fun loadVideo(uri: Uri) {
        try {
            videoView.setVideoURI(uri)
            videoView.start()
            statusText.text = "Playing video... (Real-time detection processing)"
        } catch (e: Exception) {
            statusText.text = "Error loading video"
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }
}