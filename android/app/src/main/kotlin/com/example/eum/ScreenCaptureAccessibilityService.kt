package com.example.eum

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Button
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlin.math.max
import kotlin.math.min

class ScreenCaptureAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ScreenCaptureAccessibilityService"
        
        // 정적 인스턴스 참조 추가
        @JvmStatic
        var instance: ScreenCaptureAccessibilityService? = null
            private set
            
        // 정적 hideMagnifier 함수 추가
        @JvmStatic
        fun hideMagnifierStatic(): Boolean {
            return instance?.let { service ->
                try {
                    Log.d(TAG, "=== 정적 hideMagnifier() 호출됨 ===")
                    service.hideMagnifier()
                    Log.d(TAG, "✅ 정적 hideMagnifier() 완료")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 정적 hideMagnifier() 실패: ${e.message}")
                    e.printStackTrace()
                    false
                }
            } ?: run {
                Log.w(TAG, "⚠️ ScreenCaptureAccessibilityService 인스턴스가 null입니다")
                false
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var magnifierView: View? = null
    private var controlView: View? = null

    // magnificationController 관련 변수
    private var isMagnificationActive = false

    // 확대 설정
    private var magnifierScale = 2.0f
    private var magnifierSize = 200
    private var isDraggable = true

    // 확대창 표시 상태 추적
    private var isMagnifierShowing = false

    // 현재 확대 위치
    private var currentMagnificationCenterX = 0f
    private var currentMagnificationCenterY = 0f

    // 브로드캐스트 리시버
    private var magnifierReceiver: BroadcastReceiver? = null

    // magnificationController를 안전하게 가져오는 함수
    @Suppress("DEPRECATION")
    private fun getSystemMagnificationController(): Any? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val accessibilityManager =
                    getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val magnificationControllerClass =
                    Class.forName("android.view.accessibility.AccessibilityManager\$MagnificationController")
                val getMagnificationControllerMethod =
                    accessibilityManager.javaClass.getMethod("getMagnificationController")
                getMagnificationControllerMethod.invoke(accessibilityManager)
            } catch (e: Exception) {
                Log.e(TAG, "magnificationController 가져오기 실패: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "🎉 === ScreenCaptureAccessibilityService 연결됨! ===")
        
        // 정적 인스턴스 참조 설정
        instance = this

        // AccessibilityService 설정 - 안드로이드 기본 확대 기능과 동일하게 설정
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            var flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON or
                    AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES

            // FLAG_REQUEST_TOUCH_INTERACTION_BOUNDS는 API 29 (Android 10) 이상에서만 사용 가능
            // 리플렉션을 사용하여 안전하게 접근
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val touchInteractionBoundsField = AccessibilityServiceInfo::class.java.getField("FLAG_REQUEST_TOUCH_INTERACTION_BOUNDS")
                    val touchInteractionBoundsFlag = touchInteractionBoundsField.getInt(null)
                    flags = flags or touchInteractionBoundsFlag
                    Log.d(TAG, "FLAG_REQUEST_TOUCH_INTERACTION_BOUNDS 추가됨")
                } catch (e: Exception) {
                    Log.w(TAG, "FLAG_REQUEST_TOUCH_INTERACTION_BOUNDS를 찾을 수 없습니다: ${e.message}")
                }
            }

            this.flags = flags
            notificationTimeout = 100
        }
        serviceInfo = info

        // magnificationController는 Android 8.0 (API 26) 이상에서만 지원됩니다
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "magnificationController 사용 가능")
        } else {
            Log.w(TAG, "magnificationController는 Android 8.0 (API 26) 이상에서만 지원됩니다")
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 서비스 상태 확인 및 로깅
        checkServiceStatus()

        // 브로드캐스트 리시버 설정
        setupBroadcastReceiver()
        Log.d(TAG, "AccessibilityService 초기화 완료")
    }

    private fun checkServiceStatus() {
        try {
            val accessibilityManager =
                getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

            Log.d(TAG, "활성화된 AccessibilityService 목록:")
            enabledServices.forEach { service ->
                Log.d(TAG, "- ${service.id}")
            }

            // 현재 서비스가 활성화되어 있는지 확인
            val currentServiceId = "${packageName}/${javaClass.name}"
            val isEnabled = enabledServices.any { it.id == currentServiceId }
            Log.d(TAG, "현재 서비스 ID: $currentServiceId")
            Log.d(TAG, "현재 서비스 활성화 상태: $isEnabled")

            if (!isEnabled) {
                Log.w(TAG, "⚠️ AccessibilityService가 활성화되지 않았습니다!")
                Log.w(TAG, "사용자가 설정 > 접근성 > EUM에서 서비스를 활성화해야 합니다.")
            } else {
                Log.d(TAG, "✅ AccessibilityService가 정상적으로 활성화되었습니다!")
            }

        } catch (e: Exception) {
            Log.e(TAG, "AccessibilityService 상태 확인 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 안드로이드 기본 확대 기능과 동일한 제스처 처리
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                    Log.d(TAG, "터치 상호작용 시작 감지")
                }
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                    Log.d(TAG, "터치 상호작용 종료 감지")
                }
                AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                    Log.d(TAG, "제스처 감지 시작")
                }
                AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                    Log.d(TAG, "제스처 감지 종료")
                }
                else -> {
                    // 다른 AccessibilityEvent 타입들은 무시
                }
            }
        }
    }

    override fun onInterrupt() {
        // AccessibilityService 필수 구현 메서드
        // 서비스가 중단될 때 호출됨
    }

    private fun setupBroadcastReceiver() {
        Log.d(TAG, "브로드캐스트 리시버 설정 시작")

        val filter = IntentFilter().apply {
            addAction("START_MAGNIFIER_ACCESSIBILITY")
            addAction("MAGNIFIER_SETTINGS_CHANGED")
            addAction("TOGGLE_MAGNIFICATION")
            addAction("SET_MAGNIFICATION_CENTER")
            addAction("ACTION_ENABLE_MAGNIFICATION")
            addAction("ACTION_DISABLE_MAGNIFICATION")
            addAction("ACTION_TOGGLE_MAGNIFICATION")
            addAction("ACTION_CHECK_MAGNIFICATION_STATUS")
            addAction("MAGNIFICATION_STATUS_CHANGED")
            addAction("MOVE_MAGNIFICATION_BY_GESTURE")
            addAction("MOVE_MAGNIFICATION_TO_EDGE")
            addAction("FORCE_MAGNIFICATION_MOVE")
            addAction("ACTION_HIDE_MAGNIFIER")
        }

        Log.d(TAG, "필터에 추가된 액션들: ${filter.actionsIterator().asSequence().toList()}")

        magnifierReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "=== 브로드캐스트 수신됨! ===")
                Log.d(TAG, "액션: ${intent?.action}")
