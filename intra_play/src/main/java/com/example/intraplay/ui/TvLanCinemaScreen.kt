package com.example.intraplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.intraplay.ui.components.TvFocusableCard

/**
 * 🎬 Level 2 LAN Cinema & IntraDrive Media Hub Model
 */
data class TvLanMovie(
    val id: String,
    val title: String,
    val quality: String, // 4K Ultra HD, 1080p
    val audioFormat: String, // DTS 5.1, Dolby Atmos
    val audioLanguages: String, // Dual Audio, Hindi/English
    val lanSpeed: String, // 85 MB/s LAN Stream
    val duration: String,
    val progress: Float, // 0.0f to 1.0f
    val posterUrl: String,
    val backdropUrl: String,
    val overview: String
)

val SampleLanMovies = listOf(
    TvLanMovie(
        id = "1",
        title = "Interstellar LAN Journey",
        quality = "4K Ultra HD",
        audioFormat = "DTS 5.1",
        audioLanguages = "Dual Audio",
        lanSpeed = "85 MB/s LAN Stream",
        duration = "2h 49m",
        progress = 0.65f,
        posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/rAiYTsqzbikIp80vYcrepmDyl20.jpg",
        overview = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival."
    ),
    TvLanMovie(
        id = "2",
        title = "Blade Runner 2049",
        quality = "4K Ultra HD",
        audioFormat = "Dolby Atmos",
        audioLanguages = "English 7.1",
        lanSpeed = "92 MB/s LAN Stream",
        duration = "2h 44m",
        progress = 0.35f,
        posterUrl = "https://image.tmdb.org/t/p/w500/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/ilRyASDvt7vS0Qo21i95nzzL68y.jpg",
        overview = "Young Blade Runner K's discovery of a long-buried secret leads him to track down former Blade Runner Rick Deckard."
    ),
    TvLanMovie(
        id = "3",
        title = "Cyberpunk: Edgerunners",
        quality = "1080p 60fps",
        audioFormat = "5.1 Surround",
        audioLanguages = "Japanese / English",
        lanSpeed = "45 MB/s LAN Stream",
        duration = "10 Episodes",
        progress = 0.85f,
        posterUrl = "https://image.tmdb.org/t/p/w500/z0T038dY156n7jY2j4g7uK9C2vB.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/qcpNn16V1iB963cM4iBq5y970zY.jpg",
        overview = "A street kid trying to survive in a technology and body modification-obsessed city of the future."
    ),
    TvLanMovie(
        id = "4",
        title = "Dune: Part Two",
        quality = "4K Ultra HD",
        audioFormat = "Dolby Atmos",
        audioLanguages = "Dual Audio (HI/EN)",
        lanSpeed = "96 MB/s LAN Stream",
        duration = "2h 46m",
        progress = 0.15f,
        posterUrl = "https://image.tmdb.org/t/p/w500/czembW0Rk1Ke7lCJGhkAi0ym92H.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0x2.jpg",
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family."
    ),
    TvLanMovie(
        id = "5",
        title = "Family Vacation 2026",
        quality = "1080p 60fps",
        audioFormat = "Stereo",
        audioLanguages = "Original Audio",
        lanSpeed = "35 MB/s LAN Stream",
        duration = "45m",
        progress = 0.0f,
        posterUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&auto=format&fit=crop",
        backdropUrl = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1600&auto=format&fit=crop",
        overview = "Uncompressed family memories and home video clips streamed from phone IntraDrive storage directly to TV."
    )
)

data class TvNavRailItem(
    val id: String,
    val title: String,
    val icon: ImageVector
)

val TvNavRailItems = listOf(
    TvNavRailItem("livetv", "Live TV", Icons.Default.LiveTv),
    TvNavRailItem("intradrive", "IntraDrive", Icons.Default.Folder),
    TvNavRailItem("smb", "SMB Shares", Icons.Default.Computer),
    TvNavRailItem("favorites", "Favorites", Icons.Default.Star),
    TvNavRailItem("settings", "Settings", Icons.Default.Settings)
)

/**
 * 📺 IntraPlay Level 2: LAN Cinema & IntraDrive Media Hub
 */
