package com.ycyw.back.controller;

import com.ycyw.back.model.ChatMessage;
import com.ycyw.back.model.MessageType;
import com.ycyw.back.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomService chatRoomService;

    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(
            @DestinationVariable String roomId,
            @Payload @Valid ChatMessage message) {

        ChatMessage enriched = ChatMessage.builder()
                .type(MessageType.CHAT)
                .roomId(roomId)
                .sender(message.getSender())
                .content(message.getContent())
                .timestamp(Instant.now())
                .build();

        chatRoomService.addMessage(enriched);

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, enriched);
        log.debug("Message diffusé dans la salle {} par {}", roomId, enriched.getSender());
    }

    @MessageMapping("/chat/{roomId}/join")
    public void joinRoom(
            @DestinationVariable String roomId,
            @Payload ChatMessage message) {

        chatRoomService.userJoined(roomId, message.getSender());

        ChatMessage notification = ChatMessage.builder()
                .type(MessageType.JOIN)
                .roomId(roomId)
                .sender(message.getSender())
                .content(message.getSender() + " a rejoint le tchat")
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, notification);
    }

    @MessageMapping("/chat/{roomId}/leave")
    public void leaveRoom(
            @DestinationVariable String roomId,
            @Payload ChatMessage message) {

        chatRoomService.userLeft(roomId, message.getSender());

        ChatMessage notification = ChatMessage.builder()
                .type(MessageType.LEAVE)
                .roomId(roomId)
                .sender(message.getSender())
                .content(message.getSender() + " a quitté le tchat")
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, notification);
    }

    @MessageMapping("/chat/{roomId}/typing")
    public void notifyTyping(
            @DestinationVariable String roomId,
            @Payload ChatMessage message) {

        ChatMessage typingNotification = ChatMessage.builder()
                .type(MessageType.TYPING)
                .roomId(roomId)
                .sender(message.getSender())
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, typingNotification);
    }

    @GetMapping("/{roomId}/history")
    public List<ChatMessage> getHistory(@PathVariable String roomId) {
        return chatRoomService.getHistory(roomId);
    }

    @GetMapping("/{roomId}/members")
    public Set<String> getMembers(@PathVariable String roomId) {
        return chatRoomService.getMembers(roomId);
    }

    @GetMapping("/rooms")
    public Map<String, Object> getActiveRooms() {
        Set<String> rooms = chatRoomService.getActiveRooms();
        return Map.of(
            "rooms", rooms,
            "count", rooms.size()
        );
    }
}