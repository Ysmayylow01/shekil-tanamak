package com.example.realtime_object

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Request permissions on home screen
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, 101)

        // Camera button
        findViewById<LinearLayout>(R.id.cameraCard).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // Gallery button
        findViewById<LinearLayout>(R.id.galleryCard).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        // Video button
        findViewById<LinearLayout>(R.id.videoCard).setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }

        // About button
        findViewById<LinearLayout>(R.id.aboutCard).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}