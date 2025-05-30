// Flutter의 기본 위젯들을 사용하기 위한 import
import 'package:flutter/material.dart';
// 스플래시 화면과 로그인 화면을 사용하기 위한 import
import 'screens/SplashScreen.dart';
import 'screens/login_screen.dart';
import 'screens/reduction_screen.dart';
import 'screens/register_screen.dart';
import 'screens/register_success_screen.dart';
import 'screens/forgot_password_screen.dart';

// 앱의 진입점이 되는 main 함수
void main() {
  // MyApp 위젯을 실행
  runApp(MyApp());
}

// 앱의 루트 위젯
class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    // MaterialApp은 Flutter 앱의 기본 구조를 제공하는 위젯
    return MaterialApp(
      // 앱의 제목
      title: 'Eum',
      // 앱의 테마 설정
      theme: ThemeData(
        // 기본 색상 테마를 파란색으로 설정
        primarySwatch: Colors.blue,
      ),
      // 앱이 시작될 때 보여줄 첫 화면의 경로
      initialRoute: '/',
      // 앱의 화면 전환을 위한 라우트 설정
      routes: {
        // '/' 경로로 접근하면 SplashScreen을 보여줌
        '/': (context) => SplashScreen(),
        // '/login' 경로로 접근하면 LoginScreen을 보여줌
        '/login': (context) => LoginScreen(),
        // '/reduction' 경로로 접근하면 ReductionScreen을 보여줌
        '/reduction': (context) => ReductionScreen(),
        // '/register' 경로로 접근하면 RegisterScreen을 보여줌
        '/register': (context) => RegisterScreen(),
        // '/register_success' 경로로 접근하면 RegisterSuccessScreen을 보여줌
        '/register_success': (context) => RegisterSuccessScreen(),
        // '/forgot_password' 경로로 접근하면 ForgotPasswordScreen을 보여줌
        '/forgot_password': (context) => ForgotPasswordScreen(),
      },
    );
  }
}