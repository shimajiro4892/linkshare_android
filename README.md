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
rsync -a --delete --exclude .git "android-pwa/" ../linkshare_android/
(cd ../linkshare_android && git add -A && git commit -m "Sync from link share/android-pwa" && git push)
```

## 導入手順（Android）

1. Chrome で公開URLを開く。
2. Chrome のメニュー（右上の︙）から「ホーム画面に追加」または「アプリをインストール」を選ぶ。
   ページ内に「インストールする」ボタンが出る場合はそれでもよい。
3. Chrome で共有したいページを開き、メニューの「共有...」から「リンクシェア」を選ぶ。
   共有先の一覧に出てこないときは、端末を再起動するか、Chrome を一度終了して開き直す（登録に少し時間がかかることがある）。

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
