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
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
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
    private val ACCESS_KEY = "JVZic8cgf3LNXFBS5/xvsGJ/xq7o+v8S6bSrTeMsT1ehRMmzCD1+2Q=="
    
    // 음성 녹음 관련 변수들
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val RECORDING_DURATION_MS = 10000 // 녹음 시간 (10초)
    
    // 실시간 음성 분석 관련 변수들
    private var audioRecord: AudioRecord? = null
    private var isVoiceAnalyzing = false
    private val voiceAnalysisScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 에너지 분석 설정 (범용 최적화)
    private val ENERGY_THRESHOLD = 600.0 // 범용 에너지 임계값 (낮춤)
    private val SILENCE_DURATION_MS = 3000 // 무음 지속 시간 (3초)
    private val ANALYSIS_INTERVAL_MS = 100 // 분석 간격 (100ms)
    private var lastVoiceTime = 0L // 마지막 음성 감지 시간
    
    // 범용 오디오 설정 (태블릿/핸드폰 호환)
    private val SAMPLE_RATE = 16000 // 16kHz - 음성 인식 최적
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO // 모노 채널
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // 16-bit PCM
    
    // 음성 명령 감지 관련 변수들 (범용 최적화)
    private var isListeningForCommands = false
    private val COMMAND_THRESHOLD = 1200.0 // 범용 명령 감지 임계값 (조정됨)
    private val COMMAND_SILENCE_DURATION_MS = 1000 // 명령 후 무음 지속 시간 (1초)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate 시작")
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            addOverlayView()
            initializePorcupineManager()
            Log.d(TAG, "OverlayService onCreate 완료")
        } catch (e: Exception) {
            Log.e(TAG, "OverlayService onCreate 실패: ${e.message}")
            stopSelf()
        }
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
                                    // 메뉴가 열릴 때 챗봇 완전히 비활성화
                                    if (isChatbotActivated) {
                                        deactivateChatbot()
                                    }
                                } else if (deltaY < -100 && isMenuOpen) {
                                    menu?.visibility = View.GONE
                                    isMenuOpen = false
                                    Log.d(TAG, "메뉴 닫기")
                                    // 메뉴가 닫혀도 챗봇은 비활성화 상태 유지
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
                    // 챗봇 활성화/비활성화 토글
                    if (isChatbotActivated) {
                        deactivateChatbot()
                    } else {
                        activateChatbot()
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
        
        // chatbot.png 아이콘 숨기기
        hideChatbotIcon()
    }

    // chatbot.png 아이콘 표시
    private fun showChatbotIcon() {
        try {
            Log.d(TAG, "chatbot.png 아이콘 표시 시작")
            
            // 이미 아이콘이 표시되어 있으면 제거
            hideChatbotIcon()
            
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
            
            // 화면 오른쪽 하단에 위치 (EUM 바 위)
            params.gravity = Gravity.BOTTOM or Gravity.END
            params.x = 20 // 오른쪽 여백
            params.y = 80 // EUM 바 위 여백
            
            // 아이콘 클릭 이벤트 설정
            chatbotFloatingIcon?.setOnClickListener {
                Log.d(TAG, "chatbot.png 아이콘 클릭됨!")
                onChatbotIconClicked()
            }
            
            // 화면에 아이콘 추가
            windowManager?.addView(chatbotFloatingIcon, params)
            Log.d(TAG, "chatbot.png 아이콘 표시 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "chatbot.png 아이콘 표시 실패: ${e.message}")
        }
    }
    
    // chatbot.png 아이콘 숨기기
    private fun hideChatbotIcon() {
        try {
            if (chatbotFloatingIcon != null) {
                Log.d(TAG, "chatbot.png 아이콘 숨기기 시작")
                windowManager?.removeView(chatbotFloatingIcon)
                chatbotFloatingIcon = null
                Log.d(TAG, "chatbot.png 아이콘 숨기기 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "chatbot.png 아이콘 숨기기 실패: ${e.message}")
        }
    }
    
    // chatbot.png 아이콘 클릭 시 처리
    private fun onChatbotIconClicked() {
        Log.d(TAG, "chatbot.png 아이콘 클릭으로 음성 녹음 시작")
        
        // 챗봇 아이콘 숨기기 (음성 녹음 중에는 숨김)
        hideChatbotIcon()
        
        // "이음봇아" 호출어 감지와 동일한 처리
        onWakeWordDetected()
    }



    // PorcupineManager 초기화 및 호출어 감지 시작
    private fun initializePorcupineManager() {
        try {
            Log.d(TAG, "PorcupineManager 초기화 시작")
            
            // assets에서 .ppn 파일과 .pv 파일을 복사
            val ppnFile = copyAssetToFile("이음봇아_ko_android_v3_0_0.ppn")
            val pvFile = copyAssetToFile("porcupine_params_ko.pv")
            
            Log.d(TAG, "PPN 파일 경로: ${ppnFile.absolutePath}")
            Log.d(TAG, "PV 파일 경로: ${pvFile.absolutePath}")
            Log.d(TAG, "Access Key: $ACCESS_KEY")
            
            // 호출어 감지 콜백 정의
            val wakeWordCallback = object : ai.picovoice.porcupine.PorcupineManagerCallback {
                override fun invoke(keywordIndex: Int) {
                    Log.i(TAG, "이음봇아 호출어 감지됨! (keywordIndex: $keywordIndex)")
                    onWakeWordDetected()
                }
            }
            
            // PorcupineManager 초기화
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeywordPaths(arrayOf(ppnFile.absolutePath))
                .setModelPath(pvFile.absolutePath)
                .build(this, wakeWordCallback)
            
            porcupineManager?.start()
            Log.d(TAG, "PorcupineManager 초기화 및 시작 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "PorcupineManager 초기화 실패: ${e.message}")
            e.printStackTrace()
            
            // 초기화 실패 시 테스트 시뮬레이션
            Log.w(TAG, "PorcupineManager 초기화 실패로 인한 테스트 시뮬레이션 시작")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.i(TAG, "테스트: 이음봇아 호출어 감지 시뮬레이션")
                onWakeWordDetected()
            }, 10000) // 10초 후 시뮬레이션
        }
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
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
            }
            
            Log.d(TAG, "정지 명령 피드백 제공 완료")
        } catch (e: Exception) {
            Log.e(TAG, "정지 명령 피드백 제공 실패: ${e.message}")
        }
    }
    
    // 호출어 감지 시 처리
    private fun onWakeWordDetected() {
        Log.i(TAG, "호출어 감지 - 즉시 음성 녹음 시작")
        
        // 사용자 피드백 제공
        provideWakeWordFeedback()
        
        // 챗봇 활성화와 동시에 음성 녹음 시작
        activateChatbot()
        startVoiceAnalysis()
        startVoiceRecordingInApp()
        
        Log.i(TAG, "호출어 감지와 동시에 모든 기능 활성화 완료")
    }
    
    // Flutter 앱에서 음성 녹음 시작
    private fun startVoiceRecordingInApp() {
        try {
            Log.i(TAG, "Flutter 앱에 음성 녹음 시작 브로드캐스트 전송")
            
            // 챗봇 아이콘 숨기기 (음성 녹음 중)
            hideChatbotIcon()
            
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
            Log.w(TAG, "이미 음성 분석 중입니다")
            return
        }
        
        voiceAnalysisScope.launch {
            try {
                Log.i(TAG, "실시간 음성 분석 시작")
                isVoiceAnalyzing = true
                isListeningForCommands = true
                lastVoiceTime = System.currentTimeMillis()
                
                // MediaRecorder 시작 (실제 녹음 파일 생성)
                startMediaRecorder()
                
                // AudioRecord 초기화 (범용 최적화)
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                
                // 범용 호환성을 위한 버퍼 크기 조정
                val optimizedBufferSize = bufferSize * 3 // 안정성을 위해 버퍼 크기 증가
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    optimizedBufferSize
                )
                
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 초기화 실패 - 범용 호환성 문제 가능성")
                    Log.e(TAG, "AudioRecord 상태: ${audioRecord?.state}")
                    Log.e(TAG, "버퍼 크기: $optimizedBufferSize, 최소 버퍼 크기: $bufferSize")
                    return@launch
                }
                
                audioRecord?.startRecording()
                Log.i(TAG, "AudioRecord 녹음 시작 (범용 최적화: 샘플레이트 ${SAMPLE_RATE}Hz, 버퍼 크기 ${optimizedBufferSize})")
                
                val buffer = ShortArray(bufferSize / 2)
                
                while (isVoiceAnalyzing) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0) {
                        // 에너지 계산
                        val energy = calculateEnergy(buffer, readSize)
                        Log.d(TAG, "현재 에너지: $energy")
                        
                        if (energy > ENERGY_THRESHOLD) {
                            // 음성 감지됨
                            lastVoiceTime = System.currentTimeMillis()
                            Log.d(TAG, "음성 감지됨 - 에너지: $energy")
                            
                            // 음성 명령 감지 (강한 음성인 경우)
                            detectVoiceCommand(energy)
                        } else {
                            // 무음 상태 확인 (더 정교한 로직)
                            val silenceDuration = System.currentTimeMillis() - lastVoiceTime
                            if (silenceDuration > SILENCE_DURATION_MS) {
                                Log.i(TAG, "무음 지속으로 인한 녹음 종료 (${silenceDuration}ms)")
                                stopVoiceRecording()
                                break
                            }
                        }
                        
                        // 에너지 변화율 계산 (추가 안정성)
                        if (energy > ENERGY_THRESHOLD * 1.5) {
                            Log.d(TAG, "강한 음성 감지 - 에너지: $energy")
                        }
                    }
                    
                    delay(ANALYSIS_INTERVAL_MS.toLong())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "음성 분석 실패: ${e.message}")
                isVoiceAnalyzing = false
                isListeningForCommands = false
            } finally {
                audioRecord?.apply {
                    stop()
                    release()
                }
                audioRecord = null
                isVoiceAnalyzing = false
                isListeningForCommands = false
                Log.i(TAG, "음성 분석 종료")
            }
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
    
    // 에너지 계산 (범용 최적화)
    private fun calculateEnergy(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        var maxAmplitude = 0.0
        
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
            maxAmplitude = maxOf(maxAmplitude, abs(sample))
        }
        
        // RMS 에너지와 최대 진폭을 결합한 개선된 에너지 계산
        val rmsEnergy = sqrt(sum / size)
        val normalizedEnergy = rmsEnergy * (maxAmplitude / 32768.0) // 16-bit 정규화
        
        return normalizedEnergy
    }
    
    // 음성 녹음 종료
    private fun stopVoiceRecording() {
        if (!isRecording) {
            Log.w(TAG, "녹음 중이 아닙니다")
            return
        }
        
        voiceAnalysisScope.launch {
            try {
                Log.i(TAG, "음성 녹음 종료 시작")
                
                // 음성 분석 중지
                isVoiceAnalyzing = false
                
                // MediaRecorder 중지
                if (mediaRecorder != null) {
                    mediaRecorder?.apply {
                        stop()
                        release()
                    }
                    mediaRecorder = null
                    isRecording = false
                    Log.i(TAG, "MediaRecorder 중지 완료")
                }
                
                // 백엔드로 음성 파일 전송
                val audioFile = File(filesDir, "voice_input_${System.currentTimeMillis()}.m4a")
                if (audioFile.exists()) {
                    sendVoiceToBackend(audioFile)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "음성 녹음 종료 실패: ${e.message}")
                isRecording = false
                mediaRecorder = null
            }
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService onDestroy 시작")
        try {
            // 음성 분석 중지
            isVoiceAnalyzing = false
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            
            // 녹음 중지
            if (isRecording && mediaRecorder != null) {
                try {
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    mediaRecorder = null
                    isRecording = false
                    Log.d(TAG, "MediaRecorder 리소스 해제 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaRecorder 리소스 해제 실패: ${e.message}")
                }
            }
            
            // CoroutineScope 취소
            recordingScope.cancel()
            voiceAnalysisScope.cancel()
            
            // PorcupineManager 리소스 해제
            porcupineManager?.let { manager ->
                try {
                    manager.stop()
                    manager.delete()
                    Log.d(TAG, "PorcupineManager 리소스 해제 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "PorcupineManager 리소스 해제 실패: ${e.message}")
                }
            }
            

            
            // chatbot.png 아이콘 제거
            hideChatbotIcon()
            
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
