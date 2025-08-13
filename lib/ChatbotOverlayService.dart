import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'ChatOverlayWidget.dart';
import 'utils/voice_recorder.dart';

class ChatbotOverlayService {
  static OverlayEntry? _overlayEntry;
  static bool _isVisible = false;
  static BuildContext? _context;
  static ChatOverlayWidget? _currentWidget;

  // 초기화
  static void initialize(BuildContext context) {
    _context = context;
    
    // VoiceRecorder 콜백 설정
    VoiceRecorder.onVoiceRecorded = (audioFilePath) {
      print('음성 녹음 완료: $audioFilePath');
      // 챗봇이 이미 열려있다면 음성 메시지 추가
      if (_isVisible && _overlayEntry != null) {
        // 오버레이가 이미 표시 중이므로 ChatOverlayWidget에서 처리
      }
    };
    
    VoiceRecorder.onError = (error) {
      print('음성 녹음 오류: $error');
      _showErrorSnackBar(error);
    };
  }

  // 챗봇 오버레이 표시
  static void showChatOverlay() {
    if (_isVisible || _context == null) return;

    print('챗봇 오버레이 표시 시작');
    
    _currentWidget = ChatOverlayWidget(
      onClose: hideChatOverlay,
    );
    
    _overlayEntry = OverlayEntry(
      builder: (context) => _currentWidget!,
    );

    Overlay.of(_context!).insert(_overlayEntry!);
    _isVisible = true;
    
    print('챗봇 오버레이 표시 완료');
  }

  // 챗봇 오버레이 숨기기
  static void hideChatOverlay() {
    if (!_isVisible || _overlayEntry == null) return;

    print('챗봇 오버레이 숨기기 시작');
    
    _overlayEntry!.remove();
    _overlayEntry = null;
    _currentWidget = null;
    _isVisible = false;
    
    print('챗봇 오버레이 숨기기 완료');
  }

  // 오버레이 표시 상태 확인
  static bool get isVisible => _isVisible;

  // 음성 녹음 시작 (외부에서 호출)
  static Future<void> startVoiceRecording() async {
    if (!_isVisible) {
      // 챗봇이 열려있지 않으면 먼저 열기
      showChatOverlay();
      
      // 잠시 대기 후 음성 녹음 시작
      await Future.delayed(const Duration(milliseconds: 500));
    }
    
    try {
      await VoiceRecorder.startRecording(10000); // 10초 녹음
    } catch (e) {
      print('음성 녹음 시작 실패: $e');
      _showErrorSnackBar('음성 녹음을 시작할 수 없습니다.');
    }
  }

  // 에러 스낵바 표시
  static void _showErrorSnackBar(String message) {
    if (_context == null) return;
    
    ScaffoldMessenger.of(_context!).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  // 리소스 정리
  static void dispose() {
    hideChatOverlay();
    VoiceRecorder.onVoiceRecorded = null;
    VoiceRecorder.onError = null;
    _context = null;
  }
}
