# Gboard 18.3.1 Rambler / S3Bidi wire protocol — 정적 분석 명세

대상: `input-apks/gboard-18.3.1-pixelboard-rambler.apk`의 jadx 출력 `reverse-engineering/gboard-decompiled/sources/`. 아래 경로는 이 디렉터리를 기준으로 `defpackage/` 또는 패키지명부터 적는다. **초기 정적 분석 시에는 서버 접속/패킷 캡처를 하지 않았다.** 이후 이 PC에서 검증한 성공 요청과 한계는 §6 참조. proto 선언은 원본 `.proto`가 아니라 protobuf-lite의 `new zzu(...)` 메시지 정보, 필드 배열, `zyf.S(...)` extension 번호 및 호출 지점에서 복원한 *wire-compatible 부분 명세*다. 필드의 원래 이름은 알 수 없으므로 의미를 설명하는 임의의 이름을 사용한다. 언급되지 않은 필드가 절대로 전송되지 않는다는 뜻은 아니다. 특히 jadx가 중복 블록을 잘못 복원한 `kmv.java`의 분기와 기본값은 패킷 검증 전까지 조건부로 취급한다.

## 1. 접속 및 메타데이터

* 기본 authority: `speechs3proto2-pa.googleapis.com:443` (`iet.java`의 `s3_host_name`, `xxi.java`가 `:443`을 덧붙임). TLS gRPC method **`/speech.s3.S3StubbyClientConnectorService/S3Bidi`**; `aagg.java`의 method descriptor는 bidi-streaming, request `aafw`, response `aafx`. `aago.java`는 service/method명을 확인한다. `aagf.java`, `abob.java`, `ife.java`에서 메시지를 개별 송신/수신한다. `grpc-web` 메시지나 브라우저 POST 구현이 아니다. 통상적인 HTTP/2 gRPC (5-byte gRPC 메시지 프레이밍, `content-type: application/grpc`, `te: trailers`) 채널을 써야 한다. 전송 선택은 `xws.java`의 Cronet gRPC 채널이며 Cronet을 로드하지 못하면 OkHttp gRPC로 폴백한다 (`xxp.java`, `zmm.java`, `iev.java`). *기본 gRPC 헤더는 런타임/라이브러리에서 생성되므로 각 값과 실제 ALPN 선택을 캡처로 확인하지 않았다.*
* `ieu.java`가 S3Bidi 호출에 명시적으로 추가하는 3개 metadata (정확한 문자열):

  | 이름 | 값 |
  |---|---|
  | `X-Goog-Api-Key` | Gboard's built-in key (`AIza…`, 39 chars) — not published here; extract it from the Gboard APK |
  | `X-Android-Package` | `com.google.android.googlequicksearchbox` |
  | `X-Android-Cert` | `58E1C4133F7441EC3D2C270270A14802DA47BA0E` |

  Gboard/PixelBoard의 실제 패키지·서명과 다를 수 있으며 하드코딩된 선언이지 로컬 패키지명을 자동 계산한 값이 아니다. 타 앱에서 문자열을 복제해도 서버의 앱 검증을 통과한다는 보장은 없다. `user-agent`는 고정된 Gboard 문자열이 아니다. `xws.java`는 `new CronetEngine.Builder(context).getDefaultUserAgent()`를 채널에 설정하고 폴백에서는 OkHttp gRPC 구현이 구성한다. 정확한 실기기 값은 OS/Cronet 버전과 엔진에 종속되며 소스에서 확정할 수 없다 (`abfd.java`의 gRPC user-agent 키). 임의의 Gboard 버전 문자열을 필수 헤더로 주장하지 않는다.
* **인증**: `ieu.java`가 `Authorization`/Bearer를 명시적으로 넣지 않으며 `iet.java`의 `s3_auth_token_for_debugging`는 이 분석에서 실제 사용 지점을 찾지 못했다. 그러나 공용 채널 인터셉터(`zmm.java`, `xxi.java`)와 서버측 정책을 포함해 *모든 경우에 OAuth가 불필요하다고 증명한 것은 아니다*. 별도 protobuf 내부 사용자 식별: `kmv.java`는 `mtx.z`=`jetson_send_zb`(기본 false)일 때만, 준비된 Zwieback ID의 해시를 `aafy`의 repeated `aafr` token으로 붙인다. 기본 Rambler 경로는 이 토큰을 생략한다. `mtx.y`(별도 S3 application id, 기본 빈 문자열)와 `mtx.A`(quota override, 기본 false)도 별도의 조건부 변형이다. **API 키와 위 metadata만으로 한 차례 수락됨을 §6에서 확인했으나 다른 환경의 인증/쿼터 정책은 미상**.

## 2. 메시지 레이아웃과 요청 순서

