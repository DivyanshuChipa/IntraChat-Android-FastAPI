package com.example.intraserver.lumir

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 📄 PdfUtils.kt - Native Android PDF manipulation
 * Zero external libraries: uses Android SDK built-in PdfRenderer + PdfDocument.
 */
object PdfUtils {

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