@Composable
fun TvLanCinemaScreen(
    onNavigateLiveTv: () -> Unit = {},
    onPlayMovie: (TvLanMovie) -> Unit = {}
) {
    var selectedNav by remember { mutableStateOf("intradrive") }
    var selectedMovie by remember { mutableStateOf(SampleLanMovies[0]) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        // 1. Ambient Dynamic Movie Backdrop with Scrim
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = selectedMovie.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Cinematic Gradient Scrim (Deep Midnight Purple Fade)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                TvBackground,
                                TvBackground.copy(alpha = 0.95f),
                                TvBackground.copy(alpha = 0.70f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                TvBackground.copy(alpha = 0.6f),
                                TvBackground
                            )
                        )
                    )
            )
        }

        // 2. Main Widescreen Layout (Rail + Content)
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT LEANBACK NAVIGATION RAIL
            TvLeanbackRail(
                items = TvNavRailItems,
                selectedId = selectedNav,
                onSelect = { id ->
                    selectedNav = id
                    if (id == "livetv") onNavigateLiveTv()
                }
            )

            // RIGHT CONTENT CANVAS
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 28.dp, end = 40.dp, bottom = 28.dp)
            ) {
                // Top Header: Branding + Server Status Pill + Clock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "IntraPlay",
                            color = TvTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Level 2 LAN Cinema & IntraDrive Hub",
                            color = TvTextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // LAN Server Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .border(1.dp, TvFocusBorder.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .background(TvSurface.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PC Server: Online 5GHz",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Clock
                        Text(
                            text = "08:45 PM",
                            color = TvTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // CENTER HERO SHOWCASE
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = selectedMovie.title,
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 44.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Spec Badges Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvSpecBadge(text = selectedMovie.quality, isHighlight = true)
                        Text(text = "•", color = TvTextSecondary, fontSize = 12.sp)
                        TvSpecBadge(text = selectedMovie.audioFormat)
                        Text(text = "•", color = TvTextSecondary, fontSize = 12.sp)
                        TvSpecBadge(text = selectedMovie.audioLanguages)
                        Text(text = "•", color = TvTextSecondary, fontSize = 12.sp)
                        TvSpecBadge(text = selectedMovie.lanSpeed, color = Color(0xFFA78BFA))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = selectedMovie.overview,
                        color = TvTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 21.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons (Play Movie + Favorite)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Glowing Play Button
                        TvFocusableCard(
                            onClick = { onPlayMovie(selectedMovie) },
                            shape = RoundedCornerShape(26.dp),
                            backgroundColor = Color.Transparent,
                            focusedBorderColor = Color.White,
                            scaleAmount = 1.08f
                        ) { isFocused ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                if (isFocused) Color(0xFFC084FC) else Color(0xFFA855F7),
                                                if (isFocused) Color(0xFFF472B6) else Color(0xFFE879F9)
                                            )
                                        ),
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .padding(horizontal = 26.dp, vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Play Movie",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Add to Watchlist Button
                        TvFocusableCard(
                            onClick = { /* Bookmark */ },
                            shape = RoundedCornerShape(26.dp),
                            backgroundColor = TvSurface.copy(alpha = 0.85f),
                            focusedBorderColor = TvFocusBorder,
                            scaleAmount = 1.05f
                        ) { isFocused ->
                            Box(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkBorder,
                                        contentDescription = "Save",
                                        tint = if (isFocused) Color.White else TvTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Watchlist",
                                        color = if (isFocused) Color.White else TvTextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // BOTTOM MEDIA CAROUSEL (D-PAD HORIZONTAL ROW)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "IntraDrive LAN Shares • 4K Cinema",
                        color = TvTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(end = 60.dp)
                    ) {
                        items(SampleLanMovies) { movie ->
                            TvMoviePosterCard(
                                movie = movie,
                                isSelected = movie.id == selectedMovie.id,
                                onFocus = { selectedMovie = movie },
                                onClick = {
                                    selectedMovie = movie
                                    onPlayMovie(movie)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Leanback Sidebar Rail on Left
 */
@Composable
fun TvLeanbackRail(
    items: List<TvNavRailItem>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(86.dp)
            .background(TvSurface.copy(alpha = 0.92f))
            .border(width = 1.dp, color = TvCardBorder.copy(alpha = 0.6f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Logo / Top Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    brush = Brush.linearGradient(listOf(TvFocusBorder, IntraPurple)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Navigation Items
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEach { item ->
                val isActive = item.id == selectedId

                TvFocusableCard(
                    onClick = { onSelect(item.id) },
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = if (isActive) TvSelectedCard else Color.Transparent,
                    focusedBorderColor = TvFocusBorder,
                    scaleAmount = 1.15f
                ) { isFocused ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = if (isActive) Color(0xFF8B5CF6).copy(alpha = 0.25f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isActive || isFocused) Color.White else TvTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.title,
                                color = if (isActive || isFocused) Color.White else TvTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Bottom spacer
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Horizontal Carousel Movie Poster Card with Resolution Badge and Progress Line
 */
@Composable
fun TvMoviePosterCard(
    movie: TvLanMovie,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    TvFocusableCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = TvSurfaceVariant,
        focusedBorderColor = Color(0xFFFBBF24), // Golden Amber remote focus glow
        scaleAmount = 1.12f,
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(0.68f)
    ) { isFocused ->
        if (isFocused) {
            onFocus()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )

            // Top Quality Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (movie.quality.contains("4K")) "4K" else "HD",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom Progress Bar (Watched progress)
            if (movie.progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(movie.progress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(TvFocusBorder, Color(0xFFF472B6))
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Spec Pill Badge (e.g. 4K Ultra HD, DTS 5.1, 85 MB/s)
 */
@Composable
fun TvSpecBadge(
    text: String,
    isHighlight: Boolean = false,
    color: Color = Color.White
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isHighlight) TvFocusBorder.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = if (isHighlight) TvFocusBorder.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlight) Color(0xFFD8B4FE) else color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
