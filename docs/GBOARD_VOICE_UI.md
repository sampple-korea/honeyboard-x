# Gboard 18.3.1 음성 입력 UI 역분석 노트

> 대상: `input-apks/gboard-18.3.1-pixelboard-rambler.apk`의 JADX 결과 `reverse-engineering/gboard-decompiled/`. 난독화 이름은 디컴파일된 Java의 패키지·클래스명과 `R` 리소스 ID를 함께 기록했다. APK 내부 구현 분석이며 Gboard의 실제 화면 배치·테마 선택에 따라 일부 크기는 달라질 수 있다.

## 요약 및 중요한 구분

Gboard 음성 UI에는 서로 다른 두 애니메이션 구현이 있다.

1. **`VoiceSolidPulsatingAnimationView` / `MicPulseAnimationHandler`** — 음성 키 위에 얹는 단색 원형 펄스. `Canvas.drawCircle()`로 그린다.
2. **`AssistantP6GlowView` / `MicRingAnimationHandler`** — `RING` 모드에서 마이크 주위에 그리는 Google 4색 그라데이션 글로우. 화면상 물결처럼 보일 수 있지만, 소스에 일반적인 좌우 사인파 waveform을 여러 줄 그리는 알고리즘은 없다. 둥근 캡 경로, 여러 색 경로 조각, radial/linear shader, 애니메이션되는 색상 가중치로 만든다.

따라서 “물결”을 사인파로 재현하는 것은 소스와 다르다. 역분석 근거만으로 재현한다면 먼저 **작은 마이크 버튼 + 상태에 따라 변하는 4색 둥근 링/글로우**를 구현하는 편이 가깝다. PULSE와 RING은 핸들러가 다른 대안이며 동시에 동일한 효과를 겹쳐 그리는 구조가 아니다.

## 1. UI 구성과 배치

### 1.1 마이크 버튼은 별도의 음성 전용 전체 화면 패널이 아니다

보이스 애니메이션의 공통 호스트는 다음 레이아웃이다.

- `resources/res/layout/layout_0x7f0e0710.xml`
  - 최상위 `FrameLayout`: `match_parent × match_parent`, 가운데 배치.
  - `id_0x7f0b0658`: 중앙 원형 배경.
  - `id_0x7f0b0653`: 애니메이션을 런타임에 끼워 넣는 컨테이너.
  - 중앙 ripple, 기본 마이크 이미지 `id_0x7f0b03be`, voice-animation 이미지 `id_0x7f0b03c3`, 링 이미지 `id_0x7f0b03c4`가 같은 중심에 포개진다. 두 애니메이션용 이미지 뷰는 초기 `GONE`이며 실제 표시 여부는 핸들러가 제어한다.
  - 중앙 배경·아이콘 크기는 테마 속성 `attr_0x7f0400a4`에 의존한다. 디컴파일된 테마 스타일에는 대략 32–36dp(일부 관련 스타일에서 34dp)가 있으나, 모든 기기에서 하나의 고정 크기로 볼 수 없다.
- `defpackage/kfx.java`: `R.layout.layout_0x7f0e0710`으로 음성 마이크 키를 구성하고, `animationType == 1`이면 `new khf()`(RING), 그 외에는 `new khc()`(PULSE)를 사용한다. 호출 경로 `defpackage/igr.java`는 regular-dictation 전용 조건(`ikj.e`)이 참일 때 `2`를 넘기고, 거짓일 때 `1`을 넘긴다. `ikj.e`는 client type이 `CLIENT_TYPE_REGULAR_DICTATION`인지와 flag로 설정되므로 **Agentic/Rambler client의 기본 분기는 RING(1)**, regular dictation은 PULSE(2)다. 실제 활성 실험/설정이 다르면 런타임 선택을 우선한다.

즉, 애니메이션 자체는 **키보드 상단의 작은 마이크 키/버튼 안**에 놓인다. `layout_0x7f0e0710`은 전체 키보드 레이아웃이 아니라 해당 마이크 키의 콘텐츠다.

