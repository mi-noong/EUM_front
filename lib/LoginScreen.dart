import 'package:flutter/material.dart';

class LoginScreen extends StatelessWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
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
                  // 아이디
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '아이디',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                    ),
                  ),
                  const SizedBox(height: 8),
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
                  // 비밀번호
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '비밀번호',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                    ),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    obscureText: true,
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
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: () {},
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Color(0xFF1EA1F7),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        elevation: 0,
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
                  // 아이디/비밀번호 찾기
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
                      Container(
                        height: 16,
                        width: 1,
                        color: Colors.grey[300],
                        margin: EdgeInsets.symmetric(horizontal: 8),
                      ),
                      TextButton(
                        onPressed: () {},
                        child: Text(
                          '비밀번호 찾기',
                          style: TextStyle(color: Colors.black87, fontSize: 20),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Divider(thickness: 1, color: Colors.grey[200]),
                  const SizedBox(height: 24),
                  // 소셜 로그인
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _SocialIconButton(
                        imagePath: 'assets/google.png',
                        onTap: () {},
                        label: '구글로 로그인하기', //대체텍스트
                      ),
                      const SizedBox(width: 32),
                      _SocialIconButton(
                        imagePath: 'assets/naver.png',
                        onTap: () {},
                        label: '네이버로 로그인하기', //대체텍스트
                      ),
                      const SizedBox(width: 32),
                      _SocialIconButton(
                        imagePath: 'assets/kakaotalk.png',
                        onTap: () {},
                        label: '카카오톡으로 로그인하기', //대체텍스트
                      ),
                    ],
                  ),
                  const SizedBox(height: 32),
                  // 회원가입
                  TextButton(
                    onPressed: () {},
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

// 소셜 아이콘 버튼 위젯
class _SocialIconButton extends StatelessWidget {
  final String imagePath;
  final VoidCallback onTap;
  final String label;

  const _SocialIconButton({
    required this.imagePath,
    required this.onTap,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(32),
      onTap: onTap,
      child: Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(color: Colors.grey[300]!),
          color: Colors.white,
        ),
        child: Center(
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
