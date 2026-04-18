package com.example.realtime_object

import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        viewPager.adapter = TabAdapter(this)
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = java.util.Locale.US
                isTtsReady = true
            }
        }

        // Tab icon + text
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "Detection"
                    tab.setIcon(R.drawable.ic_detection) // ← öz ikonuňy goý
                }
                1 -> {
                    tab.text = "Analyze"
                    tab.setIcon(R.drawable.ic_analyze) // ← öz ikonuňy goý
                }
            }
        }.attach()

        // 🔊 Tab geçilende ses çykarmaly
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                announceTab(position)
            }
        })
    }

    private fun announceTab(position: Int) {
        if (!isTtsReady) return
        val name = when (position) {
            0 -> "Detection"
            1 -> "Analyze"
            else -> ""
        }
        tts.speak(name, TextToSpeech.QUEUE_FLUSH, null, "tab_switch")
    }

    // ViewPager adapter
    inner class TabAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DetectionFragment()
                1 -> GeminiFragment()
                else -> DetectionFragment()
            }
        }
    }
    // onDestroy() goş:
    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}