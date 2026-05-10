package com.ycyw.back.service;

import java.util.*;
import java.time.Instant;
import com.ycyw.back.model.ChatMessage;
import com.ycyw.back.model.MessageType;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ChatRoomService {

    private final Map<String, List<ChatMessage>> histories = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> members = new ConcurrentHashMap<>();

    public ChatMessage buildAndSendMessage(String roomId, ChatMessage incoming) {
        ChatMessage enriched = ChatMessage.builder()
            .type(MessageType.CHAT)
            .roomId(roomId)
            .sender(incoming.getSender())
            .content(incoming.getContent())
            .timestamp(Instant.now())
            .build();
        addMessage(enriched);
        return enriched;
    }

    public ChatMessage userJoined(String roomId, String username) {
        members.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);
        return ChatMessage.builder()
            .type(MessageType.JOIN)
            .roomId(roomId)
            .sender(username)
            .content(username + " a rejoint le tchat")
            .timestamp(Instant.now())
            .build();
    }

    public ChatMessage userLeft(String roomId, String username) {
        getMembers(roomId).remove(username);
        return ChatMessage.builder()
            .type(MessageType.LEAVE)
            .roomId(roomId)
            .sender(username)
            .content(username + " a quitté le tchat")
            .timestamp(Instant.now())
            .build();
    }

    public ChatMessage buildTypingNotification(String roomId, String username) {
        return ChatMessage.builder()
            .type(MessageType.TYPING)
            .roomId(roomId)
            .sender(username)
            .timestamp(Instant.now())
            .build();
    }

    public void addMessage(ChatMessage message) {
        histories.computeIfAbsent(message.getRoomId(), k -> new ArrayList<>()).add(message);
    }

    public List<ChatMessage> getHistory(String roomId) {
        return Collections.unmodifiableList(histories.getOrDefault(roomId, List.of()));
    }

    public Set<String> getMembers(String roomId) {
        return members.getOrDefault(roomId, Set.of());
    }

    public Set<String> getActiveRooms() {
        return Collections.unmodifiableSet(members.keySet());
    }
}