Gboard의 Universal Dictation UI는 호스트도 선택한다. `defpackage/kgf.java`의 `b()`는 현재 accessory mode에 따라 `ptp.HEADER` 또는 `ptp.WIDGET`을 고르고, `kgl`은 HEADER 컨트롤러, `kgd`는 WIDGET 컨트롤러다. 따라서 모든 모드에 공통인 고정 폭·높이의 별도 “음성 입력 패널” 하나가 있다고 단정할 수 없다. 일반 음성 입력은 키보드 헤더의 음성 키/상태 표시로 동작할 수 있고, Rambler는 companion widget 경로의 UI를 사용한다.

- Rambler widget 예시: `resources/res/layout/layout_0x7f0e0631.xml`의 `RamblerAlWidgetSoftKeyboardView`와 `layout_0x7f0e0809.xml`의 `WidgetInnerHolderForAlRambler`. 이는 독립된 풀스크린 음성 패널이 아니라 widget 키보드/제안 strip 구조다.
- `kgf`의 헤더/위젯 구분은 런타임 모드에 의존한다. `ptp.HEADER`·`ptp.WIDGET` 중 실제 어느 쪽이 선택되는지는 키보드 모드 및 상태에 따라 달라질 수 있다.

### 1.2 링 모드의 마이크 내부

- `resources/res/layout/layout_0x7f0e05c4.xml`: 링 애니메이션 콘텐츠. `id_0x7f0b0654`의 원형 FrameLayout 내부에 흰 원형 배경, `id_0x7f0b0656`의 원형 outline, `id_0x7f0b0655`의 `AssistantP6GlowView`가 포개진다.
- `defpackage/khf.java`의 로깅 식별자는 `com/google/android/apps/inputmethod/libs/universaldictation/ui/micanimation/MicRingAnimationHandler`다. 공통 호스트의 `id_0x7f0b0653`에 `layout_0x7f0e05c4`를 넣고 `AssistantP6GlowView`의 bounds가 생긴 뒤 크기 기반 파라미터를 설정한다.
- Glow 뷰는 컨테이너를 채우고 바깥 margin은 `dimen_0x7f070712 = 2dp`. `khd.java`는 반지름을 `(viewWidth - 2dp) / 2`, 배경 글로우 두께를 `viewWidth * 0.3888889`로 설정한다.

### 1.3 버튼, 상태 텍스트, 멈춤/닫기/설정

마이크 애니메이션은 다른 음성 컨트롤과 한 줄에 배치된 고정 toolbar를 직접 그리지 않는다. 주변 버튼은 현재 HEADER/WIDGET의 soft-key/access-point 레이아웃이 배치한다.

- **일시중지**: `kgd.x()`는 widget soft key `id_0x7f0b2a32`에 `string_0x7f140873`(“Pause voice typing”) 설명을 설정한다. Header 쪽 `kgl.x()`도 `key_pos_header_power_key`에 stop/pause 기능을 등록한다. 모드/상태에 따라 header의 이 컨트롤은 “Stop voice typing”으로 설명된다.
- **닫기/종료**: `kgl.x()`는 `key_pos_header_access_points_menu`에 exit-dictation 액션을 추가하며 접근성 문자열 `string_0x7f141634`를 쓴다. 아이콘은 theme 조건에 따라 뒤로가기 화살표 또는 X다.
- **상태 안내**: `defpackage/kgm.java`가 초기화 때 “Speak now”, 듣기 시작 때 “Listening…”을 `kgf`에 전달한다. `defpackage/jsz.java`의 `StatusViewController`는 상태 텍스트를 keyboard의 `id_0x7f0b29f3`/text `id_0x7f0b2a05`에 표시한다. 뷰 정의는 `layout_0x7f0e07e8.xml` 및 `layout_0x7f0e07f6.xml`이며 텍스트는 중앙 정렬된다.
- **설정**: 위 마이크 원형/`layout_0x7f0e0710` 안에 별도 설정 톱니가 들어 있다는 근거는 없다. 음성 옵션/설정은 별도의 access point·voice options 흐름이다. 관련 접근성 문자열로 `string_0x7f141673` “More voice options”가 있다. 고정 설정 버튼의 위치·크기는 이 마이크 레이아웃에서 정해지지 않는다.
- **텍스트 dp**: 상태 TextView 크기는 `attr_0x7f04031d` 테마 속성이다. 관련 style 값에 16dp가 있으나, 실제 테마 variant에 따라 달라질 수 있다.

