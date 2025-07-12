package com.example.eum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.View

class CaptureOverlayView(
    context: Context,
    private val onDragComplete: (Rect) -> Unit
) : View(context) {
    
    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val fillPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
    }
    
    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false
    private val TAG = "CaptureOverlayView"
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "터치 시작: ${event.x}, ${event.y}")
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDragging = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    Log.d(TAG, "드래그 중: ${event.x}, ${event.y}")
                    currentX = event.x
                    currentY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    Log.d(TAG, "드래그 완료: ${event.x}, ${event.y}")
                    isDragging = false
                    
                    val rect = Rect(
                        startX.toInt().coerceAtLeast(0),
                        startY.toInt().coerceAtLeast(0),
                        currentX.toInt().coerceAtMost(width),
                        currentY.toInt().coerceAtMost(height)
                    )
                    
                    // 최소 크기 확인 (50x50 픽셀)
                    if (rect.width() >= 50 && rect.height() >= 50) {
                        Log.d(TAG, "유효한 영역 선택됨: $rect")
                        onDragComplete(rect)
                    } else {
                        Log.d(TAG, "선택된 영역이 너무 작음: ${rect.width()}x${rect.height()}")
                        invalidate()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 배경을 반투명하게
        canvas.drawColor(Color.argb(100, 0, 0, 0))
        
        if (isDragging) {
            val rect = Rect(
                startX.toInt().coerceAtLeast(0),
                startY.toInt().coerceAtLeast(0),
                currentX.toInt().coerceAtMost(width),
                currentY.toInt().coerceAtMost(height)
            )
            
            // 선택 영역 배경
            canvas.drawRect(rect, fillPaint)
            
            // 선택 영역 테두리
            canvas.drawRect(rect, paint)
            
            // 크기 정보 표시
            val text = "${rect.width()} x ${rect.height()}"
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            
            val textX = rect.left + (rect.width() - textBounds.width()) / 2
            val textY = rect.top + (rect.height() + textBounds.height()) / 2
            
            // 텍스트 배경
            val textBgRect = Rect(
                textX - 10,
                textY - textBounds.height() - 10,
                textX + textBounds.width() + 10,
                textY + 10
            )
            canvas.drawRect(textBgRect, Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                alpha = 150
            })
            
            // 텍스트
            canvas.drawText(text, textX.toFloat(), textY.toFloat(), textPaint)
        } else {
            // 안내 텍스트
            val guideText = "드래그하여 캡처할 영역을 선택하세요"
            val textBounds = android.graphics.Rect()
            textPaint.getTextBounds(guideText, 0, guideText.length, textBounds)
            
            val textX = (width - textBounds.width()) / 2
            val textY = height / 2
            
            // 텍스트 배경
            val textBgRect = Rect(
                textX - 20,
                textY - textBounds.height() - 20,
                textX + textBounds.width() + 20,
                textY + 20
            )
            canvas.drawRect(textBgRect, Paint().apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                alpha = 150
            })
            
            // 텍스트
            canvas.drawText(guideText, textX.toFloat(), textY.toFloat(), textPaint)
        }
    }
}
