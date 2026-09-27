# HoneyBoard 5.9.40.75: Rambler Bee 후크 설계

대상: `com.samsung.android.honeyboard` 5.9.40.75 (versionCode 594075300), libxposed API 102. 아래는 **정적 디컴파일 근거**다. 실제 기기에서 후크·리소스·One UI 테마를 검증한 결과가 아니며, 버전이 바뀌면 난독화 심볼을 다시 확인해야 한다. 경로의 `sources/p702z6`, `sources/p368na`, `sources/p047bg` 등은 jadx가 이름 충돌을 회피하며 붙인 디렉터리/표기다. **런타임 `Class.forName`에는 APK의 원래 패키지명 `z6`, `na`, `bg`를 사용**한다(DEX/리플렉션으로 재확인). 이 문서에서 `z6.c`와 `na.b`를 쓰지만 근거 경로는 각각 `p702z6/c.java`, `p368na/b.java`다. 삼성의 `R` 정수는 대상 버전의 `com.samsung.android.honeyboard.R`을 참조하거나 런타임 `Resources.getIdentifier(name,"id|attr", "com.samsung.android.honeyboard")`로 찾는다. 모듈의 `R`과 섞지 않는다.

## 1. 등록 지점: Proxy를 받아들이는 queen 목록

**권장:** `com.samsung.android.honeyboard.bee.b` (PluginQueenBee)의 `f22240d: androidx.databinding.ObservableArrayList<z6.c>`에 **하나의** Java `Proxy`를 추가한다. `b(Context,Eb.c)` 생성자 종료 후 이 리스트는 `OnListChangedCallback`이 등록돼 있다. `b.e(): java.util.List`가 바로 이 리스트를 반환한다. `X9.e(C7.d,F6.F,W8.b,z6.p...)`는 built-in `com.samsung.android.honeyboard.bee.c` 및 plugin `b`를 queen들(`f13945n`)로 보관한다. `X9.e.r(): void`는 각 queen의 `e()`를 합쳐 목록을 초기화하고 `BeeItem`을 만든다. **`X9.e.r()` 이전**에 add하는 것이 가장 안전하다. 후속 add도 콜백의 `onItemRangeInserted` → `K.a(z6.c)` → `K.A(String)` → `K.B(boolean)`으로 반영된다. UI/리스트 변경은 메인 스레드에서 하고, 동일 ID 재삽입 방지 및 `onDestroy`에서 자기 항목 제거를 고려한다. 근거: `sources/com/samsung/android/honeyboard/bee/b.java` (생성자, `c(z6.c)`, `e()`, list callback), `sources/Eb/b.java` (`onCreate()`에서 두 queen 제공), `sources/X9/e.java` (`r()`), `sources/com/samsung/android/honeyboard/beehive/viewmodel/K.java`.

**중요한 반례:** built-in queen `com.samsung.android.honeyboard.bee.c.c(z6.a)`는 인자가 **추상 클래스** `z6.a`이고 `na.b.f(...preset...)`로 ID를 검사한다. `Proxy`는 `z6.a`가 아니므로 이 메서드에 넣을 수 없다. plugin queen의 `c(z6.c)`는 `isSearchableOnly()`/`isExpressibleOnly()`가 참일 때 거르므로 둘 다 false로 한다. 직접 `f22240d.add(proxy)`도 가능하다. `B6.d` 전용 로직(`K.a`, plugin callback의 `instanceof`)은 Proxy에 해당하지 않아 실행되지 않는다. 그 결과 `isNew()`만으로 plugin의 `initialFirst` 자동 우선순위를 얻지 못한다. 반대로 BeeItem 생성 자체는 `z6.c`를 받으므로 **Proxy 가능**, `z6.a`를 반드시 상속할 필요는 없다. `X9.e.r()`의 `aVar.k()` 분기는 미등록 ID의 비-preset Bee를 기록만 하고 이후 처리하며, 기기 정책에 따른 UI 차이는 실기 확인 필요.

### `z6.c` 전체 계약

실제 인터페이스 선언은 `sources/p702z6/c.java`, 기본 구현 값은 `sources/p702z6/a.java`와 그 상속 클래스다. 인터페이스 jadx 선언은 `isSleep():boolean`, `z6.a`/`BeeItem` 디컴파일 본문은 `getIsSleep()`로 재명명된다(`renamed from: isSleep` 주석). **DEX의 원래 메서드명은 `isSleep`일 가능성이 높다.** Proxy는 `Method.getName()`에서 `isSleep`/`getIsSleep` 양쪽을 허용하고, 후크에서는 `Class.getDeclaredMethods()`로 실제 이름을 검증할 것. `execute(kotlin.jvm.functions.Function0)`은 JVM에서 `void`; `Function0`의 제네릭 반환은 Kotlin Unit. `Object` 기본 메서드 `equals/hashCode/toString`도 InvocationHandler에서 처리하여 Proxy를 컬렉션에 안전하게 넣는다.

