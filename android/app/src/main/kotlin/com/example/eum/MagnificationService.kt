package com.example.eum

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.graphics.Rect

class MagnificationService : AccessibilityService() {
    private val TAG = "MagnificationService"
    private var isMagnificationEnabled = false
    private var currentScale = 1.0f
    private var currentCenterX = 0f
    private var currentCenterY = 0f
    private var magnificationReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "확대 서비스 연결됨")

        try {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }

            // BroadcastReceiver 생성 및 등록
            setupMagnificationReceiver()

            Log.d(TAG, "MagnificationService 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "MagnificationService 초기화 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupMagnificationReceiver() {
        try {
            // 기존 리시버가 있으면 제거
            magnificationReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.w(TAG, "기존 리시버 제거 실패: ${e.message}")
                }
            }

            // 새로운 리시버 생성
            magnificationReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    try {
                        when (intent?.action) {
                            "ACTION_ENABLE_MAGNIFICATION" -> {
                                Log.d(TAG, "확대 기능 활성화 요청 수신")
                                if (!isMagnificationEnabled) {
                                    enableMagnification()
                                }
                            }
                            "ACTION_DISABLE_MAGNIFICATION" -> {
                                Log.d(TAG, "확대 기능 비활성화 요청 수신")
                                if (isMagnificationEnabled) {
                                    disableMagnification()
                                }
                            }
                            "ACTION_TOGGLE_MAGNIFICATION" -> {
                                Log.d(TAG, "확대 기능 토글 요청 수신")
                                if (isMagnificationEnabled) {
                                    disableMagnification()
                                } else {
                                    enableMagnification()
                                }
                            }
                            "ACTION_SET_MAGNIFICATION_SCALE" -> {
                                val scale = intent.getFloatExtra("scale", 2.0f)
                                Log.d(TAG, "확대 배율 설정 요청 수신: $scale")
                                setMagnificationScale(scale)
                            }
                            "ACTION_HIDE_MAGNIFIER" -> {
                                Log.d(TAG, "확대창 숨기기 요청 수신")
                                try {
                                    // ScreenCaptureAccessibilityService의 hideMagnifier 호출 시도
                                    val screenCaptureService = com.example.eum.ScreenCaptureAccessibilityService.instance
                                    if (screenCaptureService != null) {
                                        Log.d(TAG, "ScreenCaptureAccessibilityService 인스턴스 발견, hideMagnifier 호출")
                                        // 리플렉션을 사용하여 hideMagnifier 함수 호출
                                        val hideMagnifierMethod = screenCaptureService.javaClass.getDeclaredMethod("hideMagnifier")
                                        hideMagnifierMethod.isAccessible = true
                                        hideMagnifierMethod.invoke(screenCaptureService)
                                        Log.d(TAG, "✅ hideMagnifier 호출 성공")
                                    } else {
                                        Log.w(TAG, "ScreenCaptureAccessibilityService 인스턴스가 null입니다")
                                        // 강력한 확대창 제거 시도
                                        Log.d(TAG, "강력한 확대창 제거 시도 시작")
                                        
                                        // 1. 현재 확대 기능 완전 비활성화
                                        disableMagnification()
                                        
                                        // 2. 시스템 확대 기능 강제로 끄기 (여러 번 시도)
                                        try {
                                            if (magnificationController != null) {
                                                // 첫 번째 시도
                                                magnificationController.reset(true)
                                                Log.d(TAG, "✅ 첫 번째 시스템 확대 기능 강제 비활성화 완료")
                                                
                                                // 잠시 대기 후 두 번째 시도
                                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                    try {
                                                        magnificationController.reset(true)
                                                        Log.d(TAG, "✅ 두 번째 시스템 확대 기능 강제 비활성화 완료")
                                                    } catch (e: Exception) {
                                                        Log.w(TAG, "두 번째 시스템 확대 기능 강제 비활성화 실패: ${e.message}")
                                                    }
                                                }, 100)
                                                
                                                // 추가로 scale을 1.0으로 강제 설정
                                                try {
                                                    magnificationController.setScale(1.0f, true)
                                                    magnificationController.setCenter(0f, 0f, true)
                                                    Log.d(TAG, "✅ 확대 배율을 1.0으로 강제 설정 완료")
                                                } catch (e: Exception) {
                                                    Log.w(TAG, "확대 배율 강제 설정 실패: ${e.message}")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "시스템 확대 기능 강제 비활성화 실패: ${e.message}")
                                        }
                                        
                                        // 3. 추가 브로드캐스트로 다른 서비스들에 알림
                                        try {
                                            val forceHideIntent = Intent("FORCE_HIDE_ALL_MAGNIFIERS")
                                            forceHideIntent.setPackage(packageName)
                                            sendBroadcast(forceHideIntent)
                                            Log.d(TAG, "✅ FORCE_HIDE_ALL_MAGNIFIERS 브로드캐스트 전송 완료")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "FORCE_HIDE_ALL_MAGNIFIERS 브로드캐스트 전송 실패: ${e.message}")
                                        }
                                        
                                        Log.d(TAG, "강력한 확대창 제거 시도 완료")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "hideMagnifier 호출 실패: ${e.message}")
                                    // 예외 발생 시에도 강력한 확대창 제거 시도
                                    disableMagnification()
                                    try {
                                        if (magnificationController != null) {
                                            magnificationController.reset(true)
                                            Log.d(TAG, "✅ 예외 발생 후 시스템 확대 기능 강제 비활성화 완료")
                                        }
                                    } catch (e2: Exception) {
                                        Log.w(TAG, "예외 발생 후 시스템 확대 기능 강제 비활성화 실패: ${e2.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "브로드캐스트 처리 중 오류: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }

            // IntentFilter 생성
            val filter = IntentFilter().apply {
                addAction("ACTION_ENABLE_MAGNIFICATION")
                addAction("ACTION_DISABLE_MAGNIFICATION")
                addAction("ACTION_TOGGLE_MAGNIFICATION")
                addAction("ACTION_SET_MAGNIFICATION_SCALE")
                addAction("ACTION_HIDE_MAGNIFIER")
            }

            // 리시버 등록 (안전하게)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ (API 33+)
                    registerReceiver(magnificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    // Android 12 이하
                    registerReceiver(magnificationReceiver, filter)
                }
                Log.d(TAG, "MagnificationReceiver 등록 성공")
            } catch (e: Exception) {
                Log.e(TAG, "MagnificationReceiver 등록 실패: ${e.message}")
                e.printStackTrace()
            }

        } catch (e: Exception) {
            Log.e(TAG, "MagnificationReceiver 설정 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun enableMagnification() {
        try {
            Log.d(TAG, "=== 확대 기능 활성화 시작 ===")

            // magnificationController null 체크
            if (magnificationController == null) {
                Log.e(TAG, "❌ magnificationController가 null입니다!")
                Log.e(TAG, "AccessibilityService가 완전히 연결되지 않았습니다.")
                return
            }

            // 기본 확대 설정
            currentScale = 2.0f
            currentCenterX = 500f
            currentCenterY = 500f

            Log.d(TAG, "확대 설정 적용 중: 배율=$currentScale, 중심=($currentCenterX, $currentCenterY)")

            magnificationController.setScale(currentScale, false)
            magnificationController.setCenter(currentCenterX, currentCenterY, false)

            isMagnificationEnabled = true
            Log.d(TAG, "✅ 확대 기능 활성화 완료: 배율=$currentScale, 중심=($currentCenterX, $currentCenterY)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 확대 기능 활성화 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun disableMagnification() {
        try {
            Log.d(TAG, "=== 확대 기능 비활성화 시작 ===")

            if (magnificationController == null) {
                Log.e(TAG, "❌ magnificationController가 null입니다!")
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+
                magnificationController.reset(false)
            } else {
                // 구버전 호환
                magnificationController.setScale(1.0f, false)
                magnificationController.setCenter(0f, 0f, false)
            }

            isMagnificationEnabled = false
            Log.d(TAG, "✅ 확대 기능 비활성화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 확대 기능 비활성화 실패: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun setMagnificationScale(scale: Float) {
        try {
            Log.d(TAG, "=== 확대 배율 변경 시작: $scale ===")

            // magnificationController null 체크
            if (magnificationController == null) {
                Log.e(TAG, "❌ magnificationController가 null입니다!")
                return
            }

            currentScale = scale.coerceIn(1.0f, 5.0f) // 1.0x ~ 5.0x 범위로 제한
            magnificationController.setScale(currentScale, false)
            Log.d(TAG, "✅ 확대 배율 변경 완료: $currentScale")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 확대 배율 변경 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }

    override fun onInterrupt() { }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // 리시버 정리
            magnificationReceiver?.let {
                try {
                    unregisterReceiver(it)
                    Log.d(TAG, "MagnificationReceiver 정리 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "MagnificationReceiver 정리 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 처리 실패: ${e.message}")
        }
    }
}
