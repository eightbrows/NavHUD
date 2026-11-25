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
- `MainActivity`（直下） アプリの入口。画面の切り替え、ファイルの画面とファイル選び、権限のお願い
- `core/Tuning.kt` 調整値（設定画面で変えられないしきい値・時間・寸法）
- `core/geo` 地理計算（距離、方位、角度の差、緯度・経度と東m・北m の変換）
- `core/io` ファイルの読み書き（WP リストの CSV・GPX、座標の貼り付け、track.csv、GpsLogger の zip、背景地図のファイル、最高標高のファイル（MaxElevFile。地上高に使う）、前回のファイルの覚えとクリア（LastFileMemory）、最近のファイル（RecentFiles）、ファイルの種類の見分け（FileKind・FileKinds・FileKindException）、GpsLogger の歩数（steps.csv の StepsCsv・meta.json の歩数の状態の StepsMeta・読んだ結果の StepsLoad・counter の区間の CounterSegments。20261124-D01・20261125-D01））
- `core/model` データ型（Fix（海抜高度 mslAltM・経過時間 elapsedNs を含む）, Waypoint, 到達の理由, PositionSource）
- `core/nav` ナビ計算（NavEngine、NavState、設定（NavSettings。方位の出し方の HeadingMethod・移動のプリセットの TravelMode とプリセットの値の ReachProfile も）、設定の保存の形（SettingsCodec。初期値から変えた所だけを書く形 2 と前の形の読み替え）、移動のプリセットの決まり（Presets。比べる初期値・変えた所の数・初期値に戻す / コピー・確かめの枠）、背景地図の項目と縮尺（MapItems。線の MapItem・ランドマークの LandmarkItem）、表示言語の選択、V/S（VerticalSpeed）、GS・ETA の平均の速さ（RateTracker）、方位（HeadingSelector）・位置から求める向き（TrackHeading。向きを変えてよいかの TurnGate も）・平均した方位（AvgHeading）、歩数の時刻合わせ（StepsTimeline）、縮尺と AUTO、WP、ETA、カウントダウン、到達判定、PAN、軌跡（Trail。間引き・なめらかさ・走った跡の LiveTrail）、NO FIX）
- `core/replay` 再生の時間の進め方（LIVE / REPLAY の時計、Fix の送出、シーク、倍速）
- `core/sensor` センサーの値の下ごしらえ（端末の位置から Fix を作る。コンパスは 20261121-D01 で外した）
- `core/view` 画面に描くものの位置と文字の計算（HudScene、ARC / North Up、背景地図の線とランドマーク、距離環の数字、標高プロファイル、ピンチ・2 本指のタップの見分け方、文字の整形、数値の欄の並びと幅（NumberRows・NumberLayout）、上部バーの文字の倍率（TopBarFit）、メイン画面のボタンの置き場所（MainLayout。上部バーの並びと NO FIX の位置・左の列の並び・地図の操作を受け付ける所・知らせの幅）、WP ボタン列（WpStripLayout）、読み込んだ結果のひとこと（LoadSummary）、設定画面の欄と項目の並び・閉じた欄の覚え・縮尺のマス・表のマス（SettingsOutline）、WP設定の一覧の行の折り返し（ItemWrap））
- `source` 端末の機能とつなぐ所（GPS、位置のサービス、リプレイの送出、ファイルの選択と記憶、ファイルの権限と開けるかの確かめ（FileAccess）、設定の保存（閉じた欄・スクロール位置も）、表示言語の切り替え、権限の状態、背景地図のファイルを読む、最高標高のファイルを読む（MaxElevStore）、AltitudeConverter で海抜高度を求める（MslAltitude）、前回のファイルの覚えを SharedPreferences に置く（PrefsKeyValueStore））
- `ui` 画面（NavViewModel、メイン画面、HUD の Canvas、色、設定、WP設定、ファイルの画面（FileSheet）、移動のプリセットの名前と色（PresetLabels）、開発用の画面）。`ui/theme` は配色
- 画面は Jetpack Compose の Canvas で描く。画面は `NavState`（計算済みの値）だけを見る。
  - 例外: 背景地図のデータ（`BaseMap`。大きいので）は NavState に入れず、`source/BaseMapStore` で読んで HUD の Canvas に直接渡す。
