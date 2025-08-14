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
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException
import java.io.File
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.os.VibrationEffect
import android.os.Vibrator
import android.animation.ObjectAnimator
import android.view.animation.AnimationUtils
import kotlinx.coroutines.*
import kotlin.math.sqrt
import kotlin.math.abs
import android.provider.Settings
import android.media.audiofx.NoiseSuppressor
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var chatbotIconView: ImageView? = null
    private val CHANNEL_ID = "OverlayServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "OverlayService"
    private var isChatbotActivated = false
    private var chatbotFloatingIcon: ImageView? = null
    
    // Porcupine 관련 변수들
    private var porcupineManager: PorcupineManager? = null
    
    // 음성 녹음 관련 변수들
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val RECORDING_DURATION_MS = 10000 // 녹음 시간 (10초)
    
    // 실시간 음성 분석 관련 변수들
    private var audioRecord: AudioRecord? = null
    private var isVoiceAnalyzing = false
    private val voiceAnalysisScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 에너지 분석 설정 (음성 인식 개선 - 더 민감하게 조정)
    // 음성 인식 최적화 상수 (태블릿과 핸드폰 모두 지원)
    private val ENERGY_THRESHOLD = 8.0 // 에너지 임계값을 더 낮게 설정 (30~35dB 수준 - 더 민감하게)
    private val SILENCE_DURATION_MS = 3000 // 무음 지속 시간 (3초로 단축 - 더 빠른 응답)
    private val ANALYSIS_INTERVAL_MS = 15 // 분석 간격 (15ms로 단축 - 더 민감하게
    
    // 호출어 인식 후 딜레이 (태블릿 발화 후 안정화 시간)
    private val WAKE_WORD_DELAY_MS = 500 // 0.5초 딜레이 (더 빠른 응답)
    
    // 음성 명령 감지 상수 (태블릿 최적화)
    private val COMMAND_THRESHOLD = 50.0 // 명령 감지 임계값 (더 낮춤 - 더 민감한 감지)
    private val COMMAND_SILENCE_DURATION_MS = 2000 // 명령 후 무음 지속 시간 (2초로 단축)
    
    // 기기 타입별 최적화
    private val isTablet: Boolean by lazy {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels / displayMetrics.density
        val screenHeight = displayMetrics.heightPixels / displayMetrics.density
        val screenInches = sqrt((screenWidth * screenWidth + screenHeight * screenHeight).toDouble()) / 160.0
        screenInches >= 7.0 // 7인치 이상을 태블릿으로 간주
    }
    private var lastVoiceTime = 0L // 마지막 음성 감지 시간
    
    // 범용 오디오 설정 (태블릿/핸드폰 호환)
    private val SAMPLE_RATE = 16000 // 16kHz - 음성 인식 최적
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // 모노 채널
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit PCM
    
    // 음성 명령 감지 관련 변수들 (음성 인식 개선)
    private var isListeningForCommands = false

    // 브로드캐스트 리시버 등록
    private val chatbotResponseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CHATBOT_VOICE_RESPONSE -> {
                    val response = intent.getStringExtra("response") ?: "응답을 받지 못했습니다"
                    Log.i(TAG, "🤖 AI 챗봇 응답 수신: $response")
                    
                    // Flutter 앱에 응답 전달
                    sendChatbotResponseToFlutter(response)
                }
            }
        }
    }
    
    // 브로드캐스트 액션 상수
    companion object {
        private const val TAG = "OverlayService"
        private const val CHATBOT_VOICE_RESPONSE = "CHATBOT_VOICE_RESPONSE"
        private const val DEACTIVATE_CHATBOT_VOICE = "DEACTIVATE_CHATBOT_VOICE"
        
        // 음성 인식 관련 상수
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 에너지 임계값 및 타이밍 (더 민감하게 조정)
        private const val ENERGY_THRESHOLD = 8.0 // 30-35dB (더 민감하게)
        private const val SILENCE_DURATION_MS = 3000L // 3초 (더 빠른 응답)
        private const val ANALYSIS_INTERVAL_MS = 15L // 15ms (더 민감하게)
        private const val COMMAND_THRESHOLD = 50.0 // 음성 명령 감지 임계값 (더 민감하게)
        private const val COMMAND_SILENCE_DURATION_MS = 2000L // 명령 후 무음 대기 시간 (더 빠르게)
        private const val WAKE_WORD_DELAY_MS = 500L // 호출어 감지 후 딜레이 (0.5초 - 더 빠르게)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🎯 EUM AI 챗봇 오버레이 서비스 시작")
        
        // 기기 타입 감지 및 최적화 설정 표시
        val deviceType = if (isTablet) "태블릿" else "핸드폰"
        Log.i(TAG, "📱 기기 타입 감지: $deviceType")
        Log.i(TAG, "⚙️ 최적화 설정 - 에너지 임계값: $ENERGY_THRESHOLD, 무음 지속: ${SILENCE_DURATION_MS}ms, 분석 간격: ${ANALYSIS_INTERVAL_MS}ms")
        
        // 오버레이 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "❌ 오버레이 권한이 없습니다!")
            stopSelf()
            return
        }
        
        // 브로드캐스트 리시버 등록
        try {
            val filter = IntentFilter().apply {
                addAction(CHATBOT_VOICE_RESPONSE)
                addAction(DEACTIVATE_CHATBOT_VOICE)
            }
            registerReceiver(chatbotResponseReceiver, filter)
            Log.d(TAG, "✅ 브로드캐스트 리시버 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 브로드캐스트 리시버 등록 실패: ${e.message}")
        }
        
        // 오버레이 뷰 생성 및 추가
        createOverlayView()
        
        // PorcupineManager 초기화 및 실시간 스트림 시작
        initializePorcupineManager()
        
        Log.i(TAG, "✅ EUM AI 챗봇 오버레이 서비스 초기화 완료")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand 호출 - action: ${intent?.action}")
        
        when (intent?.action) {
            "ACTIVATE_CHATBOT" -> {
                Log.d(TAG, "EUM AI 챗봇 활성화 명령 수신")
                activateChatbot()
            }
            "DEACTIVATE_CHATBOT" -> {
                Log.d(TAG, "EUM AI 챗봇 비활성화 명령 수신")
                deactivateChatbot()
            }
            else -> {
                Log.d(TAG, "일반 onStartCommand 호출")
                try {
                    // 서비스가 시작되었음을 MainActivity에 알림
                    val broadcastIntent = Intent("OVERLAY_SERVICE_STARTED")
                    sendBroadcast(broadcastIntent)
                    Log.d(TAG, "OVERLAY_SERVICE_STARTED 브로드캐스트 전송")
                } catch (e: Exception) {
                    Log.e(TAG, "브로드캐스트 전송 실패: ${e.message}")
                }
            }
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

    // 오버레이 뷰 생성 및 추가
    private fun createOverlayView() {
        try {
            Log.d(TAG, "오버레이 뷰 생성 시작")
            
            // 알림 채널 생성 및 포그라운드 서비스 시작
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            
            // WindowManager 초기화
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 오버레이 뷰 추가
            addOverlayView()
            
            Log.d(TAG, "오버레이 뷰 생성 완료")
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 뷰 생성 실패: ${e.message}")
            stopSelf()
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
                private var startTime = 0L
                private var isLongPress = false
                
                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    try {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startY = event.rawY
                                startTime = System.currentTimeMillis()
                                isLongPress = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                // 길게 누르기 감지 (1초 이상)
                                if (System.currentTimeMillis() - startTime > 1000) {
                                    if (!isLongPress) {
                                        isLongPress = true
                                        Log.d(TAG, "EUM 바 길게 누르기 감지 - 챗봇 비활성화")
                                        // 길게 누르기로 챗봇 비활성화
                                        if (isChatbotActivated) {
                                            deactivateChatbot()
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                val deltaY = startY - event.rawY
                                val pressDuration = System.currentTimeMillis() - startTime
                                
                                if (isLongPress) {
                                    // 길게 누르기로 챗봇 비활성화된 경우
                                    Log.d(TAG, "길게 누르기로 챗봇 비활성화 완료")
                                } else if (deltaY > 100 && !isMenuOpen) {
                                    // 일반 스와이프로 메뉴 열기
                                    menu?.visibility = View.VISIBLE
                                    isMenuOpen = true
                                    Log.d(TAG, "메뉴 열기")
                                    // 메뉴가 열릴 때 챗봇 비활성화 (다른 메뉴 선택 가능하도록)
                                    if (isChatbotActivated) {
                                        Log.d(TAG, "메뉴 열림 - 챗봇 비활성화")
                                        deactivateChatbot()
                                    }
                                } else if (deltaY < -100 && isMenuOpen) {
                                    // 메뉴 닫기
                                    menu?.visibility = View.GONE
                                    isMenuOpen = false
                                    Log.d(TAG, "메뉴 닫기")
                                    // 메뉴가 닫혀도 챗봇은 비활성화 상태 유지 (자동 복원 안함)
                                    Log.d(TAG, "메뉴 닫힘 - 챗봇은 비활성화 상태 유지")
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

            // AI 챗봇 버튼 클릭 이벤트
            val btnChatbot = overlayView?.findViewById<LinearLayout>(com.example.eum.R.id.btn_chatbot)
            Log.d(TAG, "AI 챗봇 버튼 찾기: ${btnChatbot != null}")
            btnChatbot?.setOnClickListener {
                try {
                    Log.d(TAG, "AI 챗봇 버튼 클릭됨!")
                    // 챗봇이 비활성화된 상태에서만 활성화
                    if (!isChatbotActivated) {
                        activateChatbot()
                        Log.d(TAG, "챗봇 활성화 완료")
                    } else {
                        Log.d(TAG, "챗봇이 이미 활성화되어 있습니다.")
                        Toast.makeText(this, "챗봇이 이미 활성화되어 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 메뉴 닫기
                    menu?.visibility = View.GONE
                    isMenuOpen = false
                    Log.d(TAG, "메뉴 닫기 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "AI 챗봇 버튼 클릭 처리 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 뷰 추가 실패: ${e.message}")
            stopSelf()
        }
    }

    // EUM AI 챗봇 활성화
    private fun activateChatbot() {
        isChatbotActivated = true
        Log.d(TAG, "EUM AI 챗봇이 활성화되었습니다!")
        
        // chatbot.png 아이콘을 EUM 바 위에 표시
        showChatbotIcon()
        
        // Flutter 앱에 챗봇 활성화 알림
        try {
            val intent = Intent("ACTIVATE_CHATBOT_VOICE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "ACTIVATE_CHATBOT_VOICE 브로드캐스트 전송 완료")
        } catch (e: Exception) {
            Log.e(TAG, "챗봇 활성화 브로드캐스트 전송 실패: ${e.message}")
        }
    }

    // EUM AI 챗봇 비활성화
    private fun deactivateChatbot() {
        isChatbotActivated = false
        Log.d(TAG, "EUM AI 챗봇이 비활성화되었습니다!")
        
        // chatbot.png 아이콘 숨기기 (사용자가 명시적으로 비활성화할 때만)
        hideChatbotIcon()
        
        // 모니터링 중지 로그 (startWakeWordMonitoring에서 자동으로 중단됨)
        Log.d(TAG, "🔍 호출어 감지 모니터링이 자동으로 중단됩니다")
        
        // Flutter 앱에 챗봇 비활성화 알림
        try {
            val intent = Intent("DEACTIVATE_CHATBOT_VOICE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Log.d(TAG, "DEACTIVATE_CHATBOT_VOICE 브로드캐스트 전송 완료")
        } catch (e: Exception) {
            Log.e(TAG, "챗봇 비활성화 브로드캐스트 전송 실패: ${e.message}")
        }
    }

    // chatbot.png 아이콘 표시
    private fun showChatbotIcon() {
        try {
            Log.d(TAG, "chatbot.png 아이콘 표시 시작")
            
            // 이미 아이콘이 표시되어 있으면 새로 생성하지 않음
            if (chatbotFloatingIcon != null) {
                Log.d(TAG, "chatbot.png 아이콘이 이미 표시되어 있습니다.")
                return
            }
            
            // chatbot.png 아이콘 생성
            chatbotFloatingIcon = ImageView(this)
            chatbotFloatingIcon?.setImageResource(com.example.eum.R.drawable.chatbot)
            chatbotFloatingIcon?.scaleType = ImageView.ScaleType.CENTER_CROP
            
            // 아이콘 크기 및 위치 설정
            val iconSize = 120 // 120dp
            val iconSizePx = (iconSize * resources.displayMetrics.density).toInt()
            
            val params = WindowManager.LayoutParams(
                iconSizePx,
                iconSizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            // 화면 하단 가운데에 위치 (EUM 바 위)
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            params.x = 0 // 가운데 정렬
            params.y = 80 // EUM 바 위 여백
            
            // 아이콘 클릭 이벤트 설정
            chatbotFloatingIcon?.setOnClickListener {
                Log.d(TAG, "chatbot.png 아이콘 클릭됨!")
                onChatbotIconClicked()
            }
            
            // 화면에 아이콘 추가
            windowManager?.addView(chatbotFloatingIcon, params)
            Log.d(TAG, "✅ chatbot.png 아이콘 표시 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ chatbot.png 아이콘 표시 실패: ${e.message}")
        }
    }
    
    // chatbot.png 아이콘 숨기기
    private fun hideChatbotIcon() {
        try {
            if (chatbotFloatingIcon != null) {
                Log.d(TAG, "chatbot.png 아이콘 숨기기 시작")
                windowManager?.removeView(chatbotFloatingIcon)
                chatbotFloatingIcon = null
                Log.d(TAG, "✅ chatbot.png 아이콘 숨기기 완료")
            } else {
                Log.d(TAG, "chatbot.png 아이콘이 이미 숨겨져 있습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ chatbot.png 아이콘 숨기기 실패: ${e.message}")
            // 오류 발생 시에도 변수 정리
            chatbotFloatingIcon = null
        }
    }
    
    // chatbot.png 아이콘 클릭 시 처리
    private fun onChatbotIconClicked() {
        Log.d(TAG, "chatbot.png 아이콘 클릭으로 음성 녹음 시작")
        
        // 챗봇 아이콘을 클릭해도 숨기지 않고 유지
        // hideChatbotIcon() 제거 - 클릭으로는 사라지지 않음
        
        // "이음봇아" 호출어 감지와 동일한 처리
        onWakeWordDetected()
    }



    // PorcupineManager 초기화 및 실시간 스트림 연동
    private fun initializePorcupineManager() {
        try {
            Log.i(TAG, "🎯 PorcupineManager 초기화 시작 - 실시간 오디오 스트림 연동")
            
            // assets 파일을 내부 저장소로 복사
            val ppnFile = copyAssetToFile("이음봇아_ko_android_v3_0_0.ppn")
            val pvFile = copyAssetToFile("porcupine_params_ko.pv")
            
            // 파일 유효성 검사
            if (!ppnFile.exists() || ppnFile.length() == 0L) {
                throw Exception("PPN 파일이 존재하지 않거나 비어있습니다: ${ppnFile.absolutePath}")
            }
            if (!pvFile.exists() || pvFile.length() == 0L) {
                throw Exception("PV 파일이 존재하지 않거나 비어있습니다: ${pvFile.absolutePath}")
            }
            
            Log.d(TAG, "📁 PPN 파일: ${ppnFile.absolutePath} (${ppnFile.length()} bytes)")
            Log.d(TAG, "📁 PV 파일: ${pvFile.absolutePath} (${pvFile.length()} bytes)")
            
            // PorcupineManager 초기화 (올바른 API 사용법)
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey("JVZic8cgf3LNXFBS5/xvsGJ/xq7o+v8S6bSrTeMsT1ehRMmzCD1+2Q==")
                .setKeywordPaths(arrayOf(ppnFile.absolutePath))
                .setModelPath(pvFile.absolutePath)
                .setSensitivity(0.5f) // 호출어 감지 민감도
                .build(this, object : ai.picovoice.porcupine.PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        Log.i(TAG, "🎯 호출어 감지됨! (keywordIndex: $keywordIndex)")
                        onWakeWordDetected()
                    }
                })
            
            // PorcupineManager 시작
            porcupineManager?.start()
            
            Log.d(TAG, "✅ PorcupineManager 초기화 및 시작 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ PorcupineManager 초기화 실패: ${e.message}")
            e.printStackTrace()
            
            // 초기화 실패 시 대체 방법 시도
            Log.w(TAG, "⚠️ PorcupineManager 초기화 실패로 대체 방법 시도")
            tryAlternativeWakeWordDetection()
        }
    }
    
    // 대체 호출어 감지 방법 (PorcupineManager 실패 시)
    private fun tryAlternativeWakeWordDetection() {
        try {
            Log.i(TAG, "🔄 대체 호출어 감지 방법 시작 - 에너지 기반 감지")
            
            // 에너지 기반 호출어 감지 활성화
            startEnergyBasedWakeWordDetection()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 대체 호출어 감지 방법도 실패: ${e.message}")
        }
    }
    
    // 에너지 기반 호출어 감지
    private fun startEnergyBasedWakeWordDetection() {
        Log.i(TAG, "🔊 에너지 기반 호출어 감지 시작")
        
        // 에너지 임계값을 더 민감하게 설정
        val wakeWordEnergyThreshold = ENERGY_THRESHOLD * 0.5 // 더 민감하게
        
        voiceAnalysisScope.launch {
            try {
                while (isChatbotActivated) {
                    // 에너지 기반 호출어 감지 로직
                    // 실제 구현에서는 더 정교한 알고리즘이 필요
                    delay(100) // 100ms 간격으로 체크
                }
            } catch (e: Exception) {
                Log.e(TAG, "에너지 기반 호출어 감지 실패: ${e.message}")
            }
        }
    }
    
    // 호출어 감지 상태 모니터링
    private fun startWakeWordMonitoring() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                try {
                    // 챗봇이 비활성화된 경우 모니터링 중단
                    if (!isChatbotActivated) {
                        Log.d(TAG, "🔍 모니터링 중단 - 챗봇 비활성화")
                        return
                    }
                    
                    // PorcupineManager 상태 확인 (isListening 필드가 private이므로 다른 방법 사용)
                    val isPorcupineRunning = porcupineManager != null
                    Log.d(TAG, "🔍 PorcupineManager 상태: ${if (isPorcupineRunning) "실행 중" else "중지됨"}")
                    
                    // 챗봇이 활성화된 상태에서만 계속 모니터링
                    if (isChatbotActivated && isPorcupineRunning) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 5000)
                    } else {
                        Log.d(TAG, "🔍 모니터링 중단 - 챗봇 비활성화 또는 PorcupineManager 중지")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "호출어 감지 모니터링 실패: ${e.message}")
                }
            }
        }, 5000)
    }
    
    // 에너지 기반 음성 명령 감지
    private fun detectVoiceCommand(energy: Double) {
        if (!isListeningForCommands) return
        
        if (energy > COMMAND_THRESHOLD) {
            // 강한 음성 감지 - 명령 후보
            Log.d(TAG, "강한 음성 감지 - 명령 후보 (에너지: $energy)")
            
            // 명령 후 무음 지속 확인
            voiceAnalysisScope.launch {
                delay(COMMAND_SILENCE_DURATION_MS.toLong())
                
                // 무음 지속 후 명령으로 처리
                if (System.currentTimeMillis() - lastVoiceTime > COMMAND_SILENCE_DURATION_MS) {
                    Log.i(TAG, "음성 명령 감지 - 녹음 종료")
                    provideStopCommandFeedback()
                    stopVoiceRecording()
                }
            }
        }
    }
    
    // 정지 명령 감지 시 사용자 피드백 제공
    private fun provideStopCommandFeedback() {
        try {
            // 1. 진동 피드백 (짧은 진동 2회)
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    longArrayOf(0, 100, 100, 100),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                // Android 8.0 미만: 기존 방식
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
            
            Log.d(TAG, "정지 명령 피드백 제공 완료")
        } catch (e: Exception) {
            Log.e(TAG, "정지 명령 피드백 제공 실패: ${e.message}")
        }
    }
    
    // 호출어 감지 시 처리 (즉시 백엔드 전송)
    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 호출어 감지됨! - 즉시 백엔드 전송 시작")
        
        // 사용자 피드백 제공
        provideWakeWordFeedback()
        
        // 챗봇이 비활성화된 상태라면 활성화
        if (!isChatbotActivated) {
            activateChatbot()
            Log.d(TAG, "챗봇이 비활성화 상태였습니다. 활성화 완료")
        } else {
            Log.d(TAG, "챗봇이 이미 활성화된 상태입니다. 음성 녹음만 시작합니다.")
        }
        
        // 태블릿 발화 후 안정화를 위한 딜레이 적용
        voiceAnalysisScope.launch {
            Log.d(TAG, "⏳ 호출어 감지 후 ${WAKE_WORD_DELAY_MS}ms 딜레이 시작 (태블릿 발화 안정화)")
            delay(WAKE_WORD_DELAY_MS.toLong())
            Log.d(TAG, "✅ 딜레이 완료 - 음성 녹음 및 백엔드 전송 시작")
            
            // 호출어 감지 시마다 음성 녹음 시작 (챗봇 상태와 관계없이)
            startVoiceAnalysis()
            startVoiceRecordingInApp()
            
            // 백엔드 전송 시작
            startBackendUpload()
        }
        
        Log.i(TAG, "✅ 호출어 감지로 딜레이 후 백엔드 전송 시작 예약 완료")
    }
    
    // 백엔드 전송 시작
    private fun startBackendUpload() {
        try {
            Log.i(TAG, "🌐 백엔드 전송 시작")
            
            // 녹음 파일 생성 및 전송
            val audioFile = File(filesDir, "voice_input_${System.currentTimeMillis()}.m4a")
            
            // 백그라운드에서 HTTP POST 업로드
            voiceAnalysisScope.launch {
                try {
                    uploadToBackend(audioFile)
                } catch (e: Exception) {
                    Log.e(TAG, "백엔드 업로드 실패: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "백엔드 전송 시작 실패: ${e.message}")
        }
    }
    
    // HTTP POST 방식으로 백엔드 서버에 업로드
    private suspend fun uploadToBackend(audioFile: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📤 AI 챗봇 서버로 음성 파일 전송 시작: ${audioFile.name}")
                
                // 기존 AI 챗봇과 동일한 엔드포인트 사용
                val serverUrl = "http://localhost:8081/api/chatbot/chat"
                
                val connection = URL(serverUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.doInput = true
                
                // 헤더 설정
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
                connection.setRequestProperty("User-Agent", "EUM-AI-Chatbot/1.0")
                
                // 파일 업로드를 위한 multipart 데이터 생성
                val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
                val writer = connection.outputStream.bufferedWriter()
                
                // 호출어 정보 추가
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"wake_word\"\r\n\r\n")
                writer.append("이음봇아\r\n")
                
                // 기기 정보 추가
                val deviceType = if (isTablet) "태블릿" else "핸드폰"
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"device_type\"\r\n\r\n")
                writer.append("$deviceType\r\n")
                
                // 타임스탬프 추가
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"timestamp\"\r\n\r\n")
                writer.append("${System.currentTimeMillis()}\r\n")
                
                // 오디오 파일 추가
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"audio_file\"; filename=\"${audioFile.name}\"\r\n")
                writer.append("Content-Type: audio/mp4\r\n\r\n")
                writer.flush()
                
                // 파일 데이터 쓰기
                audioFile.inputStream().use { input ->
                    connection.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                writer.append("\r\n")
                writer.append("--$boundary--\r\n")
                writer.flush()
                
                val responseCode = connection.responseCode
                Log.i(TAG, "🌐 AI 챗봇 서버 응답 코드: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.i(TAG, "✅ AI 챗봇 서버 전송 성공: $response")
                    
                    // AI 챗봇 응답을 Flutter 앱에 전달
                    sendChatbotResponseToFlutter(response)
                    
                } else {
                    Log.e(TAG, "❌ AI 챗봇 서버 전송 실패: HTTP $responseCode")
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "알 수 없는 오류"
                    Log.e(TAG, "오류 응답: $errorResponse")
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                Log.e(TAG, "🌐 AI 챗봇 서버 전송 실패: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // AI 챗봇 응답을 Flutter 앱에 전달
    private fun sendChatbotResponseToFlutter(response: String) {
        try {
            Log.i(TAG, "📱 AI 챗봇 응답을 Flutter 앱에 전달")
            
            val intent = Intent("CHATBOT_VOICE_RESPONSE")
            intent.setPackage(packageName)
            intent.putExtra("response", response)
            intent.putExtra("timestamp", System.currentTimeMillis())
            
            sendBroadcast(intent)
            Log.i(TAG, "✅ CHATBOT_VOICE_RESPONSE 브로드캐스트 전송 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Flutter 앱 응답 전달 실패: ${e.message}")
        }
    }
    
    // Flutter 앱에서 음성 녹음 시작
    private fun startVoiceRecordingInApp() {
        try {
            Log.i(TAG, "Flutter 앱에 음성 녹음 시작 브로드캐스트 전송")
            
            // 챗봇 아이콘은 음성 녹음 중에도 계속 표시 (사용자가 계속 볼 수 있도록)
            // hideChatbotIcon() 제거 - 아이콘 유지
            
            // 음성 녹음 시작 브로드캐스트 전송
            val intent = Intent("START_VOICE_RECORDING")
            intent.setPackage(packageName)
            intent.putExtra("recording_duration_ms", RECORDING_DURATION_MS)
            sendBroadcast(intent)
            
            Log.i(TAG, "START_VOICE_RECORDING 브로드캐스트 전송 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "음성 녹음 시작 브로드캐스트 전송 실패: ${e.message}")
        }
    }
    
    // 실시간 음성 분석 시작
    private fun startVoiceAnalysis() {
        if (isVoiceAnalyzing) {
            Log.w(TAG, "⚠️ 이미 음성 분석 중입니다. 기존 분석을 중지하고 새로 시작합니다.")
            // 기존 음성 분석 중지
            stopVoiceRecording()
        }
        
        voiceAnalysisScope.launch {
            try {
                // 기기 타입 감지 및 최적화 설정 표시
                val deviceType = if (isTablet) "태블릿" else "핸드폰"
                Log.i(TAG, "🎤 실시간 음성 분석 시작 - 기기 타입: $deviceType")
                Log.i(TAG, "📱 최적화 설정 - 에너지 임계값: $ENERGY_THRESHOLD, 무음 지속: ${SILENCE_DURATION_MS}ms, 분석 간격: ${ANALYSIS_INTERVAL_MS}ms")
                
                isVoiceAnalyzing = true
                isListeningForCommands = true
                lastVoiceTime = System.currentTimeMillis()
                
                // MediaRecorder 시작 (실제 녹음 파일 생성)
                startMediaRecorder()
                
                // AudioRecord 초기화 (음성 인식 개선)
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                
                // 태블릿과 핸드폰 모두를 위한 버퍼 크기 최적화
                val optimizedBufferSize = if (isTablet) bufferSize * 3 else bufferSize * 2 // 태블릿은 더 큰 버퍼
                
                Log.d(TAG, "AudioRecord 설정 - 기기: $deviceType, 샘플레이트: ${SAMPLE_RATE}Hz, 버퍼 크기: $optimizedBufferSize (최소: $bufferSize)")
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    optimizedBufferSize
                )
                
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioRecord 초기화 실패 - 음성 인식 문제 가능성")
                    Log.e(TAG, "AudioRecord 상태: ${audioRecord?.state}")
                    Log.e(TAG, "버퍼 크기: $optimizedBufferSize, 최소 버퍼 크기: $bufferSize")
                    return@launch
                }
                
                // 백그라운드 소음 억제 (NoiseSuppressor) 적용 - 안전한 방식으로
                applyNoiseSuppression(audioRecord)
                
                audioRecord?.startRecording()
                Log.i(TAG, "🎤 AudioRecord 녹음 시작 - 기기: $deviceType (음성 인식 개선: 샘플레이트 ${SAMPLE_RATE}Hz, 버퍼 크기 ${optimizedBufferSize})")
                
                // 안전한 버퍼 크기 사용
                val safeBufferSize = minOf(bufferSize / 2, 1024) // 최대 1024로 제한
                val buffer = ShortArray(safeBufferSize)
                
                while (isVoiceAnalyzing && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        
                        if (readSize > 0) {
                            // 에너지 계산
                            val energy = calculateEnergy(buffer, readSize)
                            val silenceDuration = System.currentTimeMillis() - lastVoiceTime
                            
                            Log.d(TAG, "🔊 현재 에너지: $energy (임계값: $ENERGY_THRESHOLD, 무음 지속: ${silenceDuration}ms)")
                            
                            if (energy > ENERGY_THRESHOLD) {
                                // 음성 감지됨
                                lastVoiceTime = System.currentTimeMillis()
                                Log.d(TAG, "✅ 음성 감지됨 - 에너지: $energy (임계값 초과)")
                                
                                // 음성 명령 감지 (강한 음성인 경우)
                                detectVoiceCommand(energy)
                            } else {
                                // 무음 상태 확인 (더 정교한 로직)
                                Log.d(TAG, "🔇 무음 상태 - 에너지: $energy, 무음 지속 시간: ${silenceDuration}ms (임계값: ${SILENCE_DURATION_MS}ms)")
                                if (silenceDuration > SILENCE_DURATION_MS) {
                                    Log.i(TAG, "⏹️ 무음 지속으로 인한 녹음 종료 (${silenceDuration}ms)")
                                    stopVoiceRecording()
                                    break
                                }
                            }
                            
                            // 에너지 변화율 계산 (더 민감한 감지)
                            if (energy > ENERGY_THRESHOLD * 1.8) {
                                Log.d(TAG, "🔊🔊 강한 음성 감지 - 에너지: $energy")
                            } else if (energy > ENERGY_THRESHOLD * 1.2) {
                                Log.d(TAG, "🔊 중간 음성 감지 - 에너지: $energy")
                            } else if (energy > ENERGY_THRESHOLD * 0.8) {
                                Log.d(TAG, "🔊 약한 음성 감지 - 에너지: $energy (임계값 근처)")
                            }
                        } else if (readSize == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "❌ AudioRecord 오류: 잘못된 작업")
                            break
                        } else if (readSize == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "❌ AudioRecord 오류: 잘못된 값")
                            break
                        } else if (readSize == AudioRecord.ERROR_DEAD_OBJECT) {
                            Log.e(TAG, "❌ AudioRecord 오류: 죽은 객체")
                            break
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ AudioRecord 읽기 오류: ${e.message}")
                        break
                    }
                    
                    delay(ANALYSIS_INTERVAL_MS.toLong())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "음성 분석 실패: ${e.message}")
                e.printStackTrace()
            } finally {
                // 안전한 정리
                safeCleanupAudioRecord()
                isVoiceAnalyzing = false
                isListeningForCommands = false
                Log.i(TAG, "음성 분석 종료")
            }
        }
    }
    
    // 안전한 AudioRecord 정리
    private fun safeCleanupAudioRecord() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    try {
                        stop()
                        Log.d(TAG, "✅ AudioRecord 정지 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ AudioRecord 정지 중 오류: ${e.message}")
                    }
                }
                
                try {
                    release()
                    Log.d(TAG, "✅ AudioRecord 해제 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AudioRecord 해제 중 오류: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ AudioRecord 정리 중 오류: ${e.message}")
        } finally {
            audioRecord = null
        }
    }
    
    // 안전한 NoiseSuppressor 적용
    private fun applyNoiseSuppression(audioRecord: AudioRecord?) {
        try {
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                // NoiseSuppressor 지원 여부 확인
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        val noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                        if (noiseSuppressor != null) {
                            noiseSuppressor.enabled = true
                            Log.i(TAG, "🔇 백그라운드 소음 억제 활성화 - AudioSessionId: $audioSessionId")
                        } else {
                            Log.w(TAG, "⚠️ NoiseSuppressor 생성 실패")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ NoiseSuppressor 초기화 실패: ${e.message}")
                    }
                } else {
                    Log.i(TAG, "ℹ️ 이 기기는 NoiseSuppressor를 지원하지 않습니다")
                }
            } else {
                Log.w(TAG, "⚠️ AudioSessionId를 가져올 수 없습니다")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ NoiseSuppressor 적용 중 오류: ${e.message}")
        }
    }
    
    // MediaRecorder 시작
    private fun startMediaRecorder() {
        if (isRecording) {
            Log.w(TAG, "이미 녹음 중입니다")
            return
        }
        
        try {
            // 범용 오디오 포맷 지원
            val audioFile = File(filesDir, "voice_input_${System.currentTimeMillis()}.m4a")
            Log.i(TAG, "MediaRecorder 녹음 시작: ${audioFile.absolutePath}")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                
                // 범용 호환성을 위한 포맷 설정
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // MP4/AAC - 가장 호환성 좋음
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                // 샘플레이트 설정 (범용 호환성)
                setAudioSamplingRate(16000) // 16kHz - 음성 인식에 최적
                setAudioEncodingBitRate(128000) // 128kbps - 품질과 크기 균형
                
                setOutputFile(audioFile.absolutePath)
                
                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.i(TAG, "MediaRecorder 시작됨 (범용 최적화: MPEG-4/AAC, 샘플레이트: 16kHz)")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaRecorder 시작 실패: ${e.message}")
                    isRecording = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder 초기화 실패: ${e.message}")
            isRecording = false
        }
    }
    
    // 에너지 계산 함수 (35~40dB 수준 최적화)
    private fun calculateEnergy(buffer: ShortArray, size: Int): Double {
        if (size == 0) return 0.0
        
        var sum = 0.0
        var maxAmplitude = 0.0
        var minAmplitude = 0.0
        
        for (i in 0 until size) {
            val amplitude = buffer[i].toDouble()
            sum += amplitude * amplitude
            if (amplitude > maxAmplitude) maxAmplitude = amplitude
            if (amplitude < minAmplitude) minAmplitude = amplitude
        }
        
        val rmsEnergy = sqrt(sum / size)
        
        // 35~40dB 수준 음성 감지를 위한 최적화된 정규화
        val normalizationFactor = if (isTablet) 2048.0 else 4096.0 // 태블릿은 더 민감하게
        val normalizedEnergy = rmsEnergy * (maxAmplitude / normalizationFactor)
        
        // 35~40dB 수준에 최적화된 로그 스케일 에너지 계산
        val logEnergy = if (normalizedEnergy > 0) kotlin.math.log10(normalizedEnergy + 1) * 200 else 0.0
        
        // 진폭 변화율 기반 에너지 보정 (35~40dB 수준 최적화)
        val amplitudeFactor = (maxAmplitude - minAmplitude) / 32768.0
        val enhancedEnergy = logEnergy * (1 + amplitudeFactor * 4) // 더 민감하게
        
        // 기기 타입별 추가 보정 (35~40dB 수준)
        val finalEnergy = if (isTablet) enhancedEnergy * 2.0 else enhancedEnergy * 1.5
        
        return finalEnergy
    }
    
    // 음성 녹음 중지
    private fun stopVoiceRecording() {
        try {
            Log.i(TAG, "⏹️ 음성 녹음 중지 시작")
            
            // 상태 플래그 먼저 설정
            isVoiceAnalyzing = false
            isListeningForCommands = false
            
            // MediaRecorder 안전하게 중지
            try {
                mediaRecorder?.apply {
                    try {
                        if (isRecording) {
                            stop()
                            Log.d(TAG, "✅ MediaRecorder 정지 완료")
                        }
                        reset()
                        Log.d(TAG, "✅ MediaRecorder 리셋 완료")
                        release()
                        Log.d(TAG, "✅ MediaRecorder 해제 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ MediaRecorder 상태 확인 중 오류: ${e.message}")
                        try {
                            release()
                        } catch (e2: Exception) {
                            Log.w(TAG, "⚠️ MediaRecorder 강제 해제 중 오류: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ MediaRecorder 정리 중 오류: ${e.message}")
            } finally {
                mediaRecorder = null
            }
            
            // AudioRecord 안전하게 정리
            safeCleanupAudioRecord()
            
            // 녹음 파일 처리
            try {
                val audioFile = File(getExternalFilesDir(null), "voice_recording.m4a")
                if (audioFile.exists() && audioFile.length() > 0) {
                    Log.i(TAG, "📁 녹음 파일 생성됨: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                    
                    // 백엔드로 전송
                    startBackendUpload()
                } else {
                    Log.w(TAG, "⚠️ 녹음 파일이 존재하지 않거나 비어있습니다")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 녹음 파일 처리 실패: ${e.message}")
            }
            
            Log.i(TAG, "✅ 음성 녹음 중지 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 음성 녹음 중지 실패: ${e.message}")
            e.printStackTrace()
        }
    }
    
    // 호출어 감지 시 사용자 피드백 제공
    private fun provideWakeWordFeedback() {
        try {
            // 1. 진동 피드백
            provideVibrationFeedback()
            
            // 2. 아이콘 애니메이션 피드백
            provideIconAnimationFeedback()
            
            // 3. 로그 출력
            Log.i(TAG, "호출어 감지 피드백 제공 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "호출어 감지 피드백 제공 실패: ${e.message}")
        }
    }
    
    // 진동 피드백 제공
    private fun provideVibrationFeedback() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 이상: VibrationEffect 사용
                val vibrationEffect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                // Android 8.0 미만: 기존 방식
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
            
            Log.d(TAG, "진동 피드백 제공 완료")
        } catch (e: Exception) {
            Log.e(TAG, "진동 피드백 제공 실패: ${e.message}")
        }
    }
    
    // 아이콘 애니메이션 피드백 제공
    private fun provideIconAnimationFeedback() {
        try {
            chatbotIconView?.let { iconView ->
                // 1. 크기 애니메이션 (확대 후 축소)
                val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1.0f, 1.3f, 1.0f)
                val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1.0f, 1.3f, 1.0f)
                
                scaleX.duration = 300
                scaleY.duration = 300
                
                scaleX.start()
                scaleY.start()
                
                // 2. 회전 애니메이션 (약간의 회전)
                val rotation = ObjectAnimator.ofFloat(iconView, "rotation", 0f, 10f, -10f, 0f)
                rotation.duration = 400
                rotation.start()
                
                // 3. 알파 애니메이션 (깜빡임 효과)
                val alpha = ObjectAnimator.ofFloat(iconView, "alpha", 1.0f, 0.5f, 1.0f)
                alpha.duration = 200
                alpha.repeatCount = 2
                alpha.start()
                
                Log.d(TAG, "아이콘 애니메이션 피드백 제공 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "아이콘 애니메이션 피드백 제공 실패: ${e.message}")
        }
    }
    
    // 음성 녹음 시작
    private fun startVoiceRecording() {
        if (isRecording) {
            Log.w(TAG, "이미 녹음 중입니다")
            return
        }
        
        recordingScope.launch {
            try {
                val audioFile = File(filesDir, "voice_input_${System.currentTimeMillis()}.m4a")
                Log.i(TAG, "음성 녹음 시작: ${audioFile.absolutePath}")
                
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(16000) // 범용 샘플레이트
                    setAudioEncodingBitRate(128000) // 범용 비트레이트
                    setOutputFile(audioFile.absolutePath)
                    
                    try {
                        prepare()
                        start()
                        isRecording = true
                        Log.i(TAG, "MediaRecorder 시작됨")
                        
                        // 설정된 시간 후 녹음 종료
                        delay(RECORDING_DURATION_MS.toLong())
                        stopVoiceRecording(audioFile)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaRecorder 시작 실패: ${e.message}")
                        isRecording = false
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "음성 녹음 시작 실패: ${e.message}")
                isRecording = false
            }
        }
    }
    
    // 음성 녹음 종료 및 백엔드 전송
    private fun stopVoiceRecording(audioFile: File) {
        recordingScope.launch {
            try {
                if (isRecording && mediaRecorder != null) {
                    mediaRecorder?.apply {
                        stop()
                        release()
                    }
                    mediaRecorder = null
                    isRecording = false
                    Log.i(TAG, "음성 녹음 종료: ${audioFile.absolutePath}")
                    
                    // 백엔드로 음성 파일 전송
                    sendVoiceToBackend(audioFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "음성 녹음 종료 실패: ${e.message}")
                isRecording = false
                mediaRecorder = null
            }
        }
    }
    
    // 백엔드로 음성 파일 전송
    private fun sendVoiceToBackend(audioFile: File) {
        recordingScope.launch {
            try {
                Log.i(TAG, "백엔드로 음성 파일 전송 시작")
                Log.i(TAG, "음성 파일 경로: ${audioFile.absolutePath}")
                Log.i(TAG, "음성 파일 크기: ${audioFile.length()} bytes")
                
                // HTTP 요청으로 음성 파일 전송
                sendVoiceToServer(audioFile)
                
                // 음성 전송 완료 후 3초 후에 챗봇 아이콘 다시 표시
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isChatbotActivated) {
                        showChatbotIcon()
                        Log.d(TAG, "음성 전송 완료 후 챗봇 아이콘 복원")
                    }
                }, 3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "백엔드 전송 실패: ${e.message}")
                // 에러 발생 시에도 챗봇 아이콘 복원
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isChatbotActivated) {
                        showChatbotIcon()
                        Log.d(TAG, "에러 발생 후 챗봇 아이콘 복원")
                    }
                }, 1000)
            }
        }
    }
    
    // 서버로 음성 파일 전송
    private suspend fun sendVoiceToServer(audioFile: File) {
        try {
            val url = "http://localhost:8081/api/chatbot/chat"
            
            val boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("User-Agent", "EUM-App/1.0")
            
            val outputStream = connection.outputStream
            val writer = java.io.OutputStreamWriter(outputStream)
            
            // 파일 데이터 추가
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"voice\"; filename=\"${audioFile.name}\"\r\n")
            writer.append("Content-Type: audio/mp4\r\n\r\n")
            writer.flush()
            
            // 파일 내용 복사
            audioFile.inputStream().use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()
            
            writer.append("\r\n")
            writer.append("--$boundary--\r\n")
            writer.flush()
            
            val responseCode = connection.responseCode
            Log.i(TAG, "서버 응답 코드: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.i(TAG, "서버 응답: $response")
            } else {
                Log.e(TAG, "서버 오류: $responseCode")
            }
            
            connection.disconnect()
            
        } catch (e: Exception) {
            Log.e(TAG, "서버 전송 실패: ${e.message}")
        }
    }
    
    // assets 파일을 내부 저장소로 복사
    private fun copyAssetToFile(assetName: String): File {
        try {
            Log.d(TAG, "assets 파일 복사 시작: $assetName")
            
            // 여러 경로에서 파일 접근 시도
            val possiblePaths = listOf(
                assetName,  // 직접 접근
                "flutter_assets/$assetName",  // Flutter assets 경로
                "assets/$assetName"  // assets 폴더 경로
            )
            
            var inputStream: InputStream? = null
            var usedPath = ""
            
            for (path in possiblePaths) {
                try {
                    inputStream = assets.open(path)
                    usedPath = path
                    Log.d(TAG, "파일 접근 성공: $path")
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "파일 접근 실패: $path - ${e.message}")
                }
            }
            
            if (inputStream == null) {
                throw FileNotFoundException("모든 경로에서 파일을 찾을 수 없습니다: $assetName")
            }
            
            val outputFile = File(filesDir, assetName)
            val outputStream = FileOutputStream(outputFile)
            
            Log.d(TAG, "사용된 경로: $usedPath")
            Log.d(TAG, "출력 파일 경로: ${outputFile.absolutePath}")
            
            inputStream.use { input ->
                outputStream.use { output ->
                    val bytesCopied = input.copyTo(output)
                    Log.d(TAG, "파일 복사 완료: $assetName (${bytesCopied} bytes)")
                }
            }
            
            // 파일이 실제로 생성되었는지 확인
            if (outputFile.exists()) {
                Log.d(TAG, "파일 생성 확인됨: ${outputFile.absolutePath} (크기: ${outputFile.length()} bytes)")
            } else {
                Log.e(TAG, "파일 생성 실패: ${outputFile.absolutePath}")
            }
            
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "assets 파일 복사 실패: $assetName - ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // 호출어 감지 테스트 (사용자 테스트용)
    private fun testWakeWordDetection() {
        Log.i(TAG, "🧪 호출어 감지 테스트 시작")
        
        // 1. PorcupineManager 상태 확인 (isListening 필드가 private이므로 다른 방법 사용)
        val isRunning = porcupineManager != null
        Log.d(TAG, "🔍 PorcupineManager 상태: ${if (isRunning) "실행 중" else "중지됨"}")
        
        // 2. 파일 존재 여부 확인
        try {
            val ppnFile = File(filesDir, "이음봇아_ko_android_v3_0_0.ppn")
            val pvFile = File(filesDir, "porcupine_params_ko.pv")
            
            Log.d(TAG, "📁 PPN 파일: ${if (ppnFile.exists()) "존재함 (${ppnFile.length()} bytes)" else "존재하지 않음"}")
            Log.d(TAG, "📁 PV 파일: ${if (pvFile.exists()) "존재함 (${pvFile.length()} bytes)" else "존재하지 않음"}")
            
            if (!ppnFile.exists() || !pvFile.exists()) {
                Log.e(TAG, "❌ 필요한 파일이 없습니다. PorcupineManager 재초기화 시도...")
                initializePorcupineManager()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "파일 확인 실패: ${e.message}")
        }
        
        // 3. PorcupineManager 재시작 시도
        if (!isRunning) {
            Log.w(TAG, "⚠️ PorcupineManager가 null입니다. 재초기화 시도...")
            try {
                initializePorcupineManager()
                Log.d(TAG, "✅ PorcupineManager 재초기화 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ PorcupineManager 재초기화 실패: ${e.message}")
            }
        }
        
        // 4. 테스트 결과 요약
        Log.i(TAG, "📊 호출어 감지 테스트 완료")
        Log.i(TAG, "💡 '이음봇아'라고 말해보세요!")
    }

    override fun onDestroy() {
        try {
            Log.i(TAG, "🔄 OverlayService 종료 시작")
            
            // 상태 플래그 먼저 설정
            isChatbotActivated = false
            isVoiceAnalyzing = false
            isListeningForCommands = false
            
            // PorcupineManager 안전하게 정리
            try {
                porcupineManager?.apply {
                    stop()
                    delete()
                    Log.d(TAG, "✅ PorcupineManager 정리 완료")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ PorcupineManager 정리 중 오류: ${e.message}")
            } finally {
                porcupineManager = null
            }
            
            // CoroutineScope 취소
            try {
                voiceAnalysisScope.cancel()
                Log.d(TAG, "✅ voiceAnalysisScope 취소 완료")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ voiceAnalysisScope 취소 중 오류: ${e.message}")
            }
            
            // MediaRecorder 안전하게 정리
            try {
                mediaRecorder?.apply {
                    try {
                        if (isRecording) {
                            stop()
                        }
                        reset()
                        release()
                        Log.d(TAG, "✅ MediaRecorder 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ MediaRecorder 상태 확인 중 오류: ${e.message}")
                        try {
                            release()
                        } catch (e2: Exception) {
                            Log.w(TAG, "⚠️ MediaRecorder 강제 해제 중 오류: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ MediaRecorder 정리 중 오류: ${e.message}")
            } finally {
                mediaRecorder = null
            }
            
            // AudioRecord 안전하게 정리
            safeCleanupAudioRecord()
            
            // 오버레이 뷰 제거
            try {
                if (overlayView != null && windowManager != null) {
                    windowManager?.removeView(overlayView)
                    overlayView = null
                    Log.d(TAG, "✅ 오버레이 뷰 제거 완료")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 오버레이 뷰 제거 중 오류: ${e.message}")
            }
            
            // 브로드캐스트 리시버 등록 해제
            try {
                unregisterReceiver(chatbotResponseReceiver)
                Log.d(TAG, "✅ 브로드캐스트 리시버 등록 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 브로드캐스트 리시버 등록 해제 중 오류: ${e.message}")
            }
            
            Log.i(TAG, "✅ OverlayService 종료 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ OverlayService 종료 중 오류: ${e.message}")
            e.printStackTrace()
        } finally {
            super.onDestroy()
        }
    }
}
