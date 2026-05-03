package com.ycyw.back.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @NotNull
    private MessageType type;

    @NotBlank
    private String roomId;

    @NotBlank
    @Size(min = 1, max = 50)
    private String sender;

    @Size(max = 1000)
    private String content;

    @Builder.Default
    private Instant timestamp = Instant.now();
}