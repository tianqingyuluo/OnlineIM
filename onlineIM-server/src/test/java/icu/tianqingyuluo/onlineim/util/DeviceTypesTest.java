package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceTypesTest {

    @Test
    void normalizesKnownAliasesAndDefaultsToWeb() {
        assertEquals(DeviceTypes.WEB, DeviceTypes.normalize(null));
        assertEquals(DeviceTypes.WEB, DeviceTypes.normalize("  "));
        assertEquals(DeviceTypes.WEB, DeviceTypes.normalize("browser"));
        assertEquals(DeviceTypes.DESKTOP, DeviceTypes.normalize("Tauri"));
        assertEquals(DeviceTypes.PHONE, DeviceTypes.normalize("Android"));
        assertEquals(DeviceTypes.PHONE, DeviceTypes.normalize("tablet"));
        assertEquals(DeviceTypes.WEB, DeviceTypes.normalize("unknown-gadget"));
    }

    @Test
    void kickMessageNamesTheDeviceType() {
        assertEquals("您的账号已在其他网页端登录，当前设备已下线",
                DeviceTypes.kickMessage(DeviceTypes.REASON_SAME_TYPE, DeviceTypes.WEB));
        assertEquals("您的账号已在其他手机端登录，当前设备已下线",
                DeviceTypes.kickMessage(DeviceTypes.REASON_SAME_TYPE, DeviceTypes.PHONE));
        assertEquals("该设备已被远程登出",
                DeviceTypes.kickMessage(DeviceTypes.REASON_REMOTE, DeviceTypes.DESKTOP));
        assertEquals("您已在此设备重新登录，当前页面已下线",
                DeviceTypes.kickMessage(DeviceTypes.REASON_RELOGIN, DeviceTypes.WEB));
    }

    @Test
    void rejectsDeviceIdsThatCannotBeRedisKeys() {
        assertEquals("web-abc", DeviceTypes.sanitizeDeviceId(" web-abc "));
        assertNull(DeviceTypes.sanitizeDeviceId("../usr"));
        assertNull(DeviceTypes.sanitizeDeviceId("has space"));
        assertFalse(DeviceTypes.isSafeDeviceId(""));
        assertTrue(DeviceTypes.isSafeDeviceId("phone-1"));
    }
}