- リポジトリの一番上のフォルダ:
  - `art/` 空港の記号の SVG のひな形（アプリは読み込まない。形は `ui/HudCanvas` のコードで描く）
  - `tools/gm2basemap.ps1` 背景地図の変換（`mapdata/` の元のデータ → `app/src/main/assets/basemap.bin`）。使い方はスクリプトの先頭
  - `tools/gm2basemap_airportlist.ps1` 飛行場の一覧の資料を書く（gm2basemap.ps1 が読み込んで、毎回呼ぶ）
  - `tools/airport_ids.csv` 飛行場の番号（A001 など）の対応表。**番号は変えない・使い回さない**（新しい飛行場は続きの番号を変換が足す）
  - `tools/track2gpx.ps1` track.csv からエミュレータで再生する GPX を作る
  - `tools/hdg_walk_study.py` 歩くときの方位の出し方の調べ（20261121-D05-HDGWALK・20261122-HDGCORNER。歩きの track.csv をいくつでも渡すと、物差しの JSON と見比べの表を出す。アプリと同じく速さ・精度・方位を 32 ビットに丸めて読み、アプリの HDG と全点同じになる。曲がり角ごとの遅れ、精度が悪い所のあと落ち着くまでの物差し（20261122-D03）も書き出す。比べの表の「今の歩行の初期値」は D = 水平精度 × 1.0（4〜15 m）・5 秒前の位置から 4.2 m の形（20261124-D01。前の形も残す）。記録のフォルダに steps.csv があれば読む（20261124-D01）。歩きの記録 3 本（歩数なし）・4 本（歩数入り）で使う）。Python 3 の標準のライブラリだけ。記録・座標は入れない。使い方はスクリプトの先頭
  - `tools/smooth_study.py` 歩行の位置のなめらかさ・軌跡の線・予測の線の調べ（20261122-SMOOTH・WALK3。歩きの track.csv をいくつでも（車は --car）渡すと JSON を出す。歩きの記録 3 本（歩数なし）で使う）。Python 3 の標準のライブラリだけ。記録・座標は入れない。使い方はスクリプトの先頭
  - `tools/stop_hold_study.py` 止まっている間の向きの調べ（20261122-STOPHOLD。歩きの track.csv をいくつでも（車は --car）渡すと JSON を出す）。歩数を使う組、歩いている / 止まっているの判定と、それを TurnGate に入れた形（歩数入りの記録。20261124-D01・20261125-D01）も。tools/hdg_walk_study.py を読み込んで使う。Python 3 の標準のライブラリだけ。記録・座標は入れない。使い方はスクリプトの先頭
  - `tools/build_maxelev.py` 最高標高のファイルを作る（`mapdata/G04-d/` の国土数値情報 標高・傾斜度5次メッシュの ZIP 169 個 → `app/src/main/assets/maxelev.bin`。SPEC §8.10）。Python 3 と pyshp が要る（今の開発機には Python がない）。使い方はスクリプトの先頭
  - `docs/airports.html` 飛行場の一覧の資料（変換が basemap.bin と一緒に書き出す。**手で直さない**）
  - `docs/SPEC.md`（仕様）・`docs/cheatsheet.html` / `.pdf`（チートシート）・`docs/formats.html` / `.pdf`（ファイル形式の説明）・`docs/images/`（README の画像）

## 背景地図
- 仕様は SPEC §8。元のデータ（地球地図日本・山岳標高一覧の CSV・国土数値情報の空港データ・OurAirports の airports.csv と runways.csv）は `mapdata/`（git 管理外）に置き、`pwsh tools/gm2basemap.ps1` で変換する。
- 背景地図を作り直したら、`app/src/main/assets/basemap.bin`・`docs/airports.html`・`tools/airport_ids.csv` を一緒にコミットする（開発者がコミットする）。
- 線の項目・ランドマークの項目と初期値は `core/nav/MapItems.kt` と `NavSettings`（DEFAULT_MAP_ITEMS・DEFAULT_LANDMARKS）。見た目の寸法は `core/Tuning.kt`、色は `ui/HudColors`。
- 地上高の最高標高のファイル（maxelev.bin）は `mapdata/G04-d/`（git 管理外）の ZIP から `tools/build_maxelev.py` で作る。作り直したら `app/src/main/assets/maxelev.bin` をコミットする（開発者がコミットする）。

