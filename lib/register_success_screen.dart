import 'package:flutter/material.dart';

/// 회원가입 완료 화면
/// 사용자가 회원가입을 성공적으로 완료했을 때 표시되는 화면입니다.
class RegisterSuccessScreen extends StatelessWidget {
  const RegisterSuccessScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // 상단 앱바 설정
      appBar: AppBar(
        title: const Text('회원가입 완료'),
        backgroundColor: Colors.white,  // 흰색 배경
        foregroundColor: Colors.black,  // 검은색 텍스트
        elevation: 0,  // 그림자 제거
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 성공 아이콘 표시
            const Icon(Icons.check_circle, color: Colors.blue, size: 80),
            const SizedBox(height: 24),
            // 완료 메시지 표시
            const Text(
              '회원가입이 완료되었습니다.',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 32),
            // 로그인 버튼
            ElevatedButton(
              onPressed: () {
                // 로그인 화면으로 이동
                // ModalRoute.withName('/login')을 사용하여 로그인 화면까지의 모든 화면을 스택에서 제거
                Navigator.popUntil(context, ModalRoute.withName('/login'));
              },
              child: const Text('로그인'),
              // 버튼 스타일 설정
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,  // 파란색 배경
                minimumSize: const Size(200, 48),  // 최소 크기 설정
                textStyle: const TextStyle(fontSize: 20),  // 텍스트 스타일
              ),
            ),
          ],
        ),
      ),
    );
  }
} 