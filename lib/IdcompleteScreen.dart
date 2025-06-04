import 'package:flutter/material.dart';
import 'LoginScreen.dart';

class IdcompleteScreen extends StatelessWidget {
  final String name;
  final String userId;

  const IdcompleteScreen({Key? key, required this.name, required this.userId}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.account_circle, size: 120, color: Color(0xFF90CAFF)),
              SizedBox(height: 32),
              RichText(
                textAlign: TextAlign.center,
                text: TextSpan(
                  style: TextStyle(color: Colors.black, fontSize: 28, fontWeight: FontWeight.normal),
                  children: [
                    TextSpan(text: '$name님의 아이디는\n'),
                    TextSpan(
                      text: userId,
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 32),
                    ),
                    TextSpan(text: '입니다.'),
                  ],
                ),
              ),
              SizedBox(height: 40),
              SizedBox(
                width: 240,
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
                    backgroundColor: Color(0xFF1EA1F7),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: Text(
                    '로그인',
                    style: TextStyle(fontSize: 20, color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
