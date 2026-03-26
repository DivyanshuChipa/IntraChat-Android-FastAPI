package com.example.intra

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Cameraswitch
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon

@Composable
fun CallScreen(
    state: CallState,
    onEndCall: () -> Unit,
    onRejectCall: () -> Unit,
    onAcceptCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    webRTCClient: WebRTCClient? = null
) {
    // 🔥 TIMER STATE - Call duration track karne ke liye
    var callSeconds by remember(state.status) { mutableStateOf(0) }
    val context = LocalContext.current

    // 🔥 TIMER LOGIC - Connected hone pe start, status change pe auto stop
    LaunchedEffect(state.status) {
        if (state.status == CallStatus.CONNECTED) {
            callSeconds = 0 // Reset timer
            while (true) {
                delay(1000) // 1 second wait
                callSeconds++ // Increment seconds
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF321954),
                        Color(0xFF9B28D9),
                        Color(0xFFE5B80E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // --- VIDEO CALL BACKGROUND ---
        if (state.isVideoCall && state.status == CallStatus.CONNECTED) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        if (webRTCClient != null) {
                            init(webRTCClient.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            webRTCClient.setupRemoteVideoRenderer(this)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { renderer ->
                    webRTCClient?.removeRemoteVideoRenderer(renderer)
                    renderer.release()
                }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 60.dp)
        ) {
            // --- TOP SECTION: Avatar & Status (Same) ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.isVideoCall || state.status != CallStatus.CONNECTED) {
                        AvatarGlowRing(
                            isActive = state.status != CallStatus.CONNECTED
                        )

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.profilePhotoUrl != null) {
                                AsyncImage(
                                    model = state.profilePhotoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }
                }

                if (!state.isVideoCall || state.status != CallStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = state.targetUser,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedContent(
                        targetState = state.status,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "callStatus"
                    ) { status ->
                        Text(
                            text = when (status) {
                                CallStatus.OUTGOING -> "Calling..."
                                CallStatus.INCOMING -> "Incoming Call..."
                                CallStatus.CONNECTED -> formatTime(callSeconds)
                                else -> ""
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp
                        )
                    }
                } else {
                    // Transparent overlay texts for Video Call
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.targetUser,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTime(callSeconds),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // --- LOCAL VIDEO (PiP) ---
            if (state.isVideoCall && state.status == CallStatus.CONNECTED && state.isVideoEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 32.dp, end = 16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    if (webRTCClient != null) {
                                        init(webRTCClient.eglBaseContext, null)
                                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                        setZOrderMediaOverlay(true)
                                        webRTCClient.setupLocalVideoRenderer(this)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { renderer ->
                                webRTCClient?.removeLocalVideoRenderer(renderer)
                                renderer.release()
                            }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // --- BOTTOM SECTION: Buttons ---
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                if (state.status == CallStatus.INCOMING) {
                    // 🔥 INCOMING CALL UI - FIXED REJECT BUTTON

                    // ❌ Reject Button - Ab signal bhejega
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFEF4444),
                        onClick = onRejectCall // ✅ New callback
                    )

                    // ✅ Accept Button
                    CallActionButton(
                        icon = Icons.Default.Call,
                        color = Color(0xFF22C55E),
                        onClick = onAcceptCall
                    )

                } else {
                    // --- ACTIVE / OUTGOING CALL UI ---

                    CallActionButton(
                        icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        color = if (state.isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                        iconTint = if (state.isMuted) Color.Black else Color.White,
                        onClick = onToggleMute
                    )

                    if (state.isVideoCall) {
                        CallActionButton(
                            icon = if (state.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            color = if (state.isVideoEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                            iconTint = if (state.isVideoEnabled) Color.Black else Color.White,
                            onClick = onToggleVideo
                        )

                        CallActionButton(
                            icon = Icons.Default.Cameraswitch,
                            color = Color.White.copy(alpha = 0.2f),
                            iconTint = Color.White,
                            onClick = onSwitchCamera
                        )
                    }

                    // End Call Button (Same for Connected/Outgoing)
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFEF4444),
                        size = 72.dp,
                        onClick = onEndCall
                    )

                    if (!state.isVideoCall) {
                        CallActionButton(
                            icon = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            color = if (state.isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f),
                            iconTint = if (state.isSpeakerOn) Color.Black else Color.White,
                            onClick = onToggleSpeaker
                        )
                    }
                }
            }
        }
    }
}

// 🔥 ANIMATED RIPPLE RINGS COMPOSABLE
// Yeh 3 ripple rings banata hai jo continuously animate hoti rehti hain
//@Composable
//fun AnimatedRippleRings() {
    // Infinite animation for continuous ripple effect
   // val infiniteTransition = rememberInfiniteTransition(label = "ripple")

    // 3 alag-alag ripple rings ke liye animations
    //val scales = List(3) { index ->
        //infiniteTransition.animateFloat(
            //initialValue = 1f,
            //targetValue = 2f,
            //animationSpec = infiniteRepeatable(
                //animation = tween(
                   // durationMillis = 2000,
                   // easing = LinearEasing
               // ),
               // repeatMode = RepeatMode.Restart,
              //  initialStartOffset = StartOffset(index * 666) // Har ring thoda delay se start hogi
           // ),
           // label = "scale$index"
        //)
    //}

    //val alphas = List(3) { index ->
       // infiniteTransition.animateFloat(
          //  initialValue = 0.6f,
           // targetValue = 0f,
           // animationSpec = infiniteRepeatable(
               // animation = tween(
                   // durationMillis = 2000,
                   // easing = LinearEasing
                //),
               // repeatMode = RepeatMode.Restart,
              //  initialStartOffset = StartOffset(index * 666)
           // ),
           // label = "alpha$index"
       // )
   // }

    // 3 ripple rings render karte hain
   // Box(contentAlignment = Alignment.Center) {
   //     scales.forEachIndexed { index, scale ->
            //Box(
              //  modifier = Modifier
              //      .size(120.dp)
                   // .scale(scale.value)
                   // .clip(CircleShape)
                   // .background(Color.White.copy(alpha = alphas[index].value * 0.3f))
           // )
       // }
  //  }
//}

@Composable
fun CallActionButton(
    icon: ImageVector,
    color: Color,
    iconTint: Color = Color.White,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// 🔥 TIME FORMATTER - Seconds ko mm:ss format me convert karta hai
fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}