| 메서드(인자 타입) → 반환 | `z6.a` 기본값 / 통상 값 | Rambler Proxy 지침 |
|---|---|---|
| `dump(android.util.Printer)` → `void` | 덤프 유틸 호출 | 안전한 ID 출력 또는 no-op |
| `execute()` → `void` | `setNew(false)` | **필수**: 녹음/패널 토글, new=false |
| `execute(kotlin.jvm.functions.Function0)` → `void` | new=false; 기본 구현은 콜백 실행 안 함 | 콜백 의존하지 말고 `execute()`로 위임(필요 시 Function0.invoke) |
| `finish()` → `void` | 클래스별 구현 (`z6.a` 본문에 없음; 확장 구현/디컴파일 확인 필요) | **필수**: 다른 Bee 실행 시 패널·녹음 종료 |
| `getBeeFlags()` → `int` | `1`; `256`=system, `512`=privilege, `1024`=external service 플래그 판정 | `1`; system/privilege 우회 금지 |
| `getBeeId()` → `java.lang.String` | 항목별 고유 ID | **필수**: 예 `rambler_voice`, 고정·중복 금지 |
| `getBeeInfo()` → `z6.j` | 각 Bee의 `z6.j` | **필수**: 유효 아이콘·라벨 |
| `getBeeVisibility()` → `int` | 초기 0; 0=표시, 1=제거/숨김, 2=비활성 또는 정책 표시 | **필수**: 보통 0; 서비스 없으면 2 고려 |
| `getCallback()` → `z6.b` | 보관한 callback | 받은 객체 반환 |
| `getPinBeePriority()` → `int` | `Integer.MAX_VALUE` | 그대로 |
| `getSearchSuggestionType()` → `int` | 0 | 그대로 |
| `invalidate()` → `void` | `callback.e()` | 상태 변경 시 callback.e() 호출 |
| `isAsyncBadgeBee()` → `boolean` | false | false |
| `isBeeSkipFinish()` → `boolean` | false (삼성 voice는 true) | false 또는 패널 전환 UX에 따라 결정 |
| `isDead()` → `boolean` | false | false |
| `isExistingPrivacyPolicy()` → `boolean` | false | false; 별도 동의 UX가 있으면 재검토 |
| `isExpressibleOnly()` → `boolean` | false | **false** (plugin queen이 필터) |
| `isExternalServiceExecuting()` → `boolean` | `(flags & 1024)!=0` | false; true면 다른 Bee `finish()`를 건너뛰는 경로 있음 |
| `isFixedBee()` → `boolean` | false | **false**, 편집 허용 |
| `isNew()` → `boolean` | 보관된 boolean, 초기 false | 보관·setNew 반영; 자동 primary 목적이면 false |
| `isPinBee()` → `boolean` | false | **false**, 사용자가 이동/제거 가능 |
| `isPpAccepted()` → `boolean` | true | true (개인정보 동의 요구 시 별도 설계) |
| `isPrivilege()` → `boolean` | `(flags & 512)!=0` | false |
| `isSearchSuggestAvailable()` → `boolean` | false | false |
| `isSearchable()` → `boolean` | false | false |
| `isSearchableOnly()` → `boolean` | false | **false** |
| `isSelected()` → `boolean` | false, 토글형은 상태 조회 | **필수**: 패널 표시 여부; 선택 색·finish 동작 연동 |
| `isSleep()` (jadx 본문 `getIsSleep()`) → `boolean` | 보관된 sleep boolean | 보관·setSleep 반영 |
| `isStealthBee()` → `boolean` | false | false |
| `isSystem()` → `boolean` | `(flags & 256)!=0` | false |
| `isTailBee()` → `boolean` | false | false |
| `isUsingExpandView()` → `boolean` | false | false |
| `isUsingNetwork()` → `boolean` | false (삼성 voice는 네트워크일 때 true) | 실제 백엔드가 온라인이면 true; 삼성 네트워크 경고 UX 확인 |
| `needKeepContextMenu()` → `boolean` | false | false |
| `needLabel()` → `boolean` | true | true (more/swarm 라벨) |
| `needRefreshVisibility()` → `boolean` | false | false 또는 동적 상태에 맞게 |
| `onAppPrivateCommandReceived()` → `void` | no-op | no-op |
| `onDestroy()` → `void` | no-op | 녹음·스레드/뷰 정리 |
| `prepareToExecute()` → `void` | no-op | no-op |
| `renew(boolean)` → `void` | setNew(z); callback.m() | 저장 및 callback.m() |
| `setCallback(z6.b)` → `void` | 저장 | **필수**: BeeItem 생성자가 설정 |
| `setNew(boolean)` → `void` | 저장 | 저장 |
| `setSleep(boolean)` → `void` | 저장 | 저장 |
| `updateBadge()` → `void` | no-op | no-op |
| `updateBeeVisibility()` → `void` | 구현별 (기본값은 디컴파일에서 별도 확인) | 필요 시 상태 갱신 후 invalidate |