요구된 배치/크기 관점에서 확인 가능한 것은 **마이크 중심의 원형 key bounds 및 상대적 구조**다. 주변 멈춤·닫기·옵션 버튼의 x/y 좌표와 dp는 공통 mic layout에 고정돼 있지 않으며 현재 선택된 keyboard/header/widget 레이아웃과 theme에 의해 결정된다. 전체 화면이 특정 높이 dp의 음성 전용 패널로 바뀐다고 볼 근거는 없다.

## 2. 그리기 및 애니메이션 알고리즘

### 2.1 PULSE: 단색 원형 펄스

클래스 `com.google.android.libraries.inputmethod.widgets.VoiceSolidPulsatingAnimationView` (`sources/com/google/android/libraries/inputmethod/widgets/VoiceSolidPulsatingAnimationView.java`)는 `View`를 상속하고 직접 Canvas를 쓴다.

- `onDraw`: `canvas.drawCircle(width/2, height/2, radius, paint)` 한 개. `Paint`는 fill 기본값이며 `paint.setColor(i)`로 현재 단색을 사용한다.
- 핸들러: `defpackage/khc.java`, 로그 식별자 `.../MicPulseAnimationHandler`. `layout_0x7f0e05c3.xml`에 위 custom View를 inflate한다.
- View 속성의 기본값: `c = 100`, `d = false`, `e = true`. `khc.h(i)`에서 `i == -1`은 100으로 바꾸고 `c=i`로 둔다. 양수면 `a()`를 요청한다.
- theme radius endpoints는 `attr_0x7f04031b`(최솟값/시작값 `f`)와 `attr_0x7f04031c`(반대 endpoint `g`). `a()`의 최종 radius는 다음과 같다.

  `rTarget = f + (g - f) * c / 100`

  이어서 `f → rTarget` 250ms (`PathInterpolator(0,0,0,1)`), `rTarget → f` 200ms (`PathInterpolator(0.3,0,1,1)`) 순서로 애니메이션한다. 합계 450ms 후 listener가 다시 시작하므로 반복 주기는 약 450ms다. `d == true`면 새 pulse를 시작하지 않고, detach 시 현재 animator를 취소한다.
- 테마 리소스 예에는 endpoints가 18dp/14dp (`dimen_0x7f0709d1`, `dimen_0x7f0709d2`)인 변형과 16dp 등의 다른 변형이 있다. 실제 selected theme/style을 확인하지 않고 모든 화면에 같은 반지름이라고 단정하면 안 된다. 배치 뷰 bounds는 `attr_0x7f040248`에 의존하고 styles에서 36/38/40dp 변형이 확인된다.
- PULSE는 마이크 키를 숨기거나 overlay하는 **솔리드 원의 크기 변화**이며, path·사인파·4색 그라데이션을 그리지 않는다. `onDraw`에 RMS 계산도 없다.

### 2.2 RING: Google 4색 rounded-line/glow

실제 색상 애니메이션은 `com.google.android.libraries.search.p6glow.AssistantP6GlowView` (`sources/com/google/android/libraries/search/p6glow/AssistantP6GlowView.java`)의 `onDraw()`에 있다. 링 애니메이터/상태 머신은 `defpackage/upt.java` (`AssistantP6GlowAnimator`), 상태 enum은 `defpackage/upq.java`다.

#### 경로의 실제 형태 — 사인파가 아님

