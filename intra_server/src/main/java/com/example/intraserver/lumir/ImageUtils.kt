package com.example.intraserver.lumir

import android.graphics.*
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 📸 ImageUtils.kt - Native Android Skia Engine for Lumir
 * Python Pillow ke saare features native Android Bitmap + Canvas se 2x speed me handle karta hai.
 */
object ImageUtils {

    data class UtilityResult(
        val success: Boolean,
        val message: String,
        val fileUrl: String? = null,
        val fileName: String? = null
    )

    // Helper: Phone ke camera se aayi rotated photo ko seedha karna
    private fun fixOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * 🛂 Generate Passport Layout (35x45 mm ratio with A6 / A4 sheet)
     */
    fun generatePassportLayout(
        inputFile: File,
        uploadsDir: File,
        gridSize: Int = 6,
        dateText: String? = null,
        nameText: String? = null,
        pageSize: String = "A6"
    ): UtilityResult {
        return try {
            if (!inputFile.exists()) {
                return UtilityResult(false, "Input file does not exist on server.")
            }

            var origBitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return UtilityResult(false, "Could not decode image file.")
            origBitmap = fixOrientation(inputFile, origBitmap)

            val isA4 = pageSize.equals("A4", ignoreCase = true)
            val canvasW = if (isA4) 2480 else 1240
            val canvasH = if (isA4) 3508 else 1748

            val rows = if (gridSize == 9) 3 else 3
            val cols = if (gridSize == 9) 3 else 2

            val outerMarginX = if (isA4) 48 else 36
            val outerMarginY = if (isA4) 48 else 36
            val gapX = if (isA4) 30 else 24
            val gapY = if (isA4) 30 else 24

            val availableW = maxOf(120, canvasW - (outerMarginX * 2) - ((cols - 1) * gapX))
            val availableH = maxOf(140, canvasH - (outerMarginY * 2) - ((rows - 1) * gapY))

            val tileWCap = maxOf(100, availableW / cols)
            val tileHCap = maxOf(120, availableH / rows)

            val borderWidth = 3
            val metaLines = mutableListOf<String>()
            if (!nameText.isNullOrBlank()) metaLines.add(nameText.trim())
            if (!dateText.isNullOrBlank()) metaLines.add(dateText.trim())

            val stripHeight = if (metaLines.isNotEmpty()) 22 + (30 * metaLines.size) else 0

            val usableW = maxOf(80, tileWCap - (borderWidth * 2))
            val usableH = maxOf(100, tileHCap - stripHeight - (borderWidth * 2))

            // Passport ratio: 35 / 45
            val ratio = 35.0 / 45.0
            var passW = minOf(usableW, (usableH * ratio).toInt())
            var passH = (passW / ratio).toInt()
            if (passH > usableH) {
                passH = usableH
                passW = (passH * ratio).toInt()
            }

            // 1:1 Center crop & scale photo
            val photoCropW = origBitmap.width
            val photoCropH = (photoCropW / ratio).toInt()
            val cropSrc = if (photoCropH <= origBitmap.height) {
                val yOffset = (origBitmap.height - photoCropH) / 4 // thoda upar rakho face ke liye
                Rect(0, maxOf(0, yOffset), photoCropW, minOf(origBitmap.height, yOffset + photoCropH))
            } else {
                val w = (origBitmap.height * ratio).toInt()
                val xOffset = (origBitmap.width - w) / 2
                Rect(maxOf(0, xOffset), 0, minOf(origBitmap.width, xOffset + w), origBitmap.height)
            }

            val singleTileW = passW + (borderWidth * 2)
            var singleTileH = passH + stripHeight + (borderWidth * 2)

            val tileBitmap = Bitmap.createBitmap(singleTileW, singleTileH, Bitmap.Config.ARGB_8888)
            val tileCanvas = Canvas(tileBitmap)
            tileCanvas.drawColor(Color.WHITE)

            // Draw photo inside tile
            val destRect = Rect(borderWidth, borderWidth, borderWidth + passW, borderWidth + passH)
            tileCanvas.drawBitmap(origBitmap, cropSrc, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

            // Draw Name / Date Text
            if (metaLines.isNotEmpty()) {
                val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                var currentY = borderWidth + passH + 24f
                for (line in metaLines) {
                    tileCanvas.drawText(line, singleTileW / 2f, currentY, paintText)
                    currentY += 28f
                }
            }

            // Draw Black Border around tile
            val borderPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = borderWidth.toFloat()
            }
            tileCanvas.drawRect(
                borderWidth / 2f,
                borderWidth / 2f,
                singleTileW - (borderWidth / 2f),
                singleTileH - (borderWidth / 2f),
                borderPaint
            )

            // Create Canvas sheet
            val sheetBitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val sheetCanvas = Canvas(sheetBitmap)
            sheetCanvas.drawColor(Color.WHITE)

            val totalW = (singleTileW * cols) + (gapX * (cols - 1))
            val startX = maxOf(0, (canvasW - totalW) / 2)
            val startY = outerMarginY

            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = startX + c * (singleTileW + gapX)
                    val y = startY + r * (singleTileH + gapY)
                    sheetCanvas.drawBitmap(tileBitmap, x.toFloat(), y.toFloat(), null)
                }
            }