아래 `extend`는 protobuf extension, *oneof가 아니다*. 일반 `S3Request`의 필드 1–3과 확장 번호는 각각 다른 필드다. `proto2` 스타일 표기이지만 원본 필드 cardinality (`required` 여부)와 패키지 네임스페이스는 전부 확정하지 못해 `optional`을 wire 해석의 편의로 쓴다. 각 메시지 옆 난독화 클래스는 파일 근거를 뜻한다. `bytes`와 문자열, 숫자의 wire type은 `new zzu(...)` + Java 필드에서 확인했다.

```proto
// schematic only: original .proto names/required qualifiers are NOT recovered
message S3Request {                         // aafw.java
  optional string recognizer_id = 1;        // c: "beyond-a2a-recognizer"
  optional bool flag_d = 2;                 // d, default true; kmv sets true
  optional bool end_of_data = 3;            // e: only terminal request true
  // extension ranges in the original descriptor are unknown.
}
message AudioInfo {                         // aafp.java, extension S3Request[293100]
  optional float sampling_rate_hz = 2;      // d = 16000.0
  optional AudioEncoding encoding = 3;      // c = 10
  optional int32 audio_parameter = 4;       // e = i (live stream: 1); semantic name unverified
  optional int64 elapsed_millis = 9;        // f; timing from vwm.c()
}
message AudioData {                         // aafo.java, extension S3Request[293101]
  optional bytes data = 1;                  // c, slices of Ogg/Opus byte stream
}
message InputBoxInfo {                      // aagk.java, extension S3Request[1203]
  oneof input { string surrounding_text = 1; CursorText cursor_text = 4; }
  repeated string biasing_phrases = 3;      // d; only if corresponding flags/data
  repeated string user_preferences = 5;     // e; conditional instructions
}
message CursorText {                       // aafq.java
  optional string before_cursor = 1;       // c = ovi.n()
  optional string selected_text = 2;       // d = ovi.l()
  optional string after_cursor = 3;        // e = ovi.m()
}
message RecognizerInfo {                    // aaiz.java, extension S3Request[294500]
  optional RecognizerConfig config = 1;     // e
  optional bool f = 5;                     // true
  optional int32 g = 7;                    // kma.g ? 2 : 0
  optional bool i = 13;                    // true
  optional bool j = 20;                    // true
  optional bool k = 40;                    // rwr.r runtime flag
  optional bool l = 41;                    // true
  optional string routing_key = 50;        // h, default Jetson flag value below
  optional RecognizerFlags flags = 91;     // m
}
message RecognizerConfig {                 // aaej.java
  optional bool c = 4;                     // false
  // field 29: adbg message; not populated in this path
}
message RecognizerFlags {                  // aaiu.java
  optional bool c = 2;                     // !qcs.e(msl.d)
  optional bool d = 4;                     // optionally true on batch retry
}
message ClientInfo {                       // aafu.java, extension S3Request[294000]
  optional string application_id = 2;      // e = "gboard" by default
  optional string platform = 8;            // c = "Android"
  optional string build_display = 9;       // d = Build.DISPLAY
  optional string app_version = 10;        // f = major.minor from rum.f() or "-1"
  optional string model = 11;              // g = Build.MODEL
  optional string trigger_package = 16;    // h = EditorInfo.packageName if verified
  optional string i = 17;                  // i, not populated here
  optional ClientFlags flags = 22;         // j
  optional int32 client_type = 26;         // k = 1
}
message ClientFlags { optional bool supervised = 1; } // aafs.java, kma.l
message UserInfo {                         // aafy.java, extension S3Request[293000]
  optional Language primary_language = 2;  // d
  repeated Language secondary_languages = 4; // e
  repeated Token auth_token = 9;           // c; usually absent (mtx.z=false)
  optional bool unmanaged_device = 20;     // f = !qcs.e(msl.a)
}
message Language {                         // aaft.java
  optional string tag = 1;                 // c, e.g. "en-US" / "ko-KR" (runtime locale)
  optional int32 type = 2;                 // d = 1
}
message Token {                            // aafr.java
  optional string e = 1;
  optional string hash_or_token = 2;        // f, hashed Zwieback when enabled
  optional int32 token_type = 4;           // c = 2 on that path
  optional bool d = 7;
}
message LatencyConfig { optional int32 c = 1; } // aagd.java, ext S3Request[29735924]: 500
message QuotaOverride {}                    // aafv.java, ext S3Request[47096292]
message ExperimentIds { repeated int64 id = 1 [packed=true]; } // aagc.java, ext QuotaOverride[229601060], conditional 83393045
message S3RequestBatch { repeated S3Request request = 1; } // aagi.java: retry/batch route, NOT S3Bidi request type
```

