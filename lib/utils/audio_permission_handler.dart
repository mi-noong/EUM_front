import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class AudioPermissionHandler {
  /// 음성 권한 상태 확인
  static Future<bool> checkAudioPermission() async {
    try {
      final status = await Permission.microphone.status;
      print('음성 권한 상태: $status');
      return status.isGranted;
    } catch (e) {
      print('음성 권한 상태 확인 실패: $e');
      return false;
    }
  }

  /// 음성 권한 요청
  static Future<bool> requestAudioPermission(BuildContext context) async {
    try {
      print('음성 권한 요청 시작');
      
      // 권한 상태 확인
      final status = await Permission.microphone.status;
      print('현재 음성 권한 상태: $status');

      if (status.isGranted) {
        print('이미 음성 권한이 허용되어 있습니다.');
        return true;
      }

      if (status.isDenied) {
        print('음성 권한 요청 다이얼로그 표시');
        final result = await Permission.microphone.request();
        print('음성 권한 요청 결과: $result');
        return result.isGranted;
      }

      if (status.isPermanentlyDenied) {
        print('음성 권한이 영구적으로 거부되었습니다. 설정으로 이동');
        _showPermissionSettingsDialog(context);
        return false;
      }

      return false;
    } catch (e) {
      print('음성 권한 요청 실패: $e');
      return false;
    }
  }

  /// 음성 권한 설명 다이얼로그 표시
  static void showPermissionExplanation(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('음성 권한 필요'),
        content: const Text(
          '음성 호출어 기능을 사용하려면 마이크 권한이 필요합니다.\n\n'
          '권한을 허용하면:\n'
          '• "이음봇아" 호출어로 챗봇을 활성화할 수 있습니다\n'
          '• 음성 인식 기능을 사용할 수 있습니다\n\n'
          '권한을 거부하면 음성 호출어 기능이 작동하지 않습니다.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('취소'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.of(context).pop();
              bool granted = await requestAudioPermission(context);
              if (granted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('음성 권한이 허용되었습니다.'),
                    backgroundColor: Colors.green,
                  ),
                );
              }
            },
            child: const Text('권한 허용'),
          ),
        ],
      ),
    );
  }

  /// 권한 설정 다이얼로그 표시
  static void _showPermissionSettingsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('권한 설정 필요'),
        content: const Text(
          '음성 권한이 영구적으로 거부되었습니다.\n\n'
          '앱 설정에서 마이크 권한을 직접 허용해주세요.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('취소'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.of(context).pop();
              await openAppSettings();
            },
            child: const Text('설정으로 이동'),
          ),
        ],
      ),
    );
  }
} 
