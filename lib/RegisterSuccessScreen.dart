import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'LoginScreen.dart';

final baseUrl = 'http://10.0.2.2:8081';
final registerUrl = '$baseUrl/api/members/signup';

class RegisterSuccessScreen extends StatefulWidget {
  final String name;
  final String email;
  final String id;       // 아이디(=username)
  final String password;

  const RegisterSuccessScreen({
    Key? key,
    required this.name,
    required this.email,
    required this.id,
    required this.password,
  }) : super(key: key);

  @override
  State<RegisterSuccessScreen> createState() => _RegisterSuccessScreenState();
}

class _RegisterSuccessScreenState extends State<RegisterSuccessScreen> {
  @override
  void initState() {
    super.initState();
    _sendRegisterInfo();
  }

  Future<void> _sendRegisterInfo() async {
    try {
      final response = await http.post(
        Uri.parse(registerUrl),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'username': widget.id,       // 여기를 'username'으로 수정!
          'password': widget.password,
          'name': widget.name,
          'email': widget.email,
        }),
      );

      if (response.statusCode == 200) {
        print('회원가입 성공');
      } else {
        print('회원가입 실패: ${response.statusCode} - ${response.body}');
      }
    } catch (e) {
      print('회원가입 정보 전송 실패: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.check_circle,
                  size: 100,
                  color: const Color(0xFF1EA1F7),
                ),
                const SizedBox(height: 40),
                const Text(
                  '회원가입이\n완료되었습니다.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w900,
                    color: Colors.black,
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
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: () {
                      Navigator.pushAndRemoveUntil(
                        context,
                        MaterialPageRoute(builder: (context) => const LoginScreen()),
                            (route) => false,
                      );
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF1EA1F7),
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
              ],
            ),
          ),
        ),
      ),
    );
  }
}
