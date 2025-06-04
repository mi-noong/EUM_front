import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'IdcompleteScreen.dart';

// 아이디 찾기 페이지
class IdforgotScreen extends StatefulWidget {
  const IdforgotScreen({Key? key}) : super(key: key);

  @override
  State<IdforgotScreen> createState() => _IdforgotScreenState();
}

class _IdforgotScreenState extends State<IdforgotScreen> {
  bool _showAuthField = false;
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _authController = TextEditingController();

  String? _authMessage;
  Color _authMessageColor = Colors.blue;
  bool _isSending = false;
  bool _isVerifying = false;

  Future<void> _sendAuthCode() async {
    setState(() {
      _isSending = true;
      _authMessage = null;
    });
    final baseUrl = 'http://10.0.2.2:8081';
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/api/members/verify-email'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'name': _nameController.text,
          'email': _emailController.text,
        }),
      );
      print('응답 상태: ${response.statusCode}');
      print('응답 바디: ${response.body}');
      if (response.statusCode == 200) {
        setState(() {
          _showAuthField = true;
          _authMessage = '인증번호가 발송되었습니다.';
          _authMessageColor = Colors.blue;
        });
      } else {
        final errorData = jsonDecode(response.body);
        setState(() {
          _authMessage = errorData['message'] ?? '일치하는 사용자가 없습니다.\n다시 시도해주시기 바랍니다.';
          _authMessageColor = Colors.red;
          _showAuthField = false;
        });
      }
    } catch (e) {
      setState(() {
        _authMessage = '서버 연결에 실패했습니다.';
        _authMessageColor = Colors.red;
        _showAuthField = false;
      });
    } finally {
      setState(() {
        _isSending = false;
      });
    }
  }

  Future<void> _verifyAuthCode() async {
    setState(() {
      _isVerifying = true;
      _authMessage = null;
    });
    final baseUrl = 'http://10.0.2.2:8081';
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/api/members/verify-code'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': _emailController.text,
          'code': _authController.text,
        }),
      );
      print('인증 응답: ${response.statusCode}');
      print('응답 바디: ${response.body}');
      if (response.statusCode == 200) {
        setState(() {
          _authMessage = '인증번호가 일치합니다.';
          _authMessageColor = Colors.blue;
        });
      } else {
        setState(() {
          _authMessage = '인증번호가 일치하지 않습니다.';
          _authMessageColor = Colors.red;
        });
      }
    } catch (e) {
      setState(() {
        _authMessage = '서버 연결에 실패했습니다.';
        _authMessageColor = Colors.red;
      });
    } finally {
      setState(() {
        _isVerifying = false;
      });
    }
  }

  Future<void> _findId() async {
    final baseUrl = 'http://10.0.2.2:8081';
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/api/members/find-id'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': _emailController.text,
          'code': _authController.text,
        }),
      );
      print('아이디 찾기 응답: ${response.statusCode}');
      print('응답 바디: ${response.body}');
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final userId = data['memberId'] ?? '';
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (context) => IdcompleteScreen(
              name: _nameController.text,
              userId: userId,
            ),
          ),
        );
      } else {
        setState(() {
          _authMessage = '일치하는 사용자가 없습니다.\n다시 시도해주시기 바랍니다.';
          _authMessageColor = Colors.red;
        });
      }
    } catch (e) {
      setState(() {
        _authMessage = '서버 연결에 실패했습니다.';
        _authMessageColor = Colors.red;
      });
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    _authController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 24),
                const Center(
                  child: Text(
                    '아이디 찾기',
                    style: TextStyle(
                      fontSize: 60,
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
                ),
                const SizedBox(height: 80),
                const Text(
                  '사용자 이름',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _nameController,
                  decoration: InputDecoration(
                    hintText: 'Enter your Name',
                    filled: true,
                    fillColor: Color(0xFFE5E8EC),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide.none,
                    ),
                    contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                  ),
                ),
                const SizedBox(height: 24),
                const Text(
                  '이메일',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 8),
                Stack(
                  alignment: Alignment.centerRight,
                  children: [
                    TextField(
                      controller: _emailController,
                      decoration: InputDecoration(
                        hintText: 'Enter your Email',
                        filled: true,
                        fillColor: Color(0xFFE5E8EC),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(16),
                          borderSide: BorderSide.none,
                        ),
                        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                      ),
                    ),
                    Padding(
                      padding: EdgeInsets.only(right: 16),
                      child: TextButton(
                        onPressed: _isSending ? null : _sendAuthCode,
                        child: _isSending
                            ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                            : Text(
                          '인증번호 발송',
                          style: TextStyle(
                            color: Color(0xFF1EA1F7),
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                if (_showAuthField) ...[
                  const SizedBox(height: 24),
                  const Text(
                    '인증번호 입력',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                  ),
                  const SizedBox(height: 8),
                  Stack(
                    alignment: Alignment.centerRight,
                    children: [
                      TextField(
                        controller: _authController,
                        decoration: InputDecoration(
                          hintText: 'Enter your Authentication number',
                          filled: true,
                          fillColor: Color(0xFFE5E8EC),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(16),
                            borderSide: BorderSide.none,
                          ),
                          contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                        ),
                      ),
                      Padding(
                        padding: EdgeInsets.only(right: 16),
                        child: TextButton(
                          onPressed: _isVerifying ? null : _verifyAuthCode,
                          child: _isVerifying
                              ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                              : Text(
                            '확인',
                            style: TextStyle(
                              color: Color(0xFF1EA1F7),
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
                if (_authMessage != null)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
                    margin: const EdgeInsets.only(top: 24),
                    decoration: BoxDecoration(
                      color: _authMessageColor == Colors.red
                          ? Color(0xFFFFE5E5)
                          : Color(0xFFE0F7FA),
                      border: Border.all(color: _authMessageColor, width: 2),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      _authMessage!,
                      style: TextStyle(
                        color: _authMessageColor,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                const SizedBox(height: 40),
                Center(
                  child: SizedBox(
                    width: double.infinity,
                    height: 56,
                    child: ElevatedButton(
                      onPressed: _showAuthField && _authMessage == '인증번호가 일치합니다.' ? _findId : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Color(0xFF1EA1F7),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        elevation: 0,
                      ),
                      child: const Text(
                        '아이디 찾기',
                        style: TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
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
