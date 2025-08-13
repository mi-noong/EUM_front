import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'dart:typed_data';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'dart:io';
import 'utils/voice_recorder.dart';

class ChatOverlayWidget extends StatefulWidget {
  final VoidCallback? onClose;
  
  const ChatOverlayWidget({
    Key? key, 
    this.onClose,
  }) : super(key: key);

  @override
  _ChatOverlayWidgetState createState() => _ChatOverlayWidgetState();
}

class _ChatOverlayWidgetState extends State<ChatOverlayWidget> 
    with TickerProviderStateMixin {
  final List<ChatMessage> _messages = [];
  final AudioPlayer _audioPlayer = AudioPlayer();
  bool _isLoading = false;
  bool _isRecording = false;
  late AnimationController _fadeController;
  late AnimationController _slideController;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _slideController = AnimationController(
      duration: const Duration(milliseconds: 250),
      vsync: this,
    );
    _fadeController.forward();
    
    // 환영 메시지 추가
    _addWelcomeMessage();
    
    // VoiceRecorder 콜백 설정
    VoiceRecorder.onVoiceRecorded = _addVoiceMessage;
    VoiceRecorder.onError = _handleVoiceError;
    
    // 위젯이 표시되자마자 자동으로 음성 녹음 시작
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _startVoiceRecording();
    });
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _slideController.dispose();
    _audioPlayer.dispose();
    
    // 콜백 정리
    VoiceRecorder.onVoiceRecorded = null;
    VoiceRecorder.onError = null;
    
    super.dispose();
  }

  void _addWelcomeMessage() {
    setState(() {
      _messages.add(ChatMessage(
        text: "안녕하세요! 무엇을 도와드릴까요?",
        isUser: false,
        timestamp: DateTime.now(),
      ));
    });
  }

  // 음성 녹음 시작
  Future<void> _startVoiceRecording() async {
    if (_isRecording) return;
    
    setState(() {
      _isRecording = true;
      // 녹음 중임을 알리는 메시지 추가
      _messages.add(ChatMessage(
        text: "🎤 음성을 들어요... (10초간 녹음)",
        isUser: false,
        timestamp: DateTime.now(),
        isSystemMessage: true,
      ));
    });

    try {
      // 음성 녹음 시작 (10초 녹음)
      await VoiceRecorder.startRecording(10000);
    } catch (e) {
      print('음성 녹음 실패: $e');
      setState(() {
        _isRecording = false;
        // 시스템 메시지 제거
        if (_messages.isNotEmpty && _messages.last.isSystemMessage == true) {
          _messages.removeLast();
        }
      });
    }
  }

  // 음성 메시지 추가 및 백엔드 전송
  void _addVoiceMessage(String audioFilePath) {
    setState(() {
      // 시스템 메시지 제거 (녹음 중 메시지)
      if (_messages.isNotEmpty && _messages.last.isSystemMessage == true) {
        _messages.removeLast();
      }
      
      // 사용자 음성 메시지 추가
      _messages.add(ChatMessage(
        text: "🎤 음성 메시지",
        isUser: true,
        timestamp: DateTime.now(),
        isVoiceMessage: true,
      ));
      _isRecording = false;
      _isLoading = true;
    });

    // 백엔드로 음성 전송
    _sendVoiceToBackend(audioFilePath);
  }

  // 백엔드로 음성 전송
  Future<void> _sendVoiceToBackend(String audioFilePath) async {
    try {
      final file = File(audioFilePath);
      if (!await file.exists()) {
        print('음성 파일이 존재하지 않습니다: $audioFilePath');
        return;
      }

      // multipart/form-data로 파일 전송
      final request = http.MultipartRequest(
        'POST',
        Uri.parse('http://localhost:8081/api/chatbot/chat'),
      );
      
      request.headers['User-Agent'] = 'EUM-App/1.0';
      
      // 파일 추가
      final fileStream = http.ByteStream(file.openRead());
      final fileLength = await file.length();
      
      final multipartFile = http.MultipartFile(
        'voice',
        fileStream,
        fileLength,
        filename: file.path.split('/').last,
      );
      
      request.files.add(multipartFile);
      
      // 요청 전송
      final response = await request.send();
      final responseBody = await response.stream.bytesToString();
      
      if (response.statusCode == 200) {
        final responseData = jsonDecode(responseBody);
        
        // 봇 응답 메시지 추가
        setState(() {
          _messages.add(ChatMessage(
            text: responseData['text'] ?? '응답을 받았습니다.',
            isUser: false,
            timestamp: DateTime.now(),
            audioUrl: responseData['audioUrl'],
          ));
          _isLoading = false;
        });

        // 음성 응답이 있으면 자동 재생
        if (responseData['audioUrl'] != null) {
          _playAudioFromUrl(responseData['audioUrl']);
        }
        
      } else {
        print('서버 오류: ${response.statusCode}');
        _addErrorMessage();
      }
      
    } catch (e) {
      print('백엔드 전송 실패: $e');
      _addErrorMessage();
    }
  }

  // 에러 메시지 추가
  void _addErrorMessage() {
    setState(() {
      _messages.add(ChatMessage(
        text: "죄송합니다. 현재 서비스에 문제가 있습니다. 잠시 후 다시 시도해주세요.",
        isUser: false,
        timestamp: DateTime.now(),
      ));
      _isLoading = false;
    });
  }

  // 음성 에러 처리
  void _handleVoiceError(String error) {
    setState(() {
      _isRecording = false;
      _isLoading = false;
    });
    
    _addErrorMessage();
  }

  // 음성 URL에서 오디오 재생
  Future<void> _playAudioFromUrl(String audioUrl) async {
    try {
      await _audioPlayer.setUrl(audioUrl);
      await _audioPlayer.play();
    } catch (e) {
      print('오디오 재생 실패: $e');
    }
  }

  // 텍스트 메시지 전송 (테스트용)
  void _sendTextMessage(String text) {
    if (text.trim().isEmpty) return;

    // 사용자 메시지 추가
    setState(() {
      _messages.add(ChatMessage(
        text: text,
        isUser: true,
        timestamp: DateTime.now(),
      ));
      _isLoading = true;
    });

    // 임시 봇 응답 (실제로는 백엔드에서 받아야 함)
    Future.delayed(const Duration(seconds: 1), () {
      setState(() {
        _messages.add(ChatMessage(
          text: "네, 알겠습니다. 도움이 되었으면 좋겠습니다!",
          isUser: false,
          timestamp: DateTime.now(),
        ));
        _isLoading = false;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _fadeController,
      child: Material(
        color: Colors.transparent,
        child: Container(
          width: double.infinity,
          height: double.infinity,
          color: Colors.black54,
          child: Center(
            child: Container(
              width: MediaQuery.of(context).size.width * 0.9,
              height: MediaQuery.of(context).size.height * 0.7,
              decoration: BoxDecoration(
                color: const Color(0xFF2C2C2C),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Column(
                children: [
                  // 헤더
                  _buildHeader(),
                  // 메시지 리스트
                  Expanded(
                    child: _buildMessageList(),
                  ),
                  // 하단 입력 영역
                  _buildBottomControls(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 15),
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        borderRadius: BorderRadius.only(
          topLeft: Radius.circular(20),
          topRight: Radius.circular(20),
        ),
      ),
      child: Stack(
        children: [
          // 중앙에 EUM 제목
          const Center(
            child: Text(
              'EUM',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          // 오른쪽에 닫기 버튼
          Positioned(
            right: 0,
            child: IconButton(
              onPressed: widget.onClose,
              icon: const Icon(
                Icons.close,
                color: Colors.white70,
                size: 24,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageList() {
    return Container(
      padding: const EdgeInsets.all(16),
      child: ListView.builder(
        itemCount: _messages.length + (_isLoading ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == _messages.length && _isLoading) {
            return _buildLoadingMessage();
          }
          
          final message = _messages[index];
          return _buildMessageBubble(message);
        },
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: message.isUser 
            ? MainAxisAlignment.end 
            : MainAxisAlignment.start,
        children: [
          if (!message.isUser) ...[
            Container(
              width: 32,
              height: 32,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
              ),
              child: ClipOval(
                child: Image.asset(
                  'assets/chatbot.png',
                  width: 32,
                  height: 32,
                  fit: BoxFit.cover,
                ),
              ),
            ),
            const SizedBox(width: 8),
          ],
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: message.isUser 
                    ? const Color(0xFF4A90E2)
                    : const Color(0xFF3A3A3A),
                borderRadius: BorderRadius.circular(18),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    message.text,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                    ),
                  ),
                  if (message.audioUrl != null) ...[
                    const SizedBox(height: 8),
                    GestureDetector(
                      onTap: () => _playAudioFromUrl(message.audioUrl!),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12, 
                          vertical: 6
                        ),
                        decoration: BoxDecoration(
                          color: Colors.blue.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              Icons.volume_up,
                              color: Colors.blue,
                              size: 16,
                            ),
                            SizedBox(width: 4),
                            Text(
                              '음성 듣기',
                              style: TextStyle(
                                color: Colors.blue,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (message.isUser) ...[
            const SizedBox(width: 8),
            Container(
              width: 32,
              height: 32,
              decoration: const BoxDecoration(
                color: Colors.grey,
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.person,
                color: Colors.white,
                size: 20,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildLoadingMessage() {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
            ),
            child: ClipOval(
              child: Image.asset(
                'assets/chatbot.png',
                width: 32,
                height: 32,
                fit: BoxFit.cover,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFF3A3A3A),
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                ),
                SizedBox(width: 8),
                Text(
                  '입력하고 있습니다...',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomControls() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: const BoxDecoration(
        color: Color(0xFF1E1E1E),
        borderRadius: BorderRadius.only(
          bottomLeft: Radius.circular(20),
          bottomRight: Radius.circular(20),
        ),
      ),
      child: Column(
        children: [
          // 기존 입력 영역
          Row(
            children: [
              // 플러스 버튼
              Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(
                  color: Color(0xFF3A3A3A),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.add,
                  color: Colors.white70,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              // 메시지 입력 영역
              Expanded(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF3A3A3A),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: const Text(
                    '메시지',
                    style: TextStyle(
                      color: Colors.white54,
                      fontSize: 14,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              // 위로 화살표 버튼
              Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(
                  color: Color(0xFF3A3A3A),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.keyboard_arrow_up,
                  color: Colors.white70,
                  size: 20,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          // 하단 중앙의 큰 chatbot.png 아이콘
          Center(
            child: GestureDetector(
              onTap: _isRecording ? null : _startVoiceRecording,
              child: Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: _isRecording ? Colors.red.withOpacity(0.8) : Colors.transparent,
                  shape: BoxShape.circle,
                ),
                child: _isRecording 
                  ? const Icon(
                      Icons.stop,
                      color: Colors.white,
                      size: 40,
                    )
                  : Image.asset(
                      'assets/chatbot.png',
                      width: 80,
                      height: 80,
                      fit: BoxFit.contain,
                    ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ChatMessage {
  final String text;
  final bool isUser;
  final DateTime timestamp;
  final String? audioUrl;
  final bool isVoiceMessage;
  final bool isSystemMessage;

  ChatMessage({
    required this.text,
    required this.isUser,
    required this.timestamp,
    this.audioUrl,
    this.isVoiceMessage = false,
    this.isSystemMessage = false,
  });
}
