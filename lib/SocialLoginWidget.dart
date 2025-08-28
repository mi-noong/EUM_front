import 'package:flutter/material.dart';
import 'package:kakao_flutter_sdk/kakao_flutter_sdk.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:flutter/services.dart';
import 'SettingScreen.dart';

class SocialLoginWidget extends StatefulWidget {
  const SocialLoginWidget({Key? key}) : super(key: key);

  @override
  State<SocialLoginWidget> createState() => _SocialLoginWidgetState();
}

class _SocialLoginWidgetState extends State<SocialLoginWidget> {
  bool _isLoading = false;
  String? _userInfo;

  final GoogleSignIn _googleSignIn = GoogleSignIn();
  static const platform = MethodChannel('com.example.eum/naver_login');

  Future<void> _requestKakaoLogin() async {
    setState(() {
      _isLoading = true;
      _userInfo = null;
    });

    try {
      print('카카오 로그인 시작');
      
      // 카카오톡 설치 여부 확인
      bool isKakaoTalkAvailable = await isKakaoTalkInstalled();
      print('카카오톡 설치 여부: $isKakaoTalkAvailable');
      
      OAuthToken token;
      if (isKakaoTalkAvailable) {
        print('카카오톡으로 로그인 시도');
        token = await UserApi.instance.loginWithKakaoTalk();
      } else {
        print('카카오 계정으로 로그인 시도');
        token = await UserApi.instance.loginWithKakaoAccount();
      }
      
      print('OAuth 토큰 획득 성공: ${token.accessToken}');
      
      // 사용자 정보 가져오기
      User user = await UserApi.instance.me();
      print('사용자 정보 획득 성공: ${user.id}');

      // 카카오 로그인 성공 시 SettingScreen으로 이동
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (context) => const SettingScreen()),
      );
    } catch (error) {
      print('카카오 로그인 에러 상세: $error');
      setState(() {
        _userInfo = '카카오 로그인 실패: ${error.toString()}';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _requestGoogleLogin() async {
    print('구글 로그인 시작');
    setState(() {
      _isLoading = true;
      _userInfo = null;
    });

    try {
      print('GoogleSignIn.signIn() 호출');
      print('GoogleSignIn 설정 확인: ${_googleSignIn.scopes}');
      print('GoogleSignIn 클라이언트 ID: ${_googleSignIn.clientId}');
      final account = await _googleSignIn.signIn();
      print('GoogleSignIn 결과: ${account?.email}');

      if (account != null) {
        print('계정 인증 시작');
        final auth = await account.authentication;
        print('인증 완료: accessToken 있음: ${auth.accessToken != null}');

        // 백엔드로 idToken 전송 예시
        if (auth.idToken != null) {
          print('백엔드로 idToken 전송 시작');
          final response = await http.post(
            Uri.parse('http://10.0.2.2:8081/api/auth/google'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'idToken': auth.idToken}),
          );
          print('백엔드 응답: ${response.statusCode}');
        }

        // 구글 로그인 성공 시 SettingScreen으로 이동
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const SettingScreen()),
        );
      } else {
        print('구글 로그인 취소됨');
        setState(() {
          _userInfo = '구글 로그인 취소됨';
        });
      }
    } catch (error) {
      print('구글 로그인 에러: ${error.toString()}');
      setState(() {
        _userInfo = '구글 로그인 실패: ${error.toString()}';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _requestNaverLogin() async {
    setState(() {
      _isLoading = true;
      _userInfo = null;
    });
    try {
      final result = await platform.invokeMethod('login');
      if (result != null) {
        // 네이버 로그인 성공 시 SettingScreen으로 이동
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const SettingScreen()),
        );
      } else {
        setState(() {
          _userInfo = '네이버 로그인 실패';
        });
      }
    } catch (error) {
      setState(() {
        _userInfo = '네이버 로그인 실패: ${error.toString()}';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            GestureDetector(
              onTap: _isLoading ? null : _requestKakaoLogin,
              child: Semantics(
                label: '카카오톡으로 로그인',
                hint: '카카오톡 계정으로 로그인합니다',
                child: Image.asset(
                  'assets/kakaotalk.png',
                  width: 56,
                  height: 56,
                  fit: BoxFit.contain,
                  semanticLabel: '카카오톡 로그인',
                ),
              ),
            ),
            const SizedBox(width: 16),
            GestureDetector(
              onTap: _isLoading ? null : _requestGoogleLogin,
              child: Semantics(
                label: '구글로 로그인',
                hint: '구글 계정으로 로그인합니다',
                child: Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    color: Colors.white,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.grey.withOpacity(0.3),
                        spreadRadius: 1,
                        blurRadius: 3,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Image.asset(
                    'assets/google.png',
                    width: 48,
                    height: 48,
                    fit: BoxFit.contain,
                    semanticLabel: '구글 로그인',
                  ),
                ),
              ),
            ),
            const SizedBox(width: 16),
            GestureDetector(
              onTap: _isLoading ? null : _requestNaverLogin,
              child: Semantics(
                label: '네이버로 로그인',
                hint: '네이버 계정으로 로그인합니다',
                child: Image.asset(
                  'assets/naver.png',
                  width: 56,
                  height: 56,
                  fit: BoxFit.contain,
                  semanticLabel: '네이버 로그인',
                ),
              ),
            ),
          ],
        ),
        if (_isLoading)
          const Padding(
            padding: EdgeInsets.only(top: 16.0),
            child: CircularProgressIndicator(),
          ),
        if (_userInfo != null)
          Padding(
            padding: const EdgeInsets.only(top: 16.0),
            child: Text(
              _userInfo!,
              textAlign: TextAlign.center,
            ),
          ),
      ],
    );
  }
}
