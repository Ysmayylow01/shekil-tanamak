package com.example.realtime_object

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Back button
        findViewById<LinearLayout>(R.id.backBtn).setOnClickListener {
            finish()
        }
    }
}