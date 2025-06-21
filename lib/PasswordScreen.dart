import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'PasswordSuccessScreen.dart';

final baseUrl = 'http://10.0.2.2:8081';
final forgotPasswordUrl = '$baseUrl/api/members/find-password';

class PasswordScreen extends StatefulWidget {
  const PasswordScreen({Key? key}) : super(key: key);

  @override
  State<PasswordScreen> createState() => _PasswordScreenState();
}

class _PasswordScreenState extends State<PasswordScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _idController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();

  String? _errorMessage;
  Color _errorMessageColor = Colors.red;

  Future<void> _sendResetLink() async {
    setState(() {
      _errorMessage = null; // 에러 메시지 초기화
    });

    try {
      final response = await http.post(
        Uri.parse(forgotPasswordUrl),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'name': _nameController.text,
          'memberId': _idController.text,
          'email': _emailController.text,
        }),
      );

      if (response.statusCode == 200) {
        // API 응답이 성공(200)인 경우
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) => PasswordSuccessScreen(
              email: _emailController.text,
            ),
          ),
        );
      } else {
        // API 응답이 실패(200이 아닌 경우)인 경우
        // 서버에서 400, 404 등의 에러 코드를 반환하면 여기로 들어옴
        // 이는 입력한 정보(이름, 아이디, 이메일)가 회원가입 정보와 일치하지 않을 때 발생
        setState(() {
          _errorMessage = '일치하는 사용자가 없습니다.\n다시 시도해주시기 바랍니다.';
          _errorMessageColor = Colors.red;
        });
      }
    } catch (e) {
      // 서버 연결 실패나 네트워크 오류 등 예외가 발생한 경우
      setState(() {
        _errorMessage = '서버 연결에 실패했습니다.';
        _errorMessageColor = Colors.red;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
        title: null,
        centerTitle: true,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: 0),
                  Center(
                    child: Column(
                      children: [
                        Text(
                          '비밀번호 찾기',
                          style: TextStyle(
                            fontSize: 40,
                            fontWeight: FontWeight.w900,
                            letterSpacing: 1,
                            shadows: [
                              Shadow(
                                color: Colors.black26,
                                offset: Offset(2, 2),
                                blurRadius: 4,
                              ),
                            ],
                          ),
                        ),
                        SizedBox(height: 8),
                        Text(
                          '이메일로 새로운 비밀번호가 전송됩니다.',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.black54,
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  _buildInputField('사용자 이름', controller: _nameController, hint: 'Enter your Name'),
                  const SizedBox(height: 16),
                  _buildInputField('아이디', controller: _idController, hint: 'Enter your ID'),
                  const SizedBox(height: 16),
                  _buildInputField('이메일', controller: _emailController, hint: 'Enter your Email'),
                  if (_errorMessage != null) // 에러 메시지 표시
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
                      margin: const EdgeInsets.only(top: 24),
                      decoration: BoxDecoration(
                        color: Color(0xFFFFE5E5),
                        border: Border.all(color: _errorMessageColor, width: 2),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _errorMessage!,
                        style: TextStyle(
                          color: _errorMessageColor,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  const SizedBox(height: 32),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: _sendResetLink,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1EA1F7),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        elevation: 0,
                      ),
                      child: const Text(
                        '비밀번호 찾기',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
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

  Widget _buildInputField(String label, {required TextEditingController controller, required String hint, bool isPassword = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          obscureText: isPassword,
          decoration: InputDecoration(
            hintText: hint,
            filled: true,
            fillColor: const Color(0xFFE5E8EC),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide.none,
            ),
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
          ),
        ),
      ],
    );
  }
}
