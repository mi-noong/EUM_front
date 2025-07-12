package com.example.eum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayCaptureService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: CaptureOverlayView? = null
    private val CHANNEL_ID = "OverlayCaptureServiceChannel"
    private val NOTIFICATION_ID = 2
    private val TAG = "OverlayCaptureService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayCaptureService onCreate 시작")
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            showOverlay()
            Log.d(TAG, "OverlayCaptureService onCreate 완료")
        } catch (e: Exception) {
            Log.e(TAG, "OverlayCaptureService onCreate 실패: ${e.message}")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Overlay Capture Service Channel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Overlay capture service notification channel"
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "알림 채널 생성 완료")
            } catch (e: Exception) {
                Log.e(TAG, "알림 채널 생성 실패: ${e.message}")
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        return try {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EUM - 화면 캡처")
                .setContentText("드래그하여 캡처할 영역을 선택하세요")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "알림 생성 실패, 기본 알림 사용: ${e.message}")
            NotificationCompat.Builder(this, "default")
                .setContentTitle("EUM - 화면 캡처")
                .setContentText("드래그하여 캡처할 영역을 선택하세요")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun showOverlay() {
        try {
            Log.d(TAG, "오버레이 캡처 뷰 추가 시작")
            
            overlayView = CaptureOverlayView(this) { rect ->
                Log.d(TAG, "드래그 완료: $rect")
                requestScreenCapture(rect)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "오버레이 캡처 뷰 추가 완료")
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 캡처 뷰 추가 실패: ${e.message}")
            stopSelf()
        }
    }

    private fun requestScreenCapture(rect: android.graphics.Rect) {
        try {
            Log.d(TAG, "화면 캡처 요청: $rect")
            // CAPTURE_IMAGE가 아니라 ScreenCaptureActivity를 실행
            val intent = Intent(this, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("rect_left", rect.left)
            intent.putExtra("rect_top", rect.top)
            intent.putExtra("rect_right", rect.right)
            intent.putExtra("rect_bottom", rect.bottom)
            startActivity(intent)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "화면 캡처 요청 실패: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayCaptureService onDestroy 시작")
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                Log.d(TAG, "오버레이 캡처 뷰 제거 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 처리 실패: ${e.message}")
        }
    }
}