`AssistantP6GlowView.onDraw()`는 `Path D`가 비어 있을 때 shape enum을 읽어 경로를 만든다.

- `upv.LINE`: `(0,0)`에서 `(lineSize,0)`까지의 직선.
- `upv.ROUNDED_LINE`: `endAngle`을 `0.001°..180°`로 clamp하고 `endRadius`로 끝부분 원호 두 개를 만든다. 클래스의 한쪽 끝 좌표는 대략 `(-r,-2r)..(r,0)`이고 다른 쪽은 `lineSize`만큼 이동한 대응 원호다. 레이아웃은 `endAngle=180`과 `attr_0x7f040707=id_0x7f0b0071`(enum 값 1, `ROUNDED_LINE`)을 준다.
- 경로 segment buffer는 `Path[6]`이며 `colorWeights` 배열의 색상 stop을 기준으로 구간을 잘라 그린다. 색상 stroke loop는 인덱스 1–4, 즉 4개의 유색 구간이다. 투명 stop은 양 끝에 있다.
- 소스에 `sin()`/`cos()`가 등장하지만 x를 따라 진동하는 `y=A sin(kx+phase)`를 계산하는 데 쓰이지 않는다. 둥근 끝의 원호 및 radial shader 중심을 배치하는 데 쓰인다. `Paint.setPathEffect()`나 여러 개의 주기적 사인 곡선은 확인되지 않았다.

#### 실제 색상·두께·투명도

`resources/res/values/colors.xml`에서 `AssistantP6GlowView`가 참조하는 stop은 다음과 같다.

| 순서 | 리소스 | 값 |
|---|---|---|
| 시작 투명 | 상수 `0` | 투명 |
| 파랑 | `color_0x7f060067` | `#4285f4` |
| 빨강 | `color_0x7f060069` | `#ea4335` |
| 노랑 | `color_0x7f06006a` | `#f1bc29` |
| 초록 | `color_0x7f060068` | `#34a853` |
| 끝 투명 | 상수 `0` | 투명 |

순서가 Blue→Red→Yellow→Green이며, 임의의 Google brand color 추정값이 아니라 위 리소스 값이다. 내부 색 배열은 `{0, blue, red, yellow, green, 0}`. gradient line에는 stop을 중복해 연속 경계가 연결되고, path 구간을 칠할 때 loop `i=1; i<5; i++`로 4색을 사용한다.

- 레이아웃의 `app:attr_0x7f04076c="@dimen/dimen_0x7f070711"`; styleable 순서/생성자 매핑상 이 값은 `lightThickness`이며 실제 `2dp`다. `lineSize`는 별도 `attr_0x7f040770`이고 이 레이아웃에서는 직접 지정되지 않는다.
- `app:attr_0x7f040677="0.45"`; `AssistantP6GlowView` styleable의 `gradientGlowBackgroundOpacity`이며 background shader alpha를 계산할 때 적용된다. 배경 두께는 `khd.java`가 실제로 `viewWidth * 0.3888889`로 넣기 때문에 shader가 활성화된다.
- `app:attr_0x7f0405d5="180"`: `endAngle` 180도.
- `khd.java`: `endRadius=(width-2dp)/2`, background-glow thickness=`width*0.3888889`.
- `AssistantP6GlowView.onDraw()`의 넓은 background-glow stroke는 `gradientGlowBackgroundThickness`(view 너비의 `0.3888889`)로 그린다. 별도로 최종 colored path stroke는 `lightThickness=2dp`, alpha는 `255 * lightOpacity`이며 기본 light opacity는 `1.0`이다. `lineSize`는 styleable의 별도 속성(`attr_0x7f040770`)으로 이 layout에서 지정되지 않는다. 배경 glow shader만 opacity `.45`를 쓰므로 모든 픽셀/레이어가 동일한 0.45 alpha인 것은 아니다.
- 레이아웃 margin 2dp 및 view bounds 때문에 화면상 ring 크기는 mic button bounds에 종속된다. 모든 화면 공통의 고정 px/전체 너비가 아니다.

