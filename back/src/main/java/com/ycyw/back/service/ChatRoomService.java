package com.ycyw.back.service;

import com.ycyw.back.model.ChatMessage;
import com.ycyw.back.model.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatRoomService {

    @Value("${ycyw.chat.max-history-per-room:50}")
    private int maxHistoryPerRoom;

    private final Map<String, Deque<ChatMessage>> roomHistories = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> roomMembers = new ConcurrentHashMap<>();

    /**
     * @param message le message à enregistrer
     */
    public void addMessage(ChatMessage message) {
        if (message.getType() != MessageType.CHAT) {
            return;
        }

        roomHistories.computeIfAbsent(message.getRoomId(), k -> new ArrayDeque<>());
        Deque<ChatMessage> history = roomHistories.get(message.getRoomId());

        // Limite la taille de l'historique en mémoire
        if (history.size() >= maxHistoryPerRoom) {
            history.pollFirst(); // supprime le plus ancien
        }
        history.addLast(message);

        log.debug("Message enregistré dans la salle {} ({} messages en mémoire)",
                message.getRoomId(), history.size());
    }

    /**
     * @param roomId identifiant de la salle
     * @return liste des messages dans l'ordre chronologique
     */
    public List<ChatMessage> getHistory(String roomId) {
        Deque<ChatMessage> history = roomHistories.get(roomId);
        if (history == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(history);
    }

    /**
     * @param roomId identifiant de la salle
     * @param username pseudonyme de l'utilisateur
     */
    public void userJoined(String roomId, String username) {
        roomMembers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                   .add(username);
        log.info("Utilisateur '{}' a rejoint la salle '{}'", username, roomId);
    }

    /**
     * @param roomId identifiant de la salle
     * @param username pseudonyme de l'utilisateur
     */
    public void userLeft(String roomId, String username) {
        Set<String> members = roomMembers.get(roomId);
        if (members != null) {
            members.remove(username);
        }
        log.info("Utilisateur '{}' a quitté la salle '{}'", username, roomId);
    }

    /**
     * @param roomId identifiant de la salle
     * @return ensemble des pseudonymes connectés
     */
    public Set<String> getMembers(String roomId) {
        return Collections.unmodifiableSet(
            roomMembers.getOrDefault(roomId, Collections.emptySet())
        );
    }

    /**
     * @return ensemble des roomIds actifs
     */
    public Set<String> getActiveRooms() {
        Set<String> rooms = new HashSet<>();
        rooms.addAll(roomHistories.keySet());
        rooms.addAll(roomMembers.keySet());
        return Collections.unmodifiableSet(rooms);
    }
}