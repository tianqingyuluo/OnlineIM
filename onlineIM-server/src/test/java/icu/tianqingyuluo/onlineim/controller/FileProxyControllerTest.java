package icu.tianqingyuluo.onlineim.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
