package dev.sampple.rambler;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** UI copy shared by the settings app and Samsung Keyboard hooks; no module resources required. */
public final class I18n {
    private static final Map<String, String[]> TEXT = new HashMap<>();
    static {
        put("google_api_key", "Google API 키", "Google API key", "Google API キー", "Google API 密钥");
        put("api_key_built_in", "앱에 포함된 키 사용 중", "Using the key built into this app", "アプリに組み込まれたキーを使用中", "正在使用应用内置密钥");
        put("api_key_custom", "직접 입력한 키 사용 중", "Using your own key", "自分で入力したキーを使用中", "正在使用你输入的密钥");
        put("api_key_missing", "필요 · 음성 입력 전에 입력하세요", "Required · enter it before using voice input", "必須 · 音声入力の前に入力してください", "必填 · 使用语音输入前请输入");
        put("api_key_help", "Gboard 앱에 들어 있는 X-Goog-Api-Key 값(AIza로 시작)을 입력하세요. 이 앱은 키를 포함해 배포하지 않습니다. 비워 두면 빌드에 포함된 키를 사용합니다.", "Enter the X-Goog-Api-Key value found in the Gboard app (starts with AIza). HoneyBoard X does not ship this key. Leave empty to use a key built into your own build.", "Gboard アプリに含まれる X-Goog-Api-Key の値（AIza で始まる）を入力してください。HoneyBoard X はこのキーを同梱していません。空欄にするとビルドに組み込んだキーを使います。", "请输入 Gboard 应用中的 X-Goog-Api-Key 值（以 AIza 开头）。HoneyBoard X 不附带此密钥。留空则使用你自己构建时内置的密钥。");
        put("api_key_invalid", "API 키 형식이 올바르지 않습니다 (AIza로 시작하는 39자).", "That doesn't look like a Google API key (39 characters starting with AIza).", "Google API キーの形式ではありません（AIza で始まる 39 文字）。", "这不是有效的 Google API 密钥（以 AIza 开头的 39 个字符）。");
        put("no_api_key_voice", "Google API 키가 없습니다 · HoneyBoard X 설정에서 입력하세요", "No Google API key · add one in HoneyBoard X settings", "Google API キーがありません · HoneyBoard X の設定で入力してください", "没有 Google API 密钥 · 请在 HoneyBoard X 设置中添加");
        put("close_voice_input", "음성기록 모드 종료", "Close voice input", "音声入力を閉じる", "关闭语音输入");
        put("speak_now", "지금 말하세요", "Speak now", "話してください", "请开始说话");
        put("finish_voice_input", "음성 입력 완료", "Finish voice input", "音声入力を完了", "完成语音输入");
        put("paused", "일시중지됨", "Paused", "一時停止中", "已暂停");
        put("listening", "듣는 중…", "Listening…", "聞き取り中…", "正在聆听…");
        put("tap_to_finish_hold_to_resume", "탭하여 음성 입력 완료 · 길게 눌러 재개", "Tap to finish · Hold to resume", "タップで完了 · 長押しで再開", "轻点完成 · 长按继续");
        put("tap_to_finish_hold_to_pause", "탭하여 음성 입력 완료 · 길게 눌러 일시중지", "Tap to finish · Hold to pause", "タップで完了 · 長押しで一時停止", "轻点完成 · 长按暂停");
        put("low_fastest", "낮음 · 가장 빠름", "Low · fastest", "低 · 最速", "低 · 最快");
        put("medium", "보통", "Medium", "標準", "中");
        put("high", "높음", "High", "高", "高");
        put("very_high", "매우 높음", "Very high", "非常に高い", "很高");
        put("maximum_most_accurate", "최대 · 가장 정확", "Maximum · most accurate", "最大 · 最も正確", "最高 · 最准确");
        put("voice_input", "음성 입력", "Voice input", "音声入力", "语音输入");
        put("recognition_language", "인식 언어", "Recognition language", "認識する言語", "识别语言");
        put("additional_languages", "함께 인식할 언어", "Additional languages", "追加の認識言語", "其他识别语言");
        put("finish_when_silent", "말을 멈추면 자동 완료", "Finish when silent", "無音で自動終了", "静音后自动完成");
        put("aurora_effect", "오로라 효과", "Aurora effect", "オーロラ効果", "极光效果");
        put("animated_light_on_the_toolbar_during_voice_input", "음성 입력 중 툴바에 흐르는 빛", "Animated light on the toolbar during voice input", "音声入力中にツールバーに流れる光", "语音输入时工具栏上的流光");
        put("vibration", "진동", "Vibration", "バイブレーション", "振动");
        put("short_vibration_on_start_and_finish", "시작·완료 시 짧은 진동", "Short vibration on start and finish", "開始・終了時に短く振動", "开始和完成时短暂振动");
        put("sound", "소리", "Sound", "サウンド", "声音");
        put("silent_in_mute_and_do_not_disturb_modes", "무음·방해 금지 모드에서는 울리지 않음", "Silent in mute and Do Not Disturb modes", "サイレント・おやすみモードでは鳴らさない", "静音或勿扰模式下不发声");
        put("audio_and_the_paragraph_before_the_cursor_up_to_2_000_char", "음성과 커서 앞 문단(최대 2,000자)이 Google 음성 인식 서버로 전송됩니다. 마이크 버튼을 누르면 입력이 완료되고, 길게 누르면 일시중지됩니다.", "Audio and the paragraph before the cursor (up to 2,000 characters) are sent to Google speech recognition. Tap the mic to finish or hold it to pause.", "音声とカーソル直前の段落（最大2,000文字）が Google 音声認識サーバーに送信されます。マイクをタップすると完了、長押しすると一時停止します。", "音频及光标前的段落（最多 2,000 字符）将发送至 Google 语音识别服务器。轻点麦克风完成输入，长按可暂停。");
        put("ai_translation", "AI 번역", "AI translation", "AI 翻訳", "AI 翻译");
        put("codex_translation", "Codex 번역", "Codex translation", "Codex 翻訳", "Codex 翻译");
        put("translate_with_codex_in_samsung_keyboard", "삼성 키보드 번역창에서 Codex로 번역", "Translate with Codex in Samsung Keyboard", "Samsung キーボードの翻訳画面で Codex を使用", "在三星键盘的翻译窗口中使用 Codex");
        put("account", "계정", "Account", "アカウント", "账户");
        put("model", "모델", "Model", "モデル", "模型");
        put("reasoning_effort", "추론 강도", "Reasoning effort", "推論の強度", "推理强度");
        put("translation_instructions", "번역 지침", "Translation instructions", "翻訳指示", "翻译指令");
        put("use_samsung_translation_on_failure", "실패하면 삼성 번역 사용", "Use Samsung translation on failure", "失敗時は Samsung 翻訳を使用", "失败时使用三星翻译");
        put("retry_with_default_translation_if_codex_fails", "Codex 오류 시 기본 번역으로 다시 시도", "Retry with default translation if Codex fails", "Codex エラー時は標準の翻訳で再試行", "Codex 出错时使用默认翻译重试");
        put("text_to_translate_is_sent_to_chatgpt_codex_when_the_codex", "번역할 문장은 ChatGPT(Codex) 서버로 전송됩니다. Codex 5시간 또는 주간 사용량이 0%가 되면 gpt-reserve 모델로 자동 전환됩니다. 로그인 토큰은 이 앱 안에서 Android Keystore로 암호화되며 키보드로 전달되지 않습니다.", "Text to translate is sent to ChatGPT (Codex). When the Codex 5-hour or weekly allowance reaches 0%, translation switches to gpt-reserve. Login tokens are encrypted with Android Keystore in this app and are never sent to the keyboard.", "翻訳するテキストは ChatGPT（Codex）に送信されます。Codex の5時間または週間の残量が0%になると、gpt-reserve に自動で切り替わります。ログイントークンはこのアプリ内で Android Keystore により暗号化され、キーボードには渡されません。", "待翻译文本会发送至 ChatGPT（Codex）。Codex 的 5 小时或每周配额降至 0% 时，将自动切换至 gpt-reserve。登录令牌在本应用中通过 Android Keystore 加密，不会传给键盘。");
        put("about", "정보", "About", "情報", "关于");
        put("lsposed_connection", "LSPosed 연결", "LSPosed connection", "LSPosed 接続", "LSPosed 连接");
        put("reload_settings", "설정 다시 불러오기", "Reload settings", "設定を再読み込み", "重新加载设置");
        put("read_the_values_applied_to_the_keyboard", "키보드에 적용된 값을 다시 읽음", "Read the values applied to the keyboard", "キーボードに適用された値を再読み込み", "重新读取键盘当前设置");
        put("version", "버전", "Version", "バージョン", "版本");
        put("samsung_keyboard_5_9_40_75", " · 삼성 키보드 5.9.40.75", " · Samsung Keyboard 5.9.40.75", " · Samsung キーボード 5.9.40.75", " · 三星键盘 5.9.40.75");
        put("created_by", "만든 사람", "Created by", "製作者", "开发者");
        put("samsung_keyboard_extension_voice_input_and_ai_translation", "삼성 키보드 확장 · 음성 입력과 AI 번역", "Samsung Keyboard extension · Voice input and AI translation", "Samsung キーボード拡張 · 音声入力と AI 翻訳", "三星键盘扩展 · 语音输入与 AI 翻译");
        put("checking_status", "상태 확인 중…", "Checking status…", "状態を確認中…", "正在检查状态…");
        put("enable_the_module_in_lsposed_and_add_samsung_keyboard_to_i", "LSPosed에서 모듈을 켜고 범위에 삼성 키보드를 선택하세요.", "Enable the module in LSPosed and add Samsung Keyboard to its scope.", "LSPosed でモジュールを有効にし、対象に Samsung キーボードを追加してください。", "在 LSPosed 中启用模块，并将三星键盘加入作用域。");
        put("follow_keyboard_language", "키보드 입력 언어 따라가기", "Follow keyboard language", "キーボードの入力言語に合わせる", "跟随键盘输入语言");
        put("off", "사용 안 함", "Off", "オフ", "关闭");
        put("off_finish_with_mic_button", "사용 안 함 · 마이크 버튼으로만 완료", "Off · finish with mic button", "オフ · マイクボタンで終了", "关闭 · 使用麦克风按钮完成");
        put("default_instructions", "기본 지침", "Default instructions", "標準の指示", "默认指令");
        put("custom", "사용자 지정", "Custom", "カスタム", "自定义");
        put("signed_in", "로그인됨", "Signed in", "ログイン済み", "已登录");
        put("finish_sign_in_paste_callback_url", "로그인 마무리 필요 · 완료 주소 붙여넣기", "Finish sign-in · paste callback URL", "ログインを完了 · 完了 URL を貼り付け", "完成登录 · 粘贴回调地址");
        put("sign_in_required", "로그인 필요", "Sign in required", "ログインが必要", "需要登录");
        put("connecting", "연결 중…", "Connecting…", "接続中…", "正在连接…");
        put("disconnected_tap_to_retry", "연결 안 됨 · 누르면 다시 시도", "Disconnected · tap to retry", "未接続 · タップして再試行", "未连接 · 轻点重试");
        put("connecting_to_lsposed", "LSPosed에 연결하는 중…", "Connecting to LSPosed…", "LSPosed に接続中…", "正在连接 LSPosed…");
        put("please_wait", "잠시만 기다려 주세요.", "Please wait.", "しばらくお待ちください。", "请稍候。");
        put("lsposed_connection_required", "LSPosed 연결 필요", "LSPosed connection required", "LSPosed への接続が必要", "需要连接 LSPosed");
        put("enable_honeyboard_x_in_lsposed_and_add_samsung_keyboard_to", "LSPosed에서 HoneyBoard X를 켜고 범위에 삼성 키보드를 선택하세요. 연결되면 자동으로 불러옵니다.", "Enable HoneyBoard X in LSPosed and add Samsung Keyboard to its scope. Settings will load when connected.", "LSPosed で HoneyBoard X を有効にし、対象に Samsung キーボードを追加してください。接続後に設定を読み込みます。", "在 LSPosed 中启用 HoneyBoard X 并将三星键盘加入作用域。连接后将自动加载设置。");
        put("checking", "확인 중…", "Checking…", "確認中…", "正在检查…");
        put("active", "사용 중", "Active", "使用中", "使用中");
        put("settings_save_immediately_and_apply_to_the_next_voice_inpu", "설정은 바로 저장되며 다음 음성 입력·번역부터 적용됩니다.", "Settings save immediately and apply to the next voice input or translation.", "設定はすぐに保存され、次の音声入力・翻訳から適用されます。", "设置会立即保存，并在下次语音输入或翻译时生效。");
        put("enable_honeyboard_x_in_lsposed_add_samsung_keyboard_to_its", "LSPosed에서 HoneyBoard X를 켜고 범위에 삼성 키보드를 선택한 뒤 이 화면을 다시 여세요.", "Enable HoneyBoard X in LSPosed, add Samsung Keyboard to its scope, then reopen this screen.", "LSPosed で HoneyBoard X を有効にし、対象に Samsung キーボードを追加してから、この画面を開き直してください。", "在 LSPosed 中启用 HoneyBoard X，将三星键盘加入作用域后重新打开此页面。");
        put("could_not_save_settings", "설정을 저장하지 못했습니다.", "Could not save settings.", "設定を保存できませんでした。", "无法保存设置。");
        put("unknown_error", "알 수 없는 오류", "Unknown error", "不明なエラー", "未知错误");
        put("connect_to_lsposed_to_make_changes", "LSPosed 연결 후 변경할 수 있습니다.", "Connect to LSPosed to make changes.", "変更するには LSPosed に接続してください。", "连接 LSPosed 后才能修改。");
        put("enter_manually", "직접 입력…", "Enter manually…", "手動で入力…", "手动输入…");
        put("cancel", "취소", "Cancel", "キャンセル", "取消");
        put("additional_languages_up_to_3", "함께 인식할 언어 (최대 3개)", "Additional languages (up to 3)", "追加の認識言語（最大3つ）", "其他识别语言（最多 3 种）");
        put("done", "완료", "Done", "完了", "完成");
        put("e_g_ko_kr", "예: ko-KR", "e.g. ko-KR", "例: ko-KR", "例如：ko-KR");
        put("e_g_en_us_ja_jp", "예: en-US,ja-JP", "e.g. en-US,ja-JP", "例: en-US,ja-JP", "例如：en-US,ja-JP");
        put("language_code_bcp_47", "언어 코드 (BCP 47)", "Language code (BCP 47)", "言語コード（BCP 47）", "语言代码（BCP 47）");
        put("ok", "확인", "OK", "OK", "确定");
        put("s", "초", "s", "秒", "秒");
        put("gpt_6_luna_fast", "gpt-6-luna · 빠름", "gpt-6-luna · Fast", "gpt-6-luna · 高速", "gpt-6-luna · 快速");
        put("gpt_6_sol_balanced", "gpt-6-sol · 균형", "gpt-6-sol · Balanced", "gpt-6-sol · バランス", "gpt-6-sol · 均衡");
        put("gpt_6_astra_high_quality", "gpt-6-astra · 고품질", "gpt-6-astra · High quality", "gpt-6-astra · 高品質", "gpt-6-astra · 高质量");
        put("translation_model", "번역 모델", "Translation model", "翻訳モデル", "翻译模型");
        put("account_models", "계정 모델 목록", "Account models", "アカウントのモデル", "账户模型列表");
        put("no_models_available", "사용 가능한 모델이 없습니다", "No models available", "利用可能なモデルがありません", "没有可用模型");
        put("models_available_to_this_account", "계정에서 사용 가능한 모델", "Models available to this account", "このアカウントで利用可能なモデル", "此账户可用的模型");
        put("model_id", "모델 ID", "Model ID", "モデル ID", "模型 ID");
        put("source_language_and_target_language_are_replaced_with_lang", "{source_language}, {target_language} 자리에 언어 이름이 들어갑니다.", "{source_language} and {target_language} are replaced with language names.", "{source_language} と {target_language} には言語名が入ります。", "{source_language} 和 {target_language} 将替换为语言名称。");
        put("save", "저장", "Save", "保存", "保存");
        put("reset_to_default", "기본값으로", "Reset to default", "初期値に戻す", "恢复默认");
        put("usage_and_models", "사용량 및 모델", "Usage and models", "使用量とモデル", "用量与模型");
        put("sign_out", "로그아웃", "Sign out", "ログアウト", "退出登录");
        put("chatgpt_account", "ChatGPT 계정", "ChatGPT account", "ChatGPT アカウント", "ChatGPT 账户");
        put("sign_out_2", "로그아웃할까요?", "Sign out?", "ログアウトしますか？", "确定退出登录？");
        put("this_removes_sign_in_details_saved_on_this_device", "이 기기에 저장된 로그인 정보를 삭제합니다.", "This removes sign-in details saved on this device.", "この端末に保存されたログイン情報を削除します。", "这将删除保存在此设备上的登录信息。");
        put("sign_in_to_chatgpt", "ChatGPT로 로그인", "Sign in to ChatGPT", "ChatGPT にログイン", "登录 ChatGPT");
        put("sign_in_in_your_browser_when_you_see_can_t_connect_to_loca", "브라우저에서 로그인한 뒤, 'localhost에 연결할 수 없음' 화면이 나오면 주소창의 전체 주소를 복사해 이 앱에 붙여넣으세요.", "Sign in in your browser. When you see “Can't connect to localhost,” copy the full URL from the address bar and paste it here.", "ブラウザーでログイン後、「localhost に接続できません」と表示されたら、アドレスバーの URL 全体をコピーしてこのアプリに貼り付けてください。", "在浏览器中登录后，若显示“无法连接到 localhost”，请复制地址栏中的完整网址并粘贴到此应用。");
        put("open_browser", "브라우저 열기", "Open browser", "ブラウザーを開く", "打开浏览器");
        put("5_hours", "5시간", "5 hours", "5時間", "5 小时");
        put("weekly", "주간", "Weekly", "週間", "每周");
        put("d", "일", "d", "日", "天");
        put("h", "시간", "h", "時間", "小时");
        put("code_review", "코드 리뷰", "Code review", "コードレビュー", "代码审查");
        put("account_usage", "계정 사용량", "Account usage", "アカウント使用量", "账户用量");
        put("mmm_d_h_mm_a", "M월 d일 a h:mm", "MMM d, h:mm a", "M月d日 a h:mm", "M月d日 ah:mm");
        put("close", "닫기", "Close", "閉じる", "关闭");
        put("additional_limit", "추가 한도", "Additional limit", "追加の上限", "额外限额");
        put("codex_usage_is_exhausted_translation_will_use_gpt_reserve", "Codex 사용량이 소진되어 번역은 gpt-reserve 모델로 자동 전환됩니다. 초기화되면 원래 모델로 돌아갑니다.", "Codex usage is exhausted. Translation will use gpt-reserve until the allowance resets.", "Codex の使用枠を使い切りました。リセットまで翻訳には gpt-reserve を使用します。", "Codex 用量已耗尽。重置前翻译将使用 gpt-reserve。");
        put("no_usage_data_available", "표시할 사용량 정보가 없습니다.", "No usage data available.", "表示できる使用量がありません。", "没有可显示的用量信息。");
        put("remaining", " 남음", " remaining", " 残り", " 剩余");
        put("available_models_tap_to_choose", "사용 가능한 모델 · 눌러서 번역 모델로 선택", "Available models · tap to choose", "利用可能なモデル · タップして選択", "可用模型 · 轻点选择");
        put("paste_callback_url", "완료 주소 붙여넣기", "Paste callback URL", "完了 URL を貼り付け", "粘贴回调地址");
        put("paste_the_localhost_callback_url_from_your_browser_s_addre", "브라우저 주소창의 localhost 완료 주소를 붙여넣으세요. code와 state가 들어 있는 쿼리 부분만 붙여넣어도 됩니다. 코드만으로는 안전하게 완료할 수 없습니다.", "Paste the localhost callback URL from your browser's address bar. You can also paste just the query containing code and state. A code alone is not secure enough.", "ブラウザーのアドレスバーにある localhost の完了 URL を貼り付けてください。code と state を含むクエリ部分だけでもかまいません。コードだけでは安全に完了できません。", "粘贴浏览器地址栏中的 localhost 回调网址，也可以只粘贴包含 code 和 state 的查询部分。仅有 code 无法安全完成登录。");
        put("complete_sign_in", "로그인 완료", "Complete sign-in", "ログインを完了", "完成登录");
        put("signed_in_2", "로그인되었습니다", "Signed in", "ログインしました", "已登录");
        put("start_over", "처음부터", "Start over", "最初から", "重新开始");
        put("later", "나중에", "Later", "後で", "稍后");
        put("voice_input_isn_t_available_in_password_fields", "비밀번호 입력창에서는 음성 입력을 사용할 수 없습니다.", "Voice input isn't available in password fields.", "パスワード欄では音声入力を使用できません。", "密码输入框中无法使用语音输入。");
        put("allow_microphone_access_in_samsung_keyboard_settings_permi", "삼성 키보드 앱 설정 → 권한에서 마이크를 허용한 뒤 다시 눌러 주세요.", "Allow microphone access in Samsung Keyboard settings → Permissions, then try again.", "Samsung キーボードの設定 → 権限でマイクを許可してから、もう一度お試しください。", "请在三星键盘设置 → 权限中允许使用麦克风，然后重试。");
        put("this_field_doesn_t_support_safe_voice_editing", "이 입력창은 안전한 음성 편집을 지원하지 않습니다.", "This field doesn't support safe voice editing.", "この入力欄は安全な音声編集に対応していません。", "此输入框不支持安全的语音编辑。");
        put("can_t_show_the_voice_input_panel", "음성 입력 패널을 표시할 수 없습니다.", "Can't show the voice input panel.", "音声入力パネルを表示できません。", "无法显示语音输入面板。");
        put("processing_your_last_words", "마지막 말을 처리하고 있습니다…", "Processing your last words…", "最後の発話を処理中…", "正在处理最后一句话…");
        put("response_timed_out_tap_to_try_again", "응답 시간이 초과되었습니다. 다시 눌러 주세요.", "Response timed out. Tap to try again.", "応答がタイムアウトしました。タップして再試行してください。", "响应超时，请轻点重试。");
        put("finishing_your_last_words_hold_to_resume", "마지막 말을 처리한 뒤 길게 눌러 재개하세요…", "Finishing your last words… Hold to resume.", "最後の発話を処理中… 長押しで再開してください。", "正在处理最后一句话…长按继续。");
        put("allow_microphone_access_in_samsung_keyboard_settings", "삼성 키보드 설정에서 마이크 권한을 허용해 주세요.", "Allow microphone access in Samsung Keyboard settings.", "Samsung キーボードの設定でマイクを許可してください。", "请在三星键盘设置中允许使用麦克风。");
        put("microphone_permission_is_required", "마이크 권한이 필요합니다.", "Microphone permission is required.", "マイクの権限が必要です。", "需要麦克风权限。");
        put("network_or_recognition_error_tap_mic_to_retry", "네트워크 또는 음성 인식 오류 · 마이크를 눌러 재시도", "Network or recognition error · tap mic to retry", "通信または音声認識エラー · マイクをタップして再試行", "网络或语音识别出错 · 轻点麦克风重试");
        put("starting", "초기화 중…", "Starting…", "初期化中…", "正在初始化…");
        put("refreshing_voice_connection", "음성 연결을 갱신하고 있습니다…", "Refreshing voice connection…", "音声接続を更新中…", "正在刷新语音连接…");
        put("server_response_timed_out", "서버 응답 시간이 초과되었습니다.", "Server response timed out.", "サーバーの応答がタイムアウトしました。", "服务器响应超时。");
        put("voice_upload_was_interrupted_tap_to_try_again", "음성 전송이 중단되었습니다. 다시 눌러 주세요.", "Voice upload was interrupted. Tap to try again.", "音声の送信が中断されました。タップして再試行してください。", "语音传输中断，请轻点重试。");
        put("mic_opus_encoder_error_tap_mic_to_retry", "마이크/Opus 인코더 오류 · 마이크를 눌러 재시도", "Mic/Opus encoder error · tap mic to retry", "マイク/Opus エンコーダーエラー · タップして再試行", "麦克风/Opus 编码器出错 · 轻点重试");
        put("waiting_for_transcription", "인식 결과를 기다리는 중…", "Waiting for transcription…", "認識結果を待機中…", "正在等待识别结果…");
        put("voice_recording_stopped_unexpectedly", "음성 녹음이 중단되었습니다.", "Voice recording stopped unexpectedly.", "音声録音が中断されました。", "语音录制已中断。");
        put("couldn_t_verify_the_text_before_the_cursor_voice_edit_wasn", "커서 앞 원문을 확인할 수 없어 음성 편집을 적용하지 않았습니다.", "Couldn't verify the text before the cursor. Voice edit wasn't applied.", "カーソル前の原文を確認できなかったため、音声編集を適用しませんでした。", "无法验证光标前的原文，未应用语音编辑。");
        put("transcription_is_too_long", "인식 결과가 너무 깁니다.", "Transcription is too long.", "認識結果が長すぎます。", "识别结果过长。");
        put("recognized", "인식 완료", "Recognized", "認識完了", "识别完成");
        put("voice_input_error_try_again", "음성 입력 오류 · 다시 시도해 주세요.", "Voice input error · try again.", "音声入力エラー · 再試行してください。", "语音输入出错 · 请重试。");
        put("choose_up_to_3_additional_languages", "추가 언어는 최대 3개입니다.", "Choose up to 3 additional languages.", "追加の言語は最大3つまでです。", "最多可选 3 种其他语言。");
        put("silence_timeout_must_be_off_0_or_2_30_seconds", "무음 시간은 0(끄기) 또는 2~30초입니다.", "Silence timeout must be off (0) or 2–30 seconds.", "無音時間はオフ（0）または2～30秒にしてください。", "静音时长须为 0（关闭）或 2–30 秒。");
        put("check_the_language_tag_e_g_ko_kr_en_us", "언어 태그를 확인하세요 (예: ko-KR, en-US).", "Check the language tag (e.g. ko-KR, en-US).", "言語タグを確認してください（例: ko-KR、en-US）。", "请检查语言标签（如 ko-KR、en-US）。");
        put("invalid_language_tag", "잘못된 언어 태그: ", "Invalid language tag: ", "無効な言語タグ: ", "无效的语言标签：");
        put("rambler_voice_input", "Rambler 음성 입력", "Rambler voice input", "Rambler 音声入力", "Rambler 语音输入");
        put("not_connected_to_lsposed_enable_the_module_then_reopen_set", "LSPosed 서비스에 연결되지 않았습니다. 모듈을 활성화한 뒤 설정을 다시 여세요.", "Not connected to LSPosed. Enable the module, then reopen settings.", "LSPosed に接続されていません。モジュールを有効にして設定を開き直してください。", "未连接 LSPosed。请启用模块并重新打开设置。");
        put("this_framework_doesn_t_support_remote_settings", "이 프레임워크는 원격 설정을 지원하지 않습니다.", "This framework doesn't support remote settings.", "このフレームワークはリモート設定に対応していません。", "此框架不支持远程设置。");
        put("lsposed_disconnected_check_module_status", "LSPosed 미연결 · 모듈 활성화 확인", "LSPosed disconnected · check module status", "LSPosed 未接続 · モジュールを確認", "LSPosed 未连接 · 请检查模块状态");
        put("samsung_keyboard_in_scope", " · 삼성 키보드 범위 활성", " · Samsung Keyboard in scope", " · Samsung キーボードを対象に設定済み", " · 已将三星键盘加入作用域");
        put("samsung_keyboard_not_in_scope", " · 삼성 키보드 범위 미지정", " · Samsung Keyboard not in scope", " · Samsung キーボードが対象外", " · 三星键盘未加入作用域");
        put("unsupported_request", "지원하지 않는 요청", "Unsupported request", "サポートされていないリクエスト", "不支持的请求");
        put("invalid_request", "잘못된 요청", "Invalid request", "無効なリクエスト", "无效请求");
        put("translation_is_off", "번역이 꺼져 있습니다", "Translation is off", "翻訳がオフです", "翻译已关闭");
        put("invalid_translation_length_or_language", "번역 입력 길이/언어 오류", "Invalid translation length or language", "翻訳文の長さまたは言語が無効です", "翻译文本长度或语言无效");
        put("canceled", "취소됨", "Canceled", "キャンセルされました", "已取消");
        put("translation_failed", "번역 실패", "Translation failed", "翻訳に失敗しました", "翻译失败");
        put("codex_error", "Codex 오류", "Codex error", "Codex エラー", "Codex 错误");
        put("codex_response_is_incomplete", "Codex 응답이 완료되지 않았습니다", "Codex response is incomplete", "Codex の応答が完了していません", "Codex 响应未完成");
        put("invalid_codex_response_status", "Codex 응답 상태 오류", "Invalid Codex response status", "Codex の応答状態が無効です", "Codex 响应状态无效");
        put("connection_closed_before_codex_finished_responding", "Codex 응답이 완료되기 전에 연결이 종료되었습니다", "Connection closed before Codex finished responding", "Codex の応答完了前に接続が切れました", "Codex 响应完成前连接已断开");
        put("codex_returned_an_empty_translation", "Codex가 빈 번역을 반환했습니다", "Codex returned an empty translation", "Codex が空の翻訳を返しました", "Codex 返回了空翻译");
        put("voice_input_and_codex_translation_settings", "음성 입력 및 Codex 번역 설정", "Voice input and Codex translation settings", "音声入力と Codex 翻訳の設定", "语音输入与 Codex 翻译设置");
        put("translation_timed_out", "번역 시간 초과", "Translation timed out", "翻訳がタイムアウトしました", "翻译超时");
        put("can_t_connect_to_translation_service", "번역 서비스에 연결할 수 없습니다", "Can't connect to translation service", "翻訳サービスに接続できません", "无法连接翻译服务");
        put("default_translation_timed_out", "기본 번역 시간 초과", "Default translation timed out", "標準の翻訳がタイムアウトしました", "默认翻译超时");
        put("couldn_t_apply_translation", "번역 적용 실패", "Couldn't apply translation", "翻訳を適用できませんでした", "无法应用翻译");
        put("callback_url_is_too_long_paste_only_the_address_bar_url", "완료 주소가 너무 깁니다. 주소창의 URL만 붙여넣으세요.", "Callback URL is too long. Paste only the address bar URL.", "完了 URL が長すぎます。アドレスバーの URL だけを貼り付けてください。", "回调网址过长，请只粘贴地址栏的网址。");
        put("enter_the_sign_in_callback_url_from_your_browser", "브라우저의 로그인 완료 주소를 입력하세요.", "Enter the sign-in callback URL from your browser.", "ブラウザーのログイン完了 URL を入力してください。", "请输入浏览器中的登录回调网址。");
        put("check_the_callback_url_format", "완료 주소 형식을 확인하세요.", "Check the callback URL format.", "完了 URL の形式を確認してください。", "请检查回调网址格式。");
        put("enter_the_localhost_1455_auth_callback_url_a_code_alone_is", "localhost:1455/auth/callback 완료 주소를 입력하세요. 코드만으로는 안전하게 로그인할 수 없습니다.", "Enter the localhost:1455/auth/callback URL. A code alone isn't secure enough to sign in.", "localhost:1455/auth/callback の完了 URL を入力してください。コードだけでは安全にログインできません。", "请输入 localhost:1455/auth/callback 回调网址。仅凭 code 无法安全登录。");
        put("too_many_parameters_in_the_callback_url", "완료 주소에 너무 많은 항목이 있습니다.", "Too many parameters in the callback URL.", "完了 URL の項目が多すぎます。", "回调网址中包含过多参数。");
        put("check_the_callback_url_query_format", "완료 주소의 쿼리 형식을 확인하세요.", "Check the callback URL query format.", "完了 URL のクエリ形式を確認してください。", "请检查回调网址的查询参数格式。");
        put("sign_in_state_doesn_t_match_copy_the_callback_url_from_the", "로그인 상태가 맞지 않습니다. 처음 열었던 브라우저 탭의 완료 주소를 복사하거나 로그인을 처음부터 다시 시작하세요.", "Sign-in state doesn't match. Copy the callback URL from the original browser tab or start again.", "ログイン状態が一致しません。最初に開いたブラウザータブの完了 URL をコピーするか、最初からやり直してください。", "登录状态不匹配。请复制最初打开的浏览器标签页中的回调网址，或重新开始登录。");
        put("sign_in_denied", "로그인이 거부되었습니다: ", "Sign-in denied: ", "ログインが拒否されました: ", "登录被拒绝：");
        put("callback_url_has_no_authorization_code_paste_the_full_url", "완료 주소에 인증 코드가 없습니다. code와 state가 포함된 전체 URL을 붙여넣으세요.", "Callback URL has no authorization code. Paste the full URL with code and state.", "完了 URL に認証コードがありません。code と state を含む URL 全体を貼り付けてください。", "回调网址中没有授权码。请粘贴包含 code 和 state 的完整网址。");
        put("sign_in_expired_start_again_from_account", "로그인 시간이 만료되었습니다. 계정에서 로그인을 처음부터 시작하세요.", "Sign-in expired. Start again from Account.", "ログインの有効期限が切れました。アカウントからやり直してください。", "登录已过期。请在账户页面重新开始。");
        put("sign_in_to_your_codex_account", "Codex 계정 로그인이 필요합니다.", "Sign in to your Codex account.", "Codex アカウントにログインしてください。", "请登录 Codex 账户。");
        put("sign_in_expired_sign_in_again", "로그인이 만료되었습니다. 다시 로그인하세요.", "Sign-in expired. Sign in again.", "ログインの有効期限が切れました。再ログインしてください。", "登录已过期，请重新登录。");
        put("request_failed", "요청 실패", "Request failed", "リクエストに失敗しました", "请求失败");
        put("sign_in_response_has_no_access_token", "로그인 응답에 access_token이 없습니다.", "Sign-in response has no access_token.", "ログイン応答に access_token がありません。", "登录响应中没有 access_token。");
        put("korean", "한국어", "Korean", "韓国語", "韩语");
        put("silence_finish", "%d초 동안 조용하면 완료", "Finish after %d seconds of silence", "%d秒間の無音で終了", "静音 %d 秒后完成");
        put("duration_days", "%d일", "%d days", "%d日", "%d 天");
        put("duration_hours", "%d시간", "%d hours", "%d時間", "%d 小时");
        put("duration_minutes", "%d분", "%d min", "%d分", "%d 分钟");
        put("reset_days_hours", "%d일 %d시간 후", "in %d days %d hours", "あと%d日%d時間", "%d 天 %d 小时后");
        put("reset_hours_minutes", "%d시간 %d분 후", "in %d hours %d min", "あと%d時間%d分", "%d 小时 %d 分钟后");
        put("reset_minutes", "%d분 후", "in %d min", "あと%d分", "%d 分钟后");
        put("reset_at", "%s 초기화 · %s", "Resets %s · %s", "%sにリセット · %s", "%s 重置 · %s");
        put("model_selected", "번역 모델: %s", "Translation model: %s", "翻訳モデル: %s", "翻译模型：%s");
        put("auth_duplicate_key", "완료 주소에 %s가 중복되었습니다.", "Duplicate callback URL parameter: %s.", "完了 URL のパラメーター「%s」が重複しています。", "回调网址中的参数“%s”重复。");
        put("auth_http", "로그인 서버 HTTP %d", "Sign-in server HTTP %d", "ログインサーバー HTTP %d", "登录服务器 HTTP %d");
        put("auth_http_detail", "로그인 서버 HTTP %d: %s", "Sign-in server HTTP %d: %s", "ログインサーバー HTTP %d: %s", "登录服务器 HTTP %d：%s");
        put("auth_code_expired", "인증 코드가 만료되었거나 이미 사용되었습니다 (HTTP %d). 로그인을 다시 시작하세요. %s", "Authorization code expired or was already used (HTTP %d). Start sign-in again. %s", "認証コードの期限が切れたか、すでに使用されています（HTTP %d）。ログインを最初からやり直してください。%s", "授权码已过期或已使用（HTTP %d）。请重新开始登录。%s");
        put("translation_progress", "번역 중 %d초", "Translating · %d s", "翻訳中 · %d秒", "正在翻译 · %d 秒");
        put("fallback_progress", "기본 번역 중 %d초", "Default translation · %d s", "標準の翻訳中 · %d秒", "正在使用默认翻译 · %d 秒");
    }

