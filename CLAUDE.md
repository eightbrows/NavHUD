# NavHUD

地上ナビ用の ND/HSI 風 HUD アプリ（Android / Kotlin / Jetpack Compose）。
仕様は `docs/SPEC.md` が正。仕様と食い違う実装・テストを見つけたら、勝手に決めずに質問する。

## ビルド・テスト
- 単体テスト: `./gradlew test`（Windows では `gradlew.bat test`）
- JDK が PATH にない場合は Android Studio 同梱のものを使う:
  `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`

## 構成
パッケージは `app/src/main/java/io/github/eightbrows/navhud/` の下。
データは `source`（端末から受け取る）→ `core`（計算する）→ `ui`（描く）の順に流れる。`core` は Android の機能に頼らない（単体テストだけで確かめられる）。
- `MainActivity`（直下） アプリの入口。画面の切り替え、ファイルを選ぶ画面、権限のお願い
- `core/Tuning.kt` 調整値（設定画面で変えられないしきい値・時間・寸法）
- `core/geo` 地理計算（距離、方位、角度の差、緯度・経度と東m・北m の変換）
- `core/io` ファイルの読み書き（WP リストの CSV・GPX、座標の貼り付け、track.csv、GpsLogger の zip、背景地図のファイル）
- `core/model` データ型（Fix, Waypoint, 到達の理由, 方位ソースの enum, PositionSource）
- `core/nav` ナビ計算（NavEngine、NavState、設定と保存用の変換、背景地図の項目と縮尺（MapItems。線の MapItem・ランドマークの LandmarkItem）、表示言語の選択、RATE、方位選択、縮尺と AUTO、WP、ETA、カウントダウン、到達判定、PAN、軌跡、NO FIX）
- `core/replay` 再生の時間の進め方（LIVE / REPLAY の時計、Fix の送出、シーク、倍速）
- `core/sensor` センサーの値の下ごしらえ（コンパスの計算、端末の位置から Fix を作る）
- `core/view` 画面に描くものの位置と文字の計算（HudScene、ARC / North Up、背景地図の線とランドマーク、距離環の数字、標高プロファイル、ピンチ・2 本指のタップの見分け方、文字の整形）
- `source` 端末の機能とつなぐ所（GPS、位置のサービス、コンパス、リプレイの送出、ファイルの選択と記憶、設定の保存、表示言語の切り替え、権限の状態、背景地図のファイルを読む）
- `ui` 画面（NavViewModel、メイン画面、HUD の Canvas、色、設定、WP設定、開発用の画面）。`ui/theme` は配色
- 画面は Jetpack Compose の Canvas で描く。画面は `NavState`（計算済みの値）だけを見る。
  - 例外: 背景地図のデータ（`BaseMap`。大きいので）は NavState に入れず、`source/BaseMapStore` で読んで HUD の Canvas に直接渡す。
- リポジトリの一番上のフォルダ:
  - `art/` 空港の記号の SVG のひな形（アプリは読み込まない。形は `ui/HudCanvas` のコードで描く）
  - `tools/gm2basemap.ps1` 背景地図の変換（`mapdata/` の元のデータ → `app/src/main/assets/basemap.bin`）。使い方はスクリプトの先頭
  - `tools/gm2basemap_airportlist.ps1` 飛行場の一覧の資料を書く（gm2basemap.ps1 が読み込んで、毎回呼ぶ）
  - `tools/airport_ids.csv` 飛行場の番号（A001 など）の対応表。**番号は変えない・使い回さない**（新しい飛行場は続きの番号を変換が足す）
  - `tools/track2gpx.ps1` track.csv からエミュレータで再生する GPX を作る
  - `docs/airports.html` 飛行場の一覧の資料（変換が basemap.bin と一緒に書き出す。**手で直さない**）
  - `docs/SPEC.md`（仕様）・`docs/cheatsheet.html` / `.pdf`（チートシート）・`docs/formats.html` / `.pdf`（ファイル形式の説明）・`docs/images/`（README の画像）

## 背景地図
- 仕様は SPEC §8。元のデータ（地球地図日本・山岳標高一覧の CSV・国土数値情報の空港データ・OurAirports の airports.csv と runways.csv）は `mapdata/`（git 管理外）に置き、`pwsh tools/gm2basemap.ps1` で変換する。
- 背景地図を作り直したら、`app/src/main/assets/basemap.bin`・`docs/airports.html`・`tools/airport_ids.csv` を一緒にコミットする（開発者がコミットする）。
- 線の項目・ランドマークの項目と初期値は `core/nav/MapItems.kt` と `NavSettings`（DEFAULT_MAP_ITEMS・DEFAULT_LANDMARKS）。見た目の寸法は `core/Tuning.kt`、色は `ui/HudColors`。