`z6.b`는 `void e(), i(), j(), m()` 인터페이스. BeeItem이 `new Os.e(this,11)`로 callback을 설치한다. 인터페이스의 default가 JVM에서 어떻게 합성됐는지에 상관없이 **모든 메서드를 디스패치**해야 한다. 위에서 기본 본문이 보이지 않는 `finish`, `updateBeeVisibility` 등은 `z6.a`의 Kotlin DefaultImpls/후속 상속 구현 가능성이 있어 proxy가 no-op을 명시적으로 제공한다. 근거: `sources/p702z6/{c,a,b}.java`, `sources/com/samsung/android/honeyboard/beehive/data/BeeItem.java`.

## 2. 기본 primary 배치와 사용자 편집 영속화

`na.b`는 현재 설정을 `f32417d: na.a`에 보관한다. `na.c`는 preset `f32418a: ia.a`, user `f32419b: ia.c`, 선택된 `f32421d: ia.b`를 가리킨다. preset `ia.a`: `f29400a: ArrayList<BeeTag>`, `f29401b: int` (primary count), `f29402c: int` (sleep count). `BeeTag(String id, boolean isNew)`은 ID/새 배지일 뿐 **위치 정보는 목록 순서와 primary/sleep 개수**다. `na.b.d():List`, `h():int`=현재 primary 수, `l():int`=최대(일반 preset 7, 일부 8/4), `n(String):int`=rank(없으면 -1), `j(List,int,int):void`=저장. `ia.c`는 main/sub 각각 SharedPreferences `bee_user_set_main|sub`, `..._primary_count`, `..._sleeping_bee_count`를 `;` 구분 Gson BeeTag JSON으로 보관. 근거: `sources/p368na/{a,b,c}.java`, `sources/p239ia/{a,c}.java`, `sources/F0/a.java:o(ia.b,String)`, `sources/com/samsung/android/honeyboard/common/beehive/BeeTag.java`, preset 생성 `sources/p009a7/q.java`.

**실행 순서:** (1) queen의 Proxy를 등록, (2) `X9.e.r()` 내부의 `na.b.initialize():void`가 **현재 세트**를 결정한 직후에 설정을 보정, (3) 계속 `r()`이 `getBeeId`별 `n()`을 계산해 `BeeItem`으로 변환. 편의를 위해 `na.b.initialize()` after-hook에서 `f32417d`의 `d()`/`h()`/`c()`를 보고 현재 main/sub 세트에 작업하되, 훅이 다른 호출 경로에서도 실행되니 동일 ID 중복·재진입 방지. `X9.e.r()` 직전에만 처리하려면 `X9.e.f13942k: K` → `K.f22399a: na.a`를 찾고 `r()`의 `initialize()` 이후에 호출되는 `n()`/목록 처리 지점을 따로 후킹해야 한다. preset 자체를 초기 생성시 바꾸는 대안은 `ia.a` 목록 빌더(`sources/p009a7/q.java`, 기기 플래그별 가지가 많음)를 후킹하는 것으로 유지보수 부담이 크다.

**처음 설치(저장된 user set 없음):** 선택된 preset `ia.a.f29400a`에서 앞의 `f29401b` 슬롯 중 원하는 위치(예: 번역 뒤)에 `new BeeTag("rambler_voice",false)`를 삽입. primary 수는 **기존 count를 유지**하고 밀려난 마지막 primary가 swarm으로 가도록 하면 최대 정원(7/8/4)을 넘지 않는다. 빈 슬롯이면 count를 `min(old+1,l())`로 늘려도 된다. `edit_toolbar` 뒤에 넣지 말 것. preset의 원형을 바꾸면 내장 queen의 `c(z6.a)`와 `na.c.f(id)` 정책도 ID를 인식하지만 이후 앱 reset 동작이 바뀔 수 있다. preset 수정은 **런타임 메모리에서만**, 기존 preset 항목 삭제/전체 reset은 하지 않는다.

