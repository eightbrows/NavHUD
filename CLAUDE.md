# NavHUD

地上ナビ用の ND/HSI 風 HUD アプリ（Android / Kotlin / Jetpack Compose）。
仕様は `docs/SPEC.md` が正。仕様と食い違う実装・テストを見つけたら、勝手に決めずに質問する。

## ビルド・テスト
- 単体テスト: `./gradlew test`（Windows では `gradlew.bat test`）
- JDK が PATH にない場合は Android Studio 同梱のものを使う:
  `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`

## 構成
- `core/model` データ型（Fix, Waypoint, enum, PositionSource / HeadingSource）
- `core/io` ファイルの読み込み（トラックCSV）
- `core/geo` 地理計算
- `core/nav` ナビ計算（RATE、方位選択、WP、ETA、カウントダウン、NO FIX、欠損検出）
- 画面は Jetpack Compose の Canvas で描く。画面は `NavState`（計算済みの値）だけを見る。

## サンプルデータ（track.csv）
- track.csv は**意図的に git 管理外**。`/sample` は `.gitignore` で除外したまま、コミットしない。
- 開発機の置き場所: `sample/session_20260814_075234/track.csv`
- track.csv を使うテストは、ファイルがなければ `Assume` でスキップする（失敗にしない）。
- 実機のリプレイは、Download フォルダの track.csv を `ACTION_OPEN_DOCUMENT` で選び、
  URI を永続化（`takePersistableUriPermission`）して次回から再利用する。

## 結果レポート（恒久ルール）
各ステップの作業完了時に、結果レポートを HTML で作成する。
- 保存先: `docs/reports/<バージョン>.html`（例: `docs/reports/20260926-D04.html`）。コミット対象に含める。
- 1ファイルで完結させる。CSS はインライン、外部フォント・CDN・外部画像は使わない（フォントはシステムフォント指定のみ）。ブラウザで直接開いて読めること。
- 内容:
  1. 概要（今回やったことを2〜3行、バージョン、日付）
  2. テスト結果（`./gradlew test` の件数・成功・失敗・スキップ、クラスごとの表）
  3. 仕様の節ごとの状況（完了 / 一部 / 未着手 / 回答待ち）と §9 受け入れテストの達成状況
  4. 追加・変更したファイル一覧（新規 / 変更、1行説明）
  5. 仕様にない部分で自分が決めたこと
  6. 決めてほしいこと（作業を止めているものを先頭に、提案つきで）
  7. 動作確認の手順（画面のあるステップのみ。エミュレータで何をどう確認するか）
  8. 次の作業
- 画面のあるステップでは、エミュレータのスクリーンショット（`adb exec-out screencap -p`）を base64 で HTML に埋め込む。
- チャットでの報告は、要点と HTML のパスだけでよい。

## バージョン・コミット
- `app/build.gradle.kts` の `appVersionName`（`yyyyMMdd-Xnn`）だけを変える。versionCode は自動計算。
- `.githooks/` のフックがコミットメッセージの先頭にバージョンを入れる（`git config core.hooksPath .githooks`）。
- **コミットは開発者が自分で行う。** Claude はコミットしない。フックの確認などは一時リポジトリで行う。
