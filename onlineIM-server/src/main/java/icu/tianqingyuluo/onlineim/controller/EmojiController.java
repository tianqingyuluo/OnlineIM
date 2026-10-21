package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.pojo.document.UserEmoji;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.EmojiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的表情：列表 / 上传 / 从消息收藏 / 删除 / 排序。错误统一为 {code, message}：
 * 400 参数或图片无效，404 表情或消息不存在，409 已收藏过或达到数量上限，413 太大，415 格式不支持。
 */
@RestController
@RequestMapping("/api/v1/emojis")
public class EmojiController {

    private final EmojiService emojiService;

    public EmojiController(EmojiService emojiService) {
        this.emojiService = emojiService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal UserIDProvider principal) {
        return ResponseEntity.ok(listBody(emojiService.list(principal.getID())));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @AuthenticationPrincipal UserIDProvider principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        UserEmoji emoji = emojiService.upload(principal.getID(), file, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(emojiService.toResponseMap(emoji));
    }

    @PostMapping("/from-message")
    public ResponseEntity<Map<String, Object>> saveFromMessage(
            @AuthenticationPrincipal UserIDProvider principal,
            @RequestBody(required = false) Map<String, Object> body) {
        String conversationId = body == null ? null : stringOf(body.get("conversation_id"));
        String messageId = body == null ? null : stringOf(body.get("message_id"));
        UserEmoji emoji = emojiService.saveFromMessage(principal.getID(), conversationId, messageId);
        return ResponseEntity.status(HttpStatus.CREATED).body(emojiService.toResponseMap(emoji));
    }

    @DeleteMapping("/{emojiId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserIDProvider principal,
                                       @PathVariable String emojiId) {
        emojiService.delete(principal.getID(), emojiId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public ResponseEntity<Map<String, Object>> reorder(@AuthenticationPrincipal UserIDProvider principal,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("emoji_ids");
        List<String> ids = raw instanceof List<?> list
                ? list.stream().map(EmojiController::stringOf).toList()
                : null;
        return ResponseEntity.ok(listBody(emojiService.reorder(principal.getID(), ids)));
    }

    private Map<String, Object> listBody(List<UserEmoji> emojis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("emojis", emojis.stream().map(emojiService::toResponseMap).toList());
        result.put("total", emojis.size());
        result.putAll(emojiService.limits());
        return result;
    }

    private static String stringOf(Object value) {
        return value instanceof String text ? text : null;
    }
}
