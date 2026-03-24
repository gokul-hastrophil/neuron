package ai.neuron.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class ScreenCapture(
    private val service: NeuronAccessibilityService,
) {

    suspend fun takeScreenshot(): ScreenshotResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenshotResult.Error("takeScreenshot requires API 30+")
        }

        return try {
            val bitmap = captureScreen()
                ?: return ScreenshotResult.Error("Screenshot returned null bitmap")

            // Save dimensions before recycle — accessing a recycled bitmap crashes
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height

            val compressed = compressToJpeg(bitmap, JPEG_QUALITY, MAX_DIMENSION)
            bitmap.recycle()

            val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
            Log.d(TAG, "Screenshot captured: ${compressed.size} bytes, base64: ${base64.length} chars")

            ScreenshotResult.Success(
                jpeg = compressed,
                base64 = base64,
                width = bitmapWidth,
                height = bitmapHeight,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed", e)
            ScreenshotResult.Error("Screenshot failed: ${e.message}", e)
        }
    }

    private suspend fun captureScreen(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace,
                        )
                        result.hardwareBuffer.close()
                        continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot callback failed with error code: $errorCode")
                        continuation.resume(null)
                    }
                },
            )
        }
    }

    sealed class ScreenshotResult {
        data class Success(
            val jpeg: ByteArray,
            val base64: String,
            val width: Int,
            val height: Int,
        ) : ScreenshotResult()

        data class Error(
            val message: String,
            val cause: Throwable? = null,
        ) : ScreenshotResult()
    }

    companion object {
        private const val TAG = "NeuronScreenCapture"
        const val JPEG_QUALITY = 80
        const val MAX_DIMENSION = 1024

        fun compressToJpeg(bitmap: Bitmap, quality: Int, maxDimension: Int): ByteArray {
            val scaled = scaleBitmap(bitmap, maxDimension)
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            if (scaled != bitmap) scaled.recycle()
            return stream.toByteArray()
        }

        private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= maxDimension && height <= maxDimension) return bitmap

            val scale = maxDimension.toFloat() / maxOf(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
    }
}
