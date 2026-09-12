# リンクシェア（Android PWA版）

Chrome拡張「リンクシェア」と同じ動作をする、Android の Chrome 用 PWA（Web Share Target）です。
Chrome の共有メニューに「リンクシェア」として表示され、ページのタイトルとURLを X・LINE・Slack でシェア、またはクリップボードにコピーします。
Amazon の商品URLは拡張機能と同じルールで `/dp/ASIN` 形式に整理します（アフィリエイトタグとバリエーション指定は維持）。

iOS版（`ios-shortcut/`（AI-website-summarization リポジトリ内））との違いは、ショートカットではなく「ホーム画面に追加したWebページ」として動く点です。
Android には iOS のショートカットに相当する標準機能がないため、共有メニューに入れる方法として PWA を使っています。

## 公開先

PWA は HTTPS で公開されている必要があります。このフォルダの中身は、単独のリポジトリ
[shimajiro4892/linkshare_android](https://github.com/shimajiro4892/linkshare_android) に置いて GitHub Pages で公開しています。

- 公開URL: `https://shimajiro4892.github.io/linkshare_android/`
- 開発元は `AI-website-summarization` リポジトリの `link share/android-pwa/`。こちらを編集して、以下で公開側へ同期する。

```sh
# 公開用リポジトリを clone しておく（初回のみ）
git clone git@github.com:shimajiro4892/linkshare_android.git ../linkshare_android
# android-pwa/ の中身を丸ごとコピーしてコミット・プッシュ
rsync -a --delete --exclude .git --exclude .gradle --exclude build --exclude .DS_Store "android-pwa/" ../linkshare_android/
(cd ../linkshare_android && git add -A && git commit -m "Sync from link share/android-pwa" && git push)
```

## 導入手順（Android）

### Brave など Chrome 以外のブラウザを使っている場合（APK）

共有メニューに Web アプリを登録できるのは Chrome だけです（Google のサーバーで WebAPK を生成する仕組みのため）。
Brave・Firefox・Samsung Internet などでは、代わりに `android/` の小さなアプリをインストールします。

このアプリは画面を持ちません。共有メニューに次の 4 項目が直接並び、選んだ瞬間にそのアプリが開きます（タップは「共有 → 共有先」の 2 回だけ）。

- **X にシェア** : X アプリの投稿用（Composer）の共有受け口を明示して開く（パッケージ指定だけだと DM 側に振られる。`twitter://post` は本文が入らない）。見つからなければ `x.com/intent/tweet` を X アプリで、それも無理ならブラウザで開く。
- **LINE にシェア** : LINE アプリの送信先選択を開く。未インストールなら `https://line.me/R/share`。
- **Slack にシェア** : Slack アプリのチャンネル選択を開く。未インストールならコピーして Web 版を開く。
- **タイトルとURLをコピー** : クリップボードにコピーするだけ。

Amazon の URL 整理は拡張機能と同じルールで行います（`android/app/src/main/java/.../ShareLink.java`、JUnit テストつき）。

1. 端末のブラウザで https://github.com/shimajiro4892/linkshare_android/releases/latest/download/linkshare-android.apk を開いてダウンロードする。
2. ダウンロードした APK を開く。「この提供元のアプリを許可」を求められたら許可する（ブラウザまたはファイルアプリに対して）。
3. Brave などで共有ボタンを押すと、共有シートに上の 4 項目が出る。よく使う項目は長押しして「ピン留め」すると先頭に固定できる。

アプリ一覧の「リンクシェア」を開くと、この使い方ページがブラウザで開きます。

### Chrome を使っている場合（PWA として直接インストール）

1. Chrome で公開URLを開く。
2. Chrome のメニュー（右上の︙）から「アプリをインストール」を選ぶ。ページ内に「インストールする」ボタンが出る場合はそれでもよい。
3. `chrome://webapks` に「リンクシェア」が出れば登録済み。共有したいページを開き、「共有...」から「リンクシェア」を選ぶ。
   出てこないときは Chrome を一度終了して開き直す。

## 使い方

共有すると、タイトル・整理後のURL・状態メッセージが表示され、X / LINE / Slack / コピーする / 他のアプリ のボタンが出ます。
タイトルは共有前に手で直せます（アプリによってはタイトルが渡ってこないため）。

- **X**: `https://x.com/intent/tweet` を開く。X アプリが入っていればアプリで開く。
- **LINE**: `https://line.me/R/share?text=` を開く。LINE アプリが入っていればアプリで開く。
- **Slack**: 拡張機能・iOS版と同じく「コピーしてから Slack アプリを開く」方式（`slack://open`）。
- **他のアプリ**: Android の共有シートに「タイトル改行URL」のテキストを渡す。
- **コピーする**: 「タイトル改行URL」をクリップボードへ。

## LINE が開かないとき（診断）

共有画面の下にある「LINEが開かないとき（診断）」を開くと、呼び出し方式を番号順に試せます。

1. `https://line.me/R/share`（現行）
2. `line://msg/text/`
3. `intent://`（LINE アプリを直接指定、なければ 1 へフォールバック）
4. LINE it!（拡張機能と同じ `social-plugins.line.me`）
5. Android の共有シート
6. Slack を `intent://` で開く

うまく動いた番号を教えてもらえれば、それを既定の方式に切り替えます（`app.js` の `buildLineShareUrl` / `buildSlackOpenUrl`）。

## ファイル

- `index.html` / `app.css` / `app.js` : 画面と動作。共有シートから起動されると `?title=&text=&url=` 付きで開く。
- `share-target.js` : 共有パラメータの解析。アプリによって `text` にURLだけ、または「タイトル改行URL」が入ってくるので、どの形でも同じ結果にする。
- `share-utils.js` : 拡張機能（`link share/share-utils.js`）のコピー。変更したら `cp share-utils.js android-pwa/` で同期する（`npm test` が不一致を検出する）。
- `manifest.webmanifest` : PWA マニフェスト。`share_target` で共有メニューに登録する。
- `sw.js` : インストール要件を満たす最小の Service Worker。ネットワーク優先で、オフライン時だけキャッシュを返す。
- `icons/` : `link share/generate_icons.py` で生成。
- `android/` : Chrome 以外のブラウザ用の共有メニュー登録アプリ（Java、画面なし）。共有先ごとに activity-alias を 1 つ持ち、受け取ったテキストを整理して共有先アプリへ直接渡す。`ShareLink.java` は `share-utils.js` の、`SharedData.java` は `share-target.js` の移植で、それぞれ同じケースの JUnit テストがある。
- `.github/workflows/build-apk.yml` : `android/` が変わると JUnit テストを実行し、署名済み APK をビルドして Release「latest」に添付する。
- `.nojekyll` : GitHub Pages で Jekyll 処理を止め、ファイルをそのまま配信する。

## APK のビルド

署名鍵はリポジトリの外、`~/.android/linkshare-release.jks` にあり、パスワード等は `~/.android/linkshare-release.env` に入っている（Mac 上）。
同じ鍵で署名しないと上書きインストールできないので、鍵は無くさないこと。

```sh
# ローカルビルド
source ~/.android/linkshare-release.env
export ANDROID_HOME="$HOME/Library/Android/sdk"
(cd android && ./gradlew test assembleRelease)
# → android/app/build/outputs/apk/release/app-release.apk

# GitHub Actions で自動ビルドするための Secrets 登録（鍵を作り直したときも再実行）
source ~/.android/linkshare-release.env
R=shimajiro4892/linkshare_android
base64 -i "$KEYSTORE_FILE" | gh secret set KEYSTORE_BASE64 -R $R
gh secret set KEYSTORE_PASSWORD -R $R -b "$KEYSTORE_PASSWORD"
gh secret set KEY_ALIAS -R $R -b "$KEY_ALIAS"
gh secret set KEY_PASSWORD -R $R -b "$KEY_PASSWORD"
```

## ローカルで試す

```sh
cd android-pwa
python3 -m http.server 8765
# http://localhost:8765/?title=Example&url=https://example.com/ を開く
```

`localhost` は HTTPS 扱いなので、インストール以外の動作（URL整理、コピー、各ボタン）はPCの Chrome でも確認できます。
共有メニューへの登録だけは Android 実機で HTTPS 公開したURLからインストールする必要があります。

## 拡張機能との違い

- 右クリックメニューはなく、共有メニューからのみ起動できる。
- 共有元のアプリによってはタイトルが渡ってこない。その場合はタイトル欄に手で入力する。
- 共有先を開いた後も PWA の画面は残る。戻るボタンで戻れる。
- Chrome 以外のブラウザでは APK 経由になる。共有先を選ぶ画面はなく、共有シートの項目がそのまま共有先になる。タイトルの手直しはできない。
