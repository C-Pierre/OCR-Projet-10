package com.ycyw.back.service;

import com.ycyw.back.model.ChatMessage;
import com.ycyw.back.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomServiceTest {

    private ChatRoomService service;

    @BeforeEach
    void setUp() {
        service = new ChatRoomService();
    }

    @Test
    @DisplayName("Doit enregistrer un message CHAT dans l'historique")
    void should_addMessage_when_typeIsChat() {
        ChatMessage message = ChatMessage.builder()
            .type(MessageType.CHAT)
            .roomId("booking-123")
            .sender("Alice")
            .content("Bonjour !")
            .build();

        service.addMessage(message);

        List<ChatMessage> history = service.getHistory("booking-123");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getContent()).isEqualTo("Bonjour !");
    }

    @Test
    @DisplayName("Ne doit PAS enregistrer un message TYPING dans l'historique")
    void should_notAddMessage_when_typeIsTyping() {
        ChatMessage typing = ChatMessage.builder()
            .type(MessageType.TYPING)
            .roomId("booking-123")
            .sender("Alice")
            .build();

        service.addMessage(typing);

        List<ChatMessage> history = service.getHistory("booking-123");
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("Doit retourner une liste vide pour une salle inexistante")
    void should_returnEmptyList_when_roomDoesNotExist() {
        List<ChatMessage> history = service.getHistory("salle-inexistante");
        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("Doit enregistrer et retrouver les membres d'une salle")
    void should_trackMembers_when_usersJoinAndLeave() {
        service.userJoined("booking-123", "Alice");
        service.userJoined("booking-123", "Bob");

        assertThat(service.getMembers("booking-123")).containsExactlyInAnyOrder("Alice", "Bob");

        service.userLeft("booking-123", "Alice");

        assertThat(service.getMembers("booking-123")).containsExactly("Bob");
    }

    @Test
    @DisplayName("Doit lister les salles actives après activité")
    void should_listActiveRooms_when_messagesSent() {
        service.addMessage(ChatMessage.builder()
                .type(MessageType.CHAT).roomId("booking-1").sender("Alice").content("Hi").build());
        service.userJoined("booking-2", "Bob");

        assertThat(service.getActiveRooms()).containsExactlyInAnyOrder("booking-1", "booking-2");
    }
}