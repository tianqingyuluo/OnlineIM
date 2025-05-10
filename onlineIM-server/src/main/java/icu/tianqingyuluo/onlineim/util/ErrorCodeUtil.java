package icu.tianqingyuluo.onlineim.util;

import java.util.HashMap;
import java.util.Map;

public class ErrorCodeUtil {
    public static Map<String,String> getErrorOutput(String errorCode, String errorMessage) {
        Map<String,String> map = new HashMap<>();
        map.put("code", errorCode);
        map.put("message", errorMessage);
        return map;
    }
}
