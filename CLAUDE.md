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
- `core/nav` ナビ計算（RATE、方位選択、WP、ETA、カウントダウン、POSITION LOST、欠損検出）
- 画面は Jetpack Compose の Canvas で描く。画面は `NavState`（計算済みの値）だけを見る。

## サンプルデータ（track.csv）
- track.csv は**意図的に git 管理外**。`/sample` は `.gitignore` で除外したまま、コミットしない。
- 開発機の置き場所: `sample/session_20260814_075234/track.csv`
- track.csv を使うテストは、ファイルがなければ `Assume` でスキップする（失敗にしない）。
- 実機のリプレイは、Download フォルダの track.csv を `ACTION_OPEN_DOCUMENT` で選び、
  URI を永続化（`takePersistableUriPermission`）して次回から再利用する。

## バージョン・コミット
- `app/build.gradle.kts` の `appVersionName`（`yyyyMMdd-Xnn`）だけを変える。versionCode は自動計算。
- `.githooks/` のフックがコミットメッセージの先頭にバージョンを入れる（`git config core.hooksPath .githooks`）。
- **コミットは開発者が自分で行う。** Claude はコミットしない。フックの確認などは一時リポジトリで行う。
