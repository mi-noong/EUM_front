import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class StoragePermissionHandler {
  /// 저장소 권한 상태 확인
  static Future<bool> checkStoragePermission() async {
    try {
      final status = await Permission.storage.status;
      print('저장소 권한 상태: $status');
      return status.isGranted;
    } catch (e) {
      print('권한 상태 확인 실패: $e');
      return false;
    }
  }

  /// 저장소 권한 요청
  static Future<bool> requestStoragePermission(BuildContext context) async {
    try {
      print('저장소 권한 요청 시작');
      
      // 권한 상태 확인
      final status = await Permission.storage.status;
      print('현재 권한 상태: $status');

      if (status.isGranted) {
        print('이미 권한이 허용되어 있습니다.');
        return true;
      }

      if (status.isDenied) {
        print('권한 요청 다이얼로그 표시');
        final result = await Permission.storage.request();
        print('권한 요청 결과: $result');
        return result.isGranted;
      }

      if (status.isPermanentlyDenied) {
        print('권한이 영구적으로 거부되었습니다. 설정으로 이동');
        _showPermissionSettingsDialog(context);
        return false;
      }

      return false;
    } catch (e) {
      print('권한 요청 실패: $e');
      return false;
    }
  }

  /// 권한 설명 다이얼로그 표시
  static void showPermissionExplanation(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('저장소 권한 필요'),
        content: const Text(
          '이 기능을 사용하려면 저장소 접근 권한이 필요합니다.\n\n'
          '권한을 허용하면:\n'
          '• 캡처된 이미지를 저장할 수 있습니다\n'
          '• 앱이 정상적으로 작동할 수 있습니다\n\n'
          '권한을 거부하면 일부 기능이 제한될 수 있습니다.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('취소'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.of(context).pop();
              bool granted = await requestStoragePermission(context);
              if (granted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('저장소 권한이 허용되었습니다.'),
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
          '저장소 권한이 영구적으로 거부되었습니다.\n\n'
          '앱 설정에서 권한을 직접 허용해주세요.',
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
