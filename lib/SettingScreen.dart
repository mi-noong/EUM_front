import 'package:flutter/material.dart';
import 'HelpScreen.dart';
import 'PasswordSuccessScreen.dart';

class SettingScreen extends StatefulWidget {
  const SettingScreen({Key? key}) : super(key: key);

  @override
  State<SettingScreen> createState() => _SettingScreenState();
}

class _SettingScreenState extends State<SettingScreen> {
  bool _isActivated = false;

  void _toggleActivation() {
    setState(() {
      _isActivated = !_isActivated;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Stack(
          children: [
            Center(
              child: ElevatedButton(
                onPressed: _toggleActivation,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _isActivated ? Color(0xFF1EA1F7) : Colors.grey[300],
                  foregroundColor: Colors.black,
                  minimumSize: Size(340, 80),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                  ),
                  elevation: 0,
                ),
                child: Text(
                  'EUM AI 챗봇 활성화',
                  style: TextStyle(
                    fontSize: 33,
                    fontWeight: FontWeight.bold,
                    color: _isActivated ? Colors.white : Colors.black,
                  ),
                ),
              ),
            ),
            Positioned(
              right: 32,
              bottom: 32,
              child: Material(
                color: Colors.grey[300],
                shape: const CircleBorder(),
                child: InkWell(
                  borderRadius: BorderRadius.circular(100),
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (context) => const HelpScreen()),
                    );
                  },
                  child: Container(
                    width: 170,
                    height: 170,
                    alignment: Alignment.center,
                    child: Image.asset(
                      'assets/helpbutton.png',
                      width: 160,
                      height: 160,
                      fit: BoxFit.contain,
                      semanticLabel: '기능 설명 도움말 보기',
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
} 
