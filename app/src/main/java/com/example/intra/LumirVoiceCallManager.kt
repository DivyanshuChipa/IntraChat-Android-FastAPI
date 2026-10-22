package com.example.intra

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * 🎙️ LumirVoiceCallManager - Coordinates Live AI Voice Call turns with Node 2 & Lumir:
 * 1. AudioRecord (16kHz 16-bit PCM WAV)
 * 2. Uploads voice turn to /api/lumir/voice_turn
 * 3. Plays received synthesized speech through MediaPlayer
 */
class LumirVoiceCallManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "LumirVoiceCall"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val settingsManager = SettingsManager(context)

    // UI States
    var isListening by mutableStateOf(false)
        private set
    var isThinking by mutableStateOf(false)
        private set
    var isSpeaking by mutableStateOf(false)
        private set
    var userTranscript by mutableStateOf("")
        private set
    var lumirTranscript by mutableStateOf("")
        private set

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isSessionActive = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    fun startSession() {
        if (isSessionActive) return
        isSessionActive = true
        userTranscript = ""
        lumirTranscript = "Connecting to Lumir Voice Engine..."
        startListening()
    }

    fun endSession() {
        isSessionActive = false
        stopListening()
        stopPlayback()
        userTranscript = ""
        lumirTranscript = ""
        isListening = false
        isThinking = false
        isSpeaking = false
    }

    fun startListening() {
        if (!isSessionActive || isListening || isThinking || isSpeaking) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not initialize.")
                return
            }

            audioRecord?.startRecording()
            isListening = true
            userTranscript = ""

            recordingJob = scope.launch(Dispatchers.IO) {
                val pcmBuffer = ByteArrayOutputStream()
                val readBuffer = ByteArray(bufferSize)

                var silenceFrames = 0
                val silenceThreshold = 1400 // Amplitude threshold for silence
                val requiredSilenceFrames = 20 // ~1.5 seconds of silence to auto-finish speaking

                var speechStarted = false
                val startTime = System.currentTimeMillis()

                while (isActive && isListening) {
                    val readBytes = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (readBytes > 0) {
                        pcmBuffer.write(readBuffer, 0, readBytes)

                        // Calculate max amplitude in frame
                        var maxAmp = 0
                        for (i in 0 until readBytes step 2) {
                            val sample = (readBuffer[i].toInt() and 0xFF) or (readBuffer[i + 1].toInt() shl 8)
                            val abs = kotlin.math.abs(sample.toShort().toInt())
                            if (abs > maxAmp) maxAmp = abs
                        }

                        if (maxAmp > silenceThreshold) {
                            speechStarted = true
                            silenceFrames = 0
                        } else if (speechStarted) {
                            silenceFrames++
                            if (silenceFrames >= requiredSilenceFrames) {
                                // User finished speaking!
                                break
                            }
                        }

                        // Max 10 seconds per turn safety
                        if (System.currentTimeMillis() - startTime > 10000) {
                            break
                        }
                    }
                }

                stopListeningInternal()

                val rawPcm = pcmBuffer.toByteArray()
                if (rawPcm.size > 8000 && speechStarted) { // At least 0.25s of speech
                    val wavData = addWavHeader(rawPcm, sampleRate)
                    sendVoiceTurn(wavData)
                } else {
                    // Loop back to listen if no real speech was detected
                    withContext(Dispatchers.Main) {
                        if (isSessionActive) {
                            startListening()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startListening error", e)
            isListening = false
        }
    }

    fun stopListening() {
        isListening = false
        recordingJob?.cancel()
        recordingJob = null
        stopListeningInternal()
    }

    private fun stopListeningInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private suspend fun sendVoiceTurn(wavBytes: ByteArray) {
        withContext(Dispatchers.Main) {
            isListening = false
            isThinking = true
        }

        try {
            val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")
            val url = "$baseUrl/api/lumir/voice_turn"

            val tempWavFile = File(context.cacheDir, "lumir_input_${System.currentTimeMillis()}.wav").apply {
                FileOutputStream(this).use { it.write(wavBytes) }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("sender", settingsManager.getUsername() ?: "User")
                .addFormDataPart(
                    "file",
                    tempWavFile.name,
                    tempWavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            if (response.isSuccessful && response.body != null) {
                val rawUser = response.header("X-User-Text") ?: ""
                val rawReply = response.header("X-Reply-Text") ?: ""

                val uText = runCatching { URLDecoder.decode(rawUser, "UTF-8") }.getOrDefault(rawUser)
                val rText = runCatching { URLDecoder.decode(rawReply, "UTF-8") }.getOrDefault(rawReply)

                withContext(Dispatchers.Main) {
                    userTranscript = uText
                    lumirTranscript = rText
                    isThinking = false
                }

                val replyWavBytes = response.body!!.bytes()
                if (replyWavBytes.isNotEmpty()) {
                    playReplyAudio(replyWavBytes)
                } else {
                    onTurnCompleted()
                }
            } else {
                withContext(Dispatchers.Main) {
                    isThinking = false
                    lumirTranscript = "Voice error: ${response.code}"
                }
                delay(1500)
                onTurnCompleted()
            }

            tempWavFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "sendVoiceTurn error", e)
            withContext(Dispatchers.Main) {
                isThinking = false
                lumirTranscript = "Could not reach Voice Engine: ${e.message}"
            }
            delay(1500)
            onTurnCompleted()
        }
    }

    private fun playReplyAudio(wavBytes: ByteArray) {
        scope.launch(Dispatchers.Main) {
            try {
                isSpeaking = true
                stopPlayback()

                val tempAudioFile = File(context.cacheDir, "lumir_reply_${System.currentTimeMillis()}.wav").apply {
                    FileOutputStream(this).use { it.write(wavBytes) }
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempAudioFile.absolutePath)
                    setOnCompletionListener {
                        stopPlayback()
                        tempAudioFile.delete()
                        onTurnCompleted()
                    }
                    setOnErrorListener { _, _, _ ->
                        stopPlayback()
                        tempAudioFile.delete()
                        onTurnCompleted()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "playReplyAudio error", e)
                stopPlayback()
                onTurnCompleted()
            }
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isSpeaking = false
    }

    private fun onTurnCompleted() {
        scope.launch(Dispatchers.Main) {
            isSpeaking = false
            isThinking = false
            if (isSessionActive) {
                delay(400) // Brief pause before listening again
                startListening()
            }
        }
    }

    /**
     * Prepend 44-byte standard RIFF WAV header to raw 16kHz 16-bit Mono PCM
     */
    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM format
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0 // 16 bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
