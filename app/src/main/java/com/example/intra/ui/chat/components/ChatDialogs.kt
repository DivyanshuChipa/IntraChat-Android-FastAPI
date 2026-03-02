package com.example.intra.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompressImageDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var targetKbInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress Image") },
        text = {
            OutlinedTextField(
                value = targetKbInput,
                onValueChange = { targetKbInput = it },
                label = { Text("Target Size (in KB)") },
                placeholder = { Text("e.g. 60") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            Button(onClick = {
                if (targetKbInput.isNotEmpty()) {
                    onConfirm(targetKbInput)
                    onDismiss()
                }
            }) { Text("Compress") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CompressVideoDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, String) -> Unit
) {
    var crfValue by remember { mutableFloatStateOf(28f) }
    var videoFormat by remember { mutableStateOf("mp4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress Video") },
        text = {
            Column {
                Text(text = "Quality (CRF): ${crfValue.toInt()}", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = crfValue,
                    onValueChange = { crfValue = it },
                    valueRange = 0f..51f,
                    steps = 50
                )
                Text(
                    text = "Lower = Better Quality / Larger Size\nHigher = Lower Quality / Smaller Size\n(23-28 is optimal)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Output Format:", style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = (videoFormat == "mp4"),
                        onClick = { videoFormat = "mp4" }
                    )
                    Text(
                        text = "MP4",
                        modifier = Modifier.clickable { videoFormat = "mp4" }
                    )
                    Spacer(Modifier.width(16.dp))
                    RadioButton(
                        selected = (videoFormat == "mkv"),
                        onClick = { videoFormat = "mkv" }
                    )
                    Text(
                        text = "MKV",
                        modifier = Modifier.clickable { videoFormat = "mkv" }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(crfValue.toInt(), videoFormat)
                onDismiss()
            }) { Text("Compress") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RotateVideoDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var rotationType by remember { mutableStateOf("90_cw") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate Video") },
        text = {
            Column {
                val options = listOf(
                    "90° Clockwise" to "90_cw",
                    "90° Counter-Clockwise" to "90_ccw",
                    "180° / Flip" to "180",
                    "Horizontal Flip" to "hflip"
                )
                options.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rotationType = value }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (rotationType == value),
                            onClick = { rotationType = value }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(rotationType)
                onDismiss()
            }) { Text("Rotate") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
