package com.jpcottin.weatherglance

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/** The lens reads the weather out once per screen, however often the screen restarts. */
@RunWith(AndroidJUnit4::class)
class AudioInterfaceTest {
    private fun waitUntil(timeoutMs: Long, cond: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (!cond()) { if (SystemClock.uptimeMillis() > end) throw AssertionError("timed out"); Thread.sleep(50) }
    }

    @Test
    fun theIntroductionIsSpokenOnceHoweverOftenTheScreenRestarts() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx: Context = inst.targetContext
        Assume.assumeTrue("needs a TTS engine", TextToSpeech(ctx) {}.engines.isNotEmpty())
        val spoken = CopyOnWriteArrayList<String>()
        val inits = AtomicInteger()
        val audio = AudioInterface(ctx, "intro", Locale.ENGLISH) { c, listener ->
            object : TextToSpeech(c, { s -> inits.incrementAndGet(); listener.onInit(s) }) {
                override fun speak(text: CharSequence, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
                    spoken += text.toString(); return SUCCESS
                }
            }
        }
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry.createUnsafe(this) }
        val registry = owner.lifecycle as LifecycleRegistry
        registry.addObserver(audio)

        inst.runOnMainSync { registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        waitUntil(10_000) { inits.get() == 1 }
        inst.runOnMainSync {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        waitUntil(10_000) { inits.get() == 2 }
        Thread.sleep(200)   // a second intro, if any, would have been queued by now

        assertEquals(listOf("intro"), spoken)
        inst.runOnMainSync { registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
    }
}
