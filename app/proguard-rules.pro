# NavHUD のリリースビルド（R8）で残す規則。
# 既定の proguard-android-optimize.txt と、ライブラリ（AndroidX・Compose・コルーチン）に入っている規則に加えて使う。
#
# アプリのクラスを文字列の名前で引く所（Class.forName など）や、名前で書き出すシリアライズはない。
# 設定は SharedPreferences にキーの文字列で保存し、列挙型は name（宣言時の文字列。R8 でも変わらない）で読み書きする。
# マニフェストの MainActivity・NavLocationService は、AAPT が出す規則で残る。

# ViewModelProvider の既定のファクトリが、反射で NavViewModel(Application) を呼ぶ
-keepclassmembers class io.github.eightbrows.navhud.ui.NavViewModel {
    <init>(android.app.Application);
}
