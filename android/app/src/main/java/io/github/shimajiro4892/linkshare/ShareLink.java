package io.github.shimajiro4892.linkshare;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chrome拡張の share-utils.js の移植。ここを変えるときは share-utils.js とテストも合わせて変える。
 * Android 依存を持たない（JVM 上の単体テストで検証するため）。
 */
public final class ShareLink {

    public static final String FALLBACK_TITLE = "(タイトルなし)";
    public static final String STATUS_NOT_SHAREABLE = "このページは共有できません";
    public static final String STATUS_ORIGINAL = "元のURLを使用します";
    public static final String STATUS_NORMALIZED = "共有用にURLを整理しました";

    /** describeShareUrl の結果。 */
    public static final class Description {
        public final boolean shareable;
        public final String shareUrl;
        public final String status;

        Description(boolean shareable, String shareUrl, String status) {
            this.shareable = shareable;
            this.shareUrl = shareUrl;
            this.status = status;
        }
    }

    // Amazonストアのドメインだけを対象にする。amazon.jobs のような非ストアドメインや、
    // www.amazon.co.jp.example.com のような偽装ドメインを除外するためサフィックスで判定する。
    private static final List<String> AMAZON_STORE_SUFFIXES = Arrays.asList(
            "amazon.ae", "amazon.ca", "amazon.cn", "amazon.co.jp", "amazon.co.uk",
            "amazon.co.za", "amazon.com", "amazon.com.au", "amazon.com.be",
            "amazon.com.br", "amazon.com.mx", "amazon.com.tr", "amazon.de",
            "amazon.eg", "amazon.es", "amazon.fr", "amazon.ie", "amazon.in",
            "amazon.it", "amazon.nl", "amazon.pl", "amazon.sa", "amazon.se", "amazon.sg");

    // asinクエリを信頼できる商品ページのパス。
    private static final List<Pattern> AMAZON_ASIN_QUERY_PATHS = Arrays.asList(
            Pattern.compile("^/dp(?:/|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/gp/product(?:/|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/gp/aw/d(?:/|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^/gp/offer-listing(?:/|$)", Pattern.CASE_INSENSITIVE));

    private static final List<Pattern> AMAZON_ASIN_PATHS = Arrays.asList(
            Pattern.compile("/dp/([A-Z0-9]{10})(?:[/?#]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/gp/product/([A-Z0-9]{10})(?:[/?#]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/gp/aw/d/([A-Z0-9]{10})(?:[/?#]|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/exec/obidos/ASIN/([A-Z0-9]{10})(?:[/?#]|$)", Pattern.CASE_INSENSITIVE));

    private static final Pattern ASIN = Pattern.compile("^[A-Z0-9]{10}$", Pattern.CASE_INSENSITIVE);

    private ShareLink() {
    }

    public static String shareTitle(String title) {
        return title == null || title.isEmpty() ? FALLBACK_TITLE : title;
    }

    /** JSの new URL() に相当。日本語などを含む生のURLも受け付ける。 */
    static URI parseUrl(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value);
            if (uri.getScheme() != null && uri.getRawAuthority() != null) {
                return uri;
            }
        } catch (URISyntaxException ignored) {
            // 未エンコードの文字が含まれる場合は下で URL 経由でエンコードする
        }
        try {
            URL url = new URL(value);
            return new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
                    url.getPath(), url.getQuery(), url.getRef());
        } catch (MalformedURLException | URISyntaxException e) {
            return null;
        }
    }

    public static boolean isShareableUrl(String value) {
        URI uri = parseUrl(value);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https");
    }

    private static boolean isAmazonHost(String hostname) {
        String host = hostname.toLowerCase(Locale.ROOT);
        for (String suffix : AMAZON_STORE_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            if (!params.containsKey(key)) {
                params.put(key, value);
            }
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    /** クエリ値のパーセントエンコード。フォーム形式の "+" ではなく "%20" を使う（アプリのディープリンクでも安全なため）。 */
    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static String getAmazonAsin(URI uri) {
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        for (Pattern pattern : AMAZON_ASIN_PATHS) {
            Matcher match = pattern.matcher(path);
            if (match.find()) {
                return match.group(1).toUpperCase(Locale.ROOT);
            }
        }

        boolean trustedPath = false;
        for (Pattern pattern : AMAZON_ASIN_QUERY_PATHS) {
            if (pattern.matcher(path).find()) {
                trustedPath = true;
                break;
            }
        }
        if (!trustedPath) {
            return null;
        }

        String asin = queryParams(uri).get("asin");
        return asin != null && ASIN.matcher(asin).matches() ? asin.toUpperCase(Locale.ROOT) : null;
    }

    private static String origin(URI uri) {
        StringBuilder origin = new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT))
                .append("://").append(uri.getHost().toLowerCase(Locale.ROOT));
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
                || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }

    private static String normalizeAmazonUrl(URI uri) {
        if (uri.getHost() == null || !isAmazonHost(uri.getHost())) {
            return uri.toString();
        }
        String asin = getAmazonAsin(uri);
        if (asin == null) {
            return uri.toString();
        }

        Map<String, String> source = queryParams(uri);
        List<String> query = new ArrayList<>();
        String affiliateTag = source.get("tag");
        if (affiliateTag != null && !affiliateTag.isEmpty()) {
            query.add("tag=" + encode(affiliateTag));
        }
        // バリエーション指定は落とすと共有先で別の商品が開くため残す。
        String variation = source.get("th");
        if (variation != null && !variation.isEmpty()) {
            query.add("th=" + encode(variation));
            String variationSelected = source.get("psc");
            if (variationSelected != null && !variationSelected.isEmpty()) {
                query.add("psc=" + encode(variationSelected));
            }
        }

        StringBuilder normalized = new StringBuilder(origin(uri)).append("/dp/").append(asin);
        if (!query.isEmpty()) {
            normalized.append('?').append(String.join("&", query));
        }
        // フラグメントは他サイトのURLと同様に維持する（#customerReviews など）。
        if (uri.getRawFragment() != null) {
            normalized.append('#').append(uri.getRawFragment());
        }
        return normalized.toString();
    }

    public static String normalizeShareUrl(String value) {
        URI uri = parseUrl(value);
        if (uri == null || !isShareableUrl(value)) {
            return value;
        }
        return normalizeAmazonUrl(uri);
    }

    public static Description describeShareUrl(String value) {
        if (!isShareableUrl(value)) {
            return new Description(false, value == null ? "" : value, STATUS_NOT_SHAREABLE);
        }
        String shareUrl = normalizeShareUrl(value);
        String canonical = parseUrl(value).toString();
        return new Description(true, shareUrl,
                shareUrl.equals(canonical) ? STATUS_ORIGINAL : STATUS_NORMALIZED);
    }

    public static String formatShareText(String title, String url) {
        return shareTitle(title) + "\n" + url;
    }

    /**
     * X アプリの投稿画面を直接開くディープリンク。
     * ACTION_SEND だと X 側の DM 用の受け口に振られることがあるため、投稿画面を明示する。
     */
    public static String buildXAppPostUrl(String text) {
        return "twitter://post?message=" + encode(text);
    }

    /** X アプリが入っていないときの Web Intent。 */
    public static String buildXShareUrl(String title, String url) {
        return "https://x.com/intent/tweet?text=" + encode(shareTitle(title)) + "&url=" + encode(url);
    }

    /** LINE アプリが入っていないときの公式共有URL。 */
    public static String buildLineShareUrl(String text) {
        return "https://line.me/R/share?text=" + encode(text);
    }
}