`AssistantP6GlowView`의 custom styleable은 `defpackage/uqh.java`에 선언되어 있고 속성 읽기와 default는 `AssistantP6GlowView.java` 생성자에서 확인된다. 레이아웃에서 지정하지 않은 alpha·responsive 옵션은 생성자 기본값이므로 style override에 따라 달라질 수 있다.

#### 위상/시간 및 상태

상태 이름은 `defpackage/upq.java`의 `GONE`, `LISTENING`, `USER_INPUT`, `THINKING`이다. 고정 사인파 phase가 도는 모델이 아니라 6개 stop/구간의 비중 및 shader 좌표를 animator가 갱신한다.

- 기본 분포 `upy.f` (`defpackage/upy.java`)는 `{0.5,0,0,0,0,0.5}`. 초기에는 양끝 투명 비중이 있고 4개 색 중간 stop의 비중은 0이다.
- `LISTENING` 전환은 `defpackage/uql.java`에서 stop weight를 재배치한다. 코드에서 transition duration으로 450ms 및 600ms가 선언돼 있고 `uqe`의 GONE/THINKING fade/exit duration은 각각 350ms/367ms다. 실제 현재 프레임은 이전 상태와 feature/theme에 따라 달라진다.
- `USER_INPUT`일 때 responsive animator가 켜져 있으면 `defpackage/uqk.java` 경로를 쓴다. frame scheduling interval은 `upt`에서 listening 14ms, user-input 12ms, thinking 8ms다. responsive audio 단계 duration은 `200 + 50*(magnitude/70)` ms, 즉 level에 따라 200–250ms.
- responsive animator가 꺼진 USER_INPUT 경로(`defpackage/uqm.java`)는 매개변수화한 비색상 shape 비중에서 무작위 색 segment를 골라 1→3으로 강조한 뒤 되돌린다. segment 이동은 300ms Decelerate, 마지막 대기/정리 구간은 100ms다. 한 묶음에 1–3개 강조가 선택될 수 있다.
- `THINKING` helper `defpackage/uqg.java`에는 400ms, 1080ms, 4000ms, 5000ms duration 상수가 있고 animator keyframe에서 shape 분포가 바뀐다. 이 값들은 “고정 사인파 주기”가 아니다. 애니메이션 경로와 이전 상태에 따라 transition 및 반복 시퀀스에 쓰이며, 단일 통합 주기로 환산할 수 없다.
- GONE/exit에서는 P6 view alpha 및 color weight를 transition한다. `AssistantP6GlowView`는 `setLayerType(2, null)`로 attached 시 software layer를 사용한다.

불확실성/구현 한계: 속성값 중 상당수는 현재 적용 theme 및 feature flag에서 결정된다. 위 숫자는 소스 기본값, `layout_0x7f0e05c4.xml`의 명시값, 또는 클래스에 박힌 상수로 구분해 적었다. 전체 렌더링 결과가 다르면 `AssistantP6GlowView`의 실제 theme attrs와 런타임 `colorWeights`를 우선 확인해야 한다.

### 2.3 오디오 레벨 반영: RMS는 확인되지 않음

이 APK 소스에서 확인되는 mic-ring 오디오 경로는 normalized integer level 기반이며, RMS 샘플을 직접 계산하는 코드는 찾지 못했다.

