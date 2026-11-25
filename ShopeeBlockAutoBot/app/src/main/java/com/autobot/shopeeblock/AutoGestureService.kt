package com.autobot.shopeeblock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AutoGestureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoGestureService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only dispatches gestures
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    suspend fun performDragAndDrop(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        dragDurationMs: Long = 320L
    ): Boolean {
        val holdStartMs = 60L
        val holdEndMs = 75L
        val moveMs = dragDurationMs.coerceIn(140L, 1200L)

        val p1 = Path().apply { moveTo(startX, startY) }
        val stroke1 = GestureDescription.StrokeDescription(p1, 0L, holdStartMs, true)
        if (!dispatchStroke(stroke1)) return false

        val p2 = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke2 = stroke1.continueStroke(p2, 0L, moveMs, true)
        if (!dispatchStroke(stroke2)) return false

        val p3 = Path().apply { moveTo(endX, endY) }
        val stroke3 = stroke2.continueStroke(p3, 0L, holdEndMs, false)
        return dispatchStroke(stroke3)
    }

    private suspend fun dispatchStroke(stroke: GestureDescription.StrokeDescription): Boolean {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!ok && cont.isActive) {
                cont.resume(false)
            }
        }
    }
}