## エミュレータ・実機
- エミュレータは **NavHUD_Shots** を使う（自由に起動してよい）。**使い終わったら毎回閉じる**（`adb -s emulator-5554 emu kill`）。レポートを作る前に、NavHUD_Shots が動いていないことを確かめる。
- NavHUD_Shots の端末の言語は **日本語（日本）**（20261025-D04 から）。アプリの言語は初期値の「端末に合わせる」のままにして、日本語の画面にする。
- 画面写真は、英語を求められたとき以外は **日本語の画面** で撮る。
- NavHUD_Shots は、保存された起動の状態（`snapshots/default_boot`。2026-11-21 20:12 に保存。タイムゾーン Asia/Tokyo）から始まる（`-no-snapshot-save` で起動するので、閉じても保存しない）。起動し直すと、その状態に戻る（20261121-D05-HDGWALK で確かめた。前は「毎回ディスクから起動する」と書いていた）。スナップショットの保存（`adb emu avd snapshot save`）は、試したときにエミュレータが落ちたので使わない。
- 時刻が写る写真を撮る前に、`adb shell date` で JST（Asia/Tokyo）を確かめる。位置情報をオフにすると、自動のタイムゾーンがエミュレータの通信の知らせの GMT になるので、オフにしたあとはエミュレータを起動し直してから撮る（20261121-D06 から）。
- 保存の設定を書き戻したら、`adb shell sync` してから閉じる（閉じる直前の書き込みは残らないことがある）。
- Small_Phone_A14 には触らない。
- アプリを入れるのは `adb -s emulator-5554` で、エミュレータだけ。
- USB でつないでいる実機には、何も入れない（アプリを入れる・入れ直す・消す・データを消す・設定を変えることはしない）。頼まれたときに読むだけは可（デバッグ版なので `run-as` で保存の設定を読める）。

## サンプルデータ（track.csv）
- track.csv は**意図的に git 管理外**。`/sample` は `.gitignore` で除外したまま、コミットしない。
- 開発機の置き場所: `sample/session_20260814_075234/track.csv`（車の記録）、`sample/walk/session_20261121_132427/track.csv`（歩きの記録。WalkSampleTest と tools/hdg_walk_study.py で使う）、`sample/walk/session_20261122_121430/`・`session_20261122_132237/`（歩きの記録。20261122-WALK3 から。調べの道具と StopHoldAndStepsReplayTest で使う）、`sample/walk/session_20261122_213356/`・`session_20261125_164455/`・`session_20261125_165705/`・`session_20261125_170108/`（歩数（steps.csv）入りの歩きの記録。20261125-D01 から。調べの道具で使う）
- track.csv を使うテストは、ファイルがなければ `Assume` でスキップする（失敗にしない）。
- 実機のリプレイは、左の列の FILE で出すファイルの画面から、Download フォルダの track.csv を `ACTION_OPEN_DOCUMENT` で選び、
  URI を永続化（`takePersistableUriPermission`）して次回から再利用する。

## 保留中
- 画像の背景（SPEC §8.9 の保留の案）は使っていない（レポートの「次の作業」には挙げない）。
- 調整値（設定画面で変えられないしきい値・時間・寸法）は `core/Tuning.kt` に集めてある。設定画面で変えられる値の初期値は、プリセットの値（方位・到達の判定・縮尺・地図とランドマークの表・標高プロファイル・GS / ETA の平均）が `ReachProfile.CAR` / `ReachProfile.WALK`、共通の値が `NavSettings` の既定値（20261025-D01 に開発者の実機の設定に合わせた。表示モードだけは 20261025-D03 で ARC に戻した。ボタンの不透明度は 20261104-D01 で 80%、方位を止める / 戻す速度は 20261119-D01 で 4 / 6 km/h（調べ 20261108-R03-HOLD-SPEED）、歩行は 20261121-D06 で位置だけ・20261122-D01 で向きを求める距離 4 m・20261122-D03 で平均した方位線 ON と軌跡の点の間隔 3 m・20261122-D04 で軌跡のなめらかさ 3 m）。設定に出さない決まり: 20261122-D04 から向きを求める距離を水平精度に合わせて伸ばす（× 1.0・最長 15 m）、20261124-D01 から向きを変えるのは 5 秒前の位置から 4.2 m 離れたときだけ（どちらも全プリセット）。
- 歩数（GpsLogger の steps.csv）は、zip から読み込んで知らせに合計を出し、REPLAY で流すだけで、今は向き・到達の判定などの計算には使っていない（SPEC §6.7。LIVE の歩数のセンサーはまだ作っていない）。
- 設定は、初期値から変えた所だけを保存する（SettingsCodec の形 2。`settingsFormat=2`）。初期値を変えると、変えていない端末はその値に付いていく（SPEC §5.6）。

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