- `defpackage/khf.java`의 `h(int)`은 0–100 범위의 입력을 링 버퍼 `khe.a[4]`에 보관한다. 0이 아닌 입력은 최근값에 기록하고 cursor는 `& 3`으로 순환한다. 500ms 동안 추가 입력이 없으면 `defpackage/tx.java`의 reset runnable이 네 슬롯을 0으로 만든다.
- `khf.i()`는 최근 4개 슬롯 중 **하나라도 `>=70`인지** 검사한다. 해당하면 `USER_INPUT`, 아니면 `LISTENING`으로 바꾼다. 그러므로 이 handler 상태 전환은 4-sample max threshold에 가깝고 RMS 평균이 아니다.
- `upt`의 responsive 경로는 level 범위 0–100을 검증한다. 큰 값으로부터 magnitude를 `100-level`로 만들고 70에서 제한한다. `ratio=magnitude/70`; `interpolation=PathInterpolator(0,.2,.47,.95).getInterpolation(ratio)*11.111111 + 13.888889`를 써서 색 weight target을 약 13.888889–25 범위로 이동한다. 선택한 컬러 lane은 `(level/100)*7 + 2`, 즉 2–9의 비중으로 두고 다른 3 lane은 1로 둔다. 이 값은 glow shape/weight 변경이지 sine amplitude 계산식이 아니다.
- `khf` 경로에서는 500ms inactivity 후 reset하므로 목소리가 멎을 때 강조 상태가 짧은 지연 뒤 풀린다.
- `VoiceSolidPulsatingAnimationView`도 percentage `c`에 따라 radius를 보간하지만 소스 내에서 RMS 계산은 하지 않는다.

오디오 레벨의 원천으로 인식기 측 `onAudioLevelUpdate` callback 코드가 존재한다(예: `defpackage/abfw.java`의 ASR callback `l(int)`은 level 정수를 proto에 넣는다). 하지만 해당 callback부터 모든 mic-ring UI callback까지 하나의 완전한 직접 호출 체인은 JADX 결과만으로 연결하지 못했으며, level의 RMS/peak 원본 산식도 이 자료에서 확인 불가다. 따라서 재현 코드는 앱 쪽에서 구한 RMS/peak를 0–100으로 정규화할 수 있지만, 그것을 “Gboard가 RMS를 쓴다”고 주장하면 안 된다.

## 3. 한국어/영어 문자열 및 drawable

### 문자열 리소스

| ID | 영어 (`resources/res/values/strings.xml`) | 한국어 (`resources/res/values-ko/strings.xml`) | UI 의미 |
|---|---|---|---|
| `string_0x7f141652` | `Initializing…` | `초기화 중…` | 시작/초기화 |
| `string_0x7f141653` | `Listening…` | `듣는 중…` | 수신 중 |
| `string_0x7f141654` | `Speak now` | `지금 말하세요` | 발화 안내 |
| `string_0x7f14029c` | `Paused` | `일시중지됨` | 일시중지 상태 |
| `string_0x7f140873` | `Pause voice typing` | `음성 입력 일시중지` | widget pause 컨트롤 접근성 |
| `string_0x7f140d74` | `Stop voice typing` | `음성 입력 중지` | header stop 컨트롤 접근성 |
| `string_0x7f141634` | `Exit dictation mode` | `음성기록 모드 종료` | dictation 종료 접근성 |
| `string_0x7f141673` | `More voice options` | `음성 옵션 더보기` | 음성 옵션 entry 접근성 |
| `string_0x7f14165b` | `Minimize keyboard` | `키보드 최소화` | 키보드 최소화 action |

요청에서 예시로 든 “말씀하세요”는 이 리소스 파일의 정확한 표기가 아니다. 실제 확인된 문자열은 **“지금 말하세요”**, **“듣는 중…”**이다. 음성 상태 표시를 호출하는 코드는 `defpackage/kgm.java`이며 상태 TextView를 연결하는 쪽은 `defpackage/jsz.java`다.

### 아이콘 drawable

- **마이크**: `resources/res/drawable/drawable_0x7f08059f.xml`은 `drawable_0x7f080483` alias (`resources/res/values/drawables.xml`). 벡터 크기 24dp, viewport 960×960, tint `?attr/attr_0x7f040498`. `drawable_0x7f080483.xml` pathData:

  ```text
  M395,525q-35-35-35-85V200q0-50 35-85t85-35t85,35t35,85V440q0,50-35,85t-85,35t-85-35Zm45,275V717q-92-13-157.5-78T203,481q-2-17 9-29t28-12t28.5,11.5T284,480q14,70 69.5,115T480,640q72,0 127-45.5T676,480q4-17 15.5-28.5T720,440t28,12t9,29q-14,91-79,157T520,717v83q0,17-11.5,28.5T480,840t-28.5-11.5T440,800Z
  ```

