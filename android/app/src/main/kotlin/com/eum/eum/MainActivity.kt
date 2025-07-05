package com.example.eum //본인의 패키지명으로 수정 필요

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log

class MainActivity : FlutterActivity() {
    private val NAVER_CHANNEL = "com.example.eum/naver_login"
    private val OVERLAY_CHANNEL = "floating_widget"
    private var isOverlayVisible = false
    private var overlayReceiver: BroadcastReceiver? = null
    private val TAG = "MainActivity"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, NAVER_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "login") {
                NaverIdLoginSDK.authenticate(this, object : OAuthLoginCallback {
                    override fun onSuccess() {
                        val accessToken = NaverIdLoginSDK.getAccessToken()
                        result.success(accessToken)
                    }
                    override fun onFailure(httpStatus: Int, message: String) {
                        result.error("NAVER_LOGIN_FAILED", message, null)
                    }
                    override fun onError(errorCode: Int, message: String) {
                        result.error("NAVER_LOGIN_ERROR", message, null)
                    }
                })
            } else {
                result.notImplemented()
            }
        }

        overlayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "OVERLAY_SERVICE_STARTED" -> {
                        isOverlayVisible = true
                        Log.d(TAG, "오버레이 서비스 시작됨 - 상태: $isOverlayVisible")
                    }
                    "OVERLAY_SERVICE_STOPPED" -> {
                        isOverlayVisible = false
                        Log.d(TAG, "오버레이 서비스 종료됨 - 상태: $isOverlayVisible")
                    }
                }
            }
        }
        try {
            val filter = IntentFilter().apply {
                addAction("OVERLAY_SERVICE_STARTED")
                addAction("OVERLAY_SERVICE_STOPPED")
            }
            registerReceiver(overlayReceiver, filter)
            Log.d(TAG, "브로드캐스트 리시버 등록 완료")
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 리시버 등록 실패: ${e.message}")
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, OVERLAY_CHANNEL).setMethodCallHandler { call, result ->
            Log.d(TAG, "메서드 호출: ${call.method}")
            when (call.method) {
                "showOverlay" -> {
                    try {
                        Log.d(TAG, "오버레이 표시 시도...")
                        val intent = Intent(this, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        isOverlayVisible = true
                        Log.d(TAG, "오버레이 서비스 시작 명령 완료")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "오버레이 표시 실패: ${e.message}")
                        result.error("SHOW_OVERLAY_ERROR", e.message, null)
                    }
                }
                "hideOverlay" -> {
                    try {
                        Log.d(TAG, "오버레이 숨기기 시도...")
                        val intent = Intent(this, OverlayService::class.java)
                        stopService(intent)
                        isOverlayVisible = false
                        Log.d(TAG, "오버레이 서비스 종료 명령 완료")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "오버레이 숨기기 실패: ${e.message}")
                        result.error("HIDE_OVERLAY_ERROR", e.message, null)
                    }
                }
                "isOverlayVisible" -> {
                    val isServiceRunning = isServiceRunning(OverlayService::class.java)
                    Log.d(TAG, "오버레이 상태 확인: 브로드캐스트 상태=$isOverlayVisible, 서비스 상태=$isServiceRunning")
                    result.success(isServiceRunning)
                }
                "checkOverlayPermission" -> {
                    try {
                        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.canDrawOverlays(this)
                        } else {
                            true
                        }
                        Log.d(TAG, "오버레이 권한 상태: $granted")
                        result.success(granted)
                    } catch (e: Exception) {
                        Log.e(TAG, "권한 확인 실패: ${e.message}")
                        result.error("PERMISSION_CHECK_ERROR", e.message, null)
                    }
                }
                "requestOverlayPermission" -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.canDrawOverlays(this)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:$packageName")
                                )
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                Log.d(TAG, "오버레이 권한 요청 화면 열기")
                            }
                        }
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "권한 요청 실패: ${e.message}")
                        result.error("PERMISSION_REQUEST_ERROR", e.message, null)
                    }
                }
                else -> {
                    Log.w(TAG, "구현되지 않은 메서드: ${call.method}")
                    result.notImplemented()
                }
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayReceiver?.let { receiver ->
                unregisterReceiver(receiver)
                Log.d(TAG, "브로드캐스트 리시버 해제 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "브로드캐스트 리시버 해제 실패: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            NaverIdLoginSDK.initialize(
                this,
                "NT7nJduTFYkJRyR_P9uV",
                "BS_kyhA4Fa",
                "EUM"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