> `aaiz`의 13/20/40/41/50/91 등은 `new zzu`의 번호 시퀀스(제어문자 `\r`, `\u0014`, `(`, `)`, `2`, `[`)를 디코딩한 것이다. 필드 이름은 의도적으로 Java 필드 이름을 유지했다. `aagk`의 repeated 문자열은 일반 상황에서 없을 수 있다. `aafy`의 `secondary_languages`가 실제로 채워지는지는 `kmv.java`의 jadx 중복/비정상 분기(`collection != null || collection.isEmpty()` 및 `str` 대입)에 주의가 필요하다.

첫 전송(`ifh.java`, `kmv.java`, `ife.java`, `kmt.java`, `kjr.java`): 앱 번들 `session_type="jetson"`은 **로컬 세션 선택자**이며 wire 필드가 아니다 (`fce.java:314`, `kjr.java`). 선택 후 `ifp.d=true` → recognizer id `beyond-a2a-recognizer`. `AudioInfo`에는 16000 Hz, encoding 10, field 4 `audio_parameter=1`(이 코드의 `kmt.j`→`ife.i(...,1,...)`; 의미/원래 필드명 미확인, Android AudioRecord의 실제 세션 ID가 아님), 경과시간 ms. `mtx.a`=`enable_agentic_dictation` 또는 surrounding text 부재 시 `ifh.java`에서 InputBoxInfo를 생략하는 조건이 있고, surrounding text가 있을 경우 `mtx.J`=`jetson_enable_cursor_awareness` 기본 false이면 field 1 한 문자열, true이면 field 4의 3분할 메시지를 붙인다. 주변 텍스트는 입력창/선택 상태에 따라 달라지고 제한값 `mtx.K` 기본 2000은 상위 수집 정책이다. 언어는 앱/키보드의 primary `kma.a`에서 와서 `Language.tag`에 들어간다. **고정 `en-US`나 `ko-KR` 값은 없다**. 중국어 `zh-CN`→`zh-Hans`, `zh-TW`→`zh-Hant`, `zh-HK`→`yue` 변환 (`kmy.java`); `zqv.java:j()`가 tag와 type=1을 설정한다. Jetson routing key는 `mtx.x` 기본 `gboard_gemini_v3_streaming_voice_edit_mul`; override 가능. client application id는 기본 `gboard`, build/model 및 앱 버전/호출 앱은 기기·입력창 값이다 (`kmv.java`, `mtx.java`). 기타 음성 bias 및 모니터링 설정은 런타임 플래그/데이터에 의존한다. `mtx.a` 기본 false는 PixelBoard에서 켜는 활성화 플래그이므로 패치 상태에 따라 실제 요청 구성이 달라진다.

## 3. 오디오 및 종료

**encoding=10은 이 경로에서 Ogg 컨테이너에 담긴 Opus 연속 바이트**다. 원시 Opus 프레임이나 PCM을 224-byte 단위로 자른 것이 아니다: `iff.java`(`OggOpusInputStream`)가 `OggOpusEncoder`를 사용하며 `svj.e("audio/opus", ...)`가 콘텐츠 라벨을 설정한다. `com/google/audio/hearing/common/OggOpusEncoder.java`의 `init(i, 24000, 16000, true)`에서 i=1(위 경로), 두 번째 값 24000, 세 번째 16000, boolean=true를 전달한다. 24000은 인코더 호출의 비트레이트로 해석할 가능성이 높지만 **네이티브 구현의 인자 의미(프레임 길이/채널/CBR 여부)는 Java에서 확정할 수 없다**. 실제 생성 Ogg 페이지/Opus 프레임 길이도 캡처/네이티브 분석 없이 단정하지 않는다. 입력은 `aajy.java`의 `AudioRecord(6, 16000, 16, 2, ...)`: Android의 해당 상수는 VOICE_RECOGNITION=6, CHANNEL_IN_MONO=16, ENCODING_PCM_16BIT=2 (16 kHz mono 16-bit PCM). `iff.java`는 PCM을 2048-byte 버퍼에서 읽어 encoder에 공급하며 EOF에서 `flush()`로 Ogg/Opus tail을 내보낸다. **구현 주의**: `iff.java`는 `read()`가 받은 길이 `i3`가 아니라 할당한 2048바이트 전체를 `processAudioBytes`에 전달한다. 이는 마지막 부분 버퍼에서 제로 패딩/타이밍에 영향을 줄 수 있다.

