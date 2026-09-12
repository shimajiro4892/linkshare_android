package io.github.shimajiro4892.linkshare;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

/**
 * Android の共有メニューに「リンクシェア」を出すためだけの薄いアプリ。
 *
 * 共有で受け取ったタイトルとURLを、PWA（index.html）が Web Share Target で受け取るのと同じ
 * クエリ形式（?title=&text=&url=）に載せ替えて、既定のブラウザの Custom Tab で開く。
 * URLの整理やシェア先の選択はすべて PWA 側で行うので、このアプリにロジックはない。
 *
 * Chrome 以外のブラウザ（Brave など）は Web Share Target を共有メニューへ登録できないため、
 * その代わりとしてこのアプリが共有メニューに入る。
 */
public class ShareActivity extends Activity {

    private static final String PWA_URL = "https://shimajiro4892.github.io/linkshare_android/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        openInBrowser(buildTargetUrl(getIntent()));
        finish();
    }

    static Uri buildTargetUrl(Intent intent) {
        Uri.Builder builder = Uri.parse(PWA_URL).buildUpon();
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return builder.build();
        }

        // Chrome 系は EXTRA_SUBJECT にページタイトル、EXTRA_TEXT にURLを入れる。
        // アプリによっては EXTRA_TITLE を使う。空でない方をタイトルにする。
        String title = firstNonEmpty(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getStringExtra(Intent.EXTRA_TITLE));
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);

        if (title != null) {
            builder.appendQueryParameter("title", title);
        }
        if (text != null && !text.isEmpty()) {
            builder.appendQueryParameter("text", text);
        }
        return builder.build();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private void openInBrowser(Uri url) {
        CustomTabsIntent customTab = new CustomTabsIntent.Builder()
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .build();
        customTab.intent.putExtra(Intent.EXTRA_REFERRER,
                Uri.parse("android-app://" + getPackageName()));
        try {
            // Custom Tabs に対応したブラウザがなければ、通常の VIEW インテントとして開かれる。
            customTab.launchUrl(this, url);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
