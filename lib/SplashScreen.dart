import 'package:flutter/material.dart';
import 'LoginScreen.dart';

class SplashScreen extends StatefulWidget {
  @override
  _SplashScreenState createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  double _opacity = 1.0;

  @override
  void initState() {
    super.initState();
    _startSplashScreen();
  }

  void _startSplashScreen() async {
    await Future.delayed(Duration(seconds: 2));
    setState(() {
      _opacity = 0.0; // 투명도를 0으로 변경
    });
    await Future.delayed(Duration(seconds: 2)); // 사라지는 애니메이션을 기다림
    Navigator.of(context).pushReplacement(MaterialPageRoute(
      builder: (context) => LoginScreen(),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: AnimatedOpacity(
        opacity: _opacity,
        duration: Duration(seconds: 1), // 1초 동안 서서히 사라짐
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Image.asset(
                'assets/SplashIcon.png', // 로고 이미지 경로
                width: 500, // 이미지 크기 조절
                height: 200,
              ),
              SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}
