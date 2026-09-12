package io.github.shimajiro4892.linkshare;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

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
                shareToX(shared.title, description.shareUrl, text);
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

    /**
     * X アプリは ACTION_SEND の受け口を投稿用と DM 用の 2 つ持ち、パッケージ指定だけだと DM 側に
     * 振られる。投稿用（Composer）の受け口を探して明示指定する。見つからなければ公式の Web Intent
     * （x.com/intent/tweet）を X アプリで開き、それも無理ならブラウザで開く。
     * twitter://post?message= は投稿画面は開くが本文が入らなくなっている（2026年9月・実機確認）。
     */
    private void shareToX(String title, String url, String text) {
        ComponentName composer = findXComposer();
        if (composer != null) {
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .setComponent(composer)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(send);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // 下の Web Intent にフォールバック
            }
        }
        String webIntent = ShareLink.buildXShareUrl(title, url);
        if (!openInApp(PACKAGE_X, webIntent)) {
            openUrl(webIntent);
        }
    }

    private ComponentName findXComposer() {
        Intent probe = new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(PACKAGE_X);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(probe, 0);
        ResolveInfo fallback = null;
        for (ResolveInfo handler : handlers) {
            String name = handler.activityInfo.name.toLowerCase(Locale.ROOT);
            if (name.contains("compos")) {
                return new ComponentName(handler.activityInfo.packageName, handler.activityInfo.name);
            }
            // 名前で判断できないときは、DM らしくないものを候補として残す。
            if (fallback == null && !name.contains("dm") && !name.contains("message") && !name.contains("direct")) {
                fallback = handler;
            }
        }
        return fallback == null ? null : new ComponentName(fallback.activityInfo.packageName, fallback.activityInfo.name);
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