**기존 사용자 세트:** `na.c.f32421d`가 `ia.c`면 현재 user `f29407d`에 처음 한 번만 새 ID를 primary 인덱스로 삽입하고 primary count가 정원일 때 기존 마지막 primary가 swarm으로 이동하도록 count를 유지한다. `na.c.j(List<BeeTag>,int primaryCount,int sleepingCount)` 또는 `na.b.j(...)`로 저장해 다음 입력에도 유지한다. **한 번 삽입한 이력은 모듈 자신의 설정에 별도 기록**한다. ID 부재만을 근거로 매번 재삽입하면 사용자가 편집에서 뺀 Bee가 돌아온다. 사용자가 명시적으로 제거한 후에는 다시 강제하지 않는다. 전체 세트 재설정 때 다시 추가할지 정책을 명확히 선택한다. 기존 설정에 대한 자동 밀어내기는 사용자 커스텀 툴바를 바꾸므로 반드시 필요성/UX를 시험한다. 대안: 커스텀 세트가 있으면 swarm에만 한 번 추가하고 사용자 선택을 우선(그러나 ‘설치만 하면 primary’ 요구는 충족하지 않음).

`X9.e.r()`은 저장 rank의 primary 수만큼 정렬된 `BeeItem`을 `K.f22418t: BeeObservableArrayList`에 놓고, 나머지를 `K.f22412n: N`(swarm), `K.f22413o: N`(sleep)로 나눈다. `K.a(z6.c)`의 동적 추가는 ID가 preset rank 없으면 빈 자리만 채워 **가득 찬 primary를 강제로 바꾸지 않는다**. preset rank가 primary 안에 있으면 `K.c(z6.c,int)`로 삽입하고 정원 넘으면 마지막 non-pin Bee를 swarm으로 보낸다. `K.v(String reason,boolean):void`, `K.w():void`가 UI의 현재 primary/swarm/sleep 순서를 `BeeTag`로 저장하며 `X9.e.onFinishInputView(boolean)`에서 `K.w()` 호출. 툴바 편집은 `K.f22421w: H` 및 `K.p/q/r/s/t(BeeItem,...)` 경로; `isFixedBee=false`, `isPinBee=false`여야 드래그·제거 가능. `getBeeVisibility()`가 1이면 `K.A`에서 항목을 삭제하므로 숨기고 싶지 않으면 0을 반환한다. 근거: `sources/X9/e.java:r(),onFinishInputView(boolean)`, `sources/com/samsung/android/honeyboard/beehive/viewmodel/{K,H,N}.java`, `sources/com/samsung/android/honeyboard/beehive/data/BeeItem.java`.

## 3. 아이콘, 라벨, 틴트

`z6.i` 생성자: `i(android.content.Context, android.graphics.drawable.Icon, int labelResId, android.content.res.Resources targetResources, java.lang.String targetPackageName)`와 synthetic 3인자 `i(Context,Icon,int)` 및 `i(Context,int iconRes,int labelResId)`; 이어 `new z6.j(z6.i)`로 BeeInfo 생성. `i.f38804b:Icon`(기본), `f38805c:int`(label), `f38806d:Resources`, `f38807e:String`(resource package), `f38809g:int`(description override), `f38810h:String`(description fallback), `f38808f:String`(별도 영어/검색 label override), `f38811i:boolean`(특수 아이콘 틴트 분기), `f38813k/f38814l/f38815m:Icon`(대체 아이콘). `j.a():String`은 description: `f38809g` > `f38810h` > `f38805c`; `j.e():String`은 **label resId** `f38805c`에서 `context.getResources().getString()` 또는 `f38828l`일 때 targetResources 사용; `j.b():String`은 `f38808f`가 있으면 그 문자열, 없으면 label 리소스를 영어 locale로 조회. 즉 `f38810h` 문자열만 넣어서는 UI 라벨 `j.e()`를 대체하지 못한다. **모듈 자체 string 리소스 ID**를 쓰고 모듈 context/resources를 5인자 생성자의 targetResources/targetPackageName으로 전달하며 `i.f38816n=true`(j.e()가 targetResources 사용)로 만드는 방식을 검증하거나, 대상 앱 문자열 `R.string.toolbar_voice_input`을 재사용 + accessibility description `f38810h` 설정. 다른 패키지의 string ID를 HoneyBoard `Context.getResources()`에 그대로 넘기면 `NotFoundException`이 날 수 있다. targetResources 경로도 layout binding이 `j.e()`를 호출하는지 기기에서 확인. 문자열만 지원하는 public 생성자는 **없다**. 아이콘은 `Icon.createWithResource(moduleContext,moduleDrawableId)` 또는 `Icon.createWithBitmap(...)`; `j.d(Icon,Drawable)`가 외부 패키지일 경우 `icon.loadDrawable(context)` 경로를 탄다. 접근 가능한 모듈 리소스/bitmap 확인이 필요하다. 근거: `sources/p702z6/{i,j}.java`, `sources/p426p6/e.java` (j.b fallback), `sources/p047bg/c.java:j()`.

