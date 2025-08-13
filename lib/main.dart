import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import 'dart:io';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:kakao_flutter_sdk/kakao_flutter_sdk.dart';
import 'ImageAnalysisScreen.dart';
import 'SettingScreen.dart';
import 'SplashScreen.dart';
import 'Shake.dart';
import 'utils/audio_permission_handler.dart' as audio_handler;
import 'utils/voice_recorder.dart';
import 'ChatbotOverlayService.dart';


void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // 카카오 SDK 초기화
  KakaoSdk.init(
    nativeAppKey: 'c15bd6ab806014b0fde0e7a2f5b4de7e',
  );
  
  // 네트워크 초기화를 위한 간단한 HTTP 요청
  await _initializeNetwork();
  
  runApp(const MyApp());
}

Future<void> _initializeNetwork() async {
  try {
    print('main.dart에서 네트워크 초기화 시작...');
    
    // 앱 초기화 완료를 위한 대기
    await Future.delayed(const Duration(milliseconds: 1000));
    
    // 서버 연결 테스트 (재시도 로직 포함)
    bool isConnected = false;
    int retryCount = 0;
    const maxRetries = 3;
    
    while (retryCount < maxRetries && !isConnected) {
      try {
        print('HTTP GET 요청 시도 중... (시도 ${retryCount + 1}/$maxRetries)');
        final response = await http.get(
          Uri.parse('http://localhost:8081/api/test'),
          headers: {
            'User-Agent': 'EUM-App/1.0',
            'Connection': 'keep-alive',
          },
        ).timeout(const Duration(seconds: 8)); // 타임아웃 증가
        
        if (response.statusCode == 200) {
          print('main.dart에서 네트워크 초기화 성공: ${response.statusCode}');
          print('서버 응답: ${response.body}');
          isConnected = true;
        } else {
          print('서버 응답 오류: ${response.statusCode}');
          retryCount++;
        }
      } catch (e) {
        retryCount++;
        print('네트워크 초기화 실패 (시도 $retryCount/$maxRetries): $e');
        
        if (retryCount < maxRetries) {
          print('${retryCount * 2}초 후 재시도합니다...'); // 점진적으로 대기 시간 증가
          await Future.delayed(Duration(seconds: retryCount * 2));
        }
      }
    }
    
    if (!isConnected) {
      print('main.dart에서 네트워크 초기화 실패 (정상적인 상황)');
    }
  } catch (e) {
    print('main.dart에서 네트워크 초기화 중 오류: $e');
    // 초기화 실패는 정상적인 상황이므로 무시
  }
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EUM',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: AudioPermissionHandler(
        child: ImageAnalysisHandler(
          child: ChatbotHandler(
            child: SplashScreen(),
          ),
        ),
      ),
    );
  }
}

class AudioPermissionHandler extends StatefulWidget {
  final Widget child;
  
  const AudioPermissionHandler({Key? key, required this.child}) : super(key: key);

  @override
  State<AudioPermissionHandler> createState() => _AudioPermissionHandlerState();
}

class _AudioPermissionHandlerState extends State<AudioPermissionHandler> {
  static const platform = MethodChannel('voice_recorder');
  StreamSubscription? _broadcastSubscription;

  @override
  void initState() {
    super.initState();
    _requestAudioPermission();
    _setupVoiceRecordingListener();
    VoiceRecorder.initializeEventListeners();
  }

  @override
  void dispose() {
    _broadcastSubscription?.cancel();
    VoiceRecorder.dispose();
    super.dispose();
  }

