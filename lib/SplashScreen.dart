import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:io';
import 'LoginScreen.dart';

class SplashScreen extends StatefulWidget {
  const SplashScreen({Key? key}) : super(key: key);

  @override
  _SplashScreenState createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  double _opacity = 1.0;

  @override
  void initState() {
    super.initState();
    _startSplashScreen();
  }

  void _startSplashScreen() async {
    // 앱 초기화를 위한 기본 지연
    await Future.delayed(Duration(seconds: 1));
    
    // 네트워크 초기화 완료 대기 (더 긴 시간 할당)
    await _initializeNetwork();
    
    setState(() {
      _opacity = 0.0; // 투명도를 0으로 변경
    });
    await Future.delayed(Duration(seconds: 1)); // 사라지는 애니메이션을 기다림
    Navigator.of(context).pushReplacement(MaterialPageRoute(
      builder: (context) => LoginScreen(),
    ));
  }

  Future<void> _initializeNetwork() async {
    try {
      print('SplashScreen에서 네트워크 초기화 시작...');
      
      // 앱 초기화 완료를 위한 추가 대기
      await Future.delayed(Duration(milliseconds: 500));
      
      // 네트워크 연결 상태 확인
      await _checkNetworkConnectivity();
      
      // 서버 연결 테스트 (재시도 로직 포함)
      bool isConnected = false;
      int retryCount = 0;
      const maxRetries = 5; // 재시도 횟수 증가
      
      while (retryCount < maxRetries && !isConnected) {
        try {
          print('HTTP GET 요청 시도 중... (시도 ${retryCount + 1}/$maxRetries)');
          final response = await http.get(
            Uri.parse('http://192.168.219.101:8081/api/test'),
            headers: {
              'User-Agent': 'EUM-App/1.0',
              'Connection': 'keep-alive',
              'Accept': 'application/json',
            },
          ).timeout(const Duration(seconds: 8)); // 타임아웃 증가
          
          if (response.statusCode == 200) {
            print('SplashScreen에서 네트워크 초기화 성공: ${response.statusCode}');
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
        print('SplashScreen에서 네트워크 초기화 실패 (정상적인 상황)');
      }
    } catch (e) {
      print('SplashScreen에서 네트워크 초기화 중 오류: $e');
      // 초기화 실패는 정상적인 상황이므로 무시하고 계속 진행
    }
  }

  Future<void> _checkNetworkConnectivity() async {
    try {
      print('네트워크 연결 상태 확인 중...');
      
      // 인터넷 연결 테스트
      final result = await InternetAddress.lookup('google.com');
      if (result.isNotEmpty && result[0].rawAddress.isNotEmpty) {
        print('인터넷 연결 확인됨');
      } else {
        print('인터넷 연결 없음');
      }
    } catch (e) {
      print('네트워크 연결 상태 확인 실패: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: AnimatedOpacity(
        opacity: _opacity,
        duration: Duration(seconds: 1), // 1초 동안 서서히 사라짐
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Image.asset(
                'assets/SplashIcon.png', // 로고 이미지 경로
                width: 500, // 이미지 크기 조절
                height: 200,
              ),
              SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}
