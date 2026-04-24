package com.example.intra.ui.chat.components

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.DatePicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.intra.ChatMessage
import com.example.intra.SettingsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    onVideoClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onOptionSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Variables for Dialog Box
    var showCompressDialog by remember { mutableStateOf(false) }
    var showVideoCompressDialog by remember { mutableStateOf(false) }
    var showRotateDialog by remember { mutableStateOf(false) }
    var showPassportDialog by remember { mutableStateOf(false) }
    var passportPageSize by remember { mutableStateOf("A6") }
    var passportLayout by remember { mutableStateOf("3x1") }
    var passportDateInput by remember { mutableStateOf("") }
    var passportNameInput by remember { mutableStateOf("") }

    val a6Layouts = listOf("1x3", "2x3", "3x3")
    val a4Layouts = listOf("1x6", "2x6", "3x6")
    val activeLayouts = if (passportPageSize == "A4") a4Layouts else a6Layouts
    if (passportLayout !in activeLayouts) {
        passportLayout = activeLayouts.first()
    }

    // 🗓️ Date Picker logic (dialog input update only)
    val showDatePicker = {
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val formattedDate = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                passportDateInput = formattedDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showCompressDialog) {
        CompressImageDialog(
            onDismiss = { showCompressDialog = false },
            onConfirm = { targetKbInput ->
                onOptionSelected("###compress<${targetKbInput}>###")
            }
        )
    }

    if (showVideoCompressDialog) {
        CompressVideoDialog(
            onDismiss = { showVideoCompressDialog = false },
            onConfirm = { crfValue, videoFormat ->
                onOptionSelected("###compressvideo:${crfValue}:${videoFormat}###")
            }
        )
    }

    if (showRotateDialog) {
        RotateVideoDialog(
            onDismiss = { showRotateDialog = false },
            onConfirm = { rotationType ->
                onOptionSelected("###rotatevideo:${rotationType}###")
            }
        )
    }

    if (showPassportDialog) {
        AlertDialog(
            onDismissRequest = { showPassportDialog = false },
            title = { Text("Master Passport") },
            text = {
                Column {
                    Text("Page Size")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = passportPageSize == "A6",
                            onClick = { passportPageSize = "A6" }
                        )
                        Text("A6", modifier = Modifier.clickable { passportPageSize = "A6" })
                        Spacer(Modifier.width(12.dp))
                        RadioButton(
                            selected = passportPageSize == "A4",
                            onClick = { passportPageSize = "A4" }
                        )
                        Text("A4", modifier = Modifier.clickable { passportPageSize = "A4" })
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Layout Presets")
                    activeLayouts.forEach { layout ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { passportLayout = layout }
                        ) {
                            RadioButton(
                                selected = passportLayout == layout,
                                onClick = { passportLayout = layout }
                            )
                            Text(layout)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passportNameInput,
                        onValueChange = { passportNameInput = it },
                        label = { Text("Name (optional)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passportDateInput,
                        onValueChange = { passportDateInput = it },
                        label = { Text("Date (optional)") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { showDatePicker() }
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { showDatePicker() }) {
                        Text(if (passportDateInput.isBlank()) "Pick Date" else "Change Date")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val safeName = passportNameInput.trim().replace("<", "").replace(">", "")
                    val safeDate = passportDateInput.trim().replace("<", "").replace(">", "")

                    val finalCommand = buildString {
                        append("###passport###")
                        append(" ###passportpage<${passportPageSize.lowercase(Locale.US)}>###")
                        append(" ###passportlayout<$passportLayout>###")
                        if (safeDate.isNotEmpty()) {
                            append(" ###passportdate<$safeDate>###")
                        }
                        if (safeName.isNotEmpty()) {
                            append(" ###passportname<$safeName>###")
                        }
                    }

                    onOptionSelected(finalCommand)
                    showPassportDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPassportDialog = false }) { Text("Cancel") }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isSelf) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSelf)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                if (!message.isSelf && message.receiver == "Family Group" && message.senderName != null) {
                    val colors = listOf(
                        Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
                        Color(0xFF3949AB), Color(0xFF039BE5), Color(0xFF00897B),
                        Color(0xFF43A047), Color(0xFFF4511E)
                    )
                    val colorIndex = kotlin.math.abs(message.senderName.hashCode()) % colors.size
                    Text(
                        text = message.senderName,
                        color = colors[colorIndex],
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                val fileName = message.fileName ?: "File"
                val fileExtension = fileName.substringAfterLast(".", "").lowercase()

                val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif", "webp")
                val isVideo = fileExtension in listOf("mp4", "mkv", "avi", "mov", "webm")

                if (message.isLoading) {
                    if (message.localUri != null && (isImage || isVideo)) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.localUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Uploading Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .alpha(0.6f),
                                contentScale = ContentScale.Crop
                            )
                            UniqueLoader()
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UniqueLoader(Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Uploading $fileName...",
                                fontSize = 12.sp,
                                color = if (message.isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (message.type == "file" && message.fileUrl != null) {

                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            color = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (isImage || isVideo) {

                        val settingsManager = remember { SettingsManager(context) }
                        val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")

                        val fullUrl = if (message.fileUrl.startsWith("http"))
                            message.fileUrl
                        else
                            baseUrl + message.fileUrl

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.clickable {
                                if (isVideo) {
                                    onVideoClick(fullUrl)
                                } else if (isImage) {
                                    onImageClick(fullUrl)
                                }
                            }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(fullUrl)
                                    .videoFrameMillis(2000)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (isVideo) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "Play",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(48.dp)
                                        .background(Color.Black.copy(alpha=0.3f), CircleShape)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = when {
                                isImage -> Icons.Default.Image
                                isVideo -> Icons.Default.VideoLibrary
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = fileName,
                            color = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            try {
                                val settingsManager = SettingsManager(context)
                                val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")
                                val finalUrl = if (message.fileUrl.startsWith("http"))
                                    message.fileUrl
                                else
                                    baseUrl + message.fileUrl

                                if (isVideo) {
                                    onVideoClick(finalUrl)
                                } else if (isImage) {
                                    onImageClick(finalUrl)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                Log.e("Chat", "File open error", e)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isImage || isVideo) "View" else "Open",
                            fontSize = 13.sp
                        )
                    }

                } else if (message.type == "utility_server") {
                    val textColor = if (message.isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = "Server", modifier = Modifier.size(24.dp), tint = textColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Server Status", color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.weight(1f))
                            val isOnline = message.status?.lowercase() == "online"
                            Icon(
                                if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = "Status",
                                modifier = Modifier.size(20.dp),
                                tint = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = textColor.copy(alpha = 0.2f))

                        // CPU
                        ServerStatRow("CPU Usage", message.cpu ?: "0%", Icons.Default.Memory, textColor)
                        // RAM
                        ServerStatRow("RAM Usage", message.ram ?: "0%", Icons.Default.Memory, textColor) // Ideally standard Memory icon for RAM too or maybe nothing
                        // Disk
                        ServerStatRow("Disk Usage", message.disk ?: "0%", Icons.Default.Storage, textColor)
                    }
                } else if (message.type == "utility_weather") {
                    val textColor = if (message.isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier.size(24.dp), tint = textColor)
                            Spacer(Modifier.width(8.dp))
                            Text(message.location ?: "Unknown Location", color = textColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = textColor.copy(alpha = 0.2f))

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Thermostat, contentDescription = "Temp", tint = textColor)
                                Spacer(Modifier.width(4.dp))
                                Text(message.temp ?: "--°C", color = textColor, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if ((message.condition ?: "").lowercase().contains("cloud")) Icons.Default.Cloud else Icons.Default.WbSunny,
                                    contentDescription = "Condition",
                                    tint = textColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(message.condition ?: "--", color = textColor, fontSize = 16.sp)
                            }
                        }
                    }
                } else {
                    val textColor = if (message.isSelf)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    // 🛠️ NAYA: THE MARKDOWN PARSER LOGIC
                    // Pehle kachre wale '*' ko ekdum clean bullet points '•' me badal do
                    val formattedText = message.text
                        .replace(" * ", "\n\n• ") // Beech wale bullets ko proper newline aur gap do
                        .replace("\n* ", "\n• ")  // Normal line-break bullets
                        .replaceFirst("^\\* ".toRegex(), "• ") // Agar message ki shuruat me bullet ho

                    val annotatedString = buildAnnotatedString {
                        // 1. **BOLD** TEXT PARSING (The Split Trick)
                        // Text ko '**' ke hisaab se kaat do. Odd number wale tukde automatically bold honge!
                        val boldParts = formattedText.split("**")

                        boldParts.forEachIndexed { index, part ->
                            val isBold = index % 2 != 0 // Agar index odd hai (1, 3, 5), toh wo bold hai

                            if (isBold) {
                                withStyle(style = SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                                    append(part)
                                }
                            } else {
                                append(part)
                            }
                        }

                        // 2. URL PARSING (Purana logic, par ab clean text par)
                        val finalString = this.toAnnotatedString().text // Resulting text nikal lo
                        val matcher = Patterns.WEB_URL.matcher(finalString)
                        while (matcher.find()) {
                            val start = matcher.start()
                            val end = matcher.end()
                            addStyle(
                                style = SpanStyle(
                                    color = if (message.isSelf) Color.Cyan else Color(0xFF2196F3),
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = start,
                                end = end
                            )
                            addStringAnnotation(
                                tag = "URL",
                                annotation = finalString.substring(start, end),
                                start = start,
                                end = end
                            )
                        }
                    }

                    ClickableText(
                        text = annotatedString,
                        style = LocalTextStyle.current.copy(color = textColor),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    var url = annotation.item
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "http://$url"
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("Chat", "Link open error", e)
                                    }
                                }
                        }
                    )
                }

                // 🔘 Options Buttons loop
                if (message.type == "utility_options" && message.options != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    message.options.forEach { optionText ->
                        OutlinedButton(
                            onClick = {
                                when (optionText) {
                                    "🛂 Passport A6 (6 Photos)" -> onOptionSelected("###passport###")
                                    "🛂 Passport A6 (9 Photos)" -> onOptionSelected("###passport9###")
                                    "🛂 Master Passport" -> {
                                        showPassportDialog = true
                                    }
                                    "📄 Extract Text (OCR)" -> onOptionSelected("###ocr###")
                                    "📅 Passport + Date",
                                    "📅 Passport + Date/Name" -> {
                                        showPassportDialog = true
                                    }
                                    "🗜️ Compress Image" -> {
                                        showCompressDialog = true
                                    }
                                    "📄 Convert to PDF" -> onOptionSelected("###topdf###")
                                    "🧠 Analyze Image (AI)" -> onOptionSelected("###analyzeimage###")
                                    "🔗 Merge PDFs" -> onOptionSelected("###mergepdfs###")
                                    "📄 Extract PDF Text" -> onOptionSelected("###pdf2text###")
                                    "🗜️ Compress PDF" -> onOptionSelected("###compresspdf###")
                                    // Video Tools
                                    "🎵 Extract Audio (MP3)" -> onOptionSelected("###extractaudio###")
                                    "🗜️ Compress Video" -> {
                                        showVideoCompressDialog = true
                                    }
                                    "🔄 Rotate Video" -> {
                                        showRotateDialog = true
                                    }
                                    "🎞️ Convert to MP4" -> onOptionSelected("###convertmp4###")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if(message.isSelf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant),
                            border = BorderStroke(1.dp, if(message.isSelf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                        ) {
                            Text(text = optionText)
                        }
                    }
                }

                message.timestamp?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTime(it),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

fun formatTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}

@Composable
fun ServerStatRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    val floatValue = value.replace("%", "").toFloatOrNull()?.div(100f) ?: 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, color = color, fontSize = 12.sp)
                Text(value, color = color, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = floatValue,
                color = if (floatValue > 0.8f) Color(0xFFF44336) else color,
                trackColor = color.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}
