import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';
import 'dart:io';
import 'dart:convert';
import 'package:permission_handler/permission_handler.dart';
import 'package:http/http.dart' as http;
import 'package:audioplayers/audioplayers.dart';
import 'utils/storage_permission_handler.dart';


class ImageAnalysisScreen extends StatefulWidget {
  final String? capturedImagePath;
  final Map<String, dynamic>? serverResponse;
  
  const ImageAnalysisScreen({
    Key? key, 
    this.capturedImagePath,
    this.serverResponse,
  }) : super(key: key);

  @override
  State<ImageAnalysisScreen> createState() {
    print('ImageAnalysisScreen createState 호출됨');
    print('- capturedImagePath: $capturedImagePath');
    print('- serverResponse: $serverResponse');
    return _ImageAnalysisScreenState();
  }
}

class _ImageAnalysisScreenState extends State<ImageAnalysisScreen> with WidgetsBindingObserver {
  static const MethodChannel _channel = MethodChannel('image_analysis_channel');
  String? _capturedImagePath;
  bool _isCapturing = false;
  bool _isSending = false;
  bool _isPlaying = false;
  bool _hasStoragePermission = false;
  Timer? _captureTimer;
  String? _serverResponse;
  String? _translatedText;
  String? _audioBase64;
  final AudioPlayer _audioPlayer = AudioPlayer();

    @override
  void initState() {
    super.initState();
    print('=== ImageAnalysisScreen initState 시작 ===');
    print('widget.capturedImagePath: ${widget.capturedImagePath}');
    print('widget.serverResponse: ${widget.serverResponse}');
    print('widget.serverResponse 타입: ${widget.serverResponse.runtimeType}');
    
    WidgetsBinding.instance.addObserver(this);
    
    // 오디오 플레이어 초기화
    _initializeAudioPlayer();
    
    // 생성자에서 받은 캡처 이미지 경로가 있으면 설정
    if (widget.capturedImagePath != null) {
      print('캡처된 이미지 경로 설정: ${widget.capturedImagePath}');
      _capturedImagePath = widget.capturedImagePath;
    } else {
      print('캡처된 이미지 경로가 null입니다.');
    }
    
    print('initState에서 _capturedImagePath 설정 완료: $_capturedImagePath');
    print('서버 응답 데이터: ${widget.serverResponse}');
    
    _setupMethodChannel();
    _checkInitialPermissions();
    
    // 서버 응답이 있으면 즉시 처리
    if (widget.serverResponse != null) {
      print('initState에서 서버 응답 처리 시작');
      print('서버 응답 데이터 타입: ${widget.serverResponse.runtimeType}');
      print('서버 응답 데이터 내용: ${widget.serverResponse}');
      WidgetsBinding.instance.addPostFrameCallback((_) {
        print('initState PostFrameCallback 실행 - 실제 서버 응답 처리');
        print('PostFrameCallback에서 서버 응답 데이터: ${widget.serverResponse}');
        _processServerResponse(widget.serverResponse!);
      });
    } else {
      print('initState에서 서버 응답이 null입니다.');
    }
    
    print('=== ImageAnalysisScreen initState 완료 ===');
  }