`ife.java`는 인코딩된 **연속 바이트 스트림**을 최대 224바이트씩 읽고, 각 `S3Request`에 `AudioData[293101].data`를 담고 `AudioInfo[293100].elapsed_millis`만 갱신하여 송신한다. 224는 전송 chunk buffer 크기이지 Opus packet이나 Ogg page 경계가 아니다. 읽은 수가 224 미만이면 종료 처리; `ife.f()`가 `S3Request.end_of_data=true`(field 3), `flag_d=true`(field 2)인 요청 `p`를 보내고 클라이언트 송신 스트림을 half-close한다. cache-only 모드에서는 전송을 건너뛴다. batch retry는 `aagi.java` 리스트 안에 header/8192바이트 chunk/종료를 담는 별도 호출 코드이며 스트리밍 wire와 혼동하지 말 것. 다른 encoding 번호 중 `0..15`가 enum validator의 유효 범위 (`aafm.java`, `zqv.java`)라는 점은 확인했지만 **LINEAR16/PCM의 정확한 번호와 서버 지원 여부는 해당 Java enum 이름이 없어 확인하지 못했다.** 10 외의 임의 번호로 PCM을 보낼 근거가 없다.

## 4. 응답 복원과 편집

```proto
message S3Response {                        // aafx.java
  optional int32 status = 1;                // c, 0=ongoing, 1=done, 2=error, 3=not-started (ifd.java + a.java:al)
  optional int32 error_code = 2;            // d; e.g. 8 RESOURCE_EXHAUSTED
  optional string error_message = 3;       // e
}
// extension S3Response[1253625], aaix.java
message RecognitionExtension {
  oneof payload {
    RecognitionEvent recognition = 1;      // aafi
    LifecycleEvent lifecycle = 2;          // aafa
    // other payload arms 5-13,15-16,18-20 are present in schema;
    // this processor does not interpret them as transcript
  }
  optional string segment_id = 3;          // d (used as result identifier)
  optional string e = 4;
}
message RecognitionEvent {                  // aafi.java
  optional int32 state = 1;                 // c; 0=partial, 1=recognition final; other values unverified
  optional FinalResults results = 3;       // d, on final
  optional PartialResults partial = 4;     // e
  optional FinalResults g = 5;
  optional FinalResults f = 7;
  optional UnknownEvent h = 10;            // aafh, unused by ifd
}
message LifecycleEvent { optional int32 state = 1; } // aafa.java
message PartialResults { repeated PartialPiece piece = 1; } // aafe.java
message PartialPiece {                      // aafd.java
  optional string text = 1;                // c
  optional double stability = 2;           // d
}
message FinalResults {                      // aafk.java
  repeated FinalAlternative alternative = 3; // c; first nonempty candidate used
  optional FinalContainer d = 5;           // aafj.java (auxiliary)
}
message FinalAlternative {                  // aafb.java
  optional string transcript = 1;          // c
  optional float score = 2;                // d
  optional int32 edit_state = 30;          // e (0/1 distinguished)
  // further embedded fields 3,4,6,10,21 not interpreted by ifd
}
// extension FinalAlternative[74956224], aagm.java
message VoiceEditingResult {
  oneof edited_history {
    string edited_history_text = 3;        // c, edited full history
    CursorText edited_history_with_cursor = 7; // c
  }
  optional string full_transcript = 1;     // d; field 1 confirmed by live result, cursor tags stripped for cursor variant
  optional int32 user_intent = 4;          // e; aagl.java enum
}
// user_intent: 0 DEFAULT_INTENT_DICTATION, 1 INTENT_EDIT,
// 2 INTENT_EDIT_HISTORY, 3 INTENT_REWRITE, 4 INTENT_COMPOSE,
// 5 INTENT_RAMBLE, 6 INTENT_RAMBLE_HISTORY.
// Additional S3Response extensions: aage[29737259] (empty marker),
// aagb[443278602] (bool opt-in field 1),
// aaiy[171993426] (strings fields 2,4).
```

`ifd.java`가 `status=2`를 오류로 (`error_code`, `error_message` → `ifi`), `status=1`을 완료로, `status=3`을 예상 밖 미시작으로 처리한다. 전송 오류는 observer `onError`도 별도로 처리한다; RESOURCE_EXHAUSTED(8)는 `mtx.C` 조건에서 지연 가능. lifecycle event state는 `a.java:al()`의 0→시작, 1→중간, 3→종료 등의 콜백으로 매핑되지만 콜백 의미는 `iey` 호출 구현에 의존한다. recognition `state=0`이고 partial 필드가 있으면 `piece`를 연결한다. stability `>=0.9`인 *선행* 부분을 stable 영역으로, 한번 불안정 piece가 나온 뒤는 계속 unstable 영역으로 다루며 합친 텍스트를 `aaes` 결과로 콜백한다. recognition final 필드가 있으면 첫 유효 alternative를 최종 텍스트로 내보내고 state=1이면 인식 종료 콜백도 한다. `aaix.segment_id`는 결과 식별에 전달한다. `aage` extension은 진행 신호로 취급 (`ifd.java`). `aaiy`는 계측 문자열 두 개다.

