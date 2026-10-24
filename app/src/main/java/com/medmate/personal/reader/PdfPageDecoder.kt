package com.medmate.personal.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Renders off the main thread; one file handle/page per request, always safely closed. */
object PdfPageDecoder {
    suspend fun render(file: File, pageIndex: Int): Bitmap = withContext(Dispatchers.IO) {
        require(file.exists()) { "ملف الشبتر غير موجود في تخزين التطبيق" }
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // Once constructed, PdfRenderer owns the descriptor. Constructor failure
        // leaves ownership with us, so close it explicitly in that case.
        val renderer = try { PdfRenderer(fd) } catch (e: Exception) {
            runCatching { fd.close() }
            throw e
        }
        renderer.use { renderer ->
            require(pageIndex in 0 until renderer.pageCount) { "رقم الصفحة غير صالح" }
            renderer.openPage(pageIndex).use { page ->
                val ratio = min(1900f / page.width, 2400f / page.height)
                val width = (page.width * ratio).roundToInt().coerceAtLeast(1)
                val height = (page.height * ratio).roundToInt().coerceAtLeast(1)
                val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    result.eraseColor(Color.WHITE)
                    page.render(result, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result
                } catch (exception: Exception) {
                    result.recycle()
                    throw exception
                }
            }
        }
    }
}
