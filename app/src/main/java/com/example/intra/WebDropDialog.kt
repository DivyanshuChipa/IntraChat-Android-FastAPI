package com.example.intra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 🚀 WebDropDialog - Zero-Friction QR & Link Modal for IntraChat
 *
 * Allows hosts to show a WebDrop QR code and link to guests directly from their
 * personal phone without needing to touch or physically access the server device!
 */
@Composable
fun WebDropDialog(
    baseUrl: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webDropUrl = "${baseUrl.removeSuffix("/")}/drop"
    val qrBitmap = remember(webDropUrl) {
        QrCodeGenerator.generateQrBitmap(webDropUrl, 420)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E1E2E) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "",
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Intra WebDrop",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color(0xFF1F2937)
                            )
                            Text(
                                text = "Instant Zero-Friction LAN Share",
                                fontSize = 12.sp,
                                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Close",
                            tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                        )
                    }
                }

                Spacer(Modifier.height(19.dp))

                // QR Code Container
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "WebDrop QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFF6741A8))
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Scan with any phone camera (iPhone / Android) to drop files & copy texts instantly without an account!",
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(16.dp))

                // URL Display Pill with Copy Action
                Surface(
                    shape = RoundedCornerShape(25.dp),
                    color = if (isDark) Color(0xFF2A2B3D) else Color(0xFFF3F4F6),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF3B3D54) else Color(0xFFE5E7EB)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = webDropUrl,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF93C5FD) else Color(0xFFA8168F),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("WebDrop Link", webDropUrl))
                                Toast.makeText(context, "WebDrop link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Link",
                                tint = if (isDark) Color.White else Color(0xFF9A1E98),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Action Buttons Row (Share / Open)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Open Intra WebDrop on LAN: $webDropUrl")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share WebDrop Link"))
                        },
                        shape = RoundedCornerShape(25.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webDropUrl))
                            context.startActivity(browserIntent)
                        },
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF7C3AED) else Color(0xFF6741A8)
                        ),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open Drop", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
