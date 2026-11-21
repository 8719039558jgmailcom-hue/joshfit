package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.example.model.CoachLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val TAG = "WorkoutAudioEngine"
private const val SAMPLE_RATE = 22050
private const val TRAINER_VOICE_PITCH = 0.75f
private const val TRAINER_SPEECH_RATE = 1.05f

class WorkoutAudioEngine(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var beatJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var ttsReady = false
    private var lastSpokenTimeMs = 0L
    private var lastSpokenText = ""
    private var currentCoachLanguage = CoachLanguage.HINGLISH

    private var tts: TextToSpeech? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyTrainerVoiceSettings()
                    ttsReady = true
                } else {
                    Log.w(TAG, "TextToSpeech init failed with status $status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech unavailable", e)
        }
    }

    fun setCoachLanguage(language: CoachLanguage) {
        if (currentCoachLanguage == language) return
        currentCoachLanguage = language
        if (ttsReady) {
            applyTrainerVoiceSettings()
        }
    }

    private fun applyTrainerVoiceSettings() {
        val engine = tts ?: return
        try {
            val indianEnglishLocale = Locale("en", "IN")
            val hindiLocale = Locale("hi", "IN")

            // Select an authoritative Indian male voice (en-IN / hi-IN) when available
            val selectedVoice = selectIndianMaleVoice(engine)
            if (selectedVoice != null) {
                engine.voice = selectedVoice
                Log.d(TAG, "Selected trainer voice: ${selectedVoice.name} (${selectedVoice.locale})")
            } else {
                val primaryLocale = indianEnglishLocale
                val res = engine.setLanguage(primaryLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val hiRes = engine.setLanguage(hindiLocale)
                    if (hiRes == TextToSpeech.LANG_MISSING_DATA || hiRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.US)
                    }
                }
            }

            // Deep, authoritative desi gym trainer cadence
            engine.setPitch(TRAINER_VOICE_PITCH)
            engine.setSpeechRate(TRAINER_SPEECH_RATE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply trainer voice settings", e)
        }
    }

    private fun selectIndianMaleVoice(engine: TextToSpeech): Voice? {
        val available = runCatching { engine.voices }.getOrNull().orEmpty()
        if (available.isEmpty()) return null

        val indianVoices = available.filter { voice ->
            val lang = voice.locale?.language?.lowercase(Locale.ROOT)
            val country = voice.locale?.country?.uppercase(Locale.ROOT)
            country == "IN" && (lang == "en" || lang == "hi")
        }
        if (indianVoices.isEmpty()) return null

        return indianVoices.maxByOrNull { voice -> scoreIndianMaleVoice(voice) }
    }

    private fun scoreIndianMaleVoice(voice: Voice): Int {
        var score = 0
        val name = voice.name.lowercase(Locale.ROOT)
        val lang = voice.locale?.language?.lowercase(Locale.ROOT).orEmpty()
        val features = voice.features?.map { it.lowercase(Locale.ROOT) }.orEmpty()

        // Prefer en-IN slightly for Roman-script Hinglish + English readability, while supporting hi-IN
        if (lang == "en") score += 20 else if (lang == "hi") score += 15

        // Google Android TTS Indian male voice IDs & generic male indicators
        val isExplicitMale = name.contains("male") && !name.contains("female") ||
            name.contains("-end-") || name.contains("-ene-") ||
            name.contains("-hid-") || name.contains("-hie-") ||
            name.endsWith("-end") || name.endsWith("-ene") ||
            name.endsWith("-hid") || name.endsWith("-hie") ||
            features.any { it.contains("male") && !it.contains("female") }

        val isExplicitFemale = name.contains("female") ||
            name.contains("-ena-") || name.contains("-enc-") ||
            name.contains("-hia-") || name.contains("-hic-") ||
            name.endsWith("-ena") || name.endsWith("-enc") ||
            name.endsWith("-hia") || name.endsWith("-hic")

        if (isExplicitMale) score += 100
        if (isExplicitFemale) score -= 100

        if (!voice.isNetworkConnectionRequired) score += 15
        if (voice.quality >= Voice.QUALITY_HIGH) score += 10
        return score
    }

    fun speak(text: String, force: Boolean = false) {
        if (!ttsReady || text.isBlank()) return
        val now = System.currentTimeMillis()
        if (!force && text == lastSpokenText && now - lastSpokenTimeMs < 6000L) return
        if (!force && now - lastSpokenTimeMs < 2400L) return

        lastSpokenTimeMs = now
        lastSpokenText = text
        try {
            tts?.setPitch(TRAINER_VOICE_PITCH)
            tts?.setSpeechRate(TRAINER_SPEECH_RATE)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "joshfit_${now}")
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak error", e)
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun playBeep(highPitch: Boolean = false) {
        scope.launch {
            playShortTone(freqHz = if (highPitch) 880.0 else 587.3, durationMs = 110)
        }
    }

    fun playSetCompleteChime() {
        scope.launch {
            playShortTone(freqHz = 523.25, durationMs = 95)
            playShortTone(freqHz = 659.25, durationMs = 95)
            playShortTone(freqHz = 783.99, durationMs = 160)
        }
    }

    fun setBeatPlaying(shouldPlay: Boolean) {
        if (shouldPlay) {
            if (beatJob?.isActive == true) return
            beatJob = scope.launch {
                runBeatLoop()
            }
        } else {
            beatJob?.cancel()
            beatJob = null
            stopBeatTrack()
        }
    }

    private fun runBeatLoop() {
        // Synthesize a punchy 128 BPM (468ms per beat) 2-bar electronic workout groove
        val beatSamples = (SAMPLE_RATE * 0.46875).toInt()
        val barSamples = beatSamples * 4
        val pcm = ShortArray(barSamples)

        for (beat in 0 until 4) {
            val offset = beat * beatSamples
            val isAccent = (beat == 0 || beat == 2)
            for (i in 0 until beatSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // Punchy kick drum on every beat
                val kickEnv = exp(-t * 18.0)
                val kickFreq = 120.0 * exp(-t * 14.0) + 46.0
                val kick = sin(2.0 * PI * kickFreq * t) * kickEnv * (if (isAccent) 0.55 else 0.42)

                // Crisp off-beat hi-hat tick halfway through each beat
                val hatStart = beatSamples / 2
                val hat = if (i >= hatStart) {
                    val ht = (i - hatStart).toDouble() / SAMPLE_RATE
                    val hatEnv = exp(-ht * 55.0)
                    val metallic = sin(2.0 * PI * 3200.0 * ht) * 0.5 + sin(2.0 * PI * 4700.0 * ht) * 0.5
                    metallic * hatEnv * 0.16
                } else 0.0

                // Subtle bass pulse on beat 0 & 2
                val bass = if (isAccent) {
                    sin(2.0 * PI * 92.5 * t) * exp(-t * 6.0) * 0.14
                } else 0.0

                val sample = ((kick + hat + bass) * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[offset + i] = sample.toShort()
            }
        }

        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer, barSamples * 2))
                .build()

            audioTrack = track
            track.play()

            while (scope.isActive && beatJob?.isActive == true) {
                val written = track.write(pcm, 0, pcm.size)
                if (written < 0) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Workout beat playback error", e)
        } finally {
            stopBeatTrack()
        }
    }

    private fun stopBeatTrack() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.stop()
                track.release()
            }
        } catch (_: Exception) {
        } finally {
            audioTrack = null
        }
    }

    private fun playShortTone(freqHz: Double, durationMs: Int) {
        val count = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 14.0)
            samples[i] = (sin(2.0 * PI * freqHz * t) * env * 0.45 * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        var toneTrack: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            toneTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
                .build()
            toneTrack.write(samples, 0, samples.size)
            toneTrack.play()
            Thread.sleep(durationMs.toLong() + 20L)
        } catch (_: Exception) {
        } finally {
            try {
                toneTrack?.stop()
                toneTrack?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        setBeatPlaying(false)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        scope.cancel()
    }
}
