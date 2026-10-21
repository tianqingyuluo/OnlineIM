package icu.tianqingyuluo.onlineim.storage.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinIOAdapterUrlTest {

    @Test
    void usesConfiguredPublicBaseUrlInsteadOfLocalNetworkIp() {
        MinIOAdapter adapter = new RustFSAdapter("http://rustfs:9000", "ak", "sk", "https://im.example.com/");
        assertEquals("https://im.example.com/onlineim/avatars/usr_1/a.png",
                adapter.getFileUrl("onlineim", "avatars/usr_1/a.png"));
    }

    @Test
    void returnsSameOriginRelativePathWhenBaseUrlIsBlank() {
        MinIOAdapter adapter = new MinIOAdapter("http://minio:9000", "ak", "sk", " ");
        assertEquals("/onlineim/groups/avatars/grp_1/b.jpg",
                adapter.getFileUrl("onlineim", "groups/avatars/grp_1/b.jpg"));
    }
}