`BeeItemLayout` 아이콘은 `com.samsung.android.honeyboard.beehive.viewmodel.C0623q`에서 기본적으로 `PorterDuffColorFilter(color, SRC_IN)`로 틴트. `z6.i.f38811i=false`가 일반 단색 아이콘에 적절하며 true는 alpha 조정/원색 유지 분기. 색 결정은 `com.samsung.android.honeyboard.beehive.viewmodel.t.d(Context,int,boolean,boolean,boolean):int`에서 테마 속성 `bee_icon_enable_tint`, `bee_icon_select_tint`, `edit_bee_icon_tint`를 읽는다. 라벨에도 같은 색 적용. 어두운/밝은 테마별 단색 alpha 아이콘을 제공하고 선택 상태 `isSelected()`+`invalidate()`를 사용한다. 근거: `sources/com/samsung/android/honeyboard/beehive/viewmodel/{C0623q,t}.java`, `resources/res/values/attrs.xml`.

## 4. execute()에서 음성 패널을 어디에 붙이나

`HoneyBoardService.onCreateInputView():android.view.View`는 **기존** `private View viewHolderLayout`을 반환한다. `Dj.j`의 서비스 초기화에서 `R.layout.layout_honeyboard`를 inflate하고 `viewHolderLayout=inputView; setInputView(inputView)` 한다. 루트는 `com.samsung.android.honeyboard.keyboard.view.HoneyBoardLayout extends LinearLayout` (`@id/layout_honeyboard`). `@id/layout_honeyboard_inner` 아래 `@id/layout_honey_frame: FrameLayout` → `@id/layout_base_frame_root: FrameLayout` → `@id/layout_base_frame_wrapper: LinearLayout`; 이 안에 `@id/layout_header`(Bee/후보 헤더)와 `@id/layout_body_wrapper: FrameLayout` → `@id/layout_body: FrameLayout` → `@id/layout_board_frame`/`@id/layout_board`(실제 보드), `@id/layout_body_overlay`(LinearLayout)가 있다. 바깥 `@id/layout_overlay`는 표현/확장 영역 전체를 포함하므로 Rambler 전용 영역으로 전유하지 않는다. `Zh.d(View)`가 주요 ID를 찾아 `f15529g`(body), `f15530h`(body wrapper), `f15532j`(전체 overlay) 등에 보관한다. 근거: `sources/Dj/j.java`, `sources/com/samsung/android/honeyboard/service/HoneyBoardService.java`, `sources/Zh/d.java`, `resources/res/layout/{layout_honeyboard,layout_base_frame}.xml`.

**음성 헤더:** `layout_header` 아래 `layout_toolbar`의 표시 중인 `beehive_primary_constraint_layout`/`beehive_primary_container`(클래스 `com.samsung.android.honeyboard.beehive.view.BeeHivePrimaryContainer`)의 실제 bounds에 독립 스트립을 겹친다. 원래 키보드 body와 자식 visibility는 바꾸지 않는다. 왼쪽 종료, 가운데 상태/partial, 오른쪽 정사각 RING 마이크(일시중지/재개)로 구성한다. 툴바가 숨겨졌거나 표시 영역이 없으면 `layout_body` 상단으로 폴백하며 높이는 삼성 `dimen/header_layout_area_height`(기본 44dp, 가로 38dp)를 사용한다. `OnGlobalLayoutListener`로 크기·위치·부모 변경을 반영하고 해제 시 리스너와 자체 뷰만 제거한다. 전체 body를 MATCH_PARENT 높이로 가리지 않는다. 근거: `resources/res/layout/{layout_base_frame,beehive_primary_container}.xml`, `values/dimens.xml`, `values-land/dimens.xml`. 포커스·터치·한손/플로팅/분할·IME insets는 실기 검증이 필요하다.

