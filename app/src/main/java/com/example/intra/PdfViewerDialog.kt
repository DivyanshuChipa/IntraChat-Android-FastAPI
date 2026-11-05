package com.example.intra

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 📄 PdfViewerDialog.kt - Native Android In-App PDF Viewer for Intra
 * 
 * Features:
 * 1. 🚀 Zero-Dependency Renderer: Uses native Android `PdfRenderer` without heavy 3rd-party libs.
 * 2. ⚡ Smart Stream & Cache: Streams remote HTTP PDF (WebDAV / WebDrop) into temp cache seamlessly.
 * 3. 🔍 Pinch-To-Zoom & Pan: Smooth dual-finger zoom (up to 4x) with double-tap reset.
 * 4. 🛡️ Memory-Safe Rendering: Pages are decoded into Bitmaps on-demand and recycled when scrolled offscreen.
 * 5. ⬇️ 1-Tap Save: Saves document to `Downloads/Intra/` with MediaStore indexing.
 */
@Composable
fun PdfViewerDialog(
    pdfUrl: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    // States for PDF downloading & rendering setup
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var localPdfFile by remember { mutableStateOf<File?>(null) }
    var isTempDownload by remember { mutableStateOf(false) }

    // PDF Renderer state
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf {
            if (totalPages > 0) (listState.firstVisibleItemIndex + 1).coerceAtMost(totalPages) else 1
        }
    }

    // Step 1: Prepare local file (Download from HTTP or use local path)
    LaunchedEffect(pdfUrl) {
        isLoading = true
        errorMessage = null

        withContext(Dispatchers.IO) {
            try {
                val targetFile: File
                if (pdfUrl.startsWith("http://", ignoreCase = true) || pdfUrl.startsWith("https://", ignoreCase = true)) {
                    // Stream from LAN HTTP (WebDAV or WebDrop) to temp cache
                    isTempDownload = true
                    val cleanSafeName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val tempDest = File(context.cacheDir, "preview_${System.currentTimeMillis()}_$cleanSafeName")

                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.MINUTES)
                        .build()

                    val req = Request.Builder().url(pdfUrl).build()
                    val response = client.newCall(req).execute()

                    if (!response.isSuccessful || response.body == null) {
                        throw IllegalStateException("Server returned HTTP ${response.code}")
                    }

                    FileOutputStream(tempDest).use { out ->
                        response.body!!.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    targetFile = tempDest
                } else {
                    // Local file (SMB cached or local disk)
                    isTempDownload = false
                    targetFile = File(pdfUrl)
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    throw IllegalStateException("Downloaded PDF is empty or missing.")
                }

                localPdfFile = targetFile

                // Initialize PdfRenderer
                val descriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)

                pfd = descriptor
                pdfRenderer = renderer
                totalPages = renderer.pageCount
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.message ?: "Could not load PDF document."
            }
        }
    }

    // Cleanup resources on dialog close
    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                pfd?.close()
                if (isTempDownload) {
                    localPdfFile?.delete()
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0C18)) // Deep Midnight OLED background
        ) {
            if (isLoading) {
                // 🔄 Loading Screen
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFA855F7),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Loading PDF preview... 📄",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = fileName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else if (errorMessage != null) {
                // ❌ Error Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to open PDF",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6741A8))
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            } else if (pdfRenderer != null && totalPages > 0) {
                // 📄 PDF Pages View with Pinch-to-Zoom & Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Double tap resets zoom to 1f or zooms in to 2.5f
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // Pinch-to-zoom and Pan
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    val maxOffX = 1000f * (scale - 1f)
                                    val maxOffY = 2000f * (scale - 1f)
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-maxOffX, maxOffX),
                                        (offset.y + pan.y).coerceIn(-maxOffY, maxOffY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentPadding = PaddingValues(top = 80.dp, bottom = 80.dp, start = 12.dp, end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(totalPages) { pageIndex ->
                            PdfPageCard(
                                renderer = pdfRenderer!!,
                                pageIndex = pageIndex,
                                density = density
                            )
                        }
                    }
                }

                // 🏷️ Floating Page Indicator Pill (Bottom Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Surface(
                        color = Color(0xFF1B162C).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.45f)),
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = "Page $currentPage of $totalPages",
                            color = Color(0xFFE9D5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // 🔝 TOP APP BAR (Glassmorphism overlay)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F0C18).copy(alpha = 0.96f),
                                Color(0xFF0F0C18).copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title & Page Count
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = fileName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        if (totalPages > 0) {
                            Text(
                                text = "$totalPages Pages • Intra Document",
                                color = Color(0xFFC084FC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Download / Save Button
                    IconButton(
                        onClick = {
                            downloadPdfFile(context, pdfUrl, fileName)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF6741A8).copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save to Downloads",
                            tint = Color(0xFFD8B4FE),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 📄 Individual PDF Page Card
 * Renders high-resolution bitmap on-demand in IO thread and automatically recycles on dispose.
 */
@Composable
private fun PdfPageCard(
    renderer: PdfRenderer,
    pageIndex: Int,
    density: Float
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Synchronized lock: PdfRenderer only allows 1 page opened at a time
                val bitmap = synchronized(renderer) {
                    val page = renderer.openPage(pageIndex)
                    // High-resolution rendering scale: 2.0x base points
                    val scaleFactor = (density * 1.5f).coerceIn(1.5f, 2.5f)
                    val targetW = (page.width * scaleFactor).toInt()
                    val targetH = (page.height * scaleFactor).toInt()

                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(AndroidColor.WHITE) // Pure white page background
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }
                pageBitmap = bitmap
                isRendering = false
            } catch (_: Exception) {
                isRendering = false
            }
        }
    }

    DisposableEffect(pageIndex) {
        onDispose {
            pageBitmap?.recycle()
            pageBitmap = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (pageBitmap != null) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Clean placeholder while decoding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(Color(0xFFF3F4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(
                            color = Color(0xFFA855F7),
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Subtle bottom-right page badge on page itself
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 8.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * 💾 Helper: Saves the PDF file permanently to phone's `Downloads/Intra/` folder
 */
private fun downloadPdfFile(context: Context, urlOrPath: String, originalFileName: String) {
    val cleanName = if (originalFileName.isNotBlank()) originalFileName else "Intra_Doc_${System.currentTimeMillis()}.pdf"

    if (urlOrPath.startsWith("http://", ignoreCase = true) || urlOrPath.startsWith("https://", ignoreCase = true)) {
        Toast.makeText(context, "Saving '$cleanName' to Downloads/Intra...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .build()

                val request = Request.Builder().url(urlOrPath).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed (Server ${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Android 10+ Scoped Storage (MediaStore)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Intra")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val uri = context.contentResolver.insert(collection, contentValues)

                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            response.body!!.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                } else {
                    // Legacy Storage (Android 9 and below)
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val intraDir = File(downloadsDir, "Intra").apply { if (!exists()) mkdirs() }
                    val destFile = File(intraDir, cleanName)

                    destFile.outputStream().use { output ->
                        response.body!!.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf("application/pdf"),
                        null
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads/Intra/$cleanName 📄", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } else {
        // Local file copy
        try {
            val srcFile = File(urlOrPath)
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val intraDir = File(downloadsDir, "Intra").apply { if (!exists()) mkdirs() }
            val destFile = File(intraDir, cleanName)

            srcFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("application/pdf"), null)

            Toast.makeText(context, "Saved to Downloads/Intra/$cleanName 📄", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
