package com.example.realtime_object

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(context: Context, private val onResultCallback: (String) -> Unit) {

    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val textToSpeech = TextToSpeech(context) { }

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResultCallback(matches[0])
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening(language: String = "tk_TM") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        speechRecognizer.startListening(intent)
    }

    fun speak(text: String, language: String = "tk_TM") {
        val locale = when (language) {
            "tk_TM" -> Locale("tk", "TM")
            "en_US" -> Locale.US
            "ru_RU" -> Locale("ru", "RU")
            else -> Locale.US
        }
        textToSpeech.language = locale
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
        textToSpeech.shutdown()
    }
}