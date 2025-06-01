import 'package:flutter/material.dart';

class HelpScreen extends StatelessWidget {
  const HelpScreen({Key? key}) : super(key: key);

  //도움말 페이지
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const SizedBox(height: 16),
                  const Text(
                    'EUM',
                    style: TextStyle(
                      fontSize: 70,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 32),
                  _HelpCard(
                    title: 'OCR',
                    description: '사진을 선택하면 사진에 대해 설명해줍니다.',
                  ),
                  const SizedBox(height: 24),
                  _HelpCard(
                    title: '확대',
                    description: '원하는 부분을 확대해서 볼 수 있습니다.',
                  ),
                  const SizedBox(height: 24),
                  _HelpCard(
                    title: 'AI 챗봇',
                    description: '음성 채팅, 가게 또는 메뉴 추천 및 리뷰 요약을 해줍니다.',
                  ),
                  const SizedBox(height: 24),
                  _HelpCard(
                    title: '간편 실행 기능',
                    description: '스마트폰을 3회 흔들면 EUM앱이 실행됩니다.',
                  ),
                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _HelpCard extends StatelessWidget {
  final String title;
  final String description;

  const _HelpCard({required this.title, required this.description});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(vertical: 18),
          decoration: BoxDecoration(
            color: Colors.grey[300],
            borderRadius: BorderRadius.circular(20),
          ),
          child: Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 28,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(
          description,
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w400,
          ),
        ),
      ],
    );
  }
}
