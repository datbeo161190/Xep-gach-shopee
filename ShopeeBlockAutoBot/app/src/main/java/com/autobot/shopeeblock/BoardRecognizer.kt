package com.autobot.shopeeblock

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PieceShape(
    val slotIndex: Int,
    val rows: Int,
    val cols: Int,
    val cells: List<Pair<Int, Int>>,
    val centerX: Float,
    val centerY: Float
) {
    fun describe(): String = "${rows}x${cols}(${cells.size}ô)"
}

data class ScanResult(
    val boardMask: Long,
    val boardGrid: Array<BooleanArray>,
    val pieces: List<PieceShape>,
    val filledCount: Int
)

class BoardRecognizer(private val prefs: CalibrationPrefs) {

    private fun isBlockPixel(pixel: Int, satThreshold: Float, valThreshold: Float): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(pixel, hsv)
        val s = hsv[1]
        val v = hsv[2]
        return (s >= satThreshold && v >= valThreshold) || (v >= 0.78f && s >= 0.18f)
    }

    fun analyze(bitmap: Bitmap): ScanResult {
        val w = bitmap.width
        val h = bitmap.height
        val boardRect = prefs.getBoardRectPx(w, h)
        val trayRect = prefs.getTrayRectPx(w, h)
        val satTh = prefs.satThreshold
        val valTh = prefs.valThreshold

        val cellW = boardRect.width() / 8f
        val cellH = boardRect.height() / 8f

        var boardMask = 0L
        var filledCount = 0
        val grid = Array(8) { BooleanArray(8) }

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val left = boardRect.left + c * cellW
                val top = boardRect.top + r * cellH
                var hits = 0
                val samples = 5
                for (sy in 0 until samples) {
                    val py = (top + cellH * (0.25f + 0.50f * sy / (samples - 1))).toInt().coerceIn(0, h - 1)
                    for (sx in 0 until samples) {
                        val px = (left + cellW * (0.25f + 0.50f * sx / (samples - 1))).toInt().coerceIn(0, w - 1)
                        if (isBlockPixel(bitmap.getPixel(px, py), satTh, valTh)) {
                            hits++
                        }
                    }
                }
                val isFilled = hits >= 10
                grid[r][c] = isFilled
                if (isFilled) {
                    boardMask = boardMask or (1L shl (r * 8 + c))
                    filledCount++
                }
            }
        }

        val pieces = mutableListOf<PieceShape>()
        val slotW = trayRect.width() / 3f
        val miniCell = cellW * prefs.miniCellRatio

        for (slot in 0 until 3) {
            val sLeft = (trayRect.left + slot * slotW + slotW * 0.04f).toInt().coerceIn(0, w - 1)
            val sRight = (trayRect.left + (slot + 1) * slotW - slotW * 0.04f).toInt().coerceIn(sLeft + 1, w)
            val sTop = trayRect.top.toInt().coerceIn(0, h - 1)
            val sBottom = trayRect.bottom.toInt().coerceIn(sTop + 1, h)

            val piece = extractPieceFromSlot(
                bitmap = bitmap,
                slotIndex = slot,
                left = sLeft,
                top = sTop,
                right = sRight,
                bottom = sBottom,
                miniCell = miniCell,
                satTh = satTh,
                valTh = valTh
            )
            if (piece != null) {
                pieces.add(piece)
            }
        }

        return ScanResult(boardMask, grid, pieces, filledCount)
    }

    private fun extractPieceFromSlot(
        bitmap: Bitmap,
        slotIndex: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        miniCell: Float,
        satTh: Float,
        valTh: Float
    ): PieceShape? {
        val xs = ArrayList<Int>(256)
        val ys = ArrayList<Int>(256)

        val step = 2
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                if (isBlockPixel(bitmap.getPixel(x, y), satTh, valTh)) {
                    xs.add(x)
                    ys.add(y)
                }
                x += step
            }
            y += step
        }

        if (xs.size < 25) return null

        xs.sort()
        ys.sort()
        val trim = (xs.size * 0.02f).toInt()
        val minX = xs[trim].toFloat()
        val maxX = xs[xs.size - 1 - trim].toFloat()
        val minY = ys[trim].toFloat()
        val maxY = ys[ys.size - 1 - trim].toFloat()

        val bw = maxX - minX
        val bh = maxY - minY
        if (bw < miniCell * 0.45f || bh < miniCell * 0.45f) return null

        val cols = max(1, min(5, (bw / miniCell).roundToInt()))
        val rows = max(1, min(5, (bh / miniCell).roundToInt()))

        val subW = bw / cols
        val subH = bh / rows
        val cells = mutableListOf<Pair<Int, Int>>()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cLeft = minX + c * subW
                val cTop = minY + r * subH
                var hits = 0
                val sampleN = 4
                for (sy in 0 until sampleN) {
                    val py = (cTop + subH * (0.28f + 0.44f * sy / (sampleN - 1))).toInt()
                        .coerceIn(0, bitmap.height - 1)
                    for (sx in 0 until sampleN) {
                        val px = (cLeft + subW * (0.28f + 0.44f * sx / (sampleN - 1))).toInt()
                            .coerceIn(0, bitmap.width - 1)
                        if (isBlockPixel(bitmap.getPixel(px, py), satTh, valTh)) {
                            hits++
                        }
                    }
                }
                if (hits >= 5) {
                    cells.add(Pair(r, c))
                }
            }
        }

        if (cells.isEmpty()) return null

        val minR = cells.minOf { it.first }
        val maxR = cells.maxOf { it.first }
        val minC = cells.minOf { it.second }
        val maxC = cells.maxOf { it.second }
        val normCells = cells.map { Pair(it.first - minR, it.second - minC) }

        return PieceShape(
            slotIndex = slotIndex,
            rows = maxR - minR + 1,
            cols = maxC - minC + 1,
            cells = normCells,
            centerX = (minX + maxX) / 2f,
            centerY = (minY + maxY) / 2f
        )
    }
}
