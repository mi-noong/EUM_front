import 'package:flutter/material.dart';
import 'RegisterSuccessScreen.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({Key? key}) : super(key: key);

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _idController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _passwordConfirmController = TextEditingController();

  bool _passwordMismatch = false;
  String? _inputError;

  void _register() {
    setState(() {
      _passwordMismatch = false;
      _inputError = null;
    });
    if (_nameController.text.isEmpty ||
        _emailController.text.isEmpty ||
        _idController.text.isEmpty ||
        _passwordController.text.isEmpty ||
        _passwordConfirmController.text.isEmpty) {
      setState(() {
        _inputError = '정보를 입력해주세요.';
      });
      return;
    }
    if (_passwordController.text != _passwordConfirmController.text) {
      setState(() {
        _passwordMismatch = true;
      });
      return;
    }
    // 비밀번호가 일치하면 바로 완료 페이지로 이동, 정보 전달
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => RegisterSuccessScreen(
          name: _nameController.text,
          email: _emailController.text,
          id: _idController.text,
          password: _passwordController.text,
        ),
      ),
    );
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
                const SizedBox(height: 32),
                // 뒤로가기 버튼
                IconButton(
                  icon: const Icon(Icons.arrow_back),
                  onPressed: () => Navigator.pop(context),
                ),
                const SizedBox(height: 24),
                // 회원가입 제목
                const Center(
                  child: Text(
                    '회원가입',
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                // 이름 입력
                _buildInputField('사용자 이름', controller: _nameController, hint: 'Enter your Name'),
                const SizedBox(height: 16),
                // 이메일 입력
                _buildInputField('이메일', controller: _emailController, hint: 'Enter your Email'),
                const SizedBox(height: 16),
                // 아이디 입력
                _buildInputField('아이디', controller: _idController, hint: 'Enter your ID'),
                const SizedBox(height: 16),
                // 비밀번호 입력
                _buildInputField('비밀번호', controller: _passwordController, isPassword: true, hint: 'Enter your Password'),
                const SizedBox(height: 16),
                // 비밀번호 확인
                _buildInputField('비밀번호 확인', controller: _passwordConfirmController, isPassword: true, hint: 'Enter your Password', isError: _passwordMismatch),
                if (_inputError != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 4, left: 4),
                    child: Text(
                      _inputError!,
                      style: TextStyle(color: Colors.red[700], fontSize: 14, fontWeight: FontWeight.w500),
                    ),
                  ),
                if (_passwordMismatch)
                  Padding(
                    padding: const EdgeInsets.only(top: 4, left: 4),
                    child: Text(
                      '비밀번호가 일치하지 않습니다.',
                      style: TextStyle(color: Colors.red[700], fontSize: 14, fontWeight: FontWeight.w500),
                    ),
                  ),
                const SizedBox(height: 32),
                // 회원가입 버튼
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _register,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF1EA1F7),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                      elevation: 0,
                    ),
                    child: const Text(
                      '회원가입',
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
    );
  }

  Widget _buildInputField(String label, {bool isPassword = false, required TextEditingController controller, required String hint, bool isError = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w500,
          ),
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
              borderRadius: BorderRadius.circular(12),
              borderSide: isError ? BorderSide(color: Colors.red, width: 2) : BorderSide.none,
            ),
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 16,
            ),
          ),
        ),
      ],
    );
  }
} 
