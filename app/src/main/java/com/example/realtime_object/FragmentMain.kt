package com.example.realtime_object


import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.card.MaterialCardView

class FragmentMain : AppCompatActivity() {

    companion object {
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_GALLERY = 101
        private const val REQUEST_VIDEO = 102
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_main)
        val statusBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("status_bar_height", "dimen", "android")
        )

        val mainLayout = findViewById<CoordinatorLayout>(R.id.mainLayout)
        mainLayout.setPadding(
            mainLayout.paddingLeft,
            statusBarHeight,
            mainLayout.paddingRight,
            mainLayout.paddingBottom
        )
        // Initialize views
        val cardCamera = findViewById<MaterialCardView>(R.id.cardCamera)
        val cardGallery = findViewById<MaterialCardView>(R.id.cardGallery)
        val cardVideo = findViewById<MaterialCardView>(R.id.cardVideo)
        val cardAbout = findViewById<MaterialCardView>(R.id.cardAbout)

        // Camera click listener
        cardCamera.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Gallery click listener
        cardGallery.setOnClickListener {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }

        // Video click listener
        cardVideo.setOnClickListener {
            val intent = Intent(this, VideoActivity::class.java)
            startActivity(intent)
        }

        // About Us click listener
        cardAbout.setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
            // Optional: Add transition animation
          //  overridePendingTransition(android.R.anim.slide_in_right, android.R.anim.slide_out_left)
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(cameraIntent, REQUEST_CAMERA)
        } else {
            Toast.makeText(this, "Kamera elýeterli däl", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryIntent.type = "image/*"
        startActivityForResult(galleryIntent, REQUEST_GALLERY)
    }

    private fun openVideo() {
        val videoIntent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoIntent.type = "video/*"
        startActivityForResult(videoIntent, REQUEST_VIDEO)
    }

    private fun showAboutDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Biz Barada")
            .setMessage("AI Vision Assistant - surat we wideo analiz etmek üçin AI kömekçisi.\n\nVersiýa: 1.0.0\n\n© 2026 AI Vision Team")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setIcon(R.drawable.ic_info)
            .create()
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CAMERA -> {
                    val imageBitmap = data?.extras?.get("data") as? android.graphics.Bitmap
                    Toast.makeText(this, "Surat çekildi!", Toast.LENGTH_SHORT).show()
                    // Suraty AI analizine ugratmak
                    processImage(imageBitmap)
                }
                REQUEST_GALLERY -> {
                    val selectedImageUri = data?.data
                    Toast.makeText(this, "Surat saýlandy: $selectedImageUri", Toast.LENGTH_SHORT).show()
                    // Galereýa suratyny işlemek
                    processGalleryImage(selectedImageUri)
                }
                REQUEST_VIDEO -> {
                    val selectedVideoUri = data?.data
                    Toast.makeText(this, "Wideo saýlandy: $selectedVideoUri", Toast.LENGTH_SHORT).show()
                    // Wideo işlemek
                    processVideo(selectedVideoUri)
                }
            }
        }
    }

    private fun processImage(bitmap: android.graphics.Bitmap?) {
        // AI surat analiz logika
        // Bitmap-i AI modeline ugrat
    }

    private fun processGalleryImage(uri: Uri?) {
        // Galereýa suratyny AI-a ugrat
    }

    private fun processVideo(uri: Uri?) {
        // Wideo AI analiz
    }
}