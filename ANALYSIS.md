# 디컴파일 분석 자료

APK 3종을 디컴파일해 파일·클래스 구조를 확인한 결과 기록.

대상 앱 버전:
- 삼성 키보드(Honeyboard): `com.samsung.android.honeyboard` **5.9.40.75** (versionCode 594075300), Android 16 / One UI 8, arm64.
- Gboard(PixelBoard 패치본): `18.3.1.977415014-release`.
- 참고 LSPosed 모듈: `dev.codex.samsungkeyboard` (libxposed API 102, staticScope).

---

## 1. 삼성 키보드 툴바("Bee/BeeHive") 구조

툴바 항목은 "Bee", 툴바 전체는 "BeeHive"로 명명돼 있다.

### 핵심 타입 (난독화 후 이름)
- `z6.c` — Bee 인터페이스(순수 interface). 약 40개 메서드:
  `getBeeId()`, `getBeeInfo():z6.j`, `execute()`, `getBeeVisibility():int`, `isSelected()`,
  `isUsingNetwork()`, `getBeeFlags()`, `setCallback(z6.b)`, `isPinBee()`, `isSystem()` 등.
- `z6.a`(abstract, implements c) → `z6.m`(dynamic info) → `z6.n`(id/board/requester) → `z6.l`(shortcut) : Bee 구현 상속 체인.
- `z6.j` — Bee 표시 정보(아이콘/라벨). 빌더 `z6.i`로 생성:
  - `new z6.i(Context, android.graphics.drawable.Icon icon, int labelResId)` 또는 `new z6.i(Context, int iconRes, int labelRes)`.
  - `iVar.f38809g = labelRes;` 후 `new z6.j(iVar)`.
  - 아이콘 필드는 `android.graphics.drawable.Icon` 타입.
- `z6.b` — Bee 콜백(invalidate 통지). `z6.d` — 커맨드(`execute()` 단일 메서드).

### 툴바 목록 관리
- `com.samsung.android.honeyboard.beehive.data.BeeItem` — Bee 래퍼(뷰모델).
  - 생성자: `BeeItem(z6.c bee, Z9.b onExecuteListener)`.
  - `setPrimaryProperty()` = 주 툴바 표시(isPrimary=true), `setSwarmProperty()` = more/확장 영역, `setSleepingProperty()` = 수면.
- `com.samsung.android.honeyboard.beehive.data.BeeObservableArrayList` — `ArrayList` + `ObservableList`.
  `add()`가 `ObservableList` 콜백으로 통지한다.
- `com.samsung.android.honeyboard.beehive.viewmodel.K` — BeeWorld(툴바 상태 관리).
  - `implements Z9.b, ex.d`.
  - `f22418t` (타입 `BeeObservableArrayList`) = 주 툴바 항목들.
  - `f22412n`(N), `f22413o`(N) = swarm/sleeping 그룹.
  - `f22409k`(LinkedHashMap) = beeId→BeeItem 인덱스.
  - `b(z6.c)`, `c(z6.c,int)`, `a(...)` = 항목 추가 로직(정원 `beeSetting.l()` 초과 시 swarm로).
  - `B(boolean)` = 가시성/목록 갱신(populate 이후 호출).
  - `v(reason, z)` = 현재 Bee 세트 저장(BeeTag 목록으로 순서 영속화).
- `com.samsung.android.honeyboard.bee.b` — PluginQueenBee. 플러그인 Bee용 `ObservableArrayList f22240d`.
  여기에 add 시 `onItemRangeInserted` → `K.a(newBee)`로 연결. primary 배치는 preset 순서에 의존.
- `X9/e.java` — 최초 Bee population. `bees` 순회하며 `aVar.n(beeId)`(preset rank)로 primary/swarm/sleeping 분류, `BeeTag` 순서대로 정렬.
- `na.b`/`na.c` — BeeSetting(preset + 사용자 세트). `d()`=BeeTag 목록, `h()`/`l()`=primary 정원, `n(beeId)`=rank, `j(tags,...)`=저장.
- `H6.a` — HoneyBrand enum(보드 ID). TEXT/SEARCH/EMOTICON/…/CLIPBOARD_HONEY/AI_GEN_HONEY 등.

