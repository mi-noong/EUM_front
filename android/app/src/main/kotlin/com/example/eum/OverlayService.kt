package com.example.eum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val CHANNEL_ID = "OverlayServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "OverlayService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate 시작")
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            addOverlayView()
            Log.d(TAG, "OverlayService onCreate 완료")
        } catch (e: Exception) {
            Log.e(TAG, "OverlayService onCreate 실패: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand 호출")
        try {
            // 서비스가 시작되었음을 MainActivity에 알림
            val broadcastIntent = Intent("OVERLAY_SERVICE_STARTED")
            sendBroadcast(broadcastIntent)
            Log.d(TAG, "OVERLAY_SERVICE_STARTED 브로드캐스트 전송")
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 전송 실패: ${e.message}")
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Overlay Service Channel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Overlay service notification channel"
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
                .setContentTitle("EUM")
                .setContentText("오버레이 서비스가 실행 중입니다")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "알림 생성 실패, 기본 알림 사용: ${e.message}")
            // 기본 알림 생성
            NotificationCompat.Builder(this, "default")
                .setContentTitle("EUM")
                .setContentText("오버레이 서비스가 실행 중입니다")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private fun addOverlayView() {
        try {
            Log.d(TAG, "오버레이 뷰 추가 시작")
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(com.example.eum.R.layout.overlay_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.BOTTOM

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "오버레이 뷰 추가 완료")

            // 스와이프 제스처 감지
            val handle = overlayView?.findViewById<View>(com.example.eum.R.id.handle)
            val menu = overlayView?.findViewById<LinearLayout>(com.example.eum.R.id.menuLayout)
            var isMenuOpen = false

            handle?.setOnTouchListener(object : View.OnTouchListener {
                private var startY = 0f
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    try {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> startY = event.rawY
                            MotionEvent.ACTION_UP -> {
                                val deltaY = startY - event.rawY
                                if (deltaY > 100 && !isMenuOpen) {
                                    menu?.visibility = View.VISIBLE
                                    isMenuOpen = true
                                    Log.d(TAG, "메뉴 열기")
                                } else if (deltaY < -100 && isMenuOpen) {
                                    menu?.visibility = View.GONE
                                    isMenuOpen = false
                                    Log.d(TAG, "메뉴 닫기")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "터치 이벤트 처리 실패: ${e.message}")
                    }
                    return true
                }
            })

            // 이미지 분석 버튼 클릭 이벤트
            val btnOcr = overlayView?.findViewById<LinearLayout>(com.example.eum.R.id.btn_ocr)
            Log.d(TAG, "이미지 분석 버튼 찾기: ${btnOcr != null}")
            btnOcr?.setOnClickListener {
                try {
                    Log.d(TAG, "이미지 분석 버튼 클릭됨!")
                    // Flutter 앱에 이미지 분석 모드 시작을 알림
                    val intent = Intent("START_IMAGE_ANALYSIS")
                    intent.setPackage(packageName) // 명시적으로 패키지 지정
                    sendBroadcast(intent)
                    Log.d(TAG, "START_IMAGE_ANALYSIS 브로드캐스트 전송 완료 - 패키지: $packageName")
                    
                    // 메뉴 닫기
                    menu?.visibility = View.GONE
                    isMenuOpen = false
                    Log.d(TAG, "메뉴 닫기 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "이미지 분석 버튼 클릭 처리 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 뷰 추가 실패: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy 시작")
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                Log.d(TAG, "오버레이 뷰 제거 완료")
            }
            // MediaProjectionService도 함께 종료
            stopService(Intent(this, MediaProjectionService::class.java))
            // 서비스가 종료되었음을 MainActivity에 알림
            val broadcastIntent = Intent("OVERLAY_SERVICE_STOPPED")
            sendBroadcast(broadcastIntent)
            Log.d(TAG, "OVERLAY_SERVICE_STOPPED 브로드캐스트 전송")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 처리 실패: ${e.message}")
        }
    }
}
