// Flutter의 기본 위젯들을 사용하기 위한 import
import 'package:flutter/material.dart';
// 스플래시 화면과 로그인 화면을 사용하기 위한 import
import 'screens/SplashScreen.dart';
import 'screens/login_screen.dart';
import 'screens/reduction_screen.dart';
import 'screens/register_screen.dart';
import 'screens/register_success_screen.dart';
import 'screens/forgot_password_screen.dart';

void main() {
  runApp(MyApp());
}

// 앱의 루트 위젯
class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // MaterialApp은 Flutter 앱의 기본 구조를 제공하는 위젯
    return MaterialApp(
      title: 'Eum',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      // 앱이 시작될 때 보여줄 첫 화면의 경로
      initialRoute: '/',
      // 앱의 화면 전환을 위한 라우트 설정
      routes: {
    
        '/': (context) => SplashScreen(),
        '/login': (context) => LoginScreen(),
        '/reduction': (context) => ReductionScreen(),
        '/register': (context) => RegisterScreen(),
        '/register_success': (context) => RegisterSuccessScreen(),
        '/forgot_password': (context) => ForgotPasswordScreen(),
      },
    );
  }
}