- **마이크 원 배경**: `resources/res/drawable/drawable_0x7f08028e.xml` — white oval shape.
- **터치 ripple/focus**: `resources/res/drawable/drawable_0x7f080292.xml` — `FocusRingDrawable` + ripple mask.
- **링 outline**: `resources/res/drawable/drawable_0x7f080730.xml` — 투명 oval, 흰색 stroke, `dimen_0x7f070712=2dp`.
- **음성 종료/back**: `resources/res/drawable/drawable_0x7f08067b.xml`, 24dp vector, viewport 24×24:

  ```text
  M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z
  ```

- **닫기/X**: `resources/res/drawable/drawable_0x7f08068a.xml`, 24dp vector, viewport 24×24:

  ```text
  M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12 19,6.41z
  ```

음성 pause는 `SoftKeyView`/keyboard mode의 action icon이 제공한다. `string_0x7f140873`은 accessibility label이고, mic animation host에 pause vector가 직접 포함된 구조는 아니다.

### Lottie 여부

마이크 PULSE와 RING은 각각 Java Canvas View 및 `AssistantP6GlowView`에서 그린다. **마이크 물결/링을 위한 Lottie JSON은 확인되지 않았다.** voice 관련 Lottie로 검색되는 `resources/res/raw/raw_0x7f130078.json`은 `defpackage/kfx.java`가 `layout_0x7f0e0046`의 언어 표시 chip에 연결한다. JSON metadata는 `fr=60`, `ip=0`, `op=196`, 23×15, name `Chip 3_Circular_0404`다. 이는 언어 전환 chip 애니메이션이지 마이크 wave가 아니다.

## 4. 순수 Android View 재현 의사코드

아래는 source-derived 구조를 작은 독립 `View`에서 재현하기 위한 의사코드다. 실제 Gboard 전체 transition state machine을 복제하는 코드는 아니며 RMS 산식도 일부러 가정하지 않는다.

```java
// mode=PULSE: VoiceSolidPulsatingAnimationView 동작
void startPulse(float themeStartDp, float themeEndDp, int percent) {
    float start = dp(themeStartDp);
    float target = start + (dp(themeEndDp) - start) * clamp(percent, 0, 100) / 100f;
    animateRadius(start, target, 250, cubic(0, 0, 0, 1));
    animateRadius(target, start, 200, cubic(.3f, 0, 1, 1));
    // 끝나면 반복; View가 detach되면 취소
}

void onDrawPulse(Canvas c) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(pulseColor); // theme-provided solid color
    c.drawCircle(getWidth()/2f, getHeight()/2f, animatedRadiusPx, paint);
}

// mode=RING: P6 rounded-line/glow의 핵심 형태
void onDrawRing(Canvas c) {
    float w = getWidth(), h = getHeight();
    float r = (w - dp(2)) / 2f;               // khd.java
    float backgroundThickness = w * .3888889f;
    float centerY = h / 2f + r;
    float angle = radians(clamp(endAngleDeg, .001f, 180f));

    Path base = new Path();
    // source의 두 끝 원호; 이 경로는 사인 곡선이 아니라 rounded line
    base.addArc(-r, -2*r, r, 0, degrees(angle)+90, -degrees(angle));
    base.arcTo(w-r, -2*r, w+r, 0, 90, -degrees(angle), false);

    // colorWeights[6]는 끝 투명 stop + 4색 + 끝 투명 stop.
    // PathMeasure로 base를 weights 비율에 따라 6개 조각으로 분할.
    Path[] segment = splitByWeights(base, colorWeights);
    int[] color = {TRANSPARENT, BLUE_4285F4, RED_EA4335,
                   YELLOW_F1BC29, GREEN_34A853, TRANSPARENT};

    // 백그라운드 halo는 source처럼 여러 Radial/LinearGradient를 조합.
    // 반경/중심은 rounded end에서 sin/cos로 계산하되 수평 사인파는 만들지 않는다.
    drawRadialEndGlows(c, base, r, angle, backgroundThickness,
                       alpha = .45f, colors = color);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(2));              // layout attr_0x7f04076c
    paint.setAntiAlias(true);
    for (int i = 1; i <= 4; i++) {             // 네 유색 경로 조각
        paint.setColor(color[i]);
        paint.setAlpha(round(255 * lightOpacity));
        c.drawPath(segment[i], paint);
    }

    // state animator는 LISTENING / USER_INPUT / THINKING에 따라
    // colorWeights 및 glow shader 값을 보간한 뒤 invalidate를 요청한다.
}

// audio input이 있다면 앱이 측정한 값(예: RMS)을 0..100으로 정규화해 전달.
// Gboard의 공개된 이 코드 조각은 RMS 산식을 계산하지 않으며,
// 링 상태 판정은 최근 4 level 중 하나가 >=70인지 확인한다.
void onAudioLevel(int level0to100) {
    recentLevels.add(clamp(level0to100, 0, 100));
    state = recentLevels.any(v -> v >= 70) ? USER_INPUT : LISTENING;
    if (noLevelFor(500)) recentLevels.clear();
}
```

