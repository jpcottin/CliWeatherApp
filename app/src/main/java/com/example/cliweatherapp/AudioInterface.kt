package com.example.cliweatherapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

class AudioInterface(
    private val context: Context,
    private val initializationMessage: String,
    private val locale: Locale = Locale.ENGLISH
) : DefaultLifecycleObserver {
    private lateinit var tts: TextToSpeech

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = locale
                speak(initializationMessage)
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.take(32))
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        tts.shutdown()
    }

    companion object {
        private const val TAG = "AudioInterface"
    }
}
