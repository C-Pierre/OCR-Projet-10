package com.ycyw.back.service;

import lombok.RequiredArgsConstructor;
import com.ycyw.back.model.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@RequiredArgsConstructor
public class ChatBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(ChatMessage message) {
        messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), message);
    }
}