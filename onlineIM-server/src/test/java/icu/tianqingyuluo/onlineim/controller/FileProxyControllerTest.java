package icu.tianqingyuluo.onlineim.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileProxyControllerTest {

    @Test
    void onlyServesKnownPublicPrefixes() {
        assertTrue(FileProxyController.isPublicObject("avatars/usr_1/a.png"));
        assertTrue(FileProxyController.isPublicObject("groups/avatars/grp_1/b.jpg"));
        assertTrue(FileProxyController.isPublicObject("chats/images/c1/x.png"));
        assertFalse(FileProxyController.isPublicObject("avatars/../secret.txt"));
        assertFalse(FileProxyController.isPublicObject("other/file.txt"));
        assertFalse(FileProxyController.isPublicObject(""));
    }

    @Test
    void chatFilesAreDownloadOnly() {
        assertTrue(FileProxyController.isDownloadOnly("chats/files/202610/abc.pdf"));
        assertFalse(FileProxyController.isDownloadOnly("chats/images/202610/abc.png"));
        assertFalse(FileProxyController.isDownloadOnly("avatars/usr_1/a.png"));
    }

    @Test
    void voiceObjectsUseAudioContentTypes() {
        assertEquals("audio/webm", FileProxyController.contentTypeOf("chats/voices/202610/a.webm").toString());
        assertEquals("audio/ogg", FileProxyController.contentTypeOf("chats/voices/202610/a.ogg").toString());
        assertEquals("audio/mp4", FileProxyController.contentTypeOf("chats/voices/202610/a.m4a").toString());
        assertEquals("audio/mpeg", FileProxyController.contentTypeOf("chats/voices/202610/a.mp3").toString());
        assertFalse(FileProxyController.isDownloadOnly("chats/voices/202610/a.webm"));
    }

    @Test
    void parsesSingleByteRanges() {
        assertNull(FileProxyController.parseRange(null, 100));
        assertNull(FileProxyController.parseRange("bytes=0-1,5-6", 100));
        assertNull(FileProxyController.parseRange("items=0-1", 100));
        assertArrayEquals(new long[]{0, 99}, FileProxyController.parseRange("bytes=0-", 100));
        assertArrayEquals(new long[]{10, 19}, FileProxyController.parseRange("bytes=10-19", 100));
        assertArrayEquals(new long[]{90, 99}, FileProxyController.parseRange("bytes=-10", 100));
        assertArrayEquals(new long[]{50, 99}, FileProxyController.parseRange("bytes=50-500", 100));
        assertEquals(0, FileProxyController.parseRange("bytes=100-", 100).length);
        assertEquals(0, FileProxyController.parseRange("bytes=20-10", 100).length);
    }
}
