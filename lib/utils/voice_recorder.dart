import 'dart:async';
import 'dart:io';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

class VoiceRecorder {
  static const MethodChannel _channel = MethodChannel('voice_recorder');
  static const EventChannel _eventChannel = EventChannel('voice_recorder_events');
  
  static StreamSubscription? _eventSubscription;
  static bool _isRecording = false;
  
  // 음성 녹음 시작
  static Future<void> startRecording(int durationMs) async {
    if (_isRecording) {
      print('이미 녹음 중입니다');
      return;
    }
    
    try {
      print('음성 녹음 시작...');
      _isRecording = true;
      
      // 임시 디렉토리에서 파일 생성
      final directory = await getTemporaryDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final audioFile = File('${directory.path}/voice_input_$timestamp.wav');
      
      print('녹음 파일 경로: ${audioFile.path}');
      
      // 녹음 시작
      await _channel.invokeMethod('startRecording', {
        'filePath': audioFile.path,
        'durationMs': durationMs,
      });
      
      print('음성 녹음 시작 완료');
      
      // 녹음 완료 대기
      await Future.delayed(Duration(milliseconds: durationMs));
      
      // 녹음 종료
      await stopRecording(audioFile);
      
    } catch (e) {
      print('음성 녹음 시작 실패: $e');
      _isRecording = false;
    }
  }
  
  // 음성 녹음 종료
  static Future<void> stopRecording(File audioFile) async {
    try {
      print('음성 녹음 종료...');
      
      await _channel.invokeMethod('stopRecording');
      _isRecording = false;
      
      print('음성 녹음 종료 완료');
      
      // 백엔드로 음성 파일 전송
      await sendVoiceToBackend(audioFile);
      
    } catch (e) {
      print('음성 녹음 종료 실패: $e');
      _isRecording = false;
    }
  }
  
  // 콜백 함수들
  static Function(String)? onVoiceRecorded;
  static Function(String)? onError;

  // 백엔드로 음성 파일 전송
  static Future<void> sendVoiceToBackend(File audioFile) async {
    try {
      print('백엔드로 음성 파일 전송 시작');
      print('음성 파일 경로: ${audioFile.path}');
      print('음성 파일 크기: ${await audioFile.length()} bytes');
      
      // 파일이 존재하는지 확인
      if (!await audioFile.exists()) {
        print('음성 파일이 존재하지 않습니다');
        onError?.call('음성 파일이 존재하지 않습니다');
        return;
      }
      
      // 챗봇 위젯에 음성 파일 경로 전달
      onVoiceRecorded?.call(audioFile.path);
      
    } catch (e) {
      print('백엔드 전송 실패: $e');
      onError?.call('음성 처리 중 오류가 발생했습니다: $e');
    }
  }
  
  // 이벤트 리스너 초기화
  static void initializeEventListeners() {
    _eventSubscription?.cancel();
    _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
      (event) {
        print('음성 녹음 이벤트: $event');
        // 필요한 경우 이벤트 처리
      },
      onError: (error) {
        print('음성 녹음 이벤트 오류: $error');
      },
    );
  }
  
  // 리소스 정리
  static void dispose() {
    _eventSubscription?.cancel();
    _isRecording = false;
  }
} 
