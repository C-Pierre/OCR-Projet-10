import { Injectable, OnDestroy, signal, computed } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';

export interface ChatMessage {
    type: 'CHAT' | 'JOIN' | 'LEAVE' | 'TYPING';
    roomId: string;
    sender: string;
    content?: string;
    timestamp?: string;
}

export type ConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {

    private readonly _connectionState = signal<ConnectionState>('DISCONNECTED');
    readonly connectionState = this._connectionState.asReadonly();
    readonly isConnected = computed(() => this._connectionState() === 'CONNECTED');

    readonly messages$ = new Subject<ChatMessage>();

    private stompClient: Client | null = null;
    private roomSubscription: StompSubscription | null = null;
    private currentRoomId: string | null = null;


    /**
    * @param roomId
    * @param username
    */
    connect(roomId: string, username: string): void {
        if (this.stompClient?.connected) {
        this.subscribeToRoom(roomId, username);
        return;
        }

        this._connectionState.set('CONNECTING');
        this.currentRoomId = roomId;

        this.stompClient = new Client({

            webSocketFactory: () => new SockJS('/ws'),

            reconnectDelay: 5000,

            onConnect: () => {
                this._connectionState.set('CONNECTED');
                this.subscribeToRoom(roomId, username);
            },

            onDisconnect: () => {
                this._connectionState.set('DISCONNECTED');
            },

            onStompError: (frame) => {
                console.error('[WebSocketService] Erreur STOMP :', frame);
                this._connectionState.set('ERROR');
            },

            debug: (msg) => console.debug('[STOMP]', msg),
        });

        this.stompClient.activate();
    }

    disconnect(roomId: string, username: string): void {
        if (this.stompClient?.connected) {
        this.publish(roomId, { type: 'LEAVE', roomId, sender: username });
        }
        this.roomSubscription?.unsubscribe();
        this.stompClient?.deactivate();
        this._connectionState.set('DISCONNECTED');
    }


    /**
    * @param roomId
    * @param content
    * @param sender
    */
    sendMessage(roomId: string, content: string, sender: string): void {
        this.publish(roomId, {
        type: 'CHAT',
        roomId,
        sender,
        content,
        });
    }

    sendTyping(roomId: string, sender: string): void {
        this.publish(roomId, { type: 'TYPING', roomId, sender });
    }


    private subscribeToRoom(roomId: string, username: string): void {
        this.roomSubscription?.unsubscribe();

        this.roomSubscription = this.stompClient!.subscribe(
        `/topic/chat/${roomId}`,
        (frame: IMessage) => {
            const message: ChatMessage = JSON.parse(frame.body);
            this.messages$.next(message);
        }
        );

        this.publish(roomId, { type: 'JOIN', roomId, sender: username });
    }

    private publish(roomId: string, message: ChatMessage): void {
        if (!this.stompClient?.connected) {
            console.warn('[WebSocketService] Tentative d\'envoi sans connexion active');
            return;
        }

        const destination = `/app/chat/${roomId}/${message.type.toLowerCase()}`;
        const dest = message.type === 'CHAT'
        ? `/app/chat/${roomId}/send`
        : `/app/chat/${roomId}/${message.type.toLowerCase()}`;

        this.stompClient.publish({
        destination: dest,
        body: JSON.stringify(message),
        });
    }

    ngOnDestroy(): void {
        this.stompClient?.deactivate();
        this.messages$.complete();
    }
}