`drawRadialEndGlows()`/`splitByWeights()`는 Android `PathMeasure`, `RadialGradient`, `LinearGradient`, `ComposeShader`로 작성해야 한다. 위 의사코드는 형태·수치를 옮긴 요약이므로 pixel-perfect 결과가 필요하면 아래 P6 source method를 기준으로 stop 위치/gradient shader를 추가 구현한다.

## 5. 핵심 근거 파일

| 경로 | 확인 내용 |
|---|---|
| `sources/defpackage/kfx.java`, `sources/defpackage/igr.java`, `sources/defpackage/ikj.java`, `sources/defpackage/kqp.java` | pulse/ring handler 선택 및 dictation client type 분기 |
| `sources/defpackage/khc.java`, `sources/defpackage/tai.java` | `MicPulseAnimationHandler`, pulse visibility, amplitude-percent 전달 |
| `sources/com/google/android/libraries/inputmethod/widgets/VoiceSolidPulsatingAnimationView.java` | Canvas 원, radius endpoint 수식, 250/200ms animator |
| `resources/res/layout/layout_0x7f0e0710.xml` | 공통 중앙 mic host 및 겹쳐진 UI view들 |
| `resources/res/layout/layout_0x7f0e05c3.xml` | pulse 원형 View bounds |
| `sources/defpackage/khf.java`, `sources/defpackage/khd.java` | `MicRingAnimationHandler`, audio threshold, 500ms reset, bounds 기반 반지름 |
| `resources/res/layout/layout_0x7f0e05c4.xml` | Assistant P6 Glow ring layout 및 180°/0.45/rounded-line/2dp 값 |
| `sources/com/google/android/libraries/search/p6glow/AssistantP6GlowView.java` | 경로 생성, 4색 stop, gradients, Canvas draw |
| `sources/defpackage/upt.java`, `upq.java`, `uql.java`, `uqe.java`, `uqg.java`, `uqk.java`, `uqm.java`, `upy.java` | 상태, interpolation, transition/audio animation constants |
| `resources/res/values/colors.xml`, `dimens.xml`, `styles.xml`, `drawables.xml` | Google 4색, 크기/두께, mic vector alias |
| `resources/res/drawable/drawable_0x7f080483.xml` | microphone vector pathData |
| `resources/res/values/strings.xml`, `values-ko/strings.xml`; `sources/defpackage/kgm.java`, `jsz.java` | 상태 문자열 및 status text host |
| `resources/res/raw/raw_0x7f130078.json` | 언어 indicator Lottie; mic wave 애니메이션이 아님 |
