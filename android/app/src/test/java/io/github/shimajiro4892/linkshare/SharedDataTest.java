package io.github.shimajiro4892.linkshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** share-target.test.js と同じケース。 */
public class SharedDataTest {

    @Test
    public void 件名にタイトル本文にURLの形式を受け取る() {
        SharedData data = SharedData.parse("Example title", "https://example.com/path?a=1&b=2");
        assertEquals("Example title", data.title);
        assertEquals("https://example.com/path?a=1&b=2", data.url);
    }

    @Test
    public void 本文にタイトル改行URLが入っている形式からタイトルを取り出す() {
        SharedData data = SharedData.parse(null, "Example title\nhttps://example.com/path");
        assertEquals("Example title", data.title);
        assertEquals("https://example.com/path", data.url);

        data = SharedData.parse("", "https://example.com/path\n\nExample title");
        assertEquals("Example title", data.title);
    }

    @Test
    public void 件名がURLだけのときはタイトル扱いしない() {
        SharedData data = SharedData.parse("https://example.com/path", "Example title");
        assertEquals("Example title", data.title);
        assertEquals("https://example.com/path", data.url);
    }

    @Test
    public void URL末尾の句読点や閉じ括弧を落とす() {
        assertEquals("https://example.com/path", SharedData.parse(null, "see https://example.com/path). thanks").url);
        assertEquals("https://example.com/path", SharedData.parse(null, "詳細はこちら https://example.com/path。").url);
    }

    @Test
    public void URLが含まれていないときはhasUrlがfalseになる() {
        SharedData data = SharedData.parse(null, "ただの文章");
        assertFalse(data.hasUrl());
        assertEquals("ただの文章", data.title);
        assertFalse(SharedData.parse(null, null).hasUrl());
    }
}
