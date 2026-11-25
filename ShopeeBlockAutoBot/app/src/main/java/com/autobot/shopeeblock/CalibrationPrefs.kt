package com.autobot.shopeeblock

import android.content.Context
import android.graphics.RectF

class CalibrationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shopee_block_prefs", Context.MODE_PRIVATE)

    var boardLeft: Float
        get() = prefs.getFloat("board_left", 0.045f)
        set(v) = prefs.edit().putFloat("board_left", v).apply()

    var boardTop: Float
        get() = prefs.getFloat("board_top", 0.255f)
        set(v) = prefs.edit().putFloat("board_top", v).apply()

    var boardRight: Float
        get() = prefs.getFloat("board_right", 0.955f)
        set(v) = prefs.edit().putFloat("board_right", v).apply()

    var boardBottom: Float
        get() = prefs.getFloat("board_bottom", 0.675f)
        set(v) = prefs.edit().putFloat("board_bottom", v).apply()

    var trayTop: Float
        get() = prefs.getFloat("tray_top", 0.700f)
        set(v) = prefs.edit().putFloat("tray_top", v).apply()

    var trayBottom: Float
        get() = prefs.getFloat("tray_bottom", 0.835f)
        set(v) = prefs.edit().putFloat("tray_bottom", v).apply()

    var liftOffsetCells: Float
        get() = prefs.getFloat("lift_offset_cells", 2.1f)
        set(v) = prefs.edit().putFloat("lift_offset_cells", v).apply()

    var miniCellRatio: Float
        get() = prefs.getFloat("mini_cell_ratio", 0.44f)
        set(v) = prefs.edit().putFloat("mini_cell_ratio", v).apply()

    var dragDurationMs: Long
        get() = prefs.getLong("drag_duration_ms", 320L)
        set(v) = prefs.edit().putLong("drag_duration_ms", v).apply()

    var turnDelayMs: Long
        get() = prefs.getLong("turn_delay_ms", 950L)
        set(v) = prefs.edit().putLong("turn_delay_ms", v).apply()

    var satThreshold: Float
        get() = prefs.getFloat("sat_threshold", 0.34f)
        set(v) = prefs.edit().putFloat("sat_threshold", v).apply()

    var valThreshold: Float
        get() = prefs.getFloat("val_threshold", 0.46f)
        set(v) = prefs.edit().putFloat("val_threshold", v).apply()

    fun getBoardRectPx(screenW: Int, screenH: Int): RectF = RectF(
        boardLeft * screenW,
        boardTop * screenH,
        boardRight * screenW,
        boardBottom * screenH
    )

    fun getTrayRectPx(screenW: Int, screenH: Int): RectF = RectF(
        boardLeft * screenW,
        trayTop * screenH,
        boardRight * screenW,
        trayBottom * screenH
    )

    fun saveBoardAndTrayPx(board: RectF, tray: RectF, screenW: Int, screenH: Int) {
        prefs.edit()
            .putFloat("board_left", (board.left / screenW).coerceIn(0f, 0.9f))
            .putFloat("board_top", (board.top / screenH).coerceIn(0f, 0.9f))
            .putFloat("board_right", (board.right / screenW).coerceIn(0.1f, 1f))
            .putFloat("board_bottom", (board.bottom / screenH).coerceIn(0.1f, 1f))
            .putFloat("tray_top", (tray.top / screenH).coerceIn(0.1f, 0.95f))
            .putFloat("tray_bottom", (tray.bottom / screenH).coerceIn(0.15f, 1f))
            .apply()
    }
}