  Future<void> _initializeAudioPlayer() async {
    try {
      print('오디오 플레이어 초기화 시작');
      
      // 오디오 플레이어 설정
      await _audioPlayer.setReleaseMode(ReleaseMode.stop);
      await _audioPlayer.setVolume(1.0);
      
      // 이벤트 리스너 설정
      _audioPlayer.onPlayerStateChanged.listen((state) {
        print('오디오 플레이어 상태 변경: $state');
        if (mounted) {
          setState(() {
            _isPlaying = state == PlayerState.playing;
          });
        }
      });

      _audioPlayer.onPlayerComplete.listen((event) {
        print('음성 재생 완료');
        if (mounted) {
          setState(() {
            _isPlaying = false;
          });
        }
      });
      
      print('오디오 플레이어 초기화 완료');
    } catch (e) {
      print('오디오 플레이어 초기화 실패: $e');
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    print('=== didChangeDependencies 호출됨 ===');
    print('widget.capturedImagePath: ${widget.capturedImagePath}');
    print('widget.serverResponse: ${widget.serverResponse}');
    print('_capturedImagePath: $_capturedImagePath');

    // initState에서 설정된 캡처 이미지가 있으면 처리
    if (widget.capturedImagePath != null && _capturedImagePath != null) {
      print('didChangeDependencies에서 캡처 결과 처리');
      
      // 서버 응답이 있으면 실제 데이터 사용, 없으면 시뮬레이션
      if (widget.serverResponse != null) {
        print('실제 서버 응답 데이터 사용');
        print('서버 응답 데이터 타입: ${widget.serverResponse.runtimeType}');
        print('서버 응답 데이터 내용: ${widget.serverResponse}');
        WidgetsBinding.instance.addPostFrameCallback((_) {
          print('PostFrameCallback 실행 - 실제 서버 응답 처리');
          print('PostFrameCallback에서 서버 응답 데이터: ${widget.serverResponse}');
          _processServerResponse(widget.serverResponse!);
        });
      } else {
        print('서버 응답 시뮬레이션 시작');
        WidgetsBinding.instance.addPostFrameCallback((_) {
          print('PostFrameCallback 실행 - 서버 응답 시뮬레이션');
          _simulateServerResponse();
        });
      }

      _showCaptureResult();
    } else {
      print('캡처 이미지 경로가 null이어서 처리하지 않습니다.');
      print('widget.capturedImagePath: ${widget.capturedImagePath}');
      print('_capturedImagePath: $_capturedImagePath');
    }
    print('=== didChangeDependencies 완료 ===');
  }

  Future<void> _checkInitialPermissions() async {
    // 앱 시작 시 권한 상태 확인
    bool hasPermission = await StoragePermissionHandler.checkStoragePermission();
    setState(() {
      _hasStoragePermission = hasPermission;
    });
    if (!hasPermission) {
      // 권한이 없으면 안내 다이얼로그 표시
      WidgetsBinding.instance.addPostFrameCallback((_) {
        StoragePermissionHandler.showPermissionExplanation(context);
      });
    }
  }

  Future<void> _updatePermissionStatus() async {
    bool hasPermission = await StoragePermissionHandler.checkStoragePermission();
    setState(() {
      _hasStoragePermission = hasPermission;
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _captureTimer?.cancel();
    _audioPlayer.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      // 앱이 다시 활성화될 때 권한 상태 확인
      _updatePermissionStatus();
    }
  }

  void _setupMethodChannel() {
    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'captureComplete':
          setState(() {
            _capturedImagePath = call.arguments as String?;
            _isCapturing = false;
          });
          _showCaptureResult();
          // 3초 후 자동으로 캡처 상태 초기화
          _captureTimer?.cancel();
          _captureTimer = Timer(const Duration(seconds: 3), () {
            if (mounted) {
              setState(() {
                _isCapturing = false;
              });
            }
          });
          break;
        default:
          print('Unknown method: ${call.method}');
      }
    });
  }

  Future<void> _startImageAnalysis() async {
    try {
      // 저장소 권한 확인 및 요청
      bool hasPermission = await StoragePermissionHandler.requestStoragePermission(context);
      if (!hasPermission) {
        return; // 권한이 거부된 경우 메서드 종료
      }

      // 권한 상태 업데이트
      _updatePermissionStatus();

      setState(() {
        _isCapturing = true;
        _capturedImagePath = null;
        _serverResponse = null;
      });

      // 플로팅 위젯과 동일한 방식으로 오버레이 캡처 서비스 시작
      await _channel.invokeMethod('startOverlayCapture');
      print('오버레이 캡처 서비스 시작 요청 완료');
    } catch (e) {
      print('오버레이 캡처 서비스 시작 실패: $e');
      setState(() {
        _isCapturing = false;
      });
      _showError('화면 캡처를 시작할 수 없습니다.');
    }
  }

  Future<void> _sendImageToServer() async {
    print('=== 서버 전송 시작 ===');

    if (_capturedImagePath == null) {
      print('캡처된 이미지 경로가 null입니다.');
      _showError('캡처된 이미지가 없습니다.');
      return;
    }

    print('캡처된 이미지 경로: $_capturedImagePath');

    setState(() {
      _isSending = true;
      _serverResponse = null;
      _translatedText = null;
      _audioBase64 = null;
    });

    try {
      final imageFile = File(_capturedImagePath!);
      print('이미지 파일 존재 확인: ${await imageFile.exists()}');

      if (!await imageFile.exists()) {
        throw Exception('이미지 파일을 찾을 수 없습니다.');
      }

      print('이미지 파일 경로: $_capturedImagePath');

      final bytes = await imageFile.readAsBytes();
      print('이미지 파일 크기: ${bytes.length} bytes');

      // 이미지 크기가 너무 크면 압축
      String base64Image;
      if (bytes.length > 1024 * 1024) { // 1MB 이상인 경우
        print('이미지 크기가 큽니다. 압축을 시도합니다.');
        // 이미지 압축 로직을 추가할 수 있지만, 현재는 그대로 전송
        base64Image = base64Encode(bytes);
      } else {
        base64Image = base64Encode(bytes);
      }

      final requestData = {
        'imageBase64': base64Image,
        'filename': _capturedImagePath!.split('/').last,
      };

      print('서버 URL: http://192.168.219.101:8081/api/ocr/analyze');
      print('서버 전송 시작 - 데이터 크기: ${jsonEncode(requestData).length} bytes');

      final response = await http.post(
        Uri.parse('http://192.168.219.101:8081/api/ocr/analyze'),
        headers: {
          'Content-Type': 'application/json',
        },
        body: jsonEncode(requestData),
      ).timeout(const Duration(seconds: 30)); // 30초 타임아웃 추가

      print('서버 응답 상태 코드: ${response.statusCode}');
      print('서버 응답 바디: ${response.body}');

      if (response.statusCode == 200) {
        final responseData = jsonDecode(response.body);

        print('서버 응답 데이터 파싱 시작');
        print('translatedText: ${responseData['translatedText']}');
        print('audioBase64 길이: ${responseData['audioBase64']?.length ?? 0}');
        
        setState(() {
          _serverResponse = '서버 응답: ${responseData.toString()}';
          _translatedText = responseData['translatedText'];
          _audioBase64 = responseData['audioBase64'];
        });
        
        print('setState 완료 후 _audioBase64 길이: ${_audioBase64?.length ?? 0}');

        if (responseData['success'] == true) {
          print('서버 처리 성공');
          print('음성 데이터 확인: ${_audioBase64 != null ? '있음' : '없음'}');
          print('음성 데이터 길이: ${_audioBase64?.length ?? 0}');
          _showSuccess('이미지 분석이 완료되었습니다.');

          // 음성이 있으면 자동 재생
          if (_audioBase64 != null && _audioBase64!.isNotEmpty) {
            print('음성 자동 재생 시작');
            _playAudio();
          } else {
            print('음성 데이터가 없어서 재생하지 않습니다.');
          }
        } else {
          print('서버 처리 실패: ${responseData['errorMessage']}');
          _showError('서버 처리 중 오류가 발생했습니다: ${responseData['errorMessage']}');
        }
      } else {
        print('서버 오류: ${response.statusCode} - ${response.body}');
        throw Exception('서버 오류: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('서버 전송 실패: $e');
      _showError('서버 전송에 실패했습니다: $e');
    } finally {
      setState(() {
        _isSending = false;
      });
      print('=== 서버 전송 완료 ===');
    }
  }

  void _simulateServerResponse() {
    print('=== 서버 응답 시뮬레이션 시작 ===');
    
    // 실제 서버 응답과 동일한 형태의 데이터 생성
    final responseData = {
      'detectedLabels': [
        {
          'name': 'kkanpunggi',
          'confidence': 92.511,
          'geometry': null
        }
      ],
      'translatedText': '깐풍기',
      'audioBase64': 'SUQzBAAAAAAAIlRTU0UAAAAOAAADTGF2ZjYyLjAuMTAwAAAAAAAAAAAAAAD/82TEAA6AEghWeEYAg33m4kJQW9pMHwOXB8voUc5Qwc6nUbuUo1fkLvz/P8uQyifgg+p3Poxr1HOoEHXIl/1LD//yFX5P6RZVo5w5zJdoUYhAAOCdIEnxA4vIGoZD5Rb9QY0ww1wuUKOyYRAgYAhe62A3ipRc4QhEQOUEShyfsJhko14hGz5uIKY9dRx8T5D/82TESRjIJiQU0kQANSjnCBs0hj4gQkWdPmi5SiD8QF1h9KnwwXYqBmZ0QIAjhDRSxizZnGhiin7WMiqUWrFifll7JkETvpUFNCoyP+e73eV/R99719Xj8uY74U197OZ88LUcXlYgSMNwXuIlUE70ooedN4gNQOs4dAglUFigBQyLjcMYJXdvDfnXUOZcLd//82TEaCcMOiAS2YbcpaIhobh3g1QTXZX3LPY9qhEWw5E9JEzQ6tiG8MwmToFv1yPrERmQc48NJchdMEjC445vA9UfYL0tUsYdIWFBARp5Ti0kzQQmkFpNja116ttkQZCCAudYU7m4F+djbEIu0tUl0oqPtYsWk58lKSEasdM/24RtTmX8yNd278Z1/6c//77/82TETh2SkiwA0YY9exF0LcfsU96AKZOs1tb+7KPfbFEZazw/vq5RmDa1nV30bzW5vV3p8NVBNm5I40idNu23CVWcs76xK0F6bXH205EgTGoakG0IhdzVMQEYTCYiHMBsiTMiYTgJgheUCL1UWucgkK1RAsWPjhQaRB8ocQj9XElKv//Un9W1jTkbgbDcBhX/82TEWhRwJlD/WBgAIxIIxGGx4zVWrcSqXtltahpzJiyBCS56z4c',
      'success': true,
      'errorMessage': null
    };

    print('서버 응답 데이터 파싱 시작');
    print('translatedText: ${responseData['translatedText']}');
    print('audioBase64 길이: ${(responseData['audioBase64'] as String?)?.length ?? 0}');
    
    setState(() {
      _serverResponse = '서버 응답: ${responseData.toString()}';
      _translatedText = responseData['translatedText'] as String?;
      _audioBase64 = responseData['audioBase64'] as String?;
    });
    
    print('setState 완료 후 _audioBase64 길이: ${_audioBase64?.length ?? 0}');

    if (responseData['success'] == true) {
      print('서버 처리 성공');
      print('음성 데이터 확인: ${_audioBase64 != null ? '있음' : '없음'}');
      print('음성 데이터 길이: ${_audioBase64?.length ?? 0}');
      _showSuccess('이미지 분석이 완료되었습니다.');

      // 음성이 있으면 자동 재생
      if (_audioBase64 != null && _audioBase64!.isNotEmpty) {
        print('음성 자동 재생 시작');
        _playAudio();
      } else {
        print('음성 데이터가 없어서 재생하지 않습니다.');
      }
    } else {
      print('서버 처리 실패: ${responseData['errorMessage']}');
      _showError('서버 처리 중 오류가 발생했습니다: ${responseData['errorMessage']}');
    }
    
    print('=== 서버 응답 시뮬레이션 완료 ===');
  }

  void _processServerResponse(Map<String, dynamic> responseData) {
    print('=== 실제 서버 응답 처리 시작 ===');
    print('responseData 타입: ${responseData.runtimeType}');
    print('responseData 키들: ${responseData.keys.toList()}');
    
    print('서버 응답 데이터 파싱 시작');
    print('translatedText: ${responseData['translatedText']}');
    print('audioBase64 길이: ${(responseData['audioBase64'] as String?)?.length ?? 0}');
    
    setState(() {
      _serverResponse = '서버 응답: ${responseData.toString()}';
      _translatedText = responseData['translatedText'] as String?;
      _audioBase64 = responseData['audioBase64'] as String?;
    });
    
    print('setState 완료 후 _audioBase64 길이: ${_audioBase64?.length ?? 0}');
    print('setState 완료 후 _audioBase64 null 여부: ${_audioBase64 == null}');
    print('setState 완료 후 _audioBase64 빈 문자열 여부: ${_audioBase64?.isEmpty ?? true}');

    // success 필드가 없어도 translatedText와 audioBase64가 있으면 성공으로 처리
    bool isSuccess = responseData['success'] == true || 
                    (responseData['translatedText'] != null && responseData['audioBase64'] != null);
    
    print('isSuccess 계산 결과: $isSuccess');
    print('responseData[success]: ${responseData['success']}');
    print('responseData[translatedText] null 여부: ${responseData['translatedText'] == null}');
    print('responseData[audioBase64] null 여부: ${responseData['audioBase64'] == null}');
    
    if (isSuccess) {
      print('서버 처리 성공');
      print('음성 데이터 확인: ${_audioBase64 != null ? '있음' : '없음'}');
      print('음성 데이터 길이: ${_audioBase64?.length ?? 0}');
      _showSuccess('이미지 분석이 완료되었습니다.');

      // 음성이 있으면 자동 재생
      if (_audioBase64 != null && _audioBase64!.isNotEmpty) {
        print('음성 자동 재생 시작 - _playAudio() 호출');
        _playAudio();
      } else {
        print('음성 데이터가 없어서 재생하지 않습니다.');
        print('_audioBase64 null 여부: ${_audioBase64 == null}');
        print('_audioBase64 빈 문자열 여부: ${_audioBase64?.isEmpty ?? true}');
      }
    } else {
      print('서버 처리 실패: ${responseData['errorMessage']}');
      _showError('서버 처리 중 오류가 발생했습니다: ${responseData['errorMessage']}');
    }
    
    print('=== 실제 서버 응답 처리 완료 ===');
  }

  Future<void> _playAudio() async {
    print('=== _playAudio() 메서드 호출됨 ===');
    
    if (_audioBase64 == null || _audioBase64!.isEmpty) {
      print('음성 데이터가 없습니다.');
      print('_audioBase64 null 여부: ${_audioBase64 == null}');
      print('_audioBase64 빈 문자열 여부: ${_audioBase64?.isEmpty ?? true}');
      _showError('재생할 음성이 없습니다.');
      return;
    }

    try {
      print('=== 음성 재생 시작 ===');
      print('음성 데이터 크기: ${_audioBase64!.length}');
      print('음성 데이터 앞부분: ${_audioBase64!.substring(0, 50)}...');
      
      setState(() {
        _isPlaying = true;
      });

      // 기존 재생 중인 오디오가 있으면 정지
      await _audioPlayer.stop();
      
      // Base64를 바이트로 디코딩
      final audioBytes = base64Decode(_audioBase64!);
      print('음성 바이트 크기: ${audioBytes.length}');

      // 볼륨 설정 (안드로이드에서 더 안정적인 재생을 위해)
      await _audioPlayer.setVolume(1.0);
      
      // 여러 방법으로 재생 시도
      bool playbackStarted = false;
      
      // 방법 1: BytesSource로 직접 재생
      try {
        print('BytesSource로 음성 재생 시도...');
        await _audioPlayer.play(BytesSource(audioBytes));
        print('BytesSource로 음성 재생 시작 성공');
        playbackStarted = true;
      } catch (e) {
        print('BytesSource 실패: $e');
      }
      
      // 방법 2: 임시 파일로 저장 후 재생 (MP3)
      if (!playbackStarted) {
        try {
          print('임시 MP3 파일로 재생 시도...');
          final tempDir = await Directory.systemTemp.createTemp('audio');
          final tempFile = File('${tempDir.path}/audio.mp3');
          await tempFile.writeAsBytes(audioBytes);
          print('MP3 파일 저장 완료: ${tempFile.path}');
          print('MP3 파일 존재 확인: ${await tempFile.exists()}');
          print('MP3 파일 크기: ${await tempFile.length()} bytes');
          
          await _audioPlayer.play(DeviceFileSource(tempFile.path));
          print('MP3 파일로 음성 재생 시작 성공');
          playbackStarted = true;
        } catch (e) {
          print('MP3 파일 재생 실패: $e');
        }
      }
      
      // 방법 3: 임시 파일로 저장 후 재생 (WAV)
      if (!playbackStarted) {
        try {
          print('임시 WAV 파일로 재생 시도...');
          final tempDir = await Directory.systemTemp.createTemp('audio');
          final tempFile = File('${tempDir.path}/audio.wav');
          await tempFile.writeAsBytes(audioBytes);
          print('WAV 파일 저장 완료: ${tempFile.path}');
          print('WAV 파일 존재 확인: ${await tempFile.exists()}');
          print('WAV 파일 크기: ${await tempFile.length()} bytes');
          
          await _audioPlayer.play(DeviceFileSource(tempFile.path));
          print('WAV 파일로 음성 재생 시작 성공');
          playbackStarted = true;
        } catch (e) {
          print('WAV 파일 재생 실패: $e');
        }
      }
      
      if (!playbackStarted) {
        throw Exception('모든 재생 방법이 실패했습니다.');
      }
      
      print('음성 재생 명령 전송 완료');

      _showSuccess('음성 안내를 자동으로 재생합니다.');
      print('=== 음성 재생 설정 완료 ===');
    } catch (e) {
      print('음성 재생 실패: $e');
      _showError('음성 재생에 실패했습니다: $e');
      if (mounted) {
        setState(() {
          _isPlaying = false;
        });
      }
    }
  }

  void _showCaptureResult() {
    print('=== _showCaptureResult 호출됨 ===');
    print('_capturedImagePath: $_capturedImagePath');
    
    if (_capturedImagePath != null && mounted && Navigator.of(context).canPop()) {
      print('캡처 결과 표시 시작: $_capturedImagePath');
      
      // 사용자에게 캡처 완료 알림
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text('캡처 완료'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('이미지가 성공적으로 캡처되었습니다.'),
              const SizedBox(height: 10),
              Text('저장 경로: $_capturedImagePath'),
              const SizedBox(height: 10),
              const Text('서버로 자동 전송 중...', style: TextStyle(fontStyle: FontStyle.italic)),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                if (mounted && Navigator.of(context).canPop()) {
                  Navigator.of(context).pop();
                  // 다이얼로그를 닫을 때 캡처 상태 초기화
                  setState(() {
                    _isCapturing = false;
                  });
                }
              },
              child: const Text('확인'),
            ),
          ],
        ),
      );
    } else {
      print('캡처된 이미지 경로가 null이거나 위젯이 마운트되지 않았습니다.');
    }
    print('=== _showCaptureResult 완료 ===');
  }

  void _showError(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: Colors.red,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  void _showSuccess(String message) {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: Colors.green,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    print('=== ImageAnalysisScreen build() 호출됨 ===');
    print('현재 _capturedImagePath: $_capturedImagePath');
    print('현재 _translatedText: $_translatedText');
    print('현재 _audioBase64 길이: ${_audioBase64?.length ?? 0}');
    print('현재 _isPlaying: $_isPlaying');
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('이미지 분석'),
        backgroundColor: Colors.blue,
        foregroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: const Text(
                            '드래그 선택 화면 캡처',
                            style: TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: _hasStoragePermission ? Colors.green : Colors.red,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(
                                _hasStoragePermission ? Icons.check_circle : Icons.error,
                                color: Colors.white,
                                size: 14,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                _hasStoragePermission ? '권한 있음' : '권한 없음',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    if (!_hasStoragePermission) ...[
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: Colors.orange.shade100,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.warning, color: Colors.orange, size: 16),
                            const SizedBox(width: 8),
                            Expanded(
                              child: const Text(
                                '저장소 권한이 필요합니다. 권한을 허용해주세요.',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.orange,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ),
                            TextButton(
                              onPressed: () async {
                                bool granted = await StoragePermissionHandler.requestStoragePermission(context);
                                await _updatePermissionStatus();
                                if (granted) {
                                  _showSuccess('저장소 권한이 허용되었습니다.');
                                }
                              },
                              child: const Text(
                                '권한 요청',
                                style: TextStyle(fontSize: 12),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isCapturing ? null : _startImageAnalysis,
                    icon: _isCapturing
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.camera_alt),
                    label: Text(_isCapturing ? '캡처 중...' : '드래그 선택으로 캡처'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blue,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                IconButton(
                  onPressed: () => StoragePermissionHandler.showPermissionExplanation(context),
                  icon: const Icon(Icons.help_outline),
                  tooltip: '권한 안내',
                  style: IconButton.styleFrom(
                    backgroundColor: Colors.grey.shade200,
                    padding: const EdgeInsets.all(12),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            if (_capturedImagePath != null) ...[
              const Text(
                '최근 캡처 결과:',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.grey),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  _capturedImagePath!,
                  style: const TextStyle(fontSize: 12),
                ),
              ),
              const SizedBox(height: 16),
              if (_isSending) ...[
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    const SizedBox(width: 10),
                    const Text('서버로 전송 중...'),
                  ],
                ),
              ] else if (_translatedText != null) ...[
                const Text(
                  '분석 결과:',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.blue),
                    borderRadius: BorderRadius.circular(8),
                    color: Colors.blue.shade50,
                  ),
                  child: Text(
                    _translatedText!,
                    style: const TextStyle(fontSize: 16),
                  ),
                ),
                const SizedBox(height: 16),
                if (_audioBase64 != null && _audioBase64!.isNotEmpty) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.orange),
                      borderRadius: BorderRadius.circular(8),
                      color: Colors.orange.shade50,
                    ),
                    child: Row(
                      children: [
                        Icon(
                          _isPlaying ? Icons.volume_up : Icons.volume_off,
                          color: Colors.orange,
                          size: 20,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            _isPlaying ? '음성 안내 재생 중...' : '음성 안내 자동 재생 완료',
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ],
            const Spacer(),
            const Card(
              color: Colors.orange,
              child: Padding(
                padding: EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '⚠️ 주의사항',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '• 이 기능은 다른 앱의 화면을 캡처할 수 있습니다\n'
                      '• 개인정보나 민감한 정보가 포함된 화면은 캡처하지 마세요\n'
                      '• 캡처된 이미지는 앱 내부 저장소에 저장됩니다\n'
                      '• 이미지는 base64로 인코딩되어 서버로 전송됩니다',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.white,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
} 