    private I18n() {}

    private static void put(String key, String ko, String en, String ja, String zh) {
        TEXT.put(key, new String[]{ko, en, ja, zh});
    }

    public static String t(String key, Object... args) {
        return t(Locale.getDefault(), key, args);
    }

    public static String t(Context context, String key, Object... args) {
        Locale locale = context == null ? Locale.getDefault() : context.getResources().getConfiguration().getLocales().get(0);
        return t(locale, key, args);
    }

    /** Formatting follows the same language fallback as message selection. */
    static Locale displayLocale() { return displayLocale(Locale.getDefault()); }

    private static Locale displayLocale(Locale locale) {
        if (locale == null) return Locale.ENGLISH;
        String lang = locale.getLanguage();
        if ("ko".equals(lang) || "ja".equals(lang)) return locale;
        if ("zh".equals(lang) && !"Hant".equalsIgnoreCase(locale.getScript())
                && !"TW".equalsIgnoreCase(locale.getCountry()) && !"HK".equalsIgnoreCase(locale.getCountry())
                && !"MO".equalsIgnoreCase(locale.getCountry())) return locale;
        return Locale.ENGLISH;
    }

    public static String t(Locale locale, String key, Object... args) {
        String[] values = TEXT.get(key);
        if (values == null) throw new IllegalArgumentException("Unknown i18n key: " + key);
        Locale display = displayLocale(locale);
        String lang = display.getLanguage();
        int index = "ko".equals(lang) ? 0 : "ja".equals(lang) ? 2 : "zh".equals(lang) ? 3 : 1;
        String value = values[index];
        return args.length == 0 ? value : String.format(display, value, args);
    }
}