삼성 기존 voice Bee `bg.c.execute():void`는 별도 `H6.a/HoneyBrand` 보드로 전환하지 않는다. `E7.e` 서비스 구현 `kn.a.d(kotlin.jvm.functions.Function0)` → 권한 검사/요청 (`kn.a.f()`), `kn.a.g()`에서 내부 honey voice state/화면을 켠다. 종료는 `kn.a.a():void`, `bg.c.finish()`에서 동일. `isSelected()`는 text/search board 및 voice 상태에 의존한다. `H6.a`에는 voice enum이 없고 `z6.n.execute()`의 `F6.F` 보드 전환은 CLIPBOARD 등 **일반 HoneyBrand 전용**이다. 따라서 다른 보드 전환을 흉내내지 않고 툴바 헤더만 겹친다. 자체 녹음/패널에서는 삼성 음성 인식 진입 함수 `kn.a.d()`를 호출하면 삼성의 별도 음성 상태가 켜지므로 재사용하지 않는다. 근거: `sources/p047bg/c.java`, `sources/p299kn/a.java`, `sources/p702z6/n.java`, `sources/H6/a.java`.

`execute` 시 중복 호출은 toggle, `finish`/닫기/입력종료는 idempotent로: 녹음 중단 → 콜백 취소 → overlay만 부모에서 remove → selected=false → `invalidate()`; 삼성 보드 자식들의 visibility를 변경했다면 **이전 상태로 복원**, 가능한 한 변경하지 않는다. 다른 Bee 실행 시 `BeeItem.executeInner()`가 다른 Bee를 `K.j(BeeItem,BeeItem)`로 finish하나 `isBeeSkipFinish` 정책이 개입하므로 IME lifecycle 자체에서도 정리한다.

## 5. 서비스, 입력창, 전환/숨김

가장 직접적인 서비스 참조는 `com.samsung.android.honeyboard.service.HoneyBoardService.onCreateInputView()` 또는 `onStartInputView(android.view.inputmethod.EditorInfo,boolean)` 후크에서 `chain.getThisObject()`를 weak-reference로 보관하는 것이다. 후크 바깥에 전역 강한 참조를 영구 보관하지 않는다. `InputMethodService`의 public `getCurrentInputConnection():InputConnection`, `getCurrentInputEditorInfo():EditorInfo`, `getWindow()/isInputViewShown()`을 해당 인스턴스에 호출한다(표준 Android API). 후크: `HoneyBoardService.onStartInput(EditorInfo,boolean)`, `onStartInputView(EditorInfo,boolean)` **입력 대상 변경시 기존 세션 취소**; `onFinishInputView(boolean)`, `onFinishInput()`, `onDestroy()` **마이크·overlay 정리**. `onCreateInputView()`는 항상 최초/매 입력 호출된다고 가정하지 말고 view 재생성 시 root identity 변경을 확인. 앱 자체 BeeHive 컴포넌트도 `X9.e.onFinishInputView(boolean)`에서 세트를 저장한다. 근거: `sources/com/samsung/android/honeyboard/service/HoneyBoardService.java` (해당 override), `sources/X9/e.java`.

텍스트: `InputConnection.getTextBeforeCursor(int, int)` / `getTextAfterCursor(int,int)` (`GET_TEXT_WITH_STYLES`가 필요 없으면 0), `getSelectedText(int)`, `getExtractedText(ExtractedTextRequest,int)`(지원하지 않는 앱 가능), `EditorInfo.inputType`, `packageName`, `fieldId`, `initialSelStart/End`. 결과 확정 시 **그때의** 현재 연결을 다시 받아 `beginBatchEdit()` → `commitText(result,1)` → `endBatchEdit()`; 직전 editor/connection/세션 generation이 바뀌었으면 버리고 커밋하지 않는다. 일부 보안 입력창은 주변 텍스트를 null로 줄 수 있고 비밀번호 입력 타입은 수집/녹음 시작 자체를 막는 정책을 구현한다. `onUpdateSelection`도 필요시 cursor/선택 변화를 추적. `InputConnection` 호출과 뷰 변경은 IME 메인 스레드에서 수행한다.

## 6. RECORD_AUDIO 권한

