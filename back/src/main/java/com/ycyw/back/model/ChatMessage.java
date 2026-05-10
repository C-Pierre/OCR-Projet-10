package com.ycyw.back.model;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    public interface OnChat {}
    public interface OnPresence {}

    private MessageType type;

    @NotBlank(groups = {OnChat.class, OnPresence.class})
    @Size(max = 100, groups = {OnChat.class, OnPresence.class})
    private String roomId;

    @NotBlank(groups = {OnChat.class, OnPresence.class})
    @Size(min = 1, max = 50, groups = {OnChat.class, OnPresence.class})
    private String sender;

    @NotBlank(groups = OnChat.class)
    @Size(max = 1000, groups = OnChat.class)
    private String content;

    @Builder.Default
    private Instant timestamp = Instant.now();
}