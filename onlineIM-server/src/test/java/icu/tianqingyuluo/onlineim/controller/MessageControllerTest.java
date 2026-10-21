package icu.tianqingyuluo.onlineim.controller;

import icu.tianqingyuluo.onlineim.service.ChatMediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import icu.tianqingyuluo.onlineim.pojo.dto.request.MessageSendRequest;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageContextResponse;
import icu.tianqingyuluo.onlineim.pojo.dto.response.MessageResponse;
import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import icu.tianqingyuluo.onlineim.service.MessageEditService;
import icu.tianqingyuluo.onlineim.service.MessageForwardService;
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
        MessageController controller = new MessageController(messageService, mock(JwtUtil.class), mock(ChatMediaService.class), mock(MessageForwardService.class), mock(MessageEditService.class), mock(icu.tianqingyuluo.onlineim.service.MessageSearchService.class));
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
        MessageController controller = new MessageController(messageService, mock(JwtUtil.class), mock(ChatMediaService.class), mock(MessageForwardService.class), mock(MessageEditService.class), mock(icu.tianqingyuluo.onlineim.service.MessageSearchService.class));
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

    @Test
    void parsesVoiceClientDuration() {
        org.junit.jupiter.api.Assertions.assertNull(MessageController.parseClientDuration(null, null));
        org.junit.jupiter.api.Assertions.assertEquals(1500L, MessageController.parseClientDuration(null, "1500"));
        org.junit.jupiter.api.Assertions.assertEquals(2500L, MessageController.parseClientDuration("2.5", null));
        org.junit.jupiter.api.Assertions.assertEquals(800L, MessageController.parseClientDuration("9", "800"), "duration_ms 优先");
        org.junit.jupiter.api.Assertions.assertThrows(icu.tianqingyuluo.onlineim.exception.ChatMediaException.class,
                () -> MessageController.parseClientDuration("abc", null));
    }

    @Test
    void forwardRequestAcceptsSnakeCaseAndEndpointUsesPrincipal() throws Exception {
        icu.tianqingyuluo.onlineim.pojo.dto.request.MessageForwardRequest request = new ObjectMapper().readValue("""
                {
                  "source_conversation_id": "conv_1",
                  "message_ids": ["msg_1", "msg_2"],
                  "targets": ["usr_2", "grp_1"],
                  "mode": "merged",
                  "client_request_id": "req_1"
                }
                """, icu.tianqingyuluo.onlineim.pojo.dto.request.MessageForwardRequest.class);
        assertEquals("conv_1", request.getSourceConversationId());
        assertEquals(List.of("msg_1", "msg_2"), request.getMessageIds());
        assertEquals("merged", request.getMode());
        assertEquals("req_1", request.getClientRequestId());

        MessageForwardService forwardService = mock(MessageForwardService.class);
        MessageController controller = new MessageController(mock(MessageService.class), mock(JwtUtil.class),
                mock(ChatMediaService.class), forwardService, mock(MessageEditService.class),
                mock(icu.tianqingyuluo.onlineim.service.MessageSearchService.class));
        icu.tianqingyuluo.onlineim.pojo.dto.response.MessageForwardResponse expected =
                icu.tianqingyuluo.onlineim.pojo.dto.response.MessageForwardResponse.builder()
                        .mode("merged").results(List.of()).build();
        when(forwardService.forward(request, "usr_me")).thenReturn(expected);

        assertEquals(expected, controller.forwardMessages(request, () -> "usr_me").getBody());
        assertEquals(401, controller.forwardMessages(request, null).getStatusCode().value());
    }

    @Test
    void editRequestAcceptsSnakeCaseAndEndpointUsesPrincipal() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest request = mapper.readValue(
                "{\"content\":\"新内容\",\"at_users\":[\"usr_b\"]}",
                icu.tianqingyuluo.onlineim.pojo.dto.request.MessageEditRequest.class);
        assertEquals("新内容", request.getContent());
        assertEquals(List.of("usr_b"), request.getAtUsers());

        MessageEditService editService = mock(MessageEditService.class);
        MessageController controller = new MessageController(mock(MessageService.class), mock(JwtUtil.class),
                mock(ChatMediaService.class), mock(MessageForwardService.class), editService,
                mock(icu.tianqingyuluo.onlineim.service.MessageSearchService.class));
        MessageResponse expected = MessageResponse.builder().messageId("msg_1").content("新内容").edited(true).build();
        when(editService.edit("msg_1", request, "usr_me")).thenReturn(expected);

        assertEquals(expected, controller.editMessage("msg_1", request, () -> "usr_me").getBody());
        assertEquals(401, controller.editMessage("msg_1", request, null).getStatusCode().value());
    }

    @Test
    void searchEndpointUsesPrincipalAndPassesQueryParameters() throws Exception {
        icu.tianqingyuluo.onlineim.service.MessageSearchService searchService =
                mock(icu.tianqingyuluo.onlineim.service.MessageSearchService.class);
        MessageController controller = new MessageController(mock(MessageService.class), mock(JwtUtil.class),
                mock(ChatMediaService.class), mock(MessageForwardService.class), mock(MessageEditService.class),
                searchService);
        icu.tianqingyuluo.onlineim.pojo.dto.response.MessageSearchResponse expected =
                icu.tianqingyuluo.onlineim.pojo.dto.response.MessageSearchResponse.builder()
                        .keyword("你好").results(List.of()).build();
        when(searchService.search("usr_me", "你好", "conv_1", "1:msg_1", 10)).thenReturn(expected);

        assertEquals(expected, controller.searchMessages("你好", "conv_1", "1:msg_1", 10, () -> "usr_me").getBody());
        assertEquals(401, controller.searchMessages("你好", null, null, null, null).getStatusCode().value());

        Method search = MessageController.class.getMethod("searchMessages", String.class, String.class,
                String.class, Integer.class, UserIDProvider.class);
        assertEquals("/search", search.getAnnotation(GetMapping.class).value()[0]);
    }
}