//                Log.d(TAG, "패키지: ${intent?.package}")
                Log.d(TAG, "데이터: ${intent?.data}")

                when (intent?.action) {
                    "START_MAGNIFIER_ACCESSIBILITY" -> {
                        Log.d(TAG, "확대 기능 시작 요청 수신 - 처리 시작")
                        if (!isMagnifierShowing) {
                            Log.d(TAG, "showMagnifier() 호출")
                            showMagnifier()
                        } else {
                            Log.d(TAG, "확대창이 이미 표시 중입니다")
                        }
                    }

                    "MAGNIFIER_SETTINGS_CHANGED" -> {
                        val scale = intent.getFloatExtra("scale", 2.0f)
                        val size = intent.getIntExtra("size", 200)
                        val draggable = intent.getBooleanExtra("draggable", true)
                        Log.d(TAG, "설정 변경 요청: scale=$scale, size=$size, draggable=$draggable")
                        updateMagnifierSettings(scale, size, draggable)
                    }

                    "ACTION_TOGGLE_MAGNIFICATION" -> {
                        Log.d(TAG, "🔴 ACTION_TOGGLE_MAGNIFICATION 요청 수신 - toggleMagnification() 호출 시작")
                        try {
                            toggleMagnification()
                            Log.d(TAG, "✅ ACTION_TOGGLE_MAGNIFICATION 처리 완료")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ACTION_TOGGLE_MAGNIFICATION 처리 실패: ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    "SET_MAGNIFICATION_CENTER" -> {
                        val centerX = intent.getFloatExtra("centerX", 0f)
                        val centerY = intent.getFloatExtra("centerY", 0f)
                        Log.d(TAG, "확대 중심점 설정 요청: ($centerX, $centerY)")
                        setMagnificationCenter(centerX, centerY)
                    }

                    "ACTION_ENABLE_MAGNIFICATION" -> {
                        Log.d(TAG, "확대 기능 활성화 요청 수신")
                        if (!isMagnificationActive) {
                            startMagnification()
                        }
                    }

                    "ACTION_DISABLE_MAGNIFICATION" -> {
                        Log.d(TAG, "확대 기능 비활성화 요청 수신")
                        if (isMagnificationActive) {
                            stopMagnification()
                        }
                    }

                    "ACTION_CHECK_MAGNIFICATION_STATUS" -> {
                        Log.d(TAG, "확대 기능 상태 확인 요청 수신")
                        // 현재 확대 상태를 브로드캐스트로 응답
                        val responseIntent = Intent("MAGNIFICATION_STATUS_RESPONSE")
                        responseIntent.putExtra("isActive", isMagnificationActive)
                        responseIntent.setPackage(packageName)
                        sendBroadcast(responseIntent)
                        Log.d(TAG, "확대 기능 상태 응답 전송: isActive=$isMagnificationActive")
                    }

                    "ACTION_HIDE_MAGNIFIER" -> {
                        Log.d(TAG, "확대창 숨기기 요청 수신")
                        try {
                            hideMagnifier()
                            Log.d(TAG, "✅ ACTION_HIDE_MAGNIFIER 처리 완료")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ ACTION_HIDE_MAGNIFIER 처리 실패: ${e.message}")
                            e.printStackTrace()
                        }
                    }

                    "MAGNIFICATION_STATUS_CHANGED" -> {
                        val isActive = intent.getBooleanExtra("isActive", false)
                        Log.d(TAG, "확대 기능 상태 변경 알림 수신: isActive=$isActive")
                        isMagnificationActive = isActive
                        Log.d(TAG, "✅ 상태 업데이트 완료: isMagnificationActive=$isMagnificationActive")
                    }

                    "MOVE_MAGNIFICATION_BY_GESTURE" -> {
                        val direction = intent.getStringExtra("direction") ?: "center"
                        val distance = intent.getFloatExtra("distance", 100f)
                        Log.d(TAG, "제스처 기반 확대 영역 이동 요청: direction=$direction, distance=$distance")
                        moveMagnificationByGesture(direction, distance)
                    }

                    "MOVE_MAGNIFICATION_TO_EDGE" -> {
                        val edge = intent.getStringExtra("edge") ?: "center"
                        Log.d(TAG, "가장자리로 확대 영역 이동 요청: edge=$edge")
                        moveMagnificationToEdge(edge)
                    }

                    "FORCE_MAGNIFICATION_MOVE" -> {
                        val centerX = intent.getFloatExtra("centerX", 0f)
                        val centerY = intent.getFloatExtra("centerY", 0f)
                        Log.d(TAG, "강제 확대 영역 이동 요청: ($centerX, $centerY)")
                        // 여러 방법으로 확대 영역 이동 시도
                        setMagnificationCenter(centerX, centerY)
                        moveMagnificationCenter(centerX, centerY)
                        tryAlternativeMagnificationMove(centerX, centerY)
                    }

                    else -> {
                        Log.w(TAG, "알 수 없는 액션: ${intent?.action}")
                    }
                }
                Log.d(TAG, "=== 브로드캐스트 처리 완료 ===")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) { // API level 33 (Android 13)
                // Use reflection to safely access RECEIVER_NOT_EXPORTED constant
                val receiverNotExported = try {
                    Context::class.java.getField("RECEIVER_NOT_EXPORTED").getInt(null)
                } catch (e: Exception) {
                    Log.w(TAG, "RECEIVER_NOT_EXPORTED 상수를 찾을 수 없습니다: ${e.message}")
                    0 // Default value
                }
                registerReceiver(magnifierReceiver, filter, receiverNotExported)
                Log.d(TAG, "✅ API 33 이상에서 브로드캐스트 리시버 등록 완료")
            } else {
                registerReceiver(magnifierReceiver, filter)
                Log.d(TAG, "✅ 일반 브로드캐스트 리시버 등록 완료")
            }
            Log.d(TAG, "✅ 브로드캐스트 리시버 등록 완료 - 필터: $filter")

            // 등록된 리시버 확인
            Log.d(TAG, "등록된 브로드캐스트 리시버 수: ${filter.countActions()}")


        } catch (e: Exception) {
            Log.e(TAG, "❌ 브로드캐스트 리시버 등록 실패: ${e.message}")
            e.printStackTrace()

            // 대안 방법 시도
            try {
                Log.d(TAG, "대안 방법으로 브로드캐스트 리시버 등록 시도...")
                registerReceiver(magnifierReceiver, filter)
                Log.d(TAG, "✅ 대안 방법으로 브로드캐스트 리시버 등록 성공")
            } catch (e2: Exception) {
                Log.e(TAG, "❌ 대안 방법도 실패: ${e2.message}")
                e2.printStackTrace()
            }
        }
    }

    private fun showMagnifier() {
        try {
            Log.d(TAG, "=== showMagnifier() 시작 ===")
            Log.d(TAG, "현재 상태: isMagnifierShowing=$isMagnifierShowing, magnifierView=${magnifierView != null}, controlView=${controlView != null}")

            // 이미 표시 중이면 중복 생성 방지
            if (isMagnifierShowing) {
                Log.d(TAG, "돋보기가 이미 표시 중입니다")
                return
            }

            // AccessibilityService 활성화 상태 재확인
            if (!isAccessibilityServiceEnabled()) {
                Log.e(TAG, "❌ AccessibilityService가 활성화되지 않았습니다!")
                Log.e(TAG, "사용자가 설정 > 접근성 > EUM에서 서비스를 활성화해야 합니다.")
                return
            }

            Log.d(TAG, "돋보기 뷰 생성 시작")
            // 돋보기 뷰 생성 (기본 위치)
            createMagnifierView(100, 200)
            Log.d(TAG, "돋보기 뷰 생성 완료")

            Log.d(TAG, "설정 컨트롤 뷰 생성 시작")
            // 설정 컨트롤 뷰 생성
            createControlView()
            Log.d(TAG, "설정 컨트롤 뷰 생성 완료")

            // 표시 상태 업데이트
            isMagnifierShowing = true
            Log.d(TAG, "✅ isMagnifierShowing = true로 설정")

            Log.d(TAG, "UI 생성 완료 - 화면 확대는 toggleMagnification에서 처리됨")
            Log.d(TAG, "=== showMagnifier() 완료 - 최종 상태: isMagnifierShowing=$isMagnifierShowing ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 돋보기 표시 실패: ${e.message}")
            e.printStackTrace()
            isMagnifierShowing = false
            Log.d(TAG, "✅ 예외 발생 후 isMagnifierShowing을 false로 설정")
        }
    }

    private fun startMagnification() {
        try {
            Log.d(TAG, "=== startMagnification() 시작 ===")
            Log.d(TAG, "현재 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    try {
                        Log.d(TAG, "시스템 magnificationController 사용하여 확대 기능 시작")
                        // 화면 중앙을 기본 확대 위치로 설정
                        val displayMetrics = resources.displayMetrics
                        currentMagnificationCenterX = displayMetrics.widthPixels / 2f
                        currentMagnificationCenterY = displayMetrics.heightPixels / 2f
                        Log.d(TAG, "확대 중심점 설정: ($currentMagnificationCenterX, $currentMagnificationCenterY)")

                        // magnificationController를 사용하여 실제 화면 확대 (리플렉션 사용)
                        val setScaleMethod = controller.javaClass.getMethod(
                            "setScale",
                            Float::class.java,
                            Boolean::class.java
                        )
                        val setCenterMethod = controller.javaClass.getMethod(
                            "setCenter",
                            Float::class.java,
                            Float::class.java,
                            Boolean::class.java
                        )

                        // 안드로이드 기본 확대 기능과 동일한 초기 배율 설정
                        magnifierScale = 2.0f
                        Log.d(TAG, "확대 배율 설정: $magnifierScale")

                        // 확대 기능 시작 (애니메이션 없이)
                        Log.d(TAG, "확대 기능 시작 시도 (setScale 호출)")
                        setScaleMethod.invoke(controller, magnifierScale, false)
                        Log.d(TAG, "✅ setScale 호출 완료")

                        // 확대 중심점 설정 (애니메이션 없이)
                        Log.d(TAG, "확대 중심점 설정 시도 (setCenter 호출)")
                        setCenterMethod.invoke(
                            controller,
                            currentMagnificationCenterX,
                            currentMagnificationCenterY,
                            false
                        )
                        Log.d(TAG, "✅ setCenter 호출 완료")

                        // 상태 업데이트
                        isMagnificationActive = true
                        Log.d(TAG, "✅ 안드로이드 기본 확대 기능과 동일한 화면 확대 시작: scale=$magnifierScale, center=($currentMagnificationCenterX, $currentMagnificationCenterY)")
                        Log.d(TAG, "✅ 확대 기능 활성화 완료 - isMagnificationActive=$isMagnificationActive")

                        // 확대 영역 이동이 제대로 작동하는지 확인
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                Log.d(TAG, "지연된 확대 영역 이동 테스트 시작 (200ms 후)")
                                // 확대 영역 이동 테스트 (현재 위치로 다시 설정)
                                setCenterMethod.invoke(
                                    controller,
                                    currentMagnificationCenterX,
                                    currentMagnificationCenterY,
                                    false
                                )
                                Log.d(TAG, "✅ 확대 영역 이동 테스트 완료")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ 확대 영역 이동 테스트 실패: ${e.message}")
                                e.printStackTrace()
                            }
                        }, 200)

                        // 사용자에게 안내 메시지 표시
                        Log.d(TAG, "사용자 안내 메시지 표시 시도")
                        showMagnificationGuide()
                        Log.d(TAG, "사용자 안내 메시지 표시 완료")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ magnificationController 메서드 호출 실패: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    Log.w(TAG, "⚠️ 시스템 magnificationController가 null입니다")
                }
            } else {
                Log.w(TAG, "⚠️ magnificationController는 Android 8.0 (API 26) 이상에서만 지원됩니다")
            }

            Log.d(TAG, "=== startMagnification() 완료 - 최종 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 화면 확대 시작 실패: ${e.message}")
            e.printStackTrace()
            Log.d(TAG, "예외 발생 후 현재 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")
        }
    }

    private fun stopMagnification() {
        try {
            Log.d(TAG, "=== stopMagnification() 시작 ===")
            Log.d(TAG, "현재 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")

            // 먼저 UI 요소들을 제거
            Log.d(TAG, "🔍 파란색 확대창(돋보기 뷰) 제거 시작 - hideMagnifier() 호출")
            hideMagnifier()
            Log.d(TAG, "✅ hideMagnifier() 호출 완료")

            // 시스템 확대 기능 중지
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    try {
                        Log.d(TAG, "시스템 magnificationController 사용하여 확대 기능 중지")
                        // 여러 번 시도하여 확실하게 확대 기능 끄기
                        val setScaleMethod = controller.javaClass.getMethod(
                            "setScale",
                            Float::class.java,
                            Boolean::class.java
                        )

                        // 첫 번째 시도
                        Log.d(TAG, "첫 번째 확대 중지 시도 시작 (scale=1.0)")
                        setScaleMethod.invoke(controller, 1.0f, true)
                        Log.d(TAG, "✅ 첫 번째 확대 중지 시도 완료")

                        // 잠시 대기 후 두 번째 시도 (확실하게 끄기 위해)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                Log.d(TAG, "두 번째 확대 중지 시도 시작 (scale=1.0)")
                                setScaleMethod.invoke(controller, 1.0f, true)
                                Log.d(TAG, "✅ 두 번째 확대 중지 시도 완료")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ 두 번째 확대 중지 시도 실패: ${e.message}")
                                e.printStackTrace()
                            }
                        }, 50)

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ magnificationController setScale 호출 실패: ${e.message}")
                        e.printStackTrace()
                    }
                } else {
                    Log.w(TAG, "⚠️ 시스템 magnificationController가 null입니다")
                }
            } else {
                Log.w(TAG, "⚠️ magnificationController는 Android 8.0 (API 26) 이상에서만 지원됩니다")
            }

            // 상태를 확실하게 false로 설정
            isMagnificationActive = false
            isMagnifierShowing = false
            Log.d(TAG, "✅ 상태 업데이트 완료 - isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")

            // 추가로 확대 기능이 완전히 꺼졌는지 확인하고 UI 요소 강제 제거
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "지연된 UI 요소 제거 확인 시작 (200ms 후)")
                    if (isMagnifierShowing || magnifierView != null || controlView != null) {
                        Log.w(TAG, "⚠️ UI 요소가 여전히 남아있음 - 강제 제거 시도")
                        // 강제로 모든 UI 요소 제거
                        forceRemoveAllViews()
                    } else {
                        Log.d(TAG, "✅ 모든 UI 요소가 정상적으로 제거됨")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 지연된 UI 요소 제거 실패: ${e.message}")
                    e.printStackTrace()
                }
            }, 200)

            Log.d(TAG, "=== stopMagnification() 완료 - 최종 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing, magnifierView=${magnifierView != null}, controlView=${controlView != null} ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 화면 확대 중지 실패: ${e.message}")
            e.printStackTrace()
            // 예외가 발생해도 상태는 false로 설정
            isMagnificationActive = false
            isMagnifierShowing = false
            Log.d(TAG, "예외 발생 후 상태를 false로 설정")
            // 예외 발생 시에도 UI 요소 제거 시도
            try {
                Log.d(TAG, "🔍 예외 발생 후 UI 요소 제거 시도")
                forceRemoveAllViews()
            } catch (e2: Exception) {
                Log.e(TAG, "❌ 예외 발생 후 UI 요소 제거 실패: ${e2.message}")
                e2.printStackTrace()
            }
        }
    }

    private fun toggleMagnification() {
        try {
            Log.d(TAG, "=== toggleMagnification() 시작 - 현재 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing ===")

            if (isMagnificationActive) {
                Log.d(TAG, "🔴 확대 기능이 활성화되어 있음 -> 비활성화 시도")
                Log.d(TAG, "stopMagnification() 호출 시작")
                stopMagnification()
                Log.d(TAG, "stopMagnification() 호출 완료")

                // 강제로 상태 확인 및 동기화
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "지연된 상태 확인 시작 (100ms 후)")
                        // 상태가 제대로 변경되었는지 확인
                        if (isMagnificationActive || isMagnifierShowing) {
                            Log.w(TAG, "⚠️ 확대 기능이나 UI가 여전히 활성화되어 있음 - 강제 비활성화 시도")
                            // 강제로 다시 한 번 비활성화 시도
                            forceRemoveAllViews()
                        }

                        // 상태 변경을 OverlayService에 알림
                        val statusIntent = Intent("MAGNIFICATION_STATUS_CHANGED")
                        statusIntent.putExtra("isActive", isMagnificationActive)
                        statusIntent.setPackage(packageName)
                        sendBroadcast(statusIntent)
                        Log.d(TAG, "상태 변경 알림 전송: isActive=$isMagnificationActive")

                        Log.d(TAG, "지연된 상태 확인 완료 - 최종 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")

                    } catch (e: Exception) {
                        Log.e(TAG, "지연된 상태 확인 및 알림 전송 실패: ${e.message}")
                        e.printStackTrace()
                    }
                }, 100)

            } else {
                Log.d(TAG, "🟢 확대 기능이 비활성화되어 있음 -> 활성화 시도")

                // 파란색 확대창(돋보기 뷰) 표시
                if (!isMagnifierShowing) {
                    Log.d(TAG, "showMagnifier() 호출 시작")
                    showMagnifier()
                    Log.d(TAG, "showMagnifier() 호출 완료")
                    // UI 생성 후 화면 확대 시작
                    Log.d(TAG, "startMagnification() 호출 시작")
                    startMagnification()
                    Log.d(TAG, "startMagnification() 호출 완료")
                } else {
                    Log.d(TAG, "확대창이 이미 표시 중 - startMagnification()만 호출")
                    startMagnification()
                }

                // 강제로 상태 확인 및 동기화
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "지연된 상태 확인 시작 (100ms 후)")
                        // 상태가 제대로 변경되었는지 확인
                        if (!isMagnificationActive) {
                            Log.w(TAG, "⚠️ 확대 기능이 여전히 비활성화되어 있음 - 강제 활성화 시도")
                            // 강제로 다시 한 번 활성화 시도
                            if (!isMagnifierShowing) {
                                showMagnifier()
                                // UI 생성 후 화면 확대 시작
                                startMagnification()
                            } else {
                                startMagnification()
                            }
                        }

                        // 상태 변경을 OverlayService에 알림
                        val statusIntent = Intent("MAGNIFICATION_STATUS_CHANGED")
                        statusIntent.putExtra("isActive", isMagnificationActive)
                        statusIntent.setPackage(packageName)
                        sendBroadcast(statusIntent)
                        Log.d(TAG, "상태 변경 알림 전송: isActive=$isMagnificationActive")

                        // 확대 기능이 활성화된 경우 추가 상태 동기화
                        if (isMagnificationActive) {
                            // 확대창 표시 상태도 true로 설정
                            isMagnifierShowing = true
                            Log.d(TAG, "✅ 확대 기능 활성화 후 상태 동기화: isMagnifierShowing=true")
                        }

                        Log.d(TAG, "지연된 상태 확인 완료 - 최종 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")

                    } catch (e: Exception) {
                        Log.e(TAG, "지연된 상태 확인 및 알림 전송 실패: ${e.message}")
                        e.printStackTrace()
                    }
                }, 100)
            }

            Log.d(TAG, "=== toggleMagnification() 완료 - 최종 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing, magnifierView=${magnifierView != null}, controlView=${controlView != null} ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 화면 확대 토글 실패: ${e.message}")
            e.printStackTrace()
            Log.d(TAG, "예외 발생 후 현재 상태: isMagnificationActive=$isMagnificationActive, isMagnifierShowing=$isMagnifierShowing")
        }
    }

    private fun setMagnificationCenter(centerX: Float, centerY: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    try {
                        currentMagnificationCenterX = centerX
                        currentMagnificationCenterY = centerY

                        // 전체 화면 확대 모드에서도 확대 영역 이동이 가능하도록 개선
                        val setCenterMethod = controller.javaClass.getMethod(
                            "setCenter",
                            Float::class.java,
                            Float::class.java,
                            Boolean::class.java
                        )

                        // 확대 영역 이동 시 애니메이션 효과 제거 (즉시 이동)
                        setCenterMethod.invoke(controller, centerX, centerY, false)

                        // 확대 영역 이동이 제대로 적용되었는지 확인
                        Log.d(TAG, "확대 중심점 변경: ($centerX, $centerY)")

                        // 추가로 확대 영역 이동을 위한 대체 방법 시도
                        tryAlternativeMagnificationMove(centerX, centerY)

                    } catch (e: Exception) {
                        Log.e(TAG, "magnificationController setCenter 호출 실패: ${e.message}")
                        // 대체 방법으로 확대 영역 이동 시도
                        tryAlternativeMagnificationMove(centerX, centerY)
                    }
                } else {
                    Log.w(TAG, "시스템 magnificationController가 null입니다")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "확대 중심점 설정 실패: ${e.message}")
        }
    }

    // 대체 방법으로 확대 영역 이동 시도
    private fun tryAlternativeMagnificationMove(centerX: Float, centerY: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    // 방법 1: setCenter를 여러 번 호출하여 확실하게 이동
                    for (i in 1..3) {
                        try {
                            val setCenterMethod = controller.javaClass.getMethod(
                                "setCenter",
                                Float::class.java,
                                Float::class.java,
                                Boolean::class.java
                            )
                            setCenterMethod.invoke(controller, centerX, centerY, false)
                            Log.d(TAG, "대체 방법 $i - 확대 영역 이동 시도: ($centerX, $centerY)")
                        } catch (e: Exception) {
                            Log.w(TAG, "대체 방법 $i 실패: ${e.message}")
                        }
                    }

                    // 방법 2: 확대 배율을 잠시 변경했다가 복원하여 이동 강제 적용
                    try {
                        val setScaleMethod = controller.javaClass.getMethod(
                            "setScale",
                            Float::class.java,
                            Boolean::class.java
                        )

                        val currentScale = magnifierScale
                        // 배율을 잠시 변경
                        setScaleMethod.invoke(controller, currentScale + 0.1f, false)

                        // 잠시 대기 후 원래 배율로 복원
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                setScaleMethod.invoke(controller, currentScale, false)
                                Log.d(TAG, "배율 변경을 통한 확대 영역 이동 강제 적용 완료")
                            } catch (e: Exception) {
                                Log.w(TAG, "배율 복원 실패: ${e.message}")
                            }
                        }, 100)

                    } catch (e: Exception) {
                        Log.w(TAG, "배율 변경을 통한 이동 강제 적용 실패: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "대체 확대 영역 이동 방법 실패: ${e.message}")
        }
    }

    private fun createMagnifierView(x: Int = 100, y: Int = 200) {
        try {
            // 돋보기 뷰 생성 (원형)
            magnifierView = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(magnifierSize, magnifierSize)
                background = createMagnifierBackground()

                // 드래그 가능하도록 설정
                if (isDraggable) {
                    setOnTouchListener(createDragListener())
                }
            }

            val params = WindowManager.LayoutParams(
                magnifierSize,
                magnifierSize,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x
            params.y = y

            windowManager?.addView(magnifierView, params)
            Log.d(TAG, "돋보기 뷰 추가 완료 - 위치: ($x, $y)")

        } catch (e: Exception) {
            Log.e(TAG, "돋보기 뷰 생성 실패: ${e.message}")
        }
    }

    private fun createMagnifierBackground(): android.graphics.drawable.Drawable {
        val bitmap = Bitmap.createBitmap(magnifierSize, magnifierSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        // 원형 배경 (반투명 파란색)
        paint.color = Color.argb(150, 100, 150, 255)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(magnifierSize / 2f, magnifierSize / 2f, magnifierSize / 2f - 5f, paint)

        // 안내 메시지
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.textAlign = Paint.Align.CENTER
        val message = "확대 중\n${String.format("%.1f", magnifierScale)}x"
        val textBounds = Rect()
        paint.getTextBounds(message, 0, message.length, textBounds)
        canvas.drawText(
            message,
            magnifierSize / 2f,
            magnifierSize / 2f + textBounds.height() / 2f,
            paint
        )

        // 테두리
        paint.color = Color.BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(magnifierSize / 2f, magnifierSize / 2f, magnifierSize / 2f - 5f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun createDragListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = true
                    Log.d(TAG, "드래그 시작: 초기 위치 ($initialX, $initialY)")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val params = view.layoutParams as WindowManager.LayoutParams
                        val newX = initialX + (event.rawX - initialTouchX).toInt()
                        val newY = initialY + (event.rawY - initialTouchY).toInt()

                        // 화면 경계 체크
                        val displayMetrics = resources.displayMetrics
                        params.x = max(0, min(newX, displayMetrics.widthPixels - magnifierSize))
                        params.y = max(0, min(newY, displayMetrics.heightPixels - magnifierSize))

                        // 뷰 위치 업데이트
                        windowManager?.updateViewLayout(view, params)

                        // 확대 중심점을 드래그 위치로 업데이트 (더 정확한 계산)
                        val newCenterX = params.x + magnifierSize / 2f
                        val newCenterY = params.y + magnifierSize / 2f

                        Log.d(TAG, "드래그 중: 뷰 위치 (${params.x}, ${params.y}), 확대 중심점 ($newCenterX, $newCenterY)")

                        // 확대 영역 이동 (즉시 적용)
                        setMagnificationCenter(newCenterX, newCenterY)

                        // 추가로 대체 방법으로도 이동 시도
                        moveMagnificationCenter(newCenterX, newCenterY)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        val finalParams = view.layoutParams as WindowManager.LayoutParams
                        val finalCenterX = finalParams.x + magnifierSize / 2f
                        val finalCenterY = finalParams.y + magnifierSize / 2f

                        Log.d(TAG, "드래그 완료: 최종 확대 중심점 ($finalCenterX, $finalCenterY)")

                        // 최종 위치에서 확대 영역 이동 확실히 적용
                        setMagnificationCenter(finalCenterX, finalCenterY)
                        moveMagnificationCenter(finalCenterX, finalCenterY)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun createControlView() {
        try {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            controlView = inflater.inflate(R.layout.magnifier_control_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.BOTTOM
            params.y = 100

            windowManager?.addView(controlView, params)
            Log.d(TAG, "설정 컨트롤 뷰 추가 완료")

            // 설정 컨트롤 이벤트 설정
            setupControlEvents()

        } catch (e: Exception) {
            Log.e(TAG, "설정 컨트롤 뷰 생성 실패: ${e.message}")
        }
    }

    private fun setupControlEvents() {
        controlView?.let { view ->
            // 확대 배율 슬라이더
            val scaleSlider = view.findViewById<SeekBar>(R.id.scaleSlider)
            val scaleText = view.findViewById<TextView>(R.id.scaleText)

            scaleSlider?.setOnSeekBarChangeListener(object :
                android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    magnifierScale = 1.0f + (progress / 10f)
                    scaleText?.text = "${String.format("%.1f", magnifierScale)}x"

                    // magnificationController에 즉시 적용
                    if (isMagnificationActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val controller = getSystemMagnificationController()
                        if (controller != null) {
                            try {
                                val setScaleMethod = controller.javaClass.getMethod(
                                    "setScale",
                                    Float::class.java,
                                    Boolean::class.java
                                )
                                setScaleMethod.invoke(controller, magnifierScale, true)
                            } catch (e: Exception) {
                                Log.e(TAG, "magnificationController setScale 호출 실패: ${e.message}")
                            }
                        } else {
                            Log.w(TAG, "시스템 magnificationController가 null입니다")
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    updateMagnifierSettings(magnifierScale, magnifierSize, isDraggable)
                }
            })

            // 크기 슬라이더
            val sizeSlider = view.findViewById<SeekBar>(R.id.sizeSlider)
            val sizeText = view.findViewById<TextView>(R.id.sizeText)

            sizeSlider?.setOnSeekBarChangeListener(object :
                android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    magnifierSize = 100 + progress * 5
                    sizeText?.text = "${magnifierSize}px"
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    updateMagnifierSettings(magnifierScale, magnifierSize, isDraggable)
                }
            })

            // 드래그 가능 여부 스위치
            val draggableSwitch = view.findViewById<android.widget.Switch>(R.id.draggableSwitch)
            val draggableText = view.findViewById<TextView>(R.id.draggableText)

            draggableSwitch?.setOnCheckedChangeListener { _, isChecked ->
                isDraggable = isChecked
                draggableText?.text = if (isChecked) "활성화" else "비활성화"
                updateMagnifierSettings(magnifierScale, magnifierSize, isDraggable)
            }

            // 확대 토글 버튼
            val toggleButton = view.findViewById<Button>(R.id.toggleButton)
            toggleButton?.setOnClickListener {
                toggleMagnification()
            }

            // 초기화 버튼
            val resetButton = view.findViewById<Button>(R.id.resetButton)
            resetButton?.setOnClickListener {
                magnifierScale = 2.0f
                magnifierSize = 200
                isDraggable = true

                // UI 초기화
                scaleSlider?.progress = 25
                scaleText?.text = "2.0x"
                sizeSlider?.progress = 33
                sizeText?.text = "200px"
                draggableSwitch?.isChecked = true
                draggableText?.text = "활성화"

                updateMagnifierSettings(magnifierScale, magnifierSize, isDraggable)
            }

            // 닫기 버튼
            val closeButton = view.findViewById<Button>(R.id.closeButton)
            closeButton?.setOnClickListener {
                hideMagnifier()
            }
        }
    }

    private fun hideMagnifier() {
        try {
            Log.d(TAG, "=== hideMagnifier() 시작 ===")
            Log.d(TAG, "현재 상태: magnifierView=${magnifierView != null}, controlView=${controlView != null}, isMagnifierShowing=$isMagnifierShowing")

            // 돋보기 뷰 제거
            if (magnifierView != null) {
                try {
                    Log.d(TAG, "돋보기 뷰 제거 시도 시작")
                    windowManager?.removeView(magnifierView)
                    Log.d(TAG, "✅ 돋보기 뷰 제거 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 돋보기 뷰 제거 실패: ${e.message}")
                    e.printStackTrace()
                } finally {
                    // finally 블록에서 확실하게 null 설정
                    magnifierView = null
                    Log.d(TAG, "magnifierView를 null로 설정")
                }
            } else {
                Log.d(TAG, "돋보기 뷰가 이미 null입니다")
            }

            // 컨트롤 뷰 제거
            if (controlView != null) {
                try {
                    Log.d(TAG, "컨트롤 뷰 제거 시도 시작")
                    windowManager?.removeView(controlView)
                    Log.d(TAG, "✅ 컨트롤 뷰 제거 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 컨트롤 뷰 제거 실패: ${e.message}")
                    e.printStackTrace()
                } finally {
                    // finally 블록에서 확실하게 null 설정
                    controlView = null
                    Log.d(TAG, "controlView를 null로 설정")
                }
            } else {
                Log.d(TAG, "컨트롤 뷰가 이미 null입니다")
            }

            // 표시 상태 업데이트
            isMagnifierShowing = false
            Log.d(TAG, "✅ isMagnifierShowing을 false로 설정")

            // 추가로 확실하게 제거하기 위해 한 번 더 시도
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.d(TAG, "지연된 UI 요소 제거 확인 시작 (100ms 후)")
                    if (magnifierView != null || controlView != null) {
                        Log.w(TAG, "⚠️ UI 요소가 여전히 남아있음 - 강제 제거 시도")
                        forceRemoveAllViews()
                    } else {
                        Log.d(TAG, "✅ 모든 UI 요소가 정상적으로 제거됨")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 지연된 UI 요소 제거 실패: ${e.message}")
                    e.printStackTrace()
                }
            }, 100)

            Log.d(TAG, "=== hideMagnifier() 완료 - 최종 상태: isMagnifierShowing=$isMagnifierShowing, magnifierView=${magnifierView != null}, controlView=${controlView != null} ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 돋보기 숨기기 실패: ${e.message}")
            e.printStackTrace()
            // 예외 발생 시에도 강제로 상태 초기화
            forceRemoveAllViews()
        }
    }

    private fun updateMagnifierSettings(scale: Float, size: Int, draggable: Boolean) {
        magnifierScale = scale
        magnifierSize = size
        isDraggable = draggable

        // magnificationController에 즉시 적용
        if (isMagnificationActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val controller = getSystemMagnificationController()
            if (controller != null) {
                try {
                    val setScaleMethod = controller.javaClass.getMethod(
                        "setScale",
                        Float::class.java,
                        Boolean::class.java
                    )
                    val setCenterMethod = controller.javaClass.getMethod(
                        "setCenter",
                        Float::class.java,
                        Float::class.java,
                        Boolean::class.java
                    )

                    setScaleMethod.invoke(controller, scale, true)
                    setCenterMethod.invoke(
                        controller,
                        currentMagnificationCenterX,
                        currentMagnificationCenterY,
                        true
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "magnificationController 메서드 호출 실패: ${e.message}")
                }
            } else {
                Log.w(TAG, "시스템 magnificationController가 null입니다")
            }
        }

        // 현재 위치 저장
        val currentX = if (magnifierView != null) {
            val params = magnifierView!!.layoutParams as? WindowManager.LayoutParams
            params?.x ?: 100
        } else {
            100
        }
        val currentY = if (magnifierView != null) {
            val params = magnifierView!!.layoutParams as? WindowManager.LayoutParams
            params?.y ?: 200
        } else {
            200
        }

        // 돋보기 뷰 재생성
        if (magnifierView != null) {
            windowManager?.removeView(magnifierView)
        }
        createMagnifierView(currentX, currentY)

        Log.d(TAG, "돋보기 설정 업데이트 완료: scale=$scale, size=$size, draggable=$draggable")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScreenCaptureAccessibilityService onDestroy")
        try {
            // 정적 인스턴스 참조 해제
            instance = null
            
            hideMagnifier()
            // 브로드캐스트 리시버 해제
            try {
                unregisterReceiver(magnifierReceiver)
                Log.d(TAG, "돋보기 브로드캐스트 리시버 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "돋보기 브로드캐스트 리시버 해제 실패: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy 처리 실패: ${e.message}")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager =
                getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)

            val currentServiceId = "${packageName}/${javaClass.name}"
            enabledServices.any { it.id == currentServiceId }
        } catch (e: Exception) {
            Log.e(TAG, "AccessibilityService 활성화 상태 확인 실패: ${e.message}")
            false
        }
    }

    // 안드로이드 기본 확대 기능 사용법 안내
    private fun showMagnificationGuide() {
        try {
            // 개선된 안내 메시지
            val message = "확대 기능이 활성화되었습니다!\n\n" +
                    "🔍 기본 제스처:\n" +
                    "• 3손가락 탭: 확대/축소\n" +
                    "• 핀치 제스처: 배율 조절\n" +
                    "• 2손가락 드래그: 확대 영역 이동\n\n" +
                    "🎯 추가 이동 방법:\n" +
                    "• 돋보기 아이콘 드래그\n" +
                    "• 제스처 기반 이동 (Flutter에서 호출)\n" +
                    "• 가장자리로 빠른 이동\n\n" +
                    "💡 팁: 전체 화면 확대 모드에서도\n" +
                    "   확대 영역을 자유롭게 이동할 수 있습니다!"

            // Toast 메시지 표시 (메인 스레드에서 실행)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            }

            Log.d(TAG, "개선된 확대 기능 사용법 안내 메시지 표시 완료")
        } catch (e: Exception) {
            Log.e(TAG, "확대 기능 안내 메시지 표시 실패: ${e.message}")
        }
    }

    // 확대 배율 조절 (핀치 제스처 시뮬레이션)
    fun adjustMagnificationScale(newScale: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    // 배율 범위 제한 (안드로이드 기본과 동일: 1.0x ~ 8.0x)
                    val clampedScale = newScale.coerceIn(1.0f, 8.0f)

                    val setScaleMethod = controller.javaClass.getMethod(
                        "setScale",
                        Float::class.java,
                        Boolean::class.java
                    )

                    setScaleMethod.invoke(controller, clampedScale, true)
                    magnifierScale = clampedScale

                    Log.d(TAG, "확대 배율 조절 완료: $clampedScale")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "확대 배율 조절 실패: ${e.message}")
        }
    }

    // 확대 영역 이동 (2손가락 드래그 시뮬레이션)
    fun moveMagnificationCenter(newCenterX: Float, newCenterY: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    Log.d(TAG, "=== 확대 영역 이동 시작: ($newCenterX, $newCenterY) ===")

                    // 방법 1: 기본 setCenter 메서드 사용
                    try {
                        val setCenterMethod = controller.javaClass.getMethod(
                            "setCenter",
                            Float::class.java,
                            Float::class.java,
                            Boolean::class.java
                        )

                        // 애니메이션 없이 즉시 이동
                        setCenterMethod.invoke(controller, newCenterX, newCenterY, false)
                        Log.d(TAG, "기본 방법으로 확대 영역 이동 완료")

                    } catch (e: Exception) {
                        Log.w(TAG, "기본 방법 실패: ${e.message}")
                    }

                    // 방법 2: 여러 번 호출하여 확실하게 이동
                    for (i in 1..2) {
                        try {
                            val setCenterMethod = controller.javaClass.getMethod(
                                "setCenter",
                                Float::class.java,
                                Float::class.java,
                                Boolean::class.java
                            )
                            setCenterMethod.invoke(controller, newCenterX, newCenterY, false)
                            Log.d(TAG, "반복 호출 $i - 확대 영역 이동 시도")
                        } catch (e: Exception) {
                            Log.w(TAG, "반복 호출 $i 실패: ${e.message}")
                        }
                    }

                    // 방법 3: 확대 배율을 잠시 변경했다가 복원하여 이동 강제 적용
                    try {
                        val setScaleMethod = controller.javaClass.getMethod(
                            "setScale",
                            Float::class.java,
                            Boolean::class.java
                        )

                        val currentScale = magnifierScale
                        // 배율을 잠시 변경
                        setScaleMethod.invoke(controller, currentScale + 0.05f, false)

                        // 잠시 대기 후 원래 배율로 복원
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                setScaleMethod.invoke(controller, currentScale, false)
                                Log.d(TAG, "배율 변경을 통한 확대 영역 이동 강제 적용 완료")
                            } catch (e: Exception) {
                                Log.w(TAG, "배율 복원 실패: ${e.message}")
                            }
                        }, 50)

                    } catch (e: Exception) {
                        Log.w(TAG, "배율 변경을 통한 이동 강제 적용 실패: ${e.message}")
                    }

                    // 현재 위치 업데이트
                    currentMagnificationCenterX = newCenterX
                    currentMagnificationCenterY = newCenterY

                    Log.d(TAG, "✅ 확대 영역 이동 완료: ($newCenterX, $newCenterY)")

                } else {
                    Log.w(TAG, "magnificationController가 null입니다")
                }
            } else {
                Log.w(TAG, "Android 8.0 미만에서는 magnificationController를 지원하지 않습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "확대 영역 이동 실패: ${e.message}")
            e.printStackTrace()
        }
    }

    // 제스처 기반 확대 영역 이동 (전체 화면 확대 모드에서 유용)
    fun moveMagnificationByGesture(direction: String, distance: Float = 100f) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    val newCenterX: Float
                    val newCenterY: Float

                    when (direction.lowercase()) {
                        "up" -> {
                            newCenterX = currentMagnificationCenterX
                            newCenterY = currentMagnificationCenterY - distance
                        }
                        "down" -> {
                            newCenterX = currentMagnificationCenterX
                            newCenterY = currentMagnificationCenterY + distance
                        }
                        "left" -> {
                            newCenterX = currentMagnificationCenterX - distance
                            newCenterY = currentMagnificationCenterY
                        }
                        "right" -> {
                            newCenterX = currentMagnificationCenterX + distance
                            newCenterY = currentMagnificationCenterY
                        }
                        else -> {
                            Log.w(TAG, "지원하지 않는 방향: $direction")
                            return
                        }
                    }

                    // 화면 경계 체크
                    val displayMetrics = resources.displayMetrics
                    val clampedX = newCenterX.coerceIn(0f, displayMetrics.widthPixels.toFloat())
                    val clampedY = newCenterY.coerceIn(0f, displayMetrics.heightPixels.toFloat())

                    Log.d(TAG, "제스처 기반 확대 영역 이동: $direction, 거리: $distance, 새 위치: ($clampedX, $clampedY)")

                    // 확대 영역 이동 실행
                    moveMagnificationCenter(clampedX, clampedY)

                } else {
                    Log.w(TAG, "magnificationController가 null입니다")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "제스처 기반 확대 영역 이동 실패: ${e.message}")
        }
    }

    // 확대 영역을 화면 가장자리로 이동
    fun moveMagnificationToEdge(edge: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controller = getSystemMagnificationController()
                if (controller != null) {
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    val newCenterX: Float
                    val newCenterY: Float

                    when (edge.lowercase()) {
                        "top" -> {
                            newCenterX = screenWidth / 2f
                            newCenterY = 100f
                        }
                        "bottom" -> {
                            newCenterX = screenWidth / 2f
                            newCenterY = screenHeight - 100f
                        }
                        "left" -> {
                            newCenterX = 100f
                            newCenterY = screenHeight / 2f
                        }
                        "right" -> {
                            newCenterX = screenWidth - 100f
                            newCenterY = screenHeight / 2f
                        }
                        "center" -> {
                            newCenterX = screenWidth / 2f
                            newCenterY = screenHeight / 2f
                        }
                        else -> {
                            Log.w(TAG, "지원하지 않는 가장자리: $edge")
                            return
                        }
                    }

                    Log.d(TAG, "가장자리로 확대 영역 이동: $edge, 새 위치: ($newCenterX, $newCenterY)")

                    // 확대 영역 이동 실행
                    moveMagnificationCenter(newCenterX, newCenterY)

                } else {
                    Log.w(TAG, "magnificationController가 null입니다")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "가장자리로 확대 영역 이동 실패: ${e.message}")
        }
    }

    // 강제로 모든 UI 요소 제거
    private fun forceRemoveAllViews() {
        try {
            Log.d(TAG, "=== forceRemoveAllViews() 시작 - 강제 UI 요소 제거 ===")
            
            // 돋보기 뷰 강제 제거
            if (magnifierView != null) {
                try {
                    Log.d(TAG, "돋보기 뷰 강제 제거 시도")
                    windowManager?.removeView(magnifierView)
                    Log.d(TAG, "✅ 돋보기 뷰 강제 제거 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 돋보기 뷰 강제 제거 실패: ${e.message}")
                    e.printStackTrace()
                } finally {
                    magnifierView = null
                    Log.d(TAG, "magnifierView를 null로 설정")
                }
            }

            // 컨트롤 뷰 강제 제거
            if (controlView != null) {
                try {
                    Log.d(TAG, "컨트롤 뷰 강제 제거 시도")
                    windowManager?.removeView(controlView)
                    Log.d(TAG, "✅ 컨트롤 뷰 강제 제거 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 컨트롤 뷰 강제 제거 실패: ${e.message}")
                    e.printStackTrace()
                } finally {
                    controlView = null
                    Log.d(TAG, "controlView를 null로 설정")
                }
            }

            // 상태 강제 초기화
            isMagnifierShowing = false
            isMagnificationActive = false
            
            Log.d(TAG, "✅ 강제 UI 요소 제거 완료 - 최종 상태: isMagnifierShowing=$isMagnifierShowing, isMagnificationActive=$isMagnificationActive, magnifierView=${magnifierView != null}, controlView=${controlView != null}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 강제 UI 요소 제거 실패: ${e.message}")
            e.printStackTrace()
            // 예외 발생 시에도 강제로 상태 초기화
            magnifierView = null
            controlView = null
            isMagnifierShowing = false
            isMagnificationActive = false
            Log.d(TAG, "✅ 예외 발생 후 강제 상태 초기화 완료")
        }
    }
}