## エミュレータ・実機
- エミュレータは **NavHUD_Shots** を使う（自由に起動してよい）。**使い終わったら毎回閉じる**（`adb -s emulator-5554 emu kill`）。レポートを作る前に、NavHUD_Shots が動いていないことを確かめる。
- NavHUD_Shots の端末の言語は **日本語（日本）**（20261025-D04 から）。アプリの言語は初期値の「端末に合わせる」のままにして、日本語の画面にする。
- 画面写真は、英語を求められたとき以外は **日本語の画面** で撮る。
- NavHUD_Shots には起動の状態（スナップショット）がなく、毎回ディスクから起動する（20261025-D04 から）。端末の設定の変更はディスクに残る。スナップショットの保存（`adb emu avd snapshot save`）は、試したときにエミュレータが落ちたので使わない。
- Small_Phone_A14 には触らない。
- アプリを入れるのは `adb -s emulator-5554` で、エミュレータだけ。
- USB でつないでいる実機には、何も入れない（アプリを入れる・入れ直す・消す・データを消す・設定を変えることはしない）。頼まれたときに読むだけは可（デバッグ版なので `run-as` で保存の設定を読める）。

## サンプルデータ（track.csv）
- track.csv は**意図的に git 管理外**。`/sample` は `.gitignore` で除外したまま、コミットしない。
- 開発機の置き場所: `sample/session_20260814_075234/track.csv`
- track.csv を使うテストは、ファイルがなければ `Assume` でスキップする（失敗にしない）。
- 実機のリプレイは、Download フォルダの track.csv を `ACTION_OPEN_DOCUMENT` で選び、
  URI を永続化（`takePersistableUriPermission`）して次回から再利用する。

## 保留中
- 画像の背景（SPEC §8.9 の保留の案）は使っていない（レポートの「次の作業」には挙げない）。
- 調整値（設定画面で変えられないしきい値・時間・寸法）は `core/Tuning.kt` に集めてある。設定画面で変えられる値は `NavSettings` の既定値（20261025-D01 に開発者の実機の設定に合わせた。表示モードだけは 20261025-D03 で ARC に戻した）。

## 結果レポート（恒久ルール）
各ステップの作業完了時に、結果レポートを HTML で作成する。
- 保存先: `docs/reports/<バージョン>.html`（例: `docs/reports/20260926-D04.html`）。docs/reports は git 管理外。記録は開発者が別途保管する。
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

## 回答の形式（恒久ルール）
- チャットでの回答はすべて日本語で書く。
- 実装前の確認のお願い（方針・レイアウト案など）も、`docs/reports/<バージョン>-plan.html` として HTML で作り、チャットには要点とパスだけを書く。
  HTML の形式は結果レポートと同じ規定（1ファイル完結、外部リソースなし）。

## 言葉（恒久ルール）
- 縮尺は「広域・詳細」で言う（「広い・狭い」「広げる・狭める」「拡大・縮小」は使わない。英語は Wide / Detail）。画面の文字・仕様書・README・チートシート・レポートとも同じ。

## チートシート（恒久ルール）
- 画面や初期値が変わる作業のときは、同じ作業の中で `docs/cheatsheet.html` を直し、`docs/cheatsheet.pdf` も作り直す。
  PDF は Edge のヘッドレス印刷（`--headless --no-pdf-header-footer --print-to-pdf=...`）で作り、A4 縦・1ページであることを確かめる。

## ファイル形式の資料（恒久ルール）
- ファイルの読み書き（WP リストの CSV・GPX、軌跡の track.csv・zip）を変えたときは、同じ作業の中で `docs/formats.html` を直し、`docs/formats.pdf` も作り直す。
  中身は今のコードで実際に読み書きしている内容に合わせる（推測で書かない）。PDF の作り方はチートシートと同じ（A4 縦。1ページに収まらなくてよい）。

## バージョン・コミット
- `app/build.gradle.kts` の `appVersionName`（`yyyyMMdd-Xnn`）だけを変える。versionCode は自動計算。
- `.githooks/` のフックがコミットメッセージの先頭にバージョンを入れる（`git config core.hooksPath .githooks`）。
- **コミットは開発者が自分で行う。** Claude はコミットしない。フックの確認などは一時リポジトリで行う。
