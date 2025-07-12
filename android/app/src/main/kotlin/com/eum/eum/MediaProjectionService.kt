package com.example.eum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity

class MediaProjectionService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var projectionResultCode: Int = 0
    private var projectionData: Intent? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var cropRect: android.graphics.Rect? = null
    private var isRequestingPermission = false
    private val CHANNEL_ID = "MediaProjectionServiceChannel"
    private val NOTIFICATION_ID = 3
    private val TAG = "MediaProjectionService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MediaProjectionService onStartCommand 호출 - action: ${intent?.action}")

        when (intent?.action) {
            "SET_PROJECTION" -> {
                Log.d(TAG, "SET_PROJECTION 액션 처리 시작")
                // MediaProjection 권한 설정
                val resultCode = intent.getIntExtra("result_code", -1)
                val data = intent.getParcelableExtra<Intent>("data")
                Log.d(TAG, "SET_PROJECTION - resultCode: $resultCode, data: $data")
                
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // 크롭 영역 정보도 함께 설정
                    cropRect = android.graphics.Rect(
                        intent.getIntExtra("rect_left", 0),
                        intent.getIntExtra("rect_top", 0),
                        intent.getIntExtra("rect_right", 0),
                        intent.getIntExtra("rect_bottom", 0)
                    )
                    Log.d(TAG, "SET_PROJECTION - cropRect: $cropRect")
                    
                    setMediaProjection(resultCode, data)
                    // 권한 설정 완료 후 캡처 요청이 있으면 바로 실행
                    if (cropRect != null) {
                        Log.d(TAG, "권한 설정 완료 후 캡처 실행")
                        captureImageWithCurrentProjection()
                    } else {
                        Log.d(TAG, "cropRect가 null이므로 캡처 실행하지 않음")
                    }
                } else {
                    Log.e(TAG, "SET_PROJECTION - 유효하지 않은 데이터: resultCode=$resultCode, data=$data")
                }
                return START_STICKY
            }
            "PERMISSION_DENIED" -> {
                // 권한이 거부된 경우
                Log.d(TAG, "MediaProjection 권한이 거부됨")
                isRequestingPermission = false
                Toast.makeText(this, "화면 캡처 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
            "CAPTURE_IMAGE" -> {
                // 이미지 캡처 요청
                cropRect = android.graphics.Rect(
                    intent.getIntExtra("rect_left", 0),
                    intent.getIntExtra("rect_top", 0),
                    intent.getIntExtra("rect_right", 0),
                    intent.getIntExtra("rect_bottom", 0)
                )

                Log.d(TAG, "CAPTURE_IMAGE 요청 - mediaProjection: ${mediaProjection != null}, projectionData: ${projectionData != null}")

                if (mediaProjection != null) {
                    // 이미 권한이 있으면 바로 캡처
                    Log.d(TAG, "기존 MediaProjection으로 캡처 시작")
                    captureImageWithCurrentProjection()
                } else if (projectionData != null) {
                    // 권한 데이터는 있지만 MediaProjection이 null인 경우, 다시 설정
                    Log.d(TAG, "projectionData가 있으므로 MediaProjection 재설정")
                    setMediaProjection(projectionResultCode, projectionData!!)
                    captureImageWithCurrentProjection()
                } else {
                    // 권한이 없으면 권한 요청 (단, 이미 권한 요청 중이 아닌 경우에만)
                    Log.d(TAG, "MediaProjection 권한이 없으므로 권한 요청")
                    if (!isRequestingPermission) {
                        isRequestingPermission = true
                        requestMediaProjectionPermission()
                    } else {
                        Log.d(TAG, "이미 권한 요청 중입니다.")
                    }
                }
                return START_STICKY
            }
            else -> {
                // 액션이 없거나 기존 호환성을 위한 처리
                Log.d(TAG, "액션이 없는 onStartCommand 호출")

                if (intent?.hasExtra("rect_left") == true) {
                    // 크롭 영역이 있는 경우 (기존 호환성)
                    cropRect = android.graphics.Rect(
                        intent.getIntExtra("rect_left", 0),
                        intent.getIntExtra("rect_top", 0),
                        intent.getIntExtra("rect_right", 0),
                        intent.getIntExtra("rect_bottom", 0)
                    )

                    val resultCode = intent.getIntExtra("result_code", -1)
                    val data = intent.getParcelableExtra<Intent>("data")

                    if (mediaProjection == null && data != null) {
                        // 권한을 처음 받은 경우
                        setMediaProjection(resultCode, data)
                        captureImageWithCurrentProjection()
                    } else if (mediaProjection != null) {
                        // 이미 권한이 있는 경우
                        captureImageWithCurrentProjection()
                    } else {
                        // 권한이 없고 data도 없음 → 권한 요청
                        Log.d(TAG, "MediaProjection 권한이 없습니다. 권한 요청 필요.")
                        requestMediaProjectionPermission()
                    }
                } else {
                    // 서비스가 처음 시작된 경우 권한 요청
                    Log.d(TAG, "서비스 시작 - MediaProjection 권한 요청")
                    requestMediaProjectionPermission()
                }
                return START_STICKY
            }
        }
    }

    private fun setMediaProjection(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "setMediaProjection 시작 - resultCode: $resultCode")
            projectionResultCode = resultCode
            projectionData = data
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection != null) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection onStop 콜백")
                        cleanup()
                    }
                }, Handler(Looper.getMainLooper()))

                Log.d(TAG, "MediaProjection 설정 완료")
                isRequestingPermission = false
            } else {
                Log.e(TAG, "MediaProjection 생성 실패 - resultCode: $resultCode")
                isRequestingPermission = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 설정 실패: ${e.message}")
        }
    }

    private fun requestMediaProjectionPermission() {
        try {
            Log.d(TAG, "MediaProjection 권한 요청")
            val intent = Intent(this, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("rect_left", cropRect?.left ?: 0)
            intent.putExtra("rect_top", cropRect?.top ?: 0)
            intent.putExtra("rect_right", cropRect?.right ?: 0)
            intent.putExtra("rect_bottom", cropRect?.bottom ?: 0)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 권한 요청 실패: ${e.message}")
            Toast.makeText(this, "화면 캡처 권한을 요청할 수 없습니다.", Toast.LENGTH_SHORT).show()
            // 권한 요청 실패 시 서비스 종료
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Media Projection Service Channel",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Media projection service notification channel"
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun createNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EUM - 화면 캡처")
            .setContentText("화면을 캡처하고 있습니다...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun captureImageWithCurrentProjection() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection이 null입니다.")
            return
        }

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        screenDensity = displayMetrics.densityDpi

        Log.d(TAG, "화면 크기: ${screenWidth}x${screenHeight}, 밀도: $screenDensity")

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        Log.d(TAG, "VirtualDisplay 생성 완료: ${virtualDisplay != null}")

        // 대기 시간을 500ms로 단축
        Handler(Looper.getMainLooper()).postDelayed({
            captureImage()
        }, 500) // 500ms로 단축
    }

    private fun captureImage() {
        try {
            Log.d(TAG, "이미지 캡처 시작")

            var imageCaptured = false

            imageReader?.setOnImageAvailableListener({ reader ->
                if (imageCaptured) return@setOnImageAvailableListener

                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        imageCaptured = true
                        Log.d(TAG, "이미지 획득 성공")

                        // 비동기로 이미지 처리
                        Thread {
                            try {
                                // Bitmap으로 변환
                                val bitmap = imageToBitmap(image)
                                image.close()

                                // 크롭 처리
                                cropAndSaveImage(bitmap)

                                // 메인 스레드에서 리소스 정리
                                Handler(Looper.getMainLooper()).post {
                                    cleanupCaptureResources()
                                    // 캡처 완료 후 서비스 종료 (지연 시간 단축)
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        stopSelf()
                                    }, 500) // 500ms로 단축
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "이미지 처리 실패: ${e.message}")
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(this, "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                                    cleanupCaptureResources()
                                    stopSelf()
                                }
                            }
                        }.start()

                    } else {
                        Log.e(TAG, "이미지 획득 실패")
                        Toast.makeText(this, "화면 캡처에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "이미지 처리 실패: ${e.message}")
                    Toast.makeText(this, "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))

            // 타임아웃 시간을 3초로 단축
            Handler(Looper.getMainLooper()).postDelayed({
                if (!imageCaptured) {
                    Log.e(TAG, "이미지 캡처 타임아웃 - 대안 방법 시도")
                    
                    // 타임아웃 시 대안 방법으로 직접 이미지 획득 시도
                    try {
                        val image = imageReader?.acquireLatestImage()
                        if (image != null) {
                            Log.d(TAG, "대안 방법으로 이미지 획득 성공")
                            imageCaptured = true
                            
                            // 비동기로 이미지 처리
                            Thread {
                                try {
                                    // Bitmap으로 변환
                                    val bitmap = imageToBitmap(image)
                                    image.close()

                                    // 크롭 처리
                                    cropAndSaveImage(bitmap)

                                    // 메인 스레드에서 리소스 정리
                                    Handler(Looper.getMainLooper()).post {
                                        cleanupCaptureResources()
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            stopSelf()
                                        }, 500) // 500ms로 단축
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "대안 방법 실패: ${e.message}")
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(this, "화면 캡처 시간이 초과되었습니다.", Toast.LENGTH_SHORT).show()
                                        cleanupCaptureResources()
                                        stopSelf()
                                    }
                                }
                            }.start()
                        } else {
                            Log.e(TAG, "대안 방법으로도 이미지 획득 실패")
                            Toast.makeText(this, "화면 캡처 시간이 초과되었습니다.", Toast.LENGTH_SHORT).show()
                            cleanupCaptureResources()
                            Handler(Looper.getMainLooper()).postDelayed({
                                stopSelf()
                            }, 500) // 500ms로 단축
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "대안 방법 실패: ${e.message}")
                        Toast.makeText(this, "화면 캡처 시간이 초과되었습니다.", Toast.LENGTH_SHORT).show()
                        cleanupCaptureResources()
                        Handler(Looper.getMainLooper()).postDelayed({
                            stopSelf()
                        }, 500) // 500ms로 단축
                    }
                }
            }, 3000) // 3초로 단축

        } catch (e: Exception) {
            Log.e(TAG, "이미지 캡처 설정 실패: ${e.message}")
            Toast.makeText(this, "이미지 캡처 설정에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return bitmap
    }

    private fun cropAndSaveImage(originalBitmap: Bitmap) {
        try {
            Log.d(TAG, "이미지 크롭 및 저장 시작")

            cropRect?.let { rect ->
                // 크롭 영역이 화면 범위 내에 있는지 확인
                val safeRect = android.graphics.Rect(
                    rect.left.coerceAtLeast(0),
                    rect.top.coerceAtLeast(0),
                    rect.right.coerceAtMost(originalBitmap.width),
                    rect.bottom.coerceAtMost(originalBitmap.height)
                )

                if (safeRect.width() > 0 && safeRect.height() > 0) {
                    // 이미지 크롭 (메모리 효율적으로)
                    val croppedBitmap = try {
                        Bitmap.createBitmap(
                            originalBitmap,
                            safeRect.left,
                            safeRect.top,
                            safeRect.width(),
                            safeRect.height()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "비트맵 크롭 실패: ${e.message}")
                        // 크롭 실패 시 원본 비트맵 사용
                        originalBitmap
                    }

                    // 파일로 저장
                    saveImageToFile(croppedBitmap)

                    // 메모리 정리
                    if (croppedBitmap != originalBitmap) {
                        croppedBitmap.recycle()
                    }
                    originalBitmap.recycle()

                } else {
                    Log.e(TAG, "유효하지 않은 크롭 영역: $safeRect")
                    originalBitmap.recycle()
                    Toast.makeText(this, "선택된 영역이 유효하지 않습니다.", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Log.e(TAG, "크롭 영역이 null")
                originalBitmap.recycle()
                Toast.makeText(this, "크롭 영역 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "이미지 크롭 및 저장 실패: ${e.message}")
            originalBitmap.recycle()
            Toast.makeText(this, "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToFile(bitmap: Bitmap) {
        try {
            Log.d(TAG, "이미지 저장 시작")
            
            // 내부 저장소에 저장 (권한 문제 없음)
            val internalDir = File(filesDir, "EUM_Captures")
            
            if (!internalDir.exists()) {
                internalDir.mkdirs()
            }

            // 파일명 생성 (날짜_시간.png)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "capture_$timestamp.png"
            val internalFile = File(internalDir, fileName)

            // 내부 저장소에 파일로 저장 (압축 품질을 90으로 조정하여 속도 향상)
            FileOutputStream(internalFile).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                if (success) {
                    Log.d(TAG, "내부 저장소 이미지 저장 완료: ${internalFile.absolutePath}")
                    // Flutter로 결과 전송
                    sendResultToFlutter(internalFile.absolutePath)
                    return
                }
            }

            // 내부 저장소 실패 시 외부 저장소 시도
            Log.d(TAG, "외부 저장소에 저장 시도...")
            
            // Android 10 이상에서는 앱 전용 디렉토리 사용
            val appDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val externalDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                File(externalDir, "EUM_Captures")
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                File(publicDir, "EUM_Captures")
            }
            
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val externalFile = File(appDir, fileName)

            // 외부 저장소에 파일로 저장 (압축 품질을 90으로 조정)
            FileOutputStream(externalFile).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                if (success && externalFile.exists() && externalFile.length() > 0) {
                    Log.d(TAG, "외부 저장소 이미지 저장 완료: ${externalFile.absolutePath}")
                    // Flutter로 결과 전송
                    sendResultToFlutter(externalFile.absolutePath)
                } else {
                    Log.e(TAG, "외부 저장소 파일 저장 실패")
                    Toast.makeText(this, "이미지 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "이미지 저장 실패: ${e.message}")
            Toast.makeText(this, "이미지 저장에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendResultToFlutter(filePath: String) {
        try {
            Log.d(TAG, "Flutter로 결과 전송 시작: $filePath")
            
            // Broadcast로 Flutter에 결과 전송
            val intent = Intent("com.example.eum.CAPTURE_COMPLETE")
            intent.putExtra("file_path", filePath)
            intent.setPackage(packageName) // 명시적으로 패키지 지정
            
            Log.d(TAG, "브로드캐스트 전송 시도 - 액션: ${intent.action}, 패키지: ${intent.getPackage()}")
            sendBroadcast(intent)
            
            Log.d(TAG, "브로드캐스트 전송 완료")
            
            // 일반 브로드캐스트만 사용
            
        } catch (e: Exception) {
            Log.e(TAG, "Flutter로 결과 전송 실패: ${e.message}")
        }
    }

    private fun cleanupCaptureResources() {
        try {
            Log.d(TAG, "캡처 리소스 정리 시작")

            // VirtualDisplay 해제
            virtualDisplay?.let { display ->
                try {
                    display.release()
                    Log.d(TAG, "VirtualDisplay 해제 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "VirtualDisplay 해제 실패: ${e.message}")
                }
            }
            virtualDisplay = null

            // ImageReader 해제
            imageReader?.let { reader ->
                try {
                    reader.close()
                    Log.d(TAG, "ImageReader 해제 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "ImageReader 해제 실패: ${e.message}")
                }
            }
            imageReader = null

            Log.d(TAG, "캡처 리소스 정리 완료")

        } catch (e: Exception) {
            Log.e(TAG, "캡처 리소스 정리 실패: ${e.message}")
        }
    }

    private fun cleanup() {
        try {
            Log.d(TAG, "전체 리소스 정리 시작")

            cleanupCaptureResources()

            // MediaProjection 중지 (서비스가 완전히 종료될 때만)
            mediaProjection?.let { projection ->
                try {
                    projection.stop()
                    Log.d(TAG, "MediaProjection 중지 완료")
                } catch (e: Exception) {
                    Log.e(TAG, "MediaProjection 중지 실패: ${e.message}")
                }
            }
            mediaProjection = null
            projectionResultCode = 0
            projectionData = null

            Log.d(TAG, "전체 리소스 정리 완료")

        } catch (e: Exception) {
            Log.e(TAG, "전체 리소스 정리 실패: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스가 완전히 종료될 때만 mediaProjection stop
        cleanup()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 앱이 백그라운드로 갈 때도 서비스 유지
        Log.d(TAG, "onTaskRemoved - 서비스 유지")
    }
} 
