package com.example.intraplay.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.intraplay.data.IptvChannel
import com.example.intraplay.data.IptvHelper
import com.example.intraplay.data.TvSettingsManager
import com.example.intraplay.ui.components.TvCategorySidebar
import com.example.intraplay.ui.components.TvChannelCard
import com.example.intraplay.ui.components.TvPlayerView
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun TvHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { TvSettingsManager(context) }

    // State
    var playlistUrl by remember { mutableStateOf(settingsManager.getPlaylistUrl()) }
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // ExoPlayer Media3 Setup
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    // Player Event Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.message ?: "Playback Error"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Play Channel Helper
    fun playChannel(channel: IptvChannel) {
        selectedChannel = channel
        playerError = null
        isBuffering = true
        settingsManager.setLastChannelUrl(channel.streamUrl)

        try {
            val mediaItem = MediaItem.fromUri(channel.streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            playerError = e.message ?: "Failed to start stream"
            isBuffering = false
        }
    }

    // Load Playlist on launch
    fun loadChannels(force: Boolean = false) {
        scope.launch {
            isLoading = true
            channels = IptvHelper.loadPlaylist(context, playlistUrl, forceRefresh = force)
            isLoading = false

            // Auto-play last played channel or first channel
            if (selectedChannel == null && channels.isNotEmpty()) {
                val lastUrl = settingsManager.getLastChannelUrl()
                val target = channels.find { it.streamUrl == lastUrl } ?: channels.first()
                playChannel(target)
            }
        }
    }

    LaunchedEffect(playlistUrl) {
        loadChannels(force = false)
    }

    // TV Remote Back Press Handling
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // Filter Channels by Selected Category
    val filteredChannels = remember(channels, selectedCategory) {
        when (selectedCategory) {
            "All" -> channels
            "Favorites" -> {
                val favs = settingsManager.getFavorites()
                channels.filter { favs.contains(it.id) }
            }
            else -> channels.filter { it.group.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        if (isFullscreen) {
            // Fullscreen Player Mode
            TvPlayerView(
                exoPlayer = exoPlayer,
                channel = selectedChannel,
                isBuffering = isBuffering,
                errorMessage = playerError,
                isFullscreen = true,
                isFavorite = selectedChannel?.let { settingsManager.isFavorite(it.id) } ?: false,
                onToggleFavorite = { selectedChannel?.let { settingsManager.toggleFavorite(it.id) } },
                onToggleFullscreen = { isFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 2-Panel TV Landscape Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Category Sidebar
                TvCategorySidebar(
                    selectedCategoryId = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    onSettingsClick = { showSettingsDialog = true }
                )

                // Right Panel: Split Screen (Player Preview on Top + Channel Grid Below)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    // Top: Mini Player Preview Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.48f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        TvPlayerView(
                            exoPlayer = exoPlayer,
                            channel = selectedChannel,
                            isBuffering = isBuffering,
                            errorMessage = playerError,
                            isFullscreen = false,
                            isFavorite = selectedChannel?.let { settingsManager.isFavorite(it.id) } ?: false,
                            onToggleFavorite = { selectedChannel?.let { settingsManager.toggleFavorite(it.id) } },
                            onToggleFullscreen = { isFullscreen = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom: Channel Grid
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.52f)
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = TvFocusBorder,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Loading TV Channels...",
                                        color = TvTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (filteredChannels.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedCategory == "Favorites") "No favorite channels added yet.\nPress the Star icon while watching to add!" else "No channels found in this category.",
                                    color = TvTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 175.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredChannels, key = { it.id + it.streamUrl }) { channel ->
                                    val isPlaying = selectedChannel?.streamUrl == channel.streamUrl
                                    val isFav = settingsManager.isFavorite(channel.id)

                                    TvChannelCard(
                                        channel = channel,
                                        isPlaying = isPlaying,
                                        isFavorite = isFav,
                                        onClick = { playChannel(channel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TV Settings Modal Dialog
        if (showSettingsDialog) {
            var tempUrl by remember { mutableStateOf(playlistUrl) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text(
                        text = "IntraPlay TV Settings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "M3U / M3U8 Playlist Stream URL:",
                            color = TvTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { tempUrl = it },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TvFocusBorder,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempUrl.isNotBlank()) {
                                settingsManager.setPlaylistUrl(tempUrl)
                                playlistUrl = tempUrl
                                loadChannels(force = true)
                            }
                            showSettingsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TvFocusBorder)
                    ) {
                        Text("Save & Reload", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            settingsManager.resetPlaylistUrl()
                            playlistUrl = settingsManager.getPlaylistUrl()
                            loadChannels(force = true)
                            showSettingsDialog = false
                        }
                    ) {
                        Text("Reset Default", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