최종 alternative의 `VoiceEditingResult[74956224]`가 있고 `user_intent=INTENT_EDIT_HISTORY(2)` 또는 `INTENT_REWRITE(3)`일 때, `ifd.java`는 `edited_history_text` 또는 `CursorText`를 선택한다. 문자열 variant는 edited history 문자열 그대로, cursor variant는 before+selected+after를 합쳐 기존 history로, `full_transcript` 안 `<cursor>`/`</cursor>` 태그를 제거한다. 다른 의도에는 이 helper가 edited-history를 적용하지 않으며 `FinalAlternative.transcript`를 사용한다. 편집 메타(`aaer`: intent=field1, full_transcript=field4, cursor text=field5)는 내부 결과 `aaes`에 실린다. `fce.java`는 입력창의 현재 상태/선택과 결과를 재조정하고 `fbh.n(...)`을 거쳐 실제 텍스트를 편집한다. **delete/replace/rewrite를 별도의 S3 wire operation opcode나 확정적인 문자열 치환 규칙으로 환원할 근거는 없다.** 편집된 전체 텍스트/커서 컨텍스트와 입력창 상태를 함께 처리해야 한다. `FinalAlternative.edit_state`의 0/1은 `ifd.java`가 boolean 플래그로 바꾸지만 명칭·의미는 미확인.

## 5. 구현 시 검증 공백과 근거 색인

* protobuf-wire 확장번호는 `zyf.S(...)`와 `new zzu(...)`로 확인. `aafx`/`aafi` enum 상태와 `aafp.encoding`의 상징명은 Java에서 일부만 복원 가능하다. API 키/서명·패키지 문자열은 **하드코딩 값이지 본 모듈의 자격 증명 발급 경로가 아니다**. 서명 바이패스는 PixelBoard의 클라이언트 측 기능이며 서버 수락을 보장하지 않는다 (`reverse-engineering/pixelboard-source/`의 `GboardSignatureBypassBytecodePatch.kt`). 본 문서만으로 프로덕션 작동·약관/쿼터를 입증하지 않는다.
* 접속/헤더: `defpackage/{iet,xxi,ieu,aagg,aagf,aago,zmm,xxp,xws,iev,abfd}.java`.
* 요청/header 및 앱값: `defpackage/{fce,kjr,kmt,ife,ifh,ifn,ifp,ifo,kmv,kmy,mtx,zqv,aafw,aafp,aafo,aagk,aafq,aaiz,aaej,aaiu,aafu,aafs,aafy,aaft,aafr,aagd,aafv,aagc,aagi}.java`.
* 오디오: `defpackage/{aajy,iff,ife,kmt,aafm}.java`, `com/google/audio/hearing/common/OggOpusEncoder.java`. 네이티브 `libogg_opus_encoder.so`의 세부 파라미터/페이지 조립은 이 문서에서 분석하지 않았다.
* 응답/편집: `defpackage/{ifd,ifi,a,aafx,aaix,aafi,aafa,aafe,aafd,aafk,aafb,aagm,aagl,aaer,aaes,aage,aagb,aaiy,fce,fbh}.java`.

## 6. 실서버 검증 (2026-09-27)

`module/tools/run-s3.sh en-US`에서 flite TTS → 16 kHz mono WAV → `opusenc --speech --bitrate 24 --framesize 20` → JVM `OggOpusWriter`로 재패킹한 Ogg/Opus를 프레임당 20 ms로 송신했다. 이 PC에서 `javax.net.ssl.SSLSocket`의 `h2` ALPN으로 직접 접속하여 HTTP/2 gRPC trailer `grpc-status: 0`을 수신했고 서버가 실제로 다음 문장을 반환했다.

```
Opus packets=176 preSkip=312
EDIT intent=2 full=Hello world, this is a test of speech recognition. history=Hello world, this is a test of speech recognition.
FINAL Hello world, this is a test of speech recognition.
CLOSED
```

다른 flite 문장도 `FINAL Turn on the lights and open the door, please.`로 확인했다. **성공 요청**: 위 §1의 세 metadata, recognizer id `beyond-a2a-recognizer`, `AudioInfo[293100]`의 `sampling_rate_hz=16000.0`, `encoding=10`, `audio_parameter=1`, `RecognizerInfo[294500]`의 field 5/13/20/41=true, field 7=0, field 50=`gboard_gemini_v3_streaming_voice_edit_mul`, `ClientInfo[294000]`의 `application_id=gboard`, `platform=Android`, `client_type=1`, `UserInfo[293000]`의 `Language.tag=en-US`, type=1, latency 설정 `500`, audio chunk마다 `AudioData[293101]` + elapsed millis, field 2/3=true인 종료 요청 및 HTTP/2 END_STREAM. 인증 토큰 없이도 이번 테스트에서는 수락됐지만 다른 환경의 정책이나 장기 작동은 보장하지 않는다.

