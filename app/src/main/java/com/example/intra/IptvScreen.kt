package com.example.intra

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.intra.ui.chat.components.UniqueLoader

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IptvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }

    // States
    var playlistUrl by remember { mutableStateOf(settingsManager.getIptvPlaylistUrl()) }
    var channels by remember { mutableStateOf<List<IptvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedChannel by remember { mutableStateOf<IptvChannel?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isFullscreen by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    // ExoPlayer setup with HTTP User-Agent and Cross-Protocol Redirects
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

    var isPlayerBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    // Listen for player events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isPlayerBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playerError = null
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlayerBuffering = false
                android.util.Log.e("IPTV", "Playback error on ${selectedChannel?.name}: ${error.errorCodeName}", error)
                val detail = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server returned error (Geo-blocked/Token expired)"
                    androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Stream format unsupported"
                    else -> error.cause?.message ?: "Stream offline or geo-blocked"
                }
                playerError = "Playback error: $detail"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Function to load playlist
    fun refreshPlaylist(force: Boolean = false) {
        scope.launch {
            isLoading = true
            channels = IptvHelper.loadPlaylist(context, playlistUrl, forceRefresh = force)
            isLoading = false
        }
    }

    // Initial load
    LaunchedEffect(playlistUrl) {
        refreshPlaylist(force = false)
    }

    // Play channel stream
    fun playChannel(channel: IptvChannel) {
        selectedChannel = channel
        playerError = null
        try {
            val cleanUrl = channel.streamUrl.trim()
            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(cleanUrl))

            if (cleanUrl.contains(".m3u8", ignoreCase = true) || cleanUrl.contains("m3u", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (cleanUrl.contains(".mpd", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            android.util.Log.e("IPTV", "Failed to start channel ${channel.name}", e)
            playerError = "Invalid stream URL: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    // Handle back button when in fullscreen
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val insetsController = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            onBack()
        }
    }

    // Categories
    val categories = remember(channels) {
        val groups = channels.map { it.group }.distinct().sorted()
        listOf("All") + groups
    }

    // Filtered channels
    val filteredChannels = remember(channels, selectedCategory, searchQuery) {
        channels.filter { channel ->
            val matchesCategory = (selectedCategory == "All" || channel.group.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() || channel.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    // ----------------------------------------------------
    // FULLSCREEN PLAYER VIEW
    // ----------------------------------------------------
    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar in Fullscreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            isFullscreen = false
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            val insetsController = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                            insetsController?.show(WindowInsetsCompat.Type.systemBars())
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = selectedChannel?.name ?: "Live TV",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isPlayerBuffering) {
                UniqueLoader(modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    // ----------------------------------------------------
    // NORMAL PORTRAIT SCREEN
    // ----------------------------------------------------
    val isDark = isSystemInDarkTheme()
    val topBarColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFF6741A8)
    val topBarTextColor = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intra TV", color = topBarTextColor, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (channels.isNotEmpty()) "${channels.size} Free Channels Available" else "Live IPTV Hub",
                            fontSize = 12.sp,
                            color = topBarTextColor.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = topBarTextColor)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshPlaylist(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Playlist", tint = topBarTextColor)
                    }
                    IconButton(onClick = { showUrlDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Playlist Settings", tint = topBarTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ------------------------------------------------
            // 1. VIDEO PLAYER CONTAINER (TOP)
            // ------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (selectedChannel != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay controls & badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = selectedChannel?.name ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                isFullscreen = true
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                val insetsController = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                                insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                            }
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                        }
                    }

                    if (isPlayerBuffering) {
                        UniqueLoader(modifier = Modifier.align(Alignment.Center))
                    }

                    if (playerError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow)
                            Spacer(Modifier.height(4.dp))
                            Text(playerError ?: "", color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { playChannel(selectedChannel!!) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }

                } else {
                    // Placeholder when no channel is selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Choose a channel from below",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Free-to-air Indian & global live streams",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // ------------------------------------------------
            // 2. SEARCH BAR & CATEGORIES
            // ------------------------------------------------
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channel name...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Categories Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = (selectedCategory == category)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // ------------------------------------------------
            // 3. CHANNEL GRID
            // ------------------------------------------------
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Fetching IPTV channels playlist...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredChannels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels found for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        val isPlaying = (selectedChannel?.id == channel.id)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playChannel(channel) }
                                .then(
                                    if (isPlaying) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                    } else Modifier
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPlaying)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!channel.logoUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(52.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = channel.name,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isPlaying) {
                                        Icon(
                                            Icons.Default.VolumeUp,
                                            contentDescription = "Playing",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Playing",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = channel.group,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // PLAYLIST URL CONFIGURATION DIALOG
    // ----------------------------------------------------
    if (showUrlDialog) {
        var tempUrl by remember { mutableStateOf(playlistUrl) }

        Dialog(onDismissRequest = { showUrlDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "IPTV Playlist Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Enter a public or local .m3u / .m3u8 playlist URL to load live channels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("M3U Playlist URL") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                settingsManager.resetIptvPlaylistUrl()
                                playlistUrl = settingsManager.getIptvPlaylistUrl()
                                tempUrl = playlistUrl
                                showUrlDialog = false
                                refreshPlaylist(force = true)
                                Toast.makeText(context, "Reset to default India playlist", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Reset", color = Color(0xFFF44336))
                        }

                        Row {
                            TextButton(onClick = { showUrlDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (tempUrl.trim().isNotEmpty()) {
                                        settingsManager.setIptvPlaylistUrl(tempUrl.trim())
                                        playlistUrl = tempUrl.trim()
                                        showUrlDialog = false
                                        refreshPlaylist(force = true)
                                        Toast.makeText(context, "Playlist updated!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}