HoneyBoard manifest에 `android.permission.RECORD_AUDIO`가 선언돼 있다(앱의 권한은 모듈 APK의 권한과 **별개**). 앱에는 `com.samsung.android.honeyboard.base.permission.PermissionActivity.a(Context,int,String...):void`, static `f22167d: w8.b` 콜백(`onPermissionGranted(int)`)이 있다. 삼성 voice 흐름은 `kn.a.d(Function0)`에서 `w8.c.b(Context,"android.permission.RECORD_AUDIO")`를 확인하고 미허용이면 `kn.a.f()`가 `PermissionActivity`로 `requested_permissions={"android.permission.RECORD_AUDIO"}`, `request_code=836429`, `FLAG_ACTIVITY_NEW_TASK` 등을 넣어 시작한다. Activity의 `onRequestPermissionsResult`는 **836429에서만** static 콜백에 결과를 전달; 단순히 `PermissionActivity.a(context,다른코드,...)`를 호출해도 자체 인식 callback은 실행되지 않는다. `kn.a.d()`는 삼성 음성 UI까지 켜므로 Rambler용으로 그대로 호출하지 말 것. **안전한 권장:** 키보드 context에서 `checkSelfPermission`, 미허용이면 모듈의 Activity 또는 HoneyBoard의 PermissionActivity에 고유 requestCode를 주어 요청하고 권한 재확인 후 시작(자체 결과 연결 필수); 삼성의 고정 static 콜백 `f22167d`를 덮어쓰거나 기존 836429를 재사용하지 않는다. 백그라운드 Activity 시작 제한/overlay 필요 여부 실기 검증. 근거: `sources/com/samsung/android/honeyboard/base/permission/PermissionActivity.java`, `sources/p299kn/a.java:d,e,f()`, `sources/p622w8/{b,c}.java`, `reverse-engineering/samsung-keyboard-decompiled/resources/AndroidManifest.xml` 또는 `reverse-engineering/samsung-keyboard-resources/AndroidManifest.xml`.

## 7. 테마 색

뷰의 **현재 HoneyBoard-themed Context**에서 `Resources.getIdentifier("keyboard_background","attr",package)`로 `attr` ID를 얻고 `context.getTheme().resolveAttribute(attr,TypedValue,true)` 후 `TypedValue.resourceId != 0 ? context.getColor(resourceId) : typedValue.data`를 사용한다. 앱 내부 `h7.d.a(int attr,Context):int`는 `resolveAttribute`→`context.getColor(resourceId)` 방식이다. 패널 배경 `keyboard_background`, 텍스트 `normal_key_text`(실제 키 캡션 스타일인지 화면 확인), 툴바 텍스트/아이콘 `bee_icon_enable_tint`, 선택 `bee_icon_select_tint`, 편집 `edit_bee_icon_tint`, 헤더 `beehive_primary_background`가 후보. `layout_background` 이미지를 사용하는 사용자 테마가 있으므로 단색 배경만으로 완벽히 매칭된다는 보장은 없다. 색 리소스/테마 변경 시 새 context/theme에서 재평가하고 `onConfigurationChanged(Configuration)` 또는 새 input view에서 업데이트. 강제로 UI_MODE_NIGHT 색을 하드코딩하지 않는다. 근거: `sources/p208h7/d.java`, `sources/com/samsung/android/honeyboard/beehive/viewmodel/{t,C0623q}.java`, `sources/Th/f.java`, `resources/res/values/attrs.xml`.

## 8. 작동 참고 모듈: libxposed 102 패턴

참고 APK `dev.codex.samsungkeyboard`는 `META-INF/xposed/module.prop`에서 `minApiVersion=102`, `targetApiVersion=102`, `staticScope=true`, `exceptionMode=protective`; `java_init.list`에 `q`(난독화 엔트리), `scope.list`에 `com.samsung.android.honeyboard`. 엔트리 jadx `p000.C0049q extends io.github.libxposed.api.XposedModule`, `onPackageReady(XposedModuleInterface.PackageReadyParam)`에서 `getPackageName()` 검사, `isFirstPackage()` 검사, `getClassLoader()` 취득, concurrent set으로 같은 classloader 중복 방지. 리플렉션으로 앱 메서드를 찾아 `hook(Method).setId(...).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(Hooker)` 사용; 내부에서 `chain.getThisObject()/getArg()` 및 `chain.proceed()` 호출, 원본이 필요한 곳은 `getInvoker(method).setType(XposedInterface.Invoker.Type.ORIGIN)`. 모듈 자신 `dev.codex.samsungkeyboard.R` 문자열은 주로 설정 UI `Context`에서 사용하고 **HoneyBoard 리소스 테이블에 주입하는 `Resources.addLoaders` 등의 패턴은 발견하지 못했다**. 후크된 앱의 뷰에서 모듈 리소스를 사용할 때는 모듈 `Context/Resources` 접근을 별도로 검증해야 한다.

