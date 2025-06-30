import 'package:flutter/material.dart';
import 'package:shake/shake.dart';
import 'package:flutter/services.dart';

class Shake extends StatefulWidget {
  final VoidCallback onPermissionGranted;
  const Shake({Key? key, required this.onPermissionGranted}) : super(key: key);

  @override
  State<Shake> createState() => _Shake();
}

class _Shake extends State<Shake> {
  int _shakeCount = 0;
  ShakeDetector? _detector;
  static const platform = MethodChannel('floating_widget');
  bool _isOverlayVisible = false;

  @override
  void initState() {
    super.initState();
    try {
      _detector = ShakeDetector.autoStart(
        onPhoneShake: (ShakeEvent event) => _onShake(),
      );
      print('ShakeDetector 초기화 성공');
    } catch (e) {
      print('ShakeDetector 초기화 실패: $e');
    }
  }

  void _onShake() async {
    try {
      setState(() {
        _shakeCount++;
      });

      print('흔들기 감지: $_shakeCount/3');

      if (_shakeCount >= 3) {
        _shakeCount = 0;
        print('3번 흔들기 완료!');
        
        // 현재 오버레이 상태 확인
        try {
          final bool currentState = await platform.invokeMethod('isOverlayVisible') ?? false;
          _isOverlayVisible = currentState;
          print('현재 오버레이 상태: $_isOverlayVisible');
        } catch (e) {
          _isOverlayVisible = false;
          print('오버레이 상태 확인 실패: $e');
        }
        
        if (_isOverlayVisible) {
          // 바가 활성화되어 있으면 숨기기
          print('오버레이 숨기기 시도...');
          try {
            final result = await platform.invokeMethod('hideOverlay');
            print('hideOverlay 결과: $result');
            _isOverlayVisible = false;
            print('오버레이 숨기기 성공!');
          } catch (e) {
            print('오버레이 숨기기 실패: $e');
          }
        } else {
          // 바가 비활성화되어 있으면 권한 체크 후 보이기
          print('오버레이 표시 시도...');
          try {
            final bool granted = await platform.invokeMethod('checkOverlayPermission') ?? false;
            print('오버레이 권한 상태: $granted');
            if (!granted) {
              try {
                await platform.invokeMethod('requestOverlayPermission');
                print('오버레이 권한 요청 완료');
              } catch (e) {
                print('권한 요청 실패: $e');
              }
              return;
            }
            // 오버레이 띄우기
            final result = await platform.invokeMethod('showOverlay');
            print('showOverlay 결과: $result');
            _isOverlayVisible = true;
            print('오버레이 표시 성공!');
          } catch (e) {
            print('오버레이 표시 실패: $e');
          }
        }
        
        // 상태 재확인
        try {
          final bool finalState = await platform.invokeMethod('isOverlayVisible') ?? false;
          print('최종 오버레이 상태: $finalState');
        } catch (e) {
          print('최종 상태 확인 실패: $e');
        }
      }
    } catch (e) {
      print('흔들기 처리 중 오류: $e');
      _shakeCount = 0;
    }
  }

  @override
  void dispose() {
    try {
      _detector?.stopListening();
      print('ShakeDetector 정리 완료');
    } catch (e) {
      print('ShakeDetector 정리 실패: $e');
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container();
  }
}
