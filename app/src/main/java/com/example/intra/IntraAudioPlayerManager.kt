@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.intra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 🎵 IntraAudioPlayerManager.kt - Centralized Audio Player Engine for Intra
 * 
 * Provides global background audio playback across all screens (Chat, SMB, WebDrop)
 * so music never stops when closing dialogs or navigating.
 * 
 * Drives:
 * 1. Full Vinyl Screen Player (AudioPlayerDialog.kt)
 * 2. Minimalist Floating Bottom Pill (MiniPlayerPill.kt)
 */
object IntraAudioPlayerManager {

    data class TrackInfo(
        val url: String,
        val fileName: String
    )

    // Current Track
    var currentTrack by mutableStateOf<TrackInfo?>(null)
        private set

    // Playback States
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
        private set

    // Fullscreen Dialog visibility
    var isFullPlayerVisible by mutableStateOf(false)

    // ID3 Metadata
    var trackTitle by mutableStateOf<String?>(null)
        private set
    var trackArtist by mutableStateOf<String?>(null)
        private set
    var albumArtBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // Real-Time Audio Spectrum / Bass level from FFT
    var rawBeatLevel by mutableFloatStateOf(0f)

    // Internal Instances
    private var exoPlayer: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Start playing an audio file.
     * If the same file is already playing, just reveals the full player.
     */
    fun play(context: Context, url: String, fileName: String) {
        if (currentTrack?.url == url && exoPlayer != null) {
            isFullPlayerVisible = true
            return
        }

        // Clean up previous playback
        stopInternal(releasePlayer = true)

        currentTrack = TrackInfo(url, fileName)
        trackTitle = fileName.substringBeforeLast('.')
        trackArtist = "Intra Music"
        albumArtBitmap = null
        currentPosition = 0L
        duration = 0L
        isBuffering = true
        isPlaying = true
        isFullPlayerVisible = true

        val player = ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }

        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }?.let {
                    trackTitle = it
                }
                mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }?.let {
                    trackArtist = it
                }
                val artData = mediaMetadata.artworkData
                if (artData != null && artData.isNotEmpty()) {
                    try {
                        albumArtBitmap = BitmapFactory.decodeByteArray(artData, 0, artData.size)
                    } catch (_: Exception) {}
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })

        exoPlayer = player
        attachVisualizer(player)
        startProgressTracker()

        // Extract metadata if local file
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(url)
                val pic = retriever.embeddedPicture
                if (pic != null) {
                    albumArtBitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { trackTitle = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { trackArtist = it }
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        currentPosition = positionMs
    }

    fun rewind10() {
        val player = exoPlayer ?: return
        val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
        seekTo(target)
    }

    fun forward10() {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + 10000L).coerceAtMost(duration)
        seekTo(target)
    }

    fun showFullPlayer() {
        isFullPlayerVisible = true
    }

    fun hideFullPlayer() {
        isFullPlayerVisible = false
    }

    fun stop() {
        stopInternal(releasePlayer = true)
        currentTrack = null
        isFullPlayerVisible = false
    }

    private fun stopInternal(releasePlayer: Boolean) {
        progressJob?.cancel()
        progressJob = null

        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {}
        visualizer = null

        if (releasePlayer) {
            try {
                exoPlayer?.stop()
                exoPlayer?.release()
            } catch (_: Throwable) {}
                exoPlayer = null
        }
        isPlaying = false
        rawBeatLevel = 0f
    }

    private fun attachVisualizer(player: ExoPlayer) {
        try {
            val audioSessionId = player.audioSessionId
            if (audioSessionId != 0) {
                visualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[0]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                if (fft != null && fft.size >= 8 && isPlaying) {
                                    val r0 = fft[2].toInt()
                                    val i0 = fft[3].toInt()
                                    val r1 = fft[4].toInt()
                                    val i1 = fft[5].toInt()
                                    val r2 = fft[6].toInt()
                                    val i2 = fft[7].toInt()
                                    val energy = Math.sqrt((r0*r0 + i0*i0 + r1*r1 + i1*i1 + r2*r2 + i2*i2).toDouble()).toFloat()
                                    // User-tuned parameter: threshold 22f for bass drops
                                    val norm = ((energy - 22f) / 40f).coerceIn(0f, 1.8f)
                                    rawBeatLevel = norm
                                } else {
                                    rawBeatLevel = 0f
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    enabled = true
                }
            }
        } catch (_: Throwable) {}
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentPosition = player.currentPosition.coerceAtLeast(0L)
                    }
                }
                delay(250)
            }
        }
    }
}