### 기존 삼성 음성 항목
- `p047bg/c.java` (`bg.c`) — voice_input Bee.
  - `getBeeId()` = `"voice_input"`, 아이콘 `R.drawable.ic_toolbar_voice_samsung`(구글 모드면 `ic_toolbar_voice_google`), 라벨 `R.string.toolbar_voice_input`.
  - `getBeeVisibility()` 반환 1 = 숨김 상태.

### IME 서비스 / 입력 연결
- `com.samsung.android.honeyboard.service.HoneyBoardService extends InputMethodService`.
  - `onCreateInputView():View` — 키보드 루트 뷰 반환.
  - `onStartInputView(EditorInfo, boolean)`, `onFinishInputView`, `onUpdateSelection` 등 표준 오버라이드 존재.
  - `getCurrentInputConnection()` — 표준 `InputConnection` (commitText/setComposingText).
- 매니페스트 권한: RECORD_AUDIO, INTERNET, QUERY_ALL_PACKAGES 보유.

> 난독화 이름(`K`, `f22418t` 등)은 5.9.40.75 기준. 앱 업데이트 시 재매핑 필요.

---

## 2. Gboard "Rambler" 백엔드 (디컴파일 분석)

- 진입: `AgenticDictationExtension`(`defpackage/fce.java`). 음성 세션을 번들 `session_type="jetson"`으로 시작(`fce.t()` → `kjr.t()`가 해석).
- 인식기 선택: `SpeechRecognitionFactory`(`kiy.java`). jetson이면 온라인 NEW_S3(`klt.d`), 오프라인 폴백 JETSON_LITE(`klt.c`).
- 온라인 인식기: `NewS3Recognizer`(`kmt.java`) → `NetworkSpeechRecognizer`(`ife.java`)가 스트리밍.
- 전송: gRPC bidi, 호스트 `speechs3proto2-pa.googleapis.com:443`(flag `s3_host_name`, `iet.java`), 메서드 `speech.s3.S3StubbyClientConnectorService/S3Bidi`.
- 헤더: `S3ConnectionUtil`(`ieu.java`)가 고정 헤더 세트를 붙임 — `X-Goog-Api-Key`(Gboard 내장 키), `X-Android-Package`(`com.google.android.googlequicksearchbox`), `X-Android-Cert`(서명 해시).
- 요청(첫 S3Request, `S3HeaderProducer`=`ifh.java`): recognizer id `beyond-a2a-recognizer`, 오디오 정보(인코딩=10, 16000Hz), 입력창 주변 텍스트(`InputBoxInfo`, 커서/선택 포함). 이후 224바이트 opus 청크 + 종료 요청.
- 응답(`ifd.java`): 부분/최종 텍스트, "voice editing"(사용자 의도 enum `aagl` — rewrite/delete/replace 등)로 구간 치환. `AgenticDictationTextEditor`가 InputConnection으로 반영.
- PixelBoard 패치는 Gboard의 서명 화이트리스트 검사(`rvl/rpv.a(Context,String)`)를 바이패스(`GboardSignatureBypassBytecodePatch.kt`).

---

## 3. PixelBoard(참고 프로젝트) 구조

- `com.akshaykadam.pixelboard` — Gboard 18.3.1을 패치해 Rambler를 켜는 오픈소스 모드(GPLv3).
- 4개 패치: Advanced Voice Typing / AI Writing Tools / Signature Bypass / Package Rename.
- Rambler 활성화는 플래그 강제(`show_rambler_dict_settings`, `enable_agentic_dictation`, `ad_activation_type=2` 등, `GboardRambler1803StockPolicy.java`) + Voice settings 스코프 한정.
- 방식: Gboard 자체를 패치(별도 앱/키보드 아님).
- 소스 클론: `reverse-engineering/pixelboard-source/`.

---

## 4. 참고 LSPosed 모듈(`reference-lsposed-module.apk`) 구조

- `dev.codex.samsungkeyboard` — 삼성 키보드의 번역 뷰모델(`TranslationViewModel`)을 후크해 Codex(GPT) 번역을 끼워넣는 모듈. libxposed API 102, `staticScope=true`, scope=honeyboard.
- 엔트리 `C0049q extends XposedModule`, `onPackageReady`에서 honeyboard 클래스 후크.
- 설정 화면은 `androidx.preference` 리플렉션으로 항목 주입.
- 패키징 형식: `META-INF/xposed/module.prop`·`scope.list`·`java_init.list`.