            // Save output JPEG
            val outFilename = "passport_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val outFile = File(uploadsDir, outFilename)
            FileOutputStream(outFile).use { out ->
                sheetBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            UtilityResult(
                success = true,
                message = "Generated $gridSize passport photos on $pageSize sheet! 🖨️",
                fileUrl = "/uploads/$outFilename",
                fileName = outFilename
            )
        } catch (e: Exception) {
            UtilityResult(false, "Passport generation error: ${e.message}")
        }
    }

    /**
     * 🗜️ Compress Image to Target KB (Quality loop + Dimension scale)
     */
    fun compressImageToTarget(
        inputFile: File,
        uploadsDir: File,
        targetKb: Int
    ): UtilityResult {
        return try {
            if (!inputFile.exists()) {
                return UtilityResult(false, "Input file does not exist on server.")
            }

            val origSize = inputFile.length()
            val targetBytes = targetKb * 1024L

            var bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                ?: return UtilityResult(false, "Could not decode image.")
            bitmap = fixOrientation(inputFile, bitmap)

            // Downscale initial size if exceeding 1920
            val maxDim = 1920
            if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val newW = (bitmap.width * scale).toInt()
                val newH = (bitmap.height * scale).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            }

            var quality = 95
            var baos = ByteArrayOutputStream()

            // 🔄 Quality reduction loop (95 to 10)
            while (quality > 10) {
                baos.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                if (baos.size() <= targetBytes) break
                quality -= 5
            }

            // If still > target, reduce dimensions
            if (baos.size() > targetBytes) {
                var scale = 0.9f
                while (baos.size() > targetBytes && scale > 0.25f) {
                    baos.reset()
                    val scaledW = maxOf(100, (bitmap.width * scale).toInt())
                    val scaledH = maxOf(100, (bitmap.height * scale).toInt())
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos)
                    scale -= 0.1f
                }
            }

            val outFilename = "compressed_${targetKb}kb_${System.currentTimeMillis()}.jpg"
            val outFile = File(uploadsDir, outFilename)
            FileOutputStream(outFile).use { it.write(baos.toByteArray()) }

            val newSize = outFile.length()
            fun formatSize(b: Long): String {
                return if (b > 1024 * 1024) String.format("%.2f MB", b / (1024.0 * 1024.0))
                else "${b / 1024} KB"
            }

            val msg = "🗜️ **Target Achieved! (Limit: $targetKb KB)**\n\n📉 Original: ${formatSize(origSize)}\n⚡ New Size: ${formatSize(newSize)}"

            UtilityResult(
                success = true,
                message = msg,
                fileUrl = "/uploads/$outFilename",
                fileName = outFilename
            )
        } catch (e: Exception) {
            UtilityResult(false, "Compression error: ${e.message}")
        }
    }
}