**관찰/주의:** 정적 분석에서 `VoiceEditingResult.full_transcript`를 field 2로 잘못 기록했으나, 실제 응답과 `aagm.java`의 `new zzu`가 가리키는 것은 field 1이어서 수정했다. `S3Response`에는 명세에 기술하지 않은 일반 field 5 카운터가 함께 왔으며, 결과는 `RecognitionExtension[1253625]` 내 `FinalAlternative[3]`의 transcript와 `VoiceEditingResult[74956224]`의 `edited_history_text` 양쪽에 나타났다. 실제 반환 user_intent는 2 (`INTENT_EDIT_HISTORY`)였고 `full_transcript`와 `edited_history_text`에 인식 문장이 담겼다. 프레임 전송 중 flite 샘플에서는 `aage[29737259]` 진행 메시지와 lifecycle은 왔지만 텍스트 partial은 없었다(파서는 지원). 초안 오디오 요청의 일반 `S3Request.flag_d` field 2=true는 Gboard의 `ife.java` 오디오 청크에는 없어서 제거했다. 종료 요청에만 field 2/3을 설정한다. espeak-ng 합성 음성은 영어/한국어 모두 빈 final/edit 결과였고 gRPC 오류는 아니었다. 아래 Google TTS 시험에서는 한국어 final이 확인되어 앞선 빈 결과는 합성 음성 입력에 한정된 관찰로 본다.

**한국어 음성 추가 검증:** Google Translate TTS (`tl=ko`) MP3를 내려받아 ffmpeg로 16 kHz mono 변환 후 `opusenc` 및 `S3Harness ko-KR`로 전송했다. 아래 세 시험 모두 `EDIT intent=2`와 같은 내용의 `FINAL`을 받았고 gRPC 오류는 없었다. **세 시험 모두 텍스트 `PARTIAL`은 수신하지 못했다**(따라서 이 샘플들로 실시간 부분 인식은 확인되지 않음).
* 짧은 한국어 (5.592초), 입력 `안녕하세요. 오늘은 한국어 음성 인식 테스트를 하고 있습니다.` → `FINAL 안녕하세요. 오늘은 한국어 음성 인식 테스트를 하고 있습니다.`
* 한국어+영어 혼합 (15.120초), 입력 `안녕하세요, 저는 Samsung Galaxy에서 Gboard Rambler 음성 인식을 테스트하고 있습니다. 오늘은 한국어와 English 단어가 섞인 문장을 읽고 결과를 확인해 보겠습니다.` → `FINAL 안녕하세요. 저는 Samsung Galaxy에서 Gboard Gboard 램블러 음성 인식을 테스트하고 있습니다. 오늘은 한국어와 English 단어가 섞인 문장을 읽고 결과를 확인해 보겠습니다.` (`Gboard` 반복 및 `Rambler`의 한글 표기는 인식 차이.)
* 장문 한국어 (27.792초), 입력 `오늘 아침에는 집 근처 작은 카페에 들러 따뜻한 커피를 마신 뒤, 지하철을 타고 회사에 갔습니다. 창밖으로 비가 조금씩 내렸지만 사람들은 우산을 펴고 각자의 하루를 시작했습니다. 점심시간에는 동료들과 새로운 프로젝트에 대해 이야기하고, 퇴근 후에는 장을 본 다음 집에서 저녁을 만들 계획입니다.` → `FINAL`은 입력과 정확히 일치.
* 실패한 TTS 생성 시도: `오늘 아침에는 집 근처 작은 카페에 들러 따뜻한 커피를 마신 뒤, 지하철을 타고 회사에 갔습니다. 창밖으로는 비가 조금씩 내렸지만 사람들은 우산을 펴고 각자의 하루를 시작했습니다. 점심시간에는 동료들과 새로운 프로젝트에 대해 이야기하고, 퇴근 후에는 장을 본 다음 집에서 저녁을 만들 계획입니다. 하루 일정을 정리하면서 내일 해야 할 일도 미리 확인해 두겠습니다.` → Google TTS가 HTTP 400을 반환해 MP3가 만들어지지 않았으며 S3 서버에는 보내지 않았다(요청 길이 제한 가능성이 있으나 원인은 확정 불가). 문장을 줄여 재시도한 위 장문 시험은 성공했다.

순수 Java 소스는 `module/src/main/java/dev/sampple/rambler/s3/`, JVM 하네스는 `module/tools/`에 있으며 `module/build.sh`의 D8/APK 빌드도 통과했다. JVM의 flite/opusenc 의존성은 테스트 생성용이며 Android 모듈 코드는 외부 의존성이 없다.

## 7. 부분 인식 조사와 이중 스트림 구현 (2026-09-27)

