package icu.tianqingyuluo.onlineim.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.MessageService;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageControllerTest {

    @Test
    void messageSendRequestAcceptsSnakeCaseAndKeepsCamelCaseAliases() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        MessageSendRequest snakeCase = objectMapper.readValue("""
                {
                  "target_id": "usr_peer",
                  "message_type": "text",
                  "content": "hello",
                  "reply_to_message_id": "msg_target",
                  "client_message_id": "client_1",
                  "at_user_ids": ["usr_2"]
                }
                """, MessageSendRequest.class);
        MessageSendRequest camelCase = objectMapper.readValue("""
                {
                  "targetId": "usr_peer",
                  "messageType": "text",
                  "content": "hello",
                  "replyToMessageId": "msg_target",
                  "clientMsgId": "client_1",
                  "atUserIds": ["usr_2"]
                }
                """, MessageSendRequest.class);

        assertEquals("usr_peer", snakeCase.getTargetId());
        assertEquals("text", snakeCase.getMessageType());
        assertEquals("msg_target", snakeCase.getReplyToMessageId());
        assertEquals("client_1", snakeCase.getClientMsgId());
        assertEquals(List.of("usr_2"), snakeCase.getAtUserIds());
        assertEquals(snakeCase, camelCase);

        String serialized = objectMapper.writeValueAsString(snakeCase);
        assertTrue(serialized.contains("\"target_id\""));
        assertTrue(serialized.contains("\"client_message_id\""));
    }

    @Test
    void contextEndpointUsesAuthenticatedPrincipalAndForwardsWindow() throws Exception {
        MessageService messageService = mock(MessageService.class);
        MessageController controller = new MessageController(messageService, mock(JwtUtil.class));
        UserIDProvider principal = () -> "usr_me";
        MessageContextResponse expected = MessageContextResponse.builder()
                .targetMessageId("msg_100")
                .messages(List.of())
                .hasMoreBefore(true)
                .hasMoreAfter(false)
                .build();
        when(messageService.getContext("conv_1", "msg_100", 12, 8, "usr_me"))
                .thenReturn(expected);

        ResponseEntity<MessageContextResponse> response = controller.getContext(
                "conv_1", "msg_100", 12, 8, principal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(messageService).getContext("conv_1", "msg_100", 12, 8, "usr_me");

        Method endpoint = MessageController.class.getMethod(
                "getContext", String.class, String.class, Integer.class, Integer.class, UserIDProvider.class);
        GetMapping mapping = endpoint.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/{conversationId}/{messageId}/context", mapping.value()[0]);
    }

    @Test
    void sendEndpointUsesAuthenticatedPrincipal() {
        MessageService messageService = mock(MessageService.class);
        MessageController controller = new MessageController(messageService, mock(JwtUtil.class));
        UserIDProvider principal = () -> "usr_me";
        MessageSendRequest request = MessageSendRequest.builder()
                .targetId("usr_peer")
                .messageType("text")
                .content("hello")
                .clientMsgId("client_1")
                .build();
        MessageResponse expected = MessageResponse.builder().messageId("msg_1").build();
        when(messageService.sendMessage(request, "usr_me")).thenReturn(expected);

        ResponseEntity<MessageResponse> response = controller.sendMessage(request, principal);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(messageService).sendMessage(request, "usr_me");
    }
}
