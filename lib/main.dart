import 'package:flutter/material.dart';
import 'package:kakao_flutter_sdk/kakao_flutter_sdk.dart'; //카카오 SDK 초기화(소셜로그인 연동에 필요)
import 'SplashScreen.dart';

void main() {
  KakaoSdk.init(nativeAppKey: 'c15bd6ab806014b0fde0e7a2f5b4de7e');
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Eum',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: SplashScreen(),
    );
  }
}
