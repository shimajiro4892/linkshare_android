package io.github.shimajiro4892.linkshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** share-utils.test.js と同じケースで移植の一致を確認する。 */
public class ShareLinkTest {

    @Test
    public void httpとhttpsのURLだけを共有可能と判定する() {
        assertTrue(ShareLink.isShareableUrl("https://example.com/path"));
        assertTrue(ShareLink.isShareableUrl("http://example.com/path"));
        assertFalse(ShareLink.isShareableUrl("chrome://extensions"));
        assertFalse(ShareLink.isShareableUrl("not-a-url"));
        assertFalse(ShareLink.isShareableUrl(""));
        assertFalse(ShareLink.isShareableUrl(null));
    }

    @Test
    public void Amazonの商品URLをASINベースの正規URLへ整理する() {
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12?tag=mytag-22",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/%E5%95%86%E5%93%81%E5%90%8D/dp/B0ABCDEF12/ref=sxin_0?pd_rd_w=abc&tag=mytag-22&psc=1"));
        assertEquals("https://www.amazon.com/dp/B012345678",
                ShareLink.normalizeShareUrl("https://www.amazon.com/gp/product/B012345678?ref_=abc"));
        assertEquals("https://www.amazon.co.jp/s?k=keyboard&ref=nb_sb_noss",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/s?k=keyboard&ref=nb_sb_noss"));
    }

    @Test
    public void 日本語を含む未エンコードのURLも整理できる() {
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/商品名/dp/B0ABCDEF12/ref=abc"));
    }

    @Test
    public void バリエーション指定とフラグメントは維持する() {
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12?th=1&psc=1#customerReviews",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/dp/B0ABCDEF12/ref=x?th=1&psc=1&foo=bar#customerReviews"));
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/dp/B0ABCDEF12?psc=1"));
    }

    @Test
    public void 商品ページ以外のasinクエリは書き換えない() {
        String url = "https://www.amazon.co.jp/hz/mobile/mission?asin=B0ABCDEF12";
        assertEquals(url, ShareLink.normalizeShareUrl(url));
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12",
                ShareLink.normalizeShareUrl("https://www.amazon.co.jp/gp/offer-listing?asin=b0abcdef12"));
    }

    @Test
    public void Amazon以外のドメインは書き換えない() {
        String jobs = "https://www.amazon.jobs/dp/B0ABCDEF12";
        assertEquals(jobs, ShareLink.normalizeShareUrl(jobs));
        String fake = "https://www.amazon.co.jp.example.com/dp/B0ABCDEF12";
        assertEquals(fake, ShareLink.normalizeShareUrl(fake));
    }

    @Test
    public void 状態メッセージを判定する() {
        ShareLink.Description original = ShareLink.describeShareUrl("https://example.com/path");
        assertTrue(original.shareable);
        assertEquals(ShareLink.STATUS_ORIGINAL, original.status);

        ShareLink.Description normalized = ShareLink.describeShareUrl("https://www.amazon.co.jp/x/dp/B0ABCDEF12/ref=abc");
        assertEquals("https://www.amazon.co.jp/dp/B0ABCDEF12", normalized.shareUrl);
        assertEquals(ShareLink.STATUS_NORMALIZED, normalized.status);

        ShareLink.Description invalid = ShareLink.describeShareUrl("chrome://extensions");
        assertFalse(invalid.shareable);
        assertEquals(ShareLink.STATUS_NOT_SHAREABLE, invalid.status);
    }

    @Test
    public void 共有テキストをタイトル改行URLの形式で生成する() {
        assertEquals("Example title\nhttps://example.com/path",
                ShareLink.formatShareText("Example title", "https://example.com/path"));
        assertEquals("(タイトルなし)\nhttps://example.com/path",
                ShareLink.formatShareText("", "https://example.com/path"));
    }

    @Test
    public void XとLINEのフォールバックURLを生成する() {
        assertEquals("https://x.com/intent/tweet?text=Example+title&url=https%3A%2F%2Fexample.com%2Fpath",
                ShareLink.buildXShareUrl("Example title", "https://example.com/path"));
        assertEquals("https://line.me/R/share?text=t%0Ahttps%3A%2F%2Fexample.com%2F",
                ShareLink.buildLineShareUrl("t\nhttps://example.com/"));
    }
}
