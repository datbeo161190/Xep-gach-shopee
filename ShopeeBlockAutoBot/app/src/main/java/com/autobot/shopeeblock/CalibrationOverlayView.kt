package com.autobot.shopeeblock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class CalibrationOverlayView(
    context: Context,
    private val prefs: CalibrationPrefs,
    private val screenW: Int,
    private val screenH: Int
) : View(context) {

    var boardRect: RectF = prefs.getBoardRectPx(screenW, screenH)
    var trayRect: RectF = prefs.getTrayRectPx(screenW, screenH)

    var debugScan: ScanResult? = null
    var interactiveMode: Boolean = true

    private val boardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8822C55E")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val trayStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.FILL
    }

    private val filledCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAEF4444")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    private enum class DragTarget { NONE, BOARD_TL, BOARD_BR, BOARD_MOVE, TRAY_TOP, TRAY_BOTTOM }
    private var activeDrag = DragTarget.NONE
    private var lastX = 0f
    private var lastY = 0f
    private val handleRadius = 48f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(boardRect, boardStrokePaint)
        val cellW = boardRect.width() / 8f
        val cellH = boardRect.height() / 8f
        for (i in 1 until 8) {
            val x = boardRect.left + i * cellW
            canvas.drawLine(x, boardRect.top, x, boardRect.bottom, gridLinePaint)
            val y = boardRect.top + i * cellH
            canvas.drawLine(boardRect.left, y, boardRect.right, y, gridLinePaint)
        }

        trayRect.left = boardRect.left
        trayRect.right = boardRect.right
        canvas.drawRect(trayRect, trayStrokePaint)
        val slotW = trayRect.width() / 3f
        canvas.drawLine(trayRect.left + slotW, trayRect.top, trayRect.left + slotW, trayRect.bottom, trayStrokePaint)
        canvas.drawLine(trayRect.left + 2 * slotW, trayRect.top, trayRect.left + 2 * slotW, trayRect.bottom, trayStrokePaint)

        if (interactiveMode) {
            canvas.drawCircle(boardRect.left, boardRect.top, 22f, handlePaint)
            canvas.drawCircle(boardRect.right, boardRect.bottom, 22f, handlePaint)
            canvas.drawCircle(trayRect.centerX(), trayRect.top, 20f, handlePaint)
            canvas.drawCircle(trayRect.centerX(), trayRect.bottom, 20f, handlePaint)
            canvas.drawText("Kéo chấm vàng căn khung 8x8 & 3 khối", boardRect.left + 12f, boardRect.top - 18f, textPaint)
        }

        debugScan?.let { scan ->
            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    if (scan.boardGrid[r][c]) {
                        val cx = boardRect.left + (c + 0.5f) * cellW
                        val cy = boardRect.top + (r + 0.5f) * cellH
                        canvas.drawCircle(cx, cy, cellW * 0.25f, filledCellPaint)
                    }
                }
            }
            for (p in scan.pieces) {
                canvas.drawCircle(p.centerX, p.centerY, 14f, handlePaint)
                canvas.drawText(p.describe(), p.centerX - 45f, trayRect.bottom + 38f, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactiveMode) return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeDrag = when {
                    hypot(x - boardRect.left, y - boardRect.top) < handleRadius -> DragTarget.BOARD_TL
                    hypot(x - boardRect.right, y - boardRect.bottom) < handleRadius -> DragTarget.BOARD_BR
                    hypot(x - trayRect.centerX(), y - trayRect.top) < handleRadius -> DragTarget.TRAY_TOP
                    hypot(x - trayRect.centerX(), y - trayRect.bottom) < handleRadius -> DragTarget.TRAY_BOTTOM
                    boardRect.contains(x, y) -> DragTarget.BOARD_MOVE
                    else -> DragTarget.NONE
                }
                lastX = x
                lastY = y
                return activeDrag != DragTarget.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                when (activeDrag) {
                    DragTarget.BOARD_TL -> {
                        boardRect.left = (boardRect.left + dx).coerceIn(0f, boardRect.right - 200f)
                        boardRect.top = (boardRect.top + dy).coerceIn(0f, boardRect.bottom - 200f)
                    }
                    DragTarget.BOARD_BR -> {
                        boardRect.right = (boardRect.right + dx).coerceIn(boardRect.left + 200f, screenW.toFloat())
                        boardRect.bottom = (boardRect.bottom + dy).coerceIn(boardRect.top + 200f, screenH.toFloat())
                    }
                    DragTarget.BOARD_MOVE -> {
                        val w = boardRect.width()
                        val h = boardRect.height()
                        val nl = (boardRect.left + dx).coerceIn(0f, screenW - w)
                        val nt = (boardRect.top + dy).coerceIn(0f, screenH - h)
                        boardRect.set(nl, nt, nl + w, nt + h)
                    }
                    DragTarget.TRAY_TOP -> {
                        trayRect.top = (trayRect.top + dy).coerceIn(boardRect.bottom, trayRect.bottom - 60f)
                    }
                    DragTarget.TRAY_BOTTOM -> {
                        trayRect.bottom = (trayRect.bottom + dy).coerceIn(trayRect.top + 60f, screenH.toFloat())
                    }
                    DragTarget.NONE -> {}
                }
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                prefs.saveBoardAndTrayPx(boardRect, trayRect, screenW, screenH)
                activeDrag = DragTarget.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
