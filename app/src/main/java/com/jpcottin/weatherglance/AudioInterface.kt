package com.jpcottin.weatherglance

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Looper
import android.os.Handler
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

/** How a [TextToSpeech] engine is created; the default is the platform constructor. */
typealias TtsEngineFactory = (Context, TextToSpeech.OnInitListener) -> TextToSpeech

class AudioInterface(
    private val context: Context,
    private val initializationMessage: String,
    private val locale: Locale = Locale.ENGLISH,
    private val engineFactory: TtsEngineFactory = { ctx, listener -> TextToSpeech(ctx, listener) },
) : DefaultLifecycleObserver {
    private lateinit var tts: TextToSpeech
    // The weather is read out when the screen first appears, not on every return to it:
    // a second instance stacked on the lens, or a system overlay, stops and restarts this one.
    private var introduced = false

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        tts = engineFactory(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = locale
                if (!introduced) {
                    introduced = true
                    speak(initializationMessage)
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(text: String) {
        if (::tts.isInitialized) tts.speak(text, TextToSpeech.QUEUE_ADD, null, text.take(32))
    }

    /**
     * Says [text] right away, dropping whatever was queued, and runs [onDone] on the main
     * thread once it has been spoken (or at once if there is no engine, or after a short
     * timeout should the engine never report back). For a farewell before the screen goes:
     * finishing first would shut the engine down mid-sentence.
     */
    fun speakThen(text: String, onDone: () -> Unit) {
        if (!::tts.isInitialized) { onDone(); return }
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val finish = Runnable { if (!done) { done = true; onDone() } }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { if (utteranceId == FAREWELL_ID) handler.post(finish) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == FAREWELL_ID) handler.post(finish) }
        })
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, FAREWELL_ID) != TextToSpeech.SUCCESS) {
            finish.run()
            return
        }
        handler.postDelayed(finish, FAREWELL_TIMEOUT_MS)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        tts.shutdown()
    }

    companion object {
        private const val TAG = "AudioInterface"
        private const val FAREWELL_ID = "farewell"
        private const val FAREWELL_TIMEOUT_MS = 3000L
    }
}