```java
// 참고 모듈 C0049q의 구조를 Rambler에 맞게 단순화한 예시: 정확한 API 오버로드는 API 102 소스로 확인.
@Override public void onPackageReady(XposedModuleInterface.PackageReadyParam p) {
    if (!"com.samsung.android.honeyboard".equals(p.getPackageName())
            || !p.isFirstPackage() || p.getClassLoader() == null) return;
    ClassLoader cl = p.getClassLoader();
    Class<?> queen = Class.forName("com.samsung.android.honeyboard.bee.b", false, cl);
    // 클래스 이름/생성자의 Eb.c는 런타임 DEX 이름 재확인 후 getDeclaredConstructor(Context.class,...).
    // hook(ctor).setId("rambler-queen").setExceptionMode(PROTECTIVE).intercept(chain -> {
    //     Object result = chain.proceed();
    //     Object instance = chain.getThisObject(); // 이미 등록한 인스턴스인지 확인
    //     ((java.util.List<?>) reflect(instance,"f22240d")).add(proxy); // 실제 구현에선 raw List cast
    //     return result;
    // });
}
```

Proxy 생성 예시의 클래스 로더는 반드시 `z6.c`를 로드한 앱 classloader: `Class<?> iface=Class.forName("z6.c",false,cl); Object proxy=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},handler)`. handler는 primitive 반환에서 **null 금지**(`boolean`→Boolean, `int`→Integer), `equals(Object)`는 identity, `hashCode()`는 identity hash, `getBeeInfo()`는 앱 classloader의 `z6.j` 인스턴스여야 한다. `z6.i(Context,Icon,int)` synthetic 생성자가 DEX `getDeclaredConstructor`로 실제 존재하는지 확인하고 없으면 5인자 생성자를 사용한다. 앱 classloader 생성 `Class.forName("z6.i",false,cl)`의 5인자 `(Context,Icon,int,Resources,String)` 및 `z6.j(z6.i)`가 대안. **모듈 클래스를 앱 클래스 로더로 강제 로드할 필요는 없지만** Proxy에 전달하는 interface와 BeeInfo 타입은 앱의 것이다. Bean `Proxy`가 실제 Bee UI에서 막히면 `K` 생성/등록 직후 별도 `BeeItem`을 삽입하는 우회는 목록·편집·저장 일관성을 깨기 쉬우므로 우선 해당 `instanceof`/캐스팅 분기를 찾아 검증한다.

근거: `reverse-engineering/reference-module-decompiled/sources/p000/C0049q.java` (엔트리 24~60행과 intercept), `reverse-engineering/reference-module-decompiled/resources/META-INF/xposed/{module.prop,java_init.list,scope.list}`, `reverse-engineering/reference-module-decompiled/resources/AndroidManifest.xml`; 모듈 현재 코드 `module/src/main/`은 변경하지 않았다.

### 구현 전 실기 체크포인트

1. 런타임 `z6.c` 메서드 덤프와 `na.b`, `ia.a/ia.c` DEX 이름/생성자, `z6.i` 생성자 존재 여부 확인. 특히 jadx의 `p###` 경로 접두사는 실제 이름과 다를 수 있음.
2. 첫 입력에 `queen.f22240d`→`X9.e.r()`→`K.f22418t`에 ID가 **한 번** 나타나는지, 다른 Bee로 전환/편집/재시작에서 중복·재삽입이 없는지 확인.
3. preset 및 이미 저장된 main/sub 세트, 슬롯 7/8/4, `edit_toolbar`, 사용자가 Bee 제거한 상태, 정원 초과, 비표시 상태를 각각 검사.
4. 모듈 아이콘·라벨을 라이트/다크·고대비/커스텀 키보드 테마 및 시스템 언어에서 검사. 패널 위치는 일반·한손·플로팅·가로모드와 IME 숨김/입력창 전환에서 검사.
5. 권한 최초 허용/거부/영구거부, password field, null InputConnection, 백엔드 실패·부분 텍스트 후 재시작과 다른 앱으로의 커밋 방지를 검사.
