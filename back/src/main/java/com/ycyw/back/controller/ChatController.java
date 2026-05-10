package com.ycyw.back.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.ycyw.back.model.ChatMessage;
import com.ycyw.back.model.ChatMessage.OnChat;
import com.ycyw.back.model.ChatMessage.OnPresence;
import com.ycyw.back.service.ChatRoomService;
import com.ycyw.back.service.ChatBroadcastService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final ChatBroadcastService broadcastService;

    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(
        @DestinationVariable String roomId,
        @Payload @Validated(OnChat.class) ChatMessage message
    ) {
        log.debug("Message reçu dans la salle {} par {}", roomId, message.getSender());
        broadcastService.broadcast(chatRoomService.buildAndSendMessage(roomId, message));
    }

    @MessageMapping("/chat/{roomId}/join")
    public void joinRoom(
        @DestinationVariable String roomId,
        @Payload @Validated(OnPresence.class) ChatMessage message
    ) {
        broadcastService.broadcast(chatRoomService.userJoined(roomId, message.getSender()));
    }

    @MessageMapping("/chat/{roomId}/leave")
    public void leaveRoom(
        @DestinationVariable String roomId,
        @Payload @Validated(OnPresence.class) ChatMessage message
    ) {
        broadcastService.broadcast(chatRoomService.userLeft(roomId, message.getSender()));
    }

    @MessageMapping("/chat/{roomId}/typing")
    public void notifyTyping(
        @DestinationVariable String roomId,
        @Payload @Validated(OnPresence.class) ChatMessage message
    ) {
        broadcastService.broadcast(chatRoomService.buildTypingNotification(roomId, message.getSender()));
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
        return Map.of("rooms", rooms, "count", rooms.size());
    }
}