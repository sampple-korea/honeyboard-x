[![English](https://img.shields.io/badge/English-555?style=flat-square)](README.md) [![한국어](https://img.shields.io/badge/%ED%95%9C%EA%B5%AD%EC%96%B4-555?style=flat-square)](README.ko.md) [![日本語](https://img.shields.io/badge/%E6%97%A5%E6%9C%AC%E8%AA%9E-2F80ED?style=flat-square)](README.ja.md) [![简体中文](https://img.shields.io/badge/%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-555?style=flat-square)](README.zh-CN.md)

# HoneyBoard X

Samsung KeyboardにRambler音声入力とCodex翻訳を追加するLSPosedモジュールです。

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white) ![LSPosed](https://img.shields.io/badge/LSPosed-libxposed%20API%20102-6f42c1) ![License: MIT](https://img.shields.io/badge/License-MIT-blue)

## 主な機能

- **Rambler音声入力:** Samsung Keyboardのツールバーに音声入力ボタンを追加します。Gboard Ramblerの音声認識サーバーを使用し、オーロラ風ヘッダー、リアルタイム認識表示、多言語認識、音声による編集コマンドに対応します。認識結果は完了時にまとめて入力されます。無音による自動終了、長押しでの一時停止/再開、ハプティクスとサウンドの設定も可能です。
- **Codex翻訳:** Samsung Keyboardの翻訳パネルからのリクエストをChatGPT/Codex翻訳に切り替えます。モデル、推論強度、翻訳指示を設定でき、アカウントのモデルと使用量を確認できます。5時間または週間の使用量が0%になると`gpt-reserve`へ自動で切り替わり、リクエスト失敗時はSamsung標準翻訳にフォールバックできます。
- **統合設定アプリ:** One UIに着想を得たランチャーアプリで、日本語、英語、中国語、韓国語を利用できます。

## 動作要件

- Samsung Keyboardを搭載したSamsung端末。本モジュールはOne UI 8 / Android 16～17の`com.samsung.android.honeyboard` **5.9.40.75**を対象としています。キーボードの更新で非公開実装が変わると動作しなくなる場合があります。
- Android 12以降（モジュールの最小SDKは31）。
- libxposed API 102に対応したLSPosed。
- インターネット接続。音声入力にはSamsung Keyboardへのマイク権限付与、翻訳にはChatGPT/Codexへのサインインが必要です。

## インストール

1. Samsung RamblerまたはCodex翻訳の旧個別モジュールを使用している場合は、先に削除してください。アプリ専用の設定とログイントークンは引き継がれないため、設定を控えて再ログインしてください。
2. プロジェクトの**Releases**に署名済みHoneyBoard X APKがある場合はダウンロードしてインストールします。ない場合は、下記の手順でソースからビルドします。
3. LSPosedで**HoneyBoard X**を有効にし、対象範囲をSamsung Keyboard（`com.samsung.android.honeyboard`）に設定します。
4. Samsung Keyboardを強制停止してから再度開き、フックを適用します。
5. ランチャーからHoneyBoard Xを開いて音声入力を設定します。翻訳を使うには機能を有効にし、ChatGPT/Codexにサインインしてください。

## Google API キー（音声入力に必要）

HoneyBoard X は Gboard が Rambler で使う Google API キーを**同梱していません。** Gboard の APK から `X-Goog-Api-Key` の値（`AIza` で始まる 39 文字）を自分で取得し、次のいずれかで設定してください。

- **アプリで:** HoneyBoard X → 音声入力 → **Google API キー** に貼り付け
- **ビルド時:** `GOOGLE_API_KEY=AIza... module/build.sh`、または `module/local.keys`（gitignore 対象）に `google_api_key=AIza...`

リリース版 APK にはキーが入っていないため、アプリで入力する必要があります。

## 設定

アプリには音声入力、翻訳、情報の画面があります。音声入力ではメイン/追加言語、無音終了までの時間、オーロラ表示、ハプティクス、サウンドを設定できます。翻訳では機能の有効/無効、モデル、推論強度、カスタム指示、Samsung翻訳へのフォールバック、アカウント・モデル・使用量を設定または確認できます。

音声ボタンをタップすると認識結果を確定して入力し、長押しすると一時停止/再開します。音声入力にはSamsung Keyboardのマイク権限が必要です。

## ソースからビルド

JDK 17以降、Androidビルドツール（`aapt`、`zipalign`、`apksigner`、`zip`）と[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)に記載されたSDKファイルが必要です。

```bash
./module/build.sh
```

署名済みAPKは`module/build/HoneyBoardX.apk`に生成されます。環境構築、テストコマンド、設定移行、既知の制限については開発ガイドを参照してください。

## プライバシー

- 音声データとカーソル直前の文脈（最大2,000 UTF-16文字）は、音声編集コマンドの処理のためGoogle Rambler音声認識サーバーに送信されます。
- 翻訳を依頼したテキストはChatGPT/Codexサーバーに送信されます。
- ログイントークンはAndroid Keystoreで暗号化されます。音声入力にはSamsung Keyboardのマイク権限が必要です。

## 免責事項

HoneyBoard Xは独立した非公式プロジェクトであり、Samsung、Google、OpenAIとの提携・承認はありません。非公開APIやサービスの挙動に依存しているため、いつでも変更または遮断される可能性があります。利用は自己責任でお願いします。

## クレジット

- [PixelBoard](https://github.com/Akshayykadam/PixelBoard) — Rambler実装の参考
- [libxposed](https://github.com/libxposed) — LSPosedモジュールAPI

## ライセンス

[MIT](LICENSE) © 2026 sampple-korea。同梱の libxposed ライブラリは Apache-2.0 です（`module/libs/LICENSE-libxposed.txt`）。

## 制作者: [sampple-korea](https://github.com/sampple-korea)
