package com.example.intraserver.lumir

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 📄 PdfUtils.kt - Native Android PDF manipulation
 * Zero external libraries: uses Android SDK built-in PdfRenderer + PdfDocument.
 */
object PdfUtils {

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
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    /**
     * 📄 Convert one or more images into a multi-page PDF document.
     * Fits each photo into a standard A4 page (595x842 pt) while preserving aspect ratio.
     */
    fun convertImagesToPdf(inputFiles: List<File>, uploadsDir: File): ImageUtils.UtilityResult {
        if (inputFiles.isEmpty()) {
            return ImageUtils.UtilityResult(false, "No images found to convert.")
        }

        val pdfDoc = PdfDocument()
        var pageNumber = 1

        // Standard A4 dimensions in PostScript points: 595 x 842 pt
        val pageWidth = 595
        val pageHeight = 842
        val margin = 20f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        try {
            for (file in inputFiles) {
                if (!file.exists() || file.length() == 0L) continue

                // Check decoded bitmap
                val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val orientedBitmap = fixOrientation(file, rawBitmap)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                // Clear background to clean white
                canvas.drawColor(Color.WHITE)

                val availW = pageWidth - (margin * 2)
                val availH = pageHeight - (margin * 2)

                val imgW = orientedBitmap.width.toFloat()
                val imgH = orientedBitmap.height.toFloat()

                val scale = minOf(availW / imgW, availH / imgH)
                val destW = imgW * scale
                val destH = imgH * scale

                val destX = margin + ((availW - destW) / 2f)
                val destY = margin + ((availH - destH) / 2f)

                val destRect = RectF(destX, destY, destX + destW, destY + destH)
                canvas.drawBitmap(orientedBitmap, null, destRect, paint)

                pdfDoc.finishPage(page)
                if (!orientedBitmap.isRecycled) {
                    orientedBitmap.recycle()
                }

                pageNumber++
            }

            val totalPages = pageNumber - 1
            if (totalPages == 0) {
                pdfDoc.close()
                return ImageUtils.UtilityResult(false, "Could not decode any valid images for PDF.")
            }

            val prefix = if (totalPages > 1) "multipage" else "converted"
            val outFilename = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf"
            val outFile = File(uploadsDir, outFilename)

            FileOutputStream(outFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
            pdfDoc.close()

            return ImageUtils.UtilityResult(
                success = true,
                message = "📄 **Successfully converted $totalPages image(s) to PDF!**",
                fileUrl = "/uploads/$outFilename",
                fileName = outFilename
            )
        } catch (e: Exception) {
            pdfDoc.close()
            return ImageUtils.UtilityResult(false, "PDF Conversion Error: ${e.message}")
        }
    }

    fun mergePdfs(inputFiles: List<File>, uploadsDir: File): ImageUtils.UtilityResult {
        if (inputFiles.isEmpty()) {
            return ImageUtils.UtilityResult(false, "No PDF files provided to merge.")
        }

        val pdfDoc = PdfDocument()
        var totalPageCount = 0

        try {
            for (file in inputFiles) {
                if (!file.exists() || file.length() == 0L) continue

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, totalPageCount + 1).create()
                    val docPage = pdfDoc.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDoc.finishPage(docPage)

                    page.close()
                    bitmap.recycle()
                    totalPageCount++
                }

                renderer.close()
                pfd.close()
            }

            if (totalPageCount == 0) {
                pdfDoc.close()
                return ImageUtils.UtilityResult(false, "No readable pages found in provided PDFs.")
            }

            val outFilename = "merged_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdf"
            val outFile = File(uploadsDir, outFilename)
            FileOutputStream(outFile).use { fos ->
                pdfDoc.writeTo(fos)
            }
            pdfDoc.close()

            return ImageUtils.UtilityResult(
                success = true,
                message = "🔗 Successfully merged ${inputFiles.size} PDFs into $totalPageCount pages!",
                fileUrl = "/uploads/$outFilename",
                fileName = outFilename
            )
        } catch (e: Exception) {
            pdfDoc.close()
            return ImageUtils.UtilityResult(false, "PDF merge failed: ${e.message}")
        }
    }
}