**원인:** 기존 `beyond-a2a-recognizer`(Jetson) 단일 스트림은 이 오디오/라우팅키 조합에서 음성을 끝낸 뒤에만 편집/최종 텍스트를 생성한다. `onPartial` 파서가 수신 텍스트를 놓친 것이 아니다. `S3Debug.tree(byte[])`와 opt-in `Listener.onRawResponse(source, data)`로 **모든 수신 protobuf**의 경로·wire type·값·hex/유효 UTF-8과 가능한 하위 필드를 출력해 검사했다. `S3_DUMP=true module/tools/run-s3.sh en-US` 또는 `java -Ds3.dump=true -Ds3.livePartials=false -cp tools/out/classes S3Harness en-US tools/out/speech.opus`: baseline 3.52초 영어 샘플에서 +0.640~+3.636초에는 확장 `29737259`의 카운터/타임스탬프와 `1253625.2` lifecycle만, +4.015초에는 `1253625.1.3`/`.5`에 전체 문장이 왔다. **`.1.4` 부분 텍스트와 다른 중간 문자열은 없었다.** 오디오 3.52초 전송 뒤 3.5초간 half-close를 미뤄도 0.5초 간격 heartbeat만 이어지고, +7.575초의 종료 직후에 전체 문장이 왔다. 따라서 느린 청크/조기 half-close가 주요 원인이 아니다. 224바이트 청크는 Gboard `ife.java`와 동일하다.

**요청 차이 대조:** `ifh.java`는 Jetson일 때 recognizer id를 `beyond-a2a-recognizer`, 일반 dictation일 때 `recognizer`/`multi-recognizer`로 분기한다. `kmv.java`의 recognizer/flags와 `mtx.x`의 routing key는 기존 header와 일치한다. `iet.java`에 중간결과 on/off용 S3 플래그는 없다. PixelBoard `GboardRambler1803StockPolicy`는 `agentic_dictation_enable_proactive_parallel_asr` 등 **앱측** 플래그를 강제하지만 S3 protobuf 필드는 아니다. 실제 분리 시험에서 routing key만 빈 문자열로 바꾼 Jetson은 여전히 final-only, **id만 `recognizer`로 바꿔 동일 routing key/오디오를 송신하면** `.1.4.1.1` 텍스트와 `.1.4.1.2` stability(double)의 실시간 부분 응답을 받았다. `multi-recognizer`도 부분 응답을 돌려준다. 일반 recognizer는 음성을 텍스트로 빠르게 전사하지만 Jetson 편집 결과와 문장부호가 다를 수 있다.

**수정:** `S3Session.Config.livePartials=true`(기본값)는 Jetson 편집 스트림 외에 `recognizer` S3Bidi 스트림을 열고 **동일한 연속 Ogg/Opus 바이트**를 두 스트림에 224바이트 단위로 전송한다. 일반 recognizer의 partial만 `onPartial`로, Jetson의 `onEdit`/`onFinal`을 최종 정답으로 전달한다. 이미 제공한 Listener 콜백 시그니처는 그대로이고, `onRawResponse(byte[])`/`onRawResponse(source, byte[])`와 optional 스트림 실패를 분리하는 `onInterimError`만 default로 추가했다. `livePartials=false`면 원래 Jetson 단일 스트림이다. 앱측 비밀 플래그를 wire에 임의 삽입하지 않았다. **이는 클라이언트의 동시 표준 ASR 추가 구현이며, 실제 Gboard Rambler 빌드가 동일한 두 네트워크 스트림을 쓰는지까지 입증한 것은 아니다.** Gboard `ifd.java` → `kmw.java:onPartialResults` → `handleRecognitionText`는 일반 S3의 partial을 UI의 인식 텍스트 경로로 전달한다. 반면 `fce.java:onVoiceText`의 AgenticDictationExtension은 finalized span이 아니면 `Unexpected partials ... Not processing the streaming spans`로 무시한다. 따라서 관찰된 화면 실시간 **인식 텍스트**를 Jetson 편집 스트림의 응답이라고 볼 수 없고, 일반 dictation/별도 ASR 경로의 partial과 구분해야 한다. 정확히 어떤 Gboard UI 모드/플래그가 표시를 담당했는지는 기기에서 네트워크·UI를 함께 측정하지 않았으므로 미확인이다.

**실서버 재검증:** Google Translate 한국어 TTS(5.6초) → 16 kHz mono → opusenc 20 ms 프레임, 동일 오디오를 양쪽 스트림에 송신. 시간은 `S3Harness` 실행 시작 기준이며 끝의 편집 결과는 **다른 recognizer의 응답**이다.

