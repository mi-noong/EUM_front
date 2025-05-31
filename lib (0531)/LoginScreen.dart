import 'package:flutter/material.dart';
import 'register_screen.dart';
import 'forgot_password_screen.dart';

// 로그인 화면을 구성하는 StatelessWidget
// StatelessWidget은 상태가 변하지 않는 정적인 위젯
class LoginScreen extends StatelessWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // 배경색을 흰색으로 설정
      backgroundColor: Colors.white,
      // SafeArea는 시스템 UI(상태바 등)와 겹치지 않도록 해주는 위젯
      body: SafeArea(
        child: Center(
          // SingleChildScrollView는 내용이 화면을 벗어날 경우 스크롤이 가능하도록 함
          child: SingleChildScrollView(
            child: Padding(
              // 좌우 24픽셀의 패딩 적용
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                // 세로 방향 중앙 정렬
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // EUM 로고
                  const SizedBox(height: 32),
                  const Text(
                    'EUM',
                    style: TextStyle(
                      fontSize: 80,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 2,
                      shadows: [
                        Shadow(
                          color: Colors.black26,
                          offset: Offset(2, 2),
                          blurRadius: 4,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 48),
                  // 아이디 입력 섹션
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '아이디',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                    ),
                  ),
                  const SizedBox(height: 8),
                  // 아이디 입력 필드
                  TextField(
                    decoration: InputDecoration(
                      hintText: 'Enter your ID',
                      filled: true,
                      fillColor: Color(0xFFE5E8EC),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                    ),
                  ),
                  const SizedBox(height: 24),
                  // 비밀번호 입력 섹션
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '비밀번호',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                    ),
                  ),
                  const SizedBox(height: 8),
                  // 비밀번호 입력 필드
                  TextField(
                    obscureText: true, // 비밀번호를 *로 표시
                    decoration: InputDecoration(
                      hintText: 'Enter your Password',
                      filled: true,
                      fillColor: Color(0xFFE5E8EC),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide.none,
                      ),
                      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                    ),
                  ),
                  const SizedBox(height: 32),
                  // 로그인 버튼
                  SizedBox(
                    width: double.infinity, // 버튼의 너비를 최대로 설정
                    height: 48,
                    child: ElevatedButton(
                      onPressed: () {}, // 버튼 클릭 시 실행될 함수
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Color(0xFF1EA1F7),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        elevation: 0, // 그림자 제거
                      ),
                      child: const Text(
                        '로그인',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  // 아이디/비밀번호 찾기 섹션
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      TextButton(
                        onPressed: () {},
                        child: Text(
                          '아이디 찾기',
                          style: TextStyle(color: Colors.black87, fontSize: 20),
                        ),
                      ),
                      // 구분선
                      Container(
                        height: 16,
                        width: 1,
                        color: Colors.grey[300],
                        margin: EdgeInsets.symmetric(horizontal: 8),
                      ),
                      TextButton(
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(builder: (context) => ForgotPasswordScreen()),
                          );
                        },
                        child: Text(
                          '비밀번호 찾기',
                          style: TextStyle(color: Colors.black87, fontSize: 20),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // 구분선
                  Divider(thickness: 1, color: Colors.grey[200]),
                  const SizedBox(height: 24),
                  // 소셜 로그인 섹션
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _SocialIconButton(
                        imagePath: 'assets/google.png',
                        onTap: () {},
                        label: '구글로 로그인하기',
                      ),
                      const SizedBox(width: 32),
                      _SocialIconButton(
                        imagePath: 'assets/naver.png',
                        onTap: () {},
                        label: '네이버로 로그인하기',
                      ),
                      const SizedBox(width: 32),
                      _SocialIconButton(
                        imagePath: 'assets/kakaotalk.png',
                        onTap: () {},
                        label: '카카오톡으로 로그인하기',
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  // 회원가입
                  Builder(
                    builder: (context) => TextButton(
                      onPressed: () {
                        Navigator.of(context).push(
                          MaterialPageRoute(builder: (context) => RegisterScreen()),
                        );
                      },
                      style: TextButton.styleFrom(
                        padding: EdgeInsets.symmetric(vertical: 8),
                        foregroundColor: Colors.black,
                        textStyle: TextStyle(
                          fontSize: 30,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 1,
                        ),
                      ),
                      child: const Text('회원가입'),
                    ),
                  ),
                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// 소셜 로그인 버튼을 위한 커스텀 위젯
class _SocialIconButton extends StatelessWidget {
  // 이미지 경로
  final String imagePath;
  // 버튼 클릭 시 실행될 함수
  final VoidCallback onTap;
  // 접근성을 위한 레이블
  final String label;

  const _SocialIconButton({
    required this.imagePath,
    required this.onTap,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      // 버튼의 모서리를 둥글게 설정
      borderRadius: BorderRadius.circular(32),
      onTap: onTap,
      child: Container(
        width: 56,
        height: 56,
        // 컨테이너의 스타일 설정
        decoration: BoxDecoration(
          shape: BoxShape.circle, // 원형 모양
          border: Border.all(color: Colors.grey[300]!),
          color: Colors.white,
        ),
        child: Center(
          // 소셜 로그인 아이콘 이미지
          child: Image.asset(
            imagePath,
            width: 32,
            height: 32,
            semanticLabel: label,
          ),
        ),
      ),
    );
  }
} 