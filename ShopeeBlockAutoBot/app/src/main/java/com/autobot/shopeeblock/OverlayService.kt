package com.autobot.shopeeblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val CHANNEL_ID = "shopee_block_bot_channel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: CalibrationPrefs
    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var recognizer: BoardRecognizer
    private val solver = BlockSolver()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var autoJob: Job? = null

    private var controlView: View? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var calibrationView: CalibrationOverlayView? = null

    private lateinit var btnAuto: Button
    private lateinit var btnCalibrate: Button
    private lateinit var tvStatus: TextView

    private var isCalibrating = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = CalibrationPrefs(this)
        captureManager = ScreenCaptureManager(this)
        recognizer = BoardRecognizer(prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultData != null) {
            val ok = captureManager.start(resultCode, resultData)
            if (controlView == null) {
                showControlOverlay()
            }
            updateStatus(if (ok) "Sẵn sàng. Kéo ☰ lên trên bảng điểm." else "Lỗi khởi tạo quay màn hình!")
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto Xếp Gạch Shopee",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Xếp Gạch Shopee đang chạy")
            .setContentText("Sử dụng thanh nổi để căn khung và tự động xếp gạch.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun showControlOverlay() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null)
        controlView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 110
        }
        controlParams = params

        btnAuto = view.findViewById(R.id.btnAuto)
        btnCalibrate = view.findViewById(R.id.btnCalibrate)
        tvStatus = view.findViewById(R.id.tvStatus)

        val btnStep: Button = view.findViewById(R.id.btnStep)
        val btnTestScan: Button = view.findViewById(R.id.btnTestScan)
        val btnClose: Button = view.findViewById(R.id.btnClose)
        val dragHandle: View = view.findViewById(R.id.tvDragHandle)

        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x
                        initY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initX + (event.rawX - touchX).toInt()
                        params.y = initY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                }
                return false
            }
        })

        btnCalibrate.setOnClickListener { toggleCalibrationOverlay() }
        btnTestScan.setOnClickListener { runTestScan() }
        btnStep.setOnClickListener { runOneTurn() }
        btnAuto.setOnClickListener { toggleAutoLoop() }
        btnClose.setOnClickListener { stopSelf() }

        windowManager.addView(view, params)
    }

    private fun toggleCalibrationOverlay() {
        stopAutoLoop()
        if (isCalibrating) {
            removeCalibrationOverlay()
            isCalibrating = false
            btnCalibrate.text = "Khung"
            updateStatus("Đã lưu tọa độ khung!")
        } else {
            showCalibrationOverlay(interactive = true, debugScan = null)
            isCalibrating = true
            btnCalibrate.text = "Lưu"
            updateStatus("Kéo các chấm vàng cho khớp 8x8 & 3 khối.")
        }
    }

    private fun showCalibrationOverlay(interactive: Boolean, debugScan: ScanResult?) {
        removeCalibrationOverlay()
        val calView = CalibrationOverlayView(
            this,
            prefs,
            captureManager.screenWidth,
            captureManager.screenHeight
        ).apply {
            interactiveMode = interactive
            this.debugScan = debugScan
        }

        val flags = if (interactive) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )

        calibrationView = calView
        windowManager.addView(calView, params)
        controlView?.let { cv ->
            controlParams?.let { cp ->
                windowManager.removeView(cv)
                windowManager.addView(cv, cp)
            }
        }
    }

    private fun removeCalibrationOverlay() {
        calibrationView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        calibrationView = null
    }

    private fun runTestScan() {
        stopAutoLoop()
        if (isCalibrating) toggleCalibrationOverlay()

        serviceScope.launch {
            updateStatus("Đang chụp & phân tích...")
            val scan = withContext(Dispatchers.Default) {
                val bmp = captureManager.captureBitmap() ?: return@withContext null
                try {
                    recognizer.analyze(bmp)
                } finally {
                    bmp.recycle()
                }
            }
            if (scan == null) {
                updateStatus("Không chụp được màn hình!")
                return@launch
            }
            val pieceDesc = scan.pieces.joinToString(", ") { it.describe() }.ifEmpty { "0 khối" }
            updateStatus("Bảng: ${scan.filledCount}/64 ô | Khối: $pieceDesc")
            showCalibrationOverlay(interactive = false, debugScan = scan)
            delay(2500)
            if (!isCalibrating) removeCalibrationOverlay()
        }
    }

    private fun runOneTurn() {
        stopAutoLoop()
        if (isCalibrating) toggleCalibrationOverlay()
        autoJob = serviceScope.launch {
            executeSingleTurn()
        }
    }

    private fun toggleAutoLoop() {
        if (autoJob?.isActive == true) {
            stopAutoLoop()
            return
        }
        if (isCalibrating) toggleCalibrationOverlay()

        btnAuto.text = "Dừng"
        autoJob = serviceScope.launch {
            var emptyRetries = 0
            while (isActive) {
                val ok = executeSingleTurn()
                if (!ok) {
                    emptyRetries++
                    if (emptyRetries >= 3) {
                        updateStatus("Đã dừng: Hết nước đi hoặc không thấy khối.")
                        break
                    }
                    delay(800)
                } else {
                    emptyRetries = 0
                    delay(prefs.turnDelayMs)
                }
            }
            btnAuto.text = "Auto"
        }
    }

    private fun stopAutoLoop() {
        autoJob?.cancel()
        autoJob = null
        if (::btnAuto.isInitialized) {
            btnAuto.text = "Auto"
        }
    }

    private suspend fun executeSingleTurn(): Boolean {
        val gestureService = AutoGestureService.instance
        if (gestureService == null) {
            updateStatus("Chưa bật Quyền Trợ năng (Accessibility)!")
            return false
        }

        val scan = withContext(Dispatchers.Default) {
            val bmp = captureManager.captureBitmap() ?: return@withContext null
            try {
                recognizer.analyze(bmp)
            } finally {
                bmp.recycle()
            }
        }

        if (scan == null) {
            updateStatus("Lỗi chụp ảnh màn hình.")
            return false
        }

        if (scan.pieces.isEmpty()) {
            updateStatus("Đang chờ khối mới xuất hiện...")
            return false
        }

        updateStatus("AI đang tính (${scan.pieces.size} khối)...")
        val moves = withContext(Dispatchers.Default) {
            solver.solveBestSequence(scan.boardMask, scan.pieces)
        }

        if (moves.isNullOrEmpty()) {
            updateStatus("Hết nước đi hợp lệ! Hãy dùng trợ giúp.")
            return false
        }

        val boardRect = prefs.getBoardRectPx(captureManager.screenWidth, captureManager.screenHeight)
        val cellW = boardRect.width() / 8f
        val cellH = boardRect.height() / 8f
        val liftPx = prefs.liftOffsetCells * cellH
        val maxY = (captureManager.screenHeight - 16).toFloat()

        for ((idx, move) in moves.withIndex()) {
            val p = move.piece
            val targetCenterX = boardRect.left + (move.col + p.cols / 2f) * cellW
            val targetCenterY = boardRect.top + (move.row + p.rows / 2f) * cellH
            val endY = (targetCenterY + liftPx).coerceAtMost(maxY)

            updateStatus("Kéo khối ${idx + 1}/${moves.size} -> hàng ${move.row + 1}, cột ${move.col + 1}")
            val success = gestureService.performDragAndDrop(
                startX = p.centerX,
                startY = p.centerY,
                endX = targetCenterX,
                endY = endY,
                dragDurationMs = prefs.dragDurationMs
            )

            if (!success) {
                updateStatus("Kéo thả bị hủy.")
                return false
            }
            delay(if (move.clearedLines > 0) 480L else 280L)
        }

        updateStatus("Xong lượt (${moves.size} khối)!")
        return true
    }

    private fun updateStatus(text: String) {
        if (::tvStatus.isInitialized) {
            tvStatus.text = text
        }
    }

    override fun onDestroy() {
        stopAutoLoop()
        serviceScope.cancel()
        removeCalibrationOverlay()
        controlView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        captureManager.release()
        super.onDestroy()
    }
}