```text
+0.973s PARTIAL stable=false 안녕하세요
+1.839s PARTIAL stable=false 안녕하세요 오늘
+3.192s PARTIAL stable=false 안녕하세요 오늘은 한국어
+4.357s PARTIAL stable=false 안녕하세요 오늘은 한국어 음성 인식 테스트
+5.604s PARTIAL stable=true 안녕하세요 오늘은 한국어 음성 인식 테스트를 하고 있습니다
+6.140s EDIT intent=2 full=안녕하세요. 오늘은 한국어 음성 인식 테스트를 하고 있습니다. history=안녕하세요. 오늘은 한국어 음성 인식 테스트를 하고 있습니다.
+6.145s FINAL 안녕하세요. 오늘은 한국어 음성 인식 테스트를 하고 있습니다.
+6.189s CLOSED
```

`module/build.sh` APK 빌드와 `module/tools/S3ResponseTest.java`, `TransportCancelTest.java` 통과. 이중 스트림은 네트워크/서버 사용량이 두 배이며, 일반 recognizer의 일부 interim은 번복될 수 있다. 클라이언트 UI는 partial을 확정 입력으로 쓰지 말고 Jetson 최종 편집으로 교체해야 한다.

## 8. 최근 범위 편집·추가 언어 검증

`ifh.java:127`은 문자열 variant에 `ovi.n()`(커서 앞)만 넣는다. `fce.t()`는 `mtx.K=2000`을 이용해 주변을 수집하며, `fbh.l()`은 결과와 커서 앞 텍스트를 비교한다. `fbh.n()`/AgenticDictationTextEditor는 `ovi.e` offset을 사용해 diff를 적용한다. 따라서 edited history를 **항상 문서 전체**로 해석하면 안 된다. 모듈은 최근 문단/최대 2000 UTF-16자의 범위를 요청별로 고정하고 원문·절대 offset을 검증하여 그 범위만 치환한다. Gboard의 변경된 텍스트에 대한 강제 fallback은 따라 하지 않는다. 정확한 앱 입력창 치환 검증은 JVM 범위 테스트와 실기 테스트를 구분한다.

`kmv.java:283–301`의 `addSecondaryLocales`는 `kma.b` 각 locale을 `zqv.j()`로 Language(tag field 1, type=1 field 2)로 만들고 `aafy.e`에 추가한다. `aafy` 스키마에서 이는 **UserInfo[293000].secondary_languages field 4**다. `S3Messages.header()`는 주언어 field 2와 별도로 중복 제거한 field 4를 전송한다. Jetson recognizer id/routing key는 유지하며 동일 언어 구성을 interim에도 적용한다.

Google `translate_tts` → ffmpeg 16 kHz mono → opusenc 20 ms → `run-s3.sh` 실서버 결과:

| 시험 | 결과 |
|---|---|
| en-US 회귀 | `Hello world. This is a test of speech recognition.` + partial + CLOSED |
| ko-KR 회귀 | `안녕하세요. 삼성 키보드 음성 인식 테스트입니다. 오늘 날씨는 맑습니다.` + partial + CLOSED |
| ko-KR 단독 / + en-US 동일 혼합 음원 | 둘 다 `오늘 회의에서는 머신러닝과 오픈 소스 프로젝트에 대해 이야기합니다. Please send me the updated report tomorrow.` |
| `서울을 부산으로 바꿔줘.` | intent 2, `오늘 회의는 오후 세 시입니다. 장소는 부산입니다.` |
| `마지막 문장을 지워줘.` | intent 2, `오늘 회의는 오후 세 시입니다.` |
| `이 내용을 정중한 안내문으로 다시 써줘.` | intent 3, edited history=`오늘 회의는 오후 3시에 서울에서 진행될 예정입니다. 참석자분들께서는 시간을 확인해 주시기 바랍니다.` |

편집 시험의 history는 `오늘 회의는 오후 세 시입니다. 장소는 서울입니다.`였다. rewrite의 FinalAlternative.transcript는 원문+명령이었고 edited history만 실제 수정 문장이므로 intent 2/3에서 transcript를 추가 commit하면 안 된다. 혼합 시험은 추가 언어의 **전송/수락**을 확인했지만 두 결과가 동일하여 정확도 개선은 입증하지 않는다. 로그는 `module/tools/out/regression/{en,ko,mixed-primary,mixed-additional,edit-replace,edit-delete,edit-rewrite}.log`.

하네스 환경변수: `S3_ADDITIONAL=en-US`, `S3_HISTORY_FILE=/absolute/history.txt`, `S3_AUDIO=/absolute/audio.mp3`, `S3_PHRASE=...`. 기본 Google TTS; `S3_TTS=flite`는 영어 테스트용. 서버 오류/응답 없이 종료/timeout은 nonzero exit. 긴 실제 문서를 서버로 보내지 않고 합성 최근 범위만 시험했다. 장문 앞뒤 보존은 `VoiceLogicTest`에서 별도로 검증한다.
