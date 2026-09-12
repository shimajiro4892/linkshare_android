package io.github.shimajiro4892.linkshare;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 共有インテントで受け取ったデータから、共有するタイトルとURLを決める（PWA の share-target.js の移植）。
 *
 * Chrome 系は EXTRA_SUBJECT にタイトル、EXTRA_TEXT にURLを入れるが、多くのアプリは
 * EXTRA_TEXT に「タイトル\nURL」やURLだけを詰めて渡す。どの形でも同じ結果になるようにする。
 */
public final class SharedData {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    // 文末の句読点や閉じ括弧はURLの一部ではないことが多いので落とす。
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]}」』】）。、]+$");

    public final String title;
    public final String url;

    private SharedData(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public boolean hasUrl() {
        return !url.isEmpty();
    }

    public static SharedData parse(String subject, String text) {
        String safeSubject = subject == null ? "" : subject;
        String safeText = text == null ? "" : text;

        // 本文を優先し、なければ件名から探す。
        String sharedUrl = findUrl(safeText);
        if (sharedUrl.isEmpty()) {
            sharedUrl = findUrl(safeSubject);
        }

        // タイトルは件名を優先する。件名がURLだけのときは本文からURLを除いた残りを使う。
        String sharedTitle = firstLine(stripUrl(safeSubject, sharedUrl));
        if (sharedTitle.isEmpty()) {
            sharedTitle = firstLine(stripUrl(safeText, sharedUrl));
        }
        return new SharedData(sharedTitle, sharedUrl);
    }

    private static String findUrl(String value) {
        Matcher match = URL_PATTERN.matcher(value);
        if (!match.find()) {
            return "";
        }
        return TRAILING_PUNCTUATION.matcher(match.group()).replaceAll("");
    }

    private static String stripUrl(String value, String url) {
        return url.isEmpty() ? value : value.replace(url, " ");
    }

    private static String firstLine(String value) {
        for (String line : value.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }
}
