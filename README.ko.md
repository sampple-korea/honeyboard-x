[![English](https://img.shields.io/badge/English-555?style=flat-square)](README.md) [![한국어](https://img.shields.io/badge/%ED%95%9C%EA%B5%AD%EC%96%B4-2F80ED?style=flat-square)](README.ko.md) [![日本語](https://img.shields.io/badge/%E6%97%A5%E6%9C%AC%E8%AA%9E-555?style=flat-square)](README.ja.md) [![简体中文](https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-555?style=flat-square)](README.zh-CN.md)

# HoneyBoard X

삼성 키보드에 Rambler 음성 입력과 Codex 번역을 추가하는 LSPosed 모듈입니다.

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-libxposed%20API%20102-6f42c1) ![License: MIT](https://img.shields.io/badge/License-MIT-blue)

## 주요 기능

- **Rambler 음성 입력:** 삼성 키보드 툴바에 음성 입력 버튼을 추가합니다. Gboard Rambler 음성 인식 서버를 사용하며, 오로라 스타일 헤더, 실시간 인식 표시, 다국어 인식, 음성 편집 명령을 지원합니다. 인식 결과는 완료 시 한 번에 입력됩니다. 무음 자동 완료와 길게 눌러 일시정지/재개, 선택형 햅틱·사운드를 지원합니다.
- **Codex 번역:** 삼성 키보드 번역창의 요청을 ChatGPT/Codex 번역으로 처리합니다. 모델과 추론 강도, 번역 지침을 설정하고 계정 모델·사용량을 확인할 수 있습니다. 5시간 또는 주간 사용량이 0%가 되면 `gpt-reserve`로 자동 전환하고, 요청 실패 시 삼성 기본 번역으로 폴백할 수 있습니다.
- **통합 설정 앱:** One UI에서 영감을 받은 런처 앱으로, 한국어·영어·중국어·일본어를 지원합니다.

## 요구 사항

- 삼성 키보드가 설치된 삼성 기기. One UI 8 / Android 16~17의 `com.samsung.android.honeyboard` **5.9.40.75**를 대상으로 합니다. 키보드 업데이트로 비공개 구현이 바뀌면 호환되지 않을 수 있습니다.
- Android 12 이상(모듈 최소 SDK 31).
- libxposed API 102를 지원하는 LSPosed.
- 인터넷 연결. 음성 입력에는 삼성 키보드의 마이크 권한이 필요하고, 번역에는 ChatGPT/Codex 로그인이 필요합니다.

## 설치

1. 기존 별도 Samsung Rambler 또는 Codex 번역 모듈을 사용 중이라면 먼저 제거합니다. 앱별 비공개 설정과 로그인 토큰은 이전되지 않으므로 설정을 기록하고 다시 로그인해야 합니다.
2. 프로젝트 **Releases**에 서명된 HoneyBoard X APK가 있으면 내려받아 설치합니다. 없으면 아래 안내에 따라 직접 빌드합니다.
3. LSPosed에서 **HoneyBoard X**를 활성화하고 범위를 삼성 키보드(`com.samsung.android.honeyboard`)로 지정합니다.
4. 삼성 키보드를 강제 종료한 뒤 다시 실행해 후크를 적용합니다.
5. 런처에서 HoneyBoard X를 열어 음성 설정을 구성합니다. 번역을 사용하려면 기능을 켜고 ChatGPT/Codex에 로그인합니다.

## Google API 키 (음성 입력에 필요)

HoneyBoard X는 Gboard가 Rambler에 쓰는 Google API 키를 **포함하지 않습니다.** Gboard APK에서 `X-Goog-Api-Key` 값(`AIza`로 시작하는 39자)을 직접 구한 뒤 다음 중 하나로 넣으세요.

- **앱에서:** HoneyBoard X → 음성 입력 → **Google API 키**에 붙여넣기
- **빌드할 때:** `GOOGLE_API_KEY=AIza... module/build.sh` 또는 `module/local.keys`(gitignore됨)에 `google_api_key=AIza...`

배포 APK에는 키가 없으므로 앱에서 입력해야 합니다.

## 설정

앱은 음성 입력·번역·정보 화면으로 구성됩니다. 음성 입력에서는 주언어/추가 언어, 무음 종료 시간, 오로라 표시, 햅틱, 사운드를 설정할 수 있습니다. 번역에서는 기능 사용 여부, 모델, 추론 강도, 사용자 지침, 삼성 번역 폴백, 계정·모델·사용량을 설정하거나 확인할 수 있습니다.

음성 버튼을 탭하면 인식 결과를 확정해 입력하고, 길게 누르면 일시정지/재개합니다. 음성 입력을 위해 삼성 키보드에 마이크 권한을 허용하세요.

## 소스에서 빌드

JDK 17 이상, Android 빌드 도구(`aapt`, `zipalign`, `apksigner`, `zip`)와 [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)에 설명된 SDK 파일이 필요합니다.

```bash
./module/build.sh
```

서명된 APK는 `module/build/HoneyBoardX.apk`에 생성됩니다. 환경 설정, 테스트 명령, 설정 이관, 알려진 한계는 개발 안내를 참고하세요.

## 개인정보

- 음성 오디오와 커서 앞 문맥(최대 2,000 UTF-16 문자)은 음성 편집 명령 처리를 위해 Google Rambler 음성 인식 서버로 전송됩니다.
- 번역을 요청한 텍스트는 ChatGPT/Codex 서버로 전송됩니다.
- 로그인 토큰은 Android Keystore로 암호화됩니다. 음성 입력에는 삼성 키보드의 마이크 권한이 필요합니다.

## 면책 고지

HoneyBoard X는 독립적인 비공식 프로젝트이며 Samsung, Google, OpenAI와 제휴하거나 이들의 승인을 받지 않았습니다. 비공개 API와 서비스 동작에 의존하므로 언제든 변경되거나 차단될 수 있습니다. 사용에 따른 책임은 사용자에게 있습니다.

## 크레딧

- [PixelBoard](https://github.com/Akshayykadam/PixelBoard) — Rambler 구현 참고
- [libxposed](https://github.com/libxposed) — LSPosed 모듈 API

## 라이선스

[MIT](LICENSE) © 2026 sampple-korea. 포함된 libxposed 라이브러리는 Apache-2.0입니다 (`module/libs/LICENSE-libxposed.txt`).

## 제작: [sampple-korea](https://github.com/sampple-korea)
