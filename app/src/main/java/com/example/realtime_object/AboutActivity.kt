package com.example.realtime_object

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.button.MaterialButton

class AboutActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val statusBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("status_bar_height", "dimen", "android")
        )

        val mainLayout = findViewById<CoordinatorLayout>(R.id.main)
        mainLayout.setPadding(
            mainLayout.paddingLeft,
            statusBarHeight,
            mainLayout.paddingRight,
            mainLayout.paddingBottom
        )
        // Toolbar back button
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Model info click - show more details
  //      val modelCard = findViewById<LinearLayout>(R.id.modelCard)
        // If you want to add click listener to model card for more info

        // Optional: Link to TensorFlow website
        // findViewById<MaterialButton>(R.id.btnLearnMore).setOnClickListener {
        //     openTensorFlowWebsite()
        // }
    }

    private fun openTensorFlowWebsite() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tensorflow.org/lite/models/object_detection/overview"))
        startActivity(intent)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Smooth transition back
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}