package io.github.shimajiro4892.linkshare;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * 共有メニューに「X」「LINE」「Slack」「コピー」を直接並べるための、画面を持たないアプリ。
 *
 * AndroidManifest の activity-alias ごとに 1 つの共有先が割り当たっていて、どのエイリアスから
 * 起動されたかで振り分ける。受け取ったタイトルとURLを Chrome 拡張と同じルールで整理し、
 * 「タイトル改行URL」のテキストを共有先アプリへそのまま渡してすぐ終了する。
 */
public class ShareActivity extends Activity {

    /** 使い方ページ（PWA のトップ）。アプリ一覧から開いたときに表示する。 */
    private static final String USAGE_URL = "https://shimajiro4892.github.io/linkshare_android/";

    private static final String PACKAGE_X = "com.twitter.android";
    private static final String PACKAGE_LINE = "jp.naver.line.android";
    private static final String PACKAGE_SLACK = "com.Slack";

    private enum Destination { X, LINE, SLACK, COPY, NONE }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handle(getIntent());
        } finally {
            finish();
        }
    }

    private void handle(Intent intent) {
        Destination destination = destinationOf(intent);
        if (destination == Destination.NONE || !Intent.ACTION_SEND.equals(intent.getAction())) {
            openUrl(USAGE_URL);
            return;
        }

        SharedData shared = SharedData.parse(
                firstNonEmpty(intent.getStringExtra(Intent.EXTRA_SUBJECT),
                        intent.getStringExtra(Intent.EXTRA_TITLE)),
                intent.getStringExtra(Intent.EXTRA_TEXT));
        ShareLink.Description description = ShareLink.describeShareUrl(shared.url);
        if (!shared.hasUrl() || !description.shareable) {
            Toast.makeText(this, R.string.error_no_url, Toast.LENGTH_SHORT).show();
            return;
        }

        String text = ShareLink.formatShareText(shared.title, description.shareUrl);
        switch (destination) {
            case X:
                // X アプリは ACTION_SEND を DM 側で受けることがあるので、投稿画面のディープリンクで開く。
                if (!openInApp(PACKAGE_X, ShareLink.buildXAppPostUrl(text))) {
                    openUrl(ShareLink.buildXShareUrl(shared.title, description.shareUrl));
                }
                break;
            case LINE:
                sendTo(PACKAGE_LINE, text, ShareLink.buildLineShareUrl(text));
                break;
            case SLACK:
                // Slack アプリはテキスト共有を受け取るとチャンネル選択画面を開く。
                // 入っていなければコピーしておき、Web 版を開く。
                if (!sendTo(PACKAGE_SLACK, text, null)) {
                    copy(text, getString(R.string.copied_for_slack));
                    openUrl("https://app.slack.com/");
                }
                break;
            case COPY:
                copy(text, getString(R.string.copied));
                break;
            default:
                break;
        }
    }

    /** どの activity-alias から起動されたかで共有先を決める。 */
    private Destination destinationOf(Intent intent) {
        String component = intent.getComponent() == null ? "" : intent.getComponent().getShortClassName();
        if (component.endsWith("ShareToX")) {
            return Destination.X;
        }
        if (component.endsWith("ShareToLine")) {
            return Destination.LINE;
        }
        if (component.endsWith("ShareToSlack")) {
            return Destination.SLACK;
        }
        if (component.endsWith("CopyShareText")) {
            return Destination.COPY;
        }
        return Destination.NONE;
    }

    /**
     * 指定アプリにテキストを共有する。アプリが入っていなければ fallbackUrl をブラウザで開く。
     * 共有できたか（fallback も含めて何かを開けたか）を返す。
     */
    private boolean sendTo(String packageName, String text, String fallbackUrl) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setPackage(packageName)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(send);
            return true;
        } catch (ActivityNotFoundException e) {
            return fallbackUrl != null && openUrl(fallbackUrl);
        }
    }

    /** 指定アプリで URL（ディープリンク）を開く。アプリが入っていなければ false。 */
    private boolean openInApp(String packageName, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private boolean openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void copy(String text, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text));
        // Android 13 以降はシステムがコピー済みの表示を出すので、二重に出さない。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
