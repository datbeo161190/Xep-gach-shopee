package com.autobot.shopeeblock

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var screenWidth: Int = 1080
        private set
    var screenHeight: Int = 2400
        private set
    private var screenDensity: Int = 420

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            release()
        }
    }

    fun start(resultCode: Int, data: Intent): Boolean {
        release()
        resolveMetrics()

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, data) ?: return false
        mediaProjection = projection

        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = projection.createVirtualDisplay(
            "ShopeeBlockCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null
        )
        return true
    }

    private fun resolveMetrics() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = context.resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
    }

    fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        var image = reader.acquireLatestImage()
        var retries = 0
        while (image == null && retries < 8) {
            Thread.sleep(35)
            image = reader.acquireLatestImage()
            retries++
        }
        if (image == null) return null

        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val fullBmp = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBmp.copyPixelsFromBuffer(buffer)

            if (fullBmp.width == screenWidth) {
                fullBmp
            } else {
                val cropped = Bitmap.createBitmap(fullBmp, 0, 0, screenWidth, screenHeight)
                fullBmp.recycle()
                cropped
            }
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
    }

    fun release() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }
}
