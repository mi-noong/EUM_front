package com.example.eum

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class ScreenCaptureActivity : Activity() {
    private var cropRect: android.graphics.Rect? = null
    private val TAG = "ScreenCaptureActivity"
    
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ScreenCaptureActivity onCreate 시작")
        
        try {
            // 투명한 배경으로 설정하고 전체 화면으로 만들기
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            
            // 크롭할 영역 정보 가져오기
            cropRect = android.graphics.Rect(
                intent.getIntExtra("rect_left", 0),
                intent.getIntExtra("rect_top", 0),
                intent.getIntExtra("rect_right", 0),
                intent.getIntExtra("rect_bottom", 0)
            )
            
            Log.d(TAG, "크롭 영역: $cropRect")

            // MediaProjection 권한 요청
            requestMediaProjectionPermission()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 실패: ${e.message}")
            finish()
        }
    }
    
    private fun requestMediaProjectionPermission() {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            )
            Log.d(TAG, "MediaProjection 권한 요청")
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 권한 요청 실패: ${e.message}")
            Toast.makeText(this, "화면 캡처 권한을 요청할 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        Log.d(TAG, "onActivityResult 호출됨 - requestCode: $requestCode, resultCode: $resultCode, data: $data")
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            Log.d(TAG, "MediaProjection 결과 처리 - resultCode: $resultCode, RESULT_OK: ${RESULT_OK}")
            
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "MediaProjection 권한 승인됨")
                // 권한을 받은 후 MediaProjectionService에 전달
                setMediaProjectionToService(resultCode, data)
                finish()
            } else {
                Log.d(TAG, "MediaProjection 권한 거부됨 - resultCode: $resultCode, data: $data")
                Toast.makeText(this, "화면 캡처 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                
                // 권한이 거부되면 MediaProjectionService에 알림
                notifyPermissionDenied()
                finish()
            }
        }
    }
    
    private fun setMediaProjectionToService(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "MediaProjectionService에 권한 설정")
            Log.d(TAG, "전달할 데이터 - resultCode: $resultCode, data: $data")
            
            val serviceIntent = Intent(this, MediaProjectionService::class.java)
            serviceIntent.action = "SET_PROJECTION"
            serviceIntent.putExtra("result_code", resultCode)
            serviceIntent.putExtra("data", data)
            // 크롭 영역 정보도 함께 전달
            serviceIntent.putExtra("rect_left", cropRect?.left ?: 0)
            serviceIntent.putExtra("rect_top", cropRect?.top ?: 0)
            serviceIntent.putExtra("rect_right", cropRect?.right ?: 0)
            serviceIntent.putExtra("rect_bottom", cropRect?.bottom ?: 0)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "MediaProjectionService에 권한 설정 완료")
            
            // Activity 종료 (서비스는 계속 실행)
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjectionService에 권한 설정 실패: ${e.message}")
            Toast.makeText(this, "화면 캡처 서비스를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestCaptureAfterPermission() {
        try {
            Log.d(TAG, "권한 설정 후 캡처 요청")
            
            val serviceIntent = Intent(this, MediaProjectionService::class.java)
            serviceIntent.action = "CAPTURE_IMAGE"
            serviceIntent.putExtra("rect_left", cropRect?.left ?: 0)
            serviceIntent.putExtra("rect_top", cropRect?.top ?: 0)
            serviceIntent.putExtra("rect_right", cropRect?.right ?: 0)
            serviceIntent.putExtra("rect_bottom", cropRect?.bottom ?: 0)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "캡처 요청 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "캡처 요청 실패: ${e.message}")
            Toast.makeText(this, "화면 캡처를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun notifyPermissionDenied() {
        try {
            Log.d(TAG, "권한 거부 알림을 MediaProjectionService에 전송")
            
            val serviceIntent = Intent(this, MediaProjectionService::class.java)
            serviceIntent.action = "PERMISSION_DENIED"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "권한 거부 알림 전송 완료")
            
        } catch (e: Exception) {
            Log.e(TAG, "권한 거부 알림 전송 실패: ${e.message}")
        }
    }
}