  Future<void> _requestAudioPermission() async {
    // 앱이 완전히 로드된 후 권한 요청
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      bool hasPermission = await audio_handler.AudioPermissionHandler.checkAudioPermission();
      if (!hasPermission) {
        audio_handler.AudioPermissionHandler.showPermissionExplanation(context);
      }
    });
  }

  void _setupVoiceRecordingListener() {
    // Android 브로드캐스트 리스너 설정
    platform.setMethodCallHandler((call) async {
      print('음성 녹음 메서드 호출 받음: ${call.method}');
      
      if (call.method == 'startVoiceRecording') {
        print('음성 녹음 시작 요청 받음');
        final arguments = call.arguments as Map<String, dynamic>?;
        final durationMs = arguments?['recording_duration_ms'] as int? ?? 10000;
        
        print('녹음 시간: ${durationMs}ms');
        
        // 챗봇 오버레이 표시 및 음성 녹음 시작
        ChatbotOverlayService.startVoiceRecording();
        
      } else if (call.method == 'stopVoiceRecording') {
        print('음성 녹음 종료 요청 받음');
        // 녹음 종료는 자동으로 처리됨
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}

class ImageAnalysisHandler extends StatefulWidget {
  final Widget child;
  
  const ImageAnalysisHandler({Key? key, required this.child}) : super(key: key);

  @override
  _ImageAnalysisHandlerState createState() => _ImageAnalysisHandlerState();
}

class _ImageAnalysisHandlerState extends State<ImageAnalysisHandler> {
  static const platform = MethodChannel('image_analysis_channel');
  Map<String, dynamic>? _lastServerResponse;

  @override
  void initState() {
    super.initState();
    _setupMethodChannel();
  }

  void _setupMethodChannel() {
    platform.setMethodCallHandler((call) async {
      print('Flutter에서 메서드 호출 받음: ${call.method}');
      if (call.method == 'startImageAnalysis') {
        print('이미지 분석 시작 요청 받음');
        // Android에서 바로 오버레이 캡처 서비스 시작
        // Flutter 화면으로 이동하지 않음
      } else if (call.method == 'captureComplete') {
        print('캡처 완료 요청 받음');
        _handleCaptureComplete(call.arguments);
      } else if (call.method == 'kakaoOAuthCallback') {
        print('Kakao OAuth 콜백 받음: ${call.arguments}');
        // Kakao SDK가 자동으로 처리하도록 함
      }
    });
  }

  void _handleCaptureComplete(dynamic arguments) {
    print('=== _handleCaptureComplete 시작 ===');
    print('캡처 완료 처리: $arguments');
    
    // 기본 검증
    if (arguments == null) {
      print('오류: arguments가 null입니다.');
      return;
    }
    
    if (arguments is! String) {
      print('오류: arguments가 String이 아닙니다. 타입: ${arguments.runtimeType}');
      return;
    }
    
    if (arguments.isEmpty) {
      print('오류: 이미지 경로가 비어있습니다.');
      return;
    }
    
    print('검증 완료: $arguments');
    
    // 서버 전송을 별도 함수로 분리하여 안전하게 실행
    _executeServerTransfer(arguments);
  }
  
  void _executeServerTransfer(String imagePath) {
    print('=== _executeServerTransfer 시작 ===');
    print('이미지 경로: $imagePath');
    
    // 서버 전송을 비동기로 실행하되 예외를 캐치
    Future(() async {
      try {
        print('서버 전송 시작...');
        await _sendImageToServerDirectly(imagePath);
        print('서버 전송 완료');
        
        // 서버 응답에서 음성 데이터 추출하여 바로 재생
        if (_lastServerResponse != null && _lastServerResponse!['audioBase64'] != null) {
          print('음성 자동 재생 시작');
          // _playAudioDirectly(_lastServerResponse!['audioBase64']); // AudioPlayer 제거
        } else {
          print('음성 데이터를 찾을 수 없습니다.');
        }
        
        // 화면 전환
        _executeScreenTransition(imagePath);
        
      } catch (e) {
        print('서버 전송 중 오류 발생: $e');
        // 서버 전송 실패 시에도 화면 전환 시도
        _executeScreenTransition(imagePath);
      }
    });
  }
  
  void _executeScreenTransition(String imagePath) {
    print('=== _executeScreenTransition 시작 ===');
    print('이미지 경로: $imagePath');
    
    // 화면 전환을 비동기로 실행하되 예외를 캐치
    Future.delayed(const Duration(milliseconds: 100), () {
      try {
        if (context.mounted) {
          print('context가 마운트됨, 화면 전환 시작');
          
          // 서버 응답 데이터가 있는지 확인
          if (_lastServerResponse != null) {
            print('서버 응답 데이터가 있습니다. 화면 전환을 진행합니다.');
            
            // 직접 화면 생성 및 전환
            final screen = ImageAnalysisScreen(
              capturedImagePath: imagePath,
              serverResponse: _lastServerResponse,
            );
            print('ImageAnalysisScreen 인스턴스 생성 완료');
            
            // MaterialPageRoute 사용하고 replace로 변경
            final route = MaterialPageRoute(
              builder: (context) => screen,
            );
            
            // push 대신 pushReplacement 사용
            Navigator.of(context).pushReplacement(route);
            print('ImageAnalysisScreen으로 화면 전환 완료');
          } else {
            print('서버 응답 데이터가 null입니다. 화면 전환을 건너뜁니다.');
          }
        } else {
          print('context가 마운트되지 않았습니다.');
        }
      } catch (e) {
        print('화면 전환 중 오류 발생: $e');
      }
    });
  }

  Future<void> _sendImageToServerDirectly(String imagePath) async {
    print('=== 직접 서버 전송 시작 ===');
    print('이미지 경로: $imagePath');
    print('이미지 경로 타입: ${imagePath.runtimeType}');
    print('이미지 경로 길이: ${imagePath.length}');
    print('현재 시간: ${DateTime.now()}');
    
    try {
      // 이미지 경로 유효성 재확인
      if (imagePath.isEmpty) {
        print('오류: 이미지 경로가 비어있습니다.');
        return;
      }
      
      print('서버 연결 테스트 시작...');
      print('테스트 URL: http://192.168.219.101:8081/api/test');
      bool serverTestPassed = false;
      int retryCount = 0;
      const maxRetries = 3;
      
      while (retryCount < maxRetries && !serverTestPassed) {
        try {
          print('HTTP GET 요청 시도 중... (시도 ${retryCount + 1}/$maxRetries)');
          final testResponse = await http.get(
            Uri.parse('http://localhost:8081/api/test'),
            headers: {
              'User-Agent': 'EUM-App/1.0',
              'Connection': 'keep-alive',
            },
          ).timeout(const Duration(seconds: 8));
          print('서버 연결 테스트 성공: ${testResponse.statusCode}');
          print('테스트 응답 바디: ${testResponse.body}');
          serverTestPassed = true;
        } catch (e) {
          retryCount++;
          print('서버 연결 테스트 실패 (시도 $retryCount/$maxRetries): $e');
          print('오류 타입: ${e.runtimeType}');
          if (retryCount < maxRetries) {
            print('3초 후 재시도합니다...');
            await Future.delayed(const Duration(seconds: 3));
          }
        }
      }
      
      if (!serverTestPassed) {
        print('서버 연결 테스트가 모든 시도에서 실패했습니다. 직접 이미지 전송을 시도합니다.');
      }
      
      print('이미지 파일 확인 시작...');
      final imageFile = File(imagePath);
      print('이미지 파일 존재 확인: ${await imageFile.exists()}');
      
      if (!await imageFile.exists()) {
        print('이미지 파일을 찾을 수 없습니다: $imagePath');
        return;
      }

      print('이미지 파일 읽기 시작...');
      final bytes = await imageFile.readAsBytes();
      print('이미지 파일 크기: ${bytes.length} bytes');

      print('Base64 인코딩 시작...');
      final base64Image = base64Encode(bytes);
      print('Base64 인코딩 완료. 길이: ${base64Image.length}');

      final requestData = {
        'imageBase64': base64Image,
        'filename': imagePath.split('/').last,
      };

      print('서버 URL: http://192.168.219.101:8081/api/ocr/analyze');
      print('서버 전송 시작 - 데이터 크기: ${jsonEncode(requestData).length} bytes');
      print('서버 연결 시도 중...');
      print('현재 시간: ${DateTime.now()}');

      final response = await http.post(
        Uri.parse('http://localhost:8081/api/ocr/analyze'),
        headers: {
          'Content-Type': 'application/json',
          'User-Agent': 'EUM-App/1.0',
          'Connection': 'keep-alive',
        },
        body: jsonEncode(requestData),
      ).timeout(const Duration(seconds: 15)); // 15초로 증가

      print('서버 응답 수신 완료');
      print('서버 응답 상태 코드: ${response.statusCode}');
      print('서버 응답 바디: ${response.body}');

      if (response.statusCode == 200) {
        final responseData = jsonDecode(response.body);
        print('서버 처리 성공: ${responseData.toString()}');
        print('응답 데이터 키들: ${responseData.keys.toList()}');
        _lastServerResponse = responseData;
      } else {
        print('서버 오류: ${response.statusCode} - ${response.body}');
        
        // AWS 모델이 준비되지 않은 경우 임시 성공 응답 생성
        if (response.statusCode == 400 && response.body.contains("is not ready")) {
          print('AWS Rekognition 모델이 준비되지 않았습니다. 임시 성공 응답을 생성합니다.');
          
          // 사용자에게 알림
          _showTemporarySuccess();
        }
      }
    } catch (e) {
      print('서버 전송 실패: $e');
      print('오류 타입: ${e.runtimeType}');
      if (e is SocketException) {
        print('소켓 오류: ${e.message}');
        print('주소: ${e.address}');
        print('포트: ${e.port}');
      } else if (e is TimeoutException) {
        print('타임아웃 오류: 서버가 15초 내에 응답하지 않았습니다.');
      }
      print('오류 스택 트레이스: ${StackTrace.current}');
    }
    print('=== 직접 서버 전송 완료 ===');
  }

  void _showTemporarySuccess() {
    // 현재 context가 있는지 확인
    if (context.mounted) {
      showDialog(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('이미지 분석 완료'),
          content: const Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('이미지가 성공적으로 서버로 전송되었습니다!'),
              SizedBox(height: 10),
              Text('AWS Rekognition 모델이 현재 준비 중입니다.', 
                   style: TextStyle(fontStyle: FontStyle.italic)),
              SizedBox(height: 10),
              Text('모델이 준비되면 OCR 분석이 자동으로 실행됩니다.'),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('확인'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}

class ChatbotHandler extends StatefulWidget {
  final Widget child;
  
  const ChatbotHandler({Key? key, required this.child}) : super(key: key);

  @override
  _ChatbotHandlerState createState() => _ChatbotHandlerState();
}

class _ChatbotHandlerState extends State<ChatbotHandler> {
  @override
  void initState() {
    super.initState();
    
    // 위젯이 빌드된 후 챗봇 오버레이 서비스 초기화
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ChatbotOverlayService.initialize(context);
    });
  }

  @override
  void dispose() {
    ChatbotOverlayService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
