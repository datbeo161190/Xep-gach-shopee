package com.autobot.shopeeblock

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: CalibrationPrefs
    private lateinit var tvPermStatus: TextView

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Đã bật thanh nổi! Hãy mở game Shopee.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Bạn cần cho phép quay màn hình để tool nhận diện bảng.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = CalibrationPrefs(this)
        tvPermStatus = findViewById(R.id.tvPermStatus)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAccessibilityPerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Tìm và bật 'Auto Xếp Gạch - Tự Kéo Thả' trong Dịch vụ đã cài đặt.",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Vui lòng cấp quyền Hiển thị trên ứng dụng khác trước!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AutoGestureService.instance == null) {
                Toast.makeText(this, "Vui lòng bật Dịch vụ Trợ năng trước!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        setupSliders()
    }

    private fun setupSliders() {
        val tvLift = findViewById<TextView>(R.id.tvLiftOffset)
        val seekLift = findViewById<SeekBar>(R.id.seekLiftOffset)
        seekLift.progress = (prefs.liftOffsetCells * 10).toInt().coerceIn(0, 45)
        tvLift.text = String.format(Locale.US, "Độ nâng khối trên ngón tay: %.1f ô", prefs.liftOffsetCells)
        seekLift.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = p / 10f
            prefs.liftOffsetCells = v
            tvLift.text = String.format(Locale.US, "Độ nâng khối trên ngón tay: %.1f ô", v)
        })

        val tvScale = findViewById<TextView>(R.id.tvMiniScale)
        val seekScale = findViewById<SeekBar>(R.id.seekMiniScale)
        seekScale.progress = ((prefs.miniCellRatio - 0.30f) * 100).toInt().coerceIn(0, 40)
        tvScale.text = String.format(Locale.US, "Tỷ lệ ô khối chờ (so với bảng 8x8): %.2f", prefs.miniCellRatio)
        seekScale.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val v = 0.30f + p / 100f
            prefs.miniCellRatio = v
            tvScale.text = String.format(Locale.US, "Tỷ lệ ô khối chờ (so với bảng 8x8): %.2f", v)
        })

        val tvDrag = findViewById<TextView>(R.id.tvDragDuration)
        val seekDrag = findViewById<SeekBar>(R.id.seekDragDuration)
        seekDrag.progress = ((prefs.dragDurationMs - 150L) / 10L).toInt().coerceIn(0, 50)
        tvDrag.text = "Thời gian kéo 1 khối: ${prefs.dragDurationMs} ms"
        seekDrag.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val ms = 150L + p * 10L
            prefs.dragDurationMs = ms
            tvDrag.text = "Thời gian kéo 1 khối: $ms ms"
        })

        val tvDelay = findViewById<TextView>(R.id.tvTurnDelay)
        val seekDelay = findViewById<SeekBar>(R.id.seekTurnDelay)
        seekDelay.progress = ((prefs.turnDelayMs - 350L) / 100L).toInt().coerceIn(0, 20)
        tvDelay.text = "Độ trễ chờ nạp 3 khối mới: ${prefs.turnDelayMs} ms"
        seekDelay.setOnSeekBarChangeListener(simpleSeekListener { p ->
            val ms = 350L + p * 100L
            prefs.turnDelayMs = ms
            tvDelay.text = "Độ trễ chờ nạp 3 khối mới: $ms ms"
        })
    }

    private fun simpleSeekListener(onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    override fun onResume() {
        super.onResume()
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = AutoGestureService.instance != null
        tvPermStatus.text = buildString {
            append("• Hiển thị trên ứng dụng khác: ")
            append(if (overlayOk) "ĐÃ CẤP ✓
" else "CHƯA CẤP ✗
")
            append("• Dịch vụ Trợ năng (Kéo thả): ")
            append(if (accOk) "ĐÃ BẬT ✓" else "CHƯA BẬT ✗")
        }
    }
}
