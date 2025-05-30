import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'dart:io';

class RegisterScreen extends StatefulWidget { // 회원가입 화면
  const RegisterScreen({super.key});

  @override
  _RegisterScreenState createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> { // 회원가입 화면 상태
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _emailController = TextEditingController();
  final _memberIdController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  // 검증할 정보
  final String _validName = '이은아';
  final String _validEmail = 'leeah03200320@gmail.com';
  final String _validMemberId = 'leeah03200320';
  final String _validPassword = 'leeah0320!!';

  Future<void> _register() async {
    if (_formKey.currentState!.validate()) {
      final baseUrl = 'http://127.0.0.1:8081'; // 로컬 환경에서 실행

      try { // 회원가입 요청
        final response = await http.post(
          Uri.parse('$baseUrl/api/members/signup'), 
          headers: {
            'Content-Type': 'application/json',
          },
          body: jsonEncode({ // 요청 본문
            'memberId': _memberIdController.text,
            'password': _passwordController.text,
            'name': _nameController.text,
            'email': _emailController.text,
          }),
        );

        if (response.statusCode == 200) { 
          print('회원가입 성공: ${response.body}');
          Navigator.pushNamed(context, '/register_success');
        } else {
          print('회원가입 실패: ${response.body}');
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('서버 연결에 실패했습니다. 다시 시도해 주세요.')),
          );
        }
      } catch (e) {
        print('에러 발생: $e');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('서버 연결에 실패했습니다. 다시 시도해 주세요.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) { // 화면 레이아웃 구성
    return Scaffold(
      appBar: AppBar(
        title: const Text('회원가입'),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(
                    labelText: '이름',
                    border: OutlineInputBorder(),
                  ),
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '이름을 입력해주세요';
                    }
                    if (value != _validName) {
                      return '올바른 이름을 입력해주세요';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _emailController,
                  decoration: const InputDecoration(
                    labelText: '이메일',
                    border: OutlineInputBorder(),
                  ),
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '이메일을 입력해주세요';
                    }
                    if (value != _validEmail) {
                      return '올바른 이메일을 입력해주세요';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _memberIdController,
                  decoration: const InputDecoration(
                    labelText: '아이디',
                    border: OutlineInputBorder(),
                  ),
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '아이디를 입력해주세요';
                    }
                    if (value != _validMemberId) {
                      return '올바른 아이디를 입력해주세요';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _passwordController,
                  decoration: const InputDecoration(
                    labelText: '비밀번호',
                    border: OutlineInputBorder(),
                  ),
                  obscureText: true,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '비밀번호를 입력해주세요';
                    }
                    if (value != _validPassword) {
                      return '올바른 비밀번호를 입력해주세요';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _confirmPasswordController,
                  decoration: const InputDecoration(
                    labelText: '비밀번호 확인',
                    border: OutlineInputBorder(),
                  ),
                  obscureText: true,
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return '비밀번호를 다시 입력해주세요';
                    }
                    if (value != _passwordController.text) {
                      return '비밀번호가 일치하지 않습니다';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: _register,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    textStyle: const TextStyle(fontSize: 18),
                  ),
                  child: const Text('회원가입'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() { // 화면 종료 시 컨트롤러 해제
    _nameController.dispose();
    _emailController.dispose();
    _memberIdController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }
}

void main() { // 앱 실행
  runApp(MaterialApp(
    home: RegisterScreen(),
    theme: ThemeData(
      primarySwatch: Colors.blue,
    ),
  ));
} 
