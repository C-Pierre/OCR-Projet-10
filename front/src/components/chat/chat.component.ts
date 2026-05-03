import {
    Component,
    OnInit,
    OnDestroy,
    signal,
    computed,
    effect,
    inject,
    ViewChild,
    ElementRef,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { WebSocketService, ChatMessage } from 'src/core/service/websocket/websocket.service';

@Component({
    selector: 'app-chat',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './chat.component.html',
    styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {

    readonly wsService = inject(WebSocketService);

    readonly username    = signal('');
    readonly roomId      = signal('booking-42');
    readonly messageText = signal('');
    readonly messages    = signal<ChatMessage[]>([]);
    readonly typingUsers = signal<Set<string>>(new Set());
    readonly isJoined    = signal(false);

    readonly typingLabel = computed(() => {
        const others = [...this.typingUsers()].filter(u => u !== this.username());
        if (others.length === 0) return '';
        if (others.length === 1) return `${others[0]} est en train d'écrire…`;
        return `${others.slice(0, -1).join(', ')} et ${others.at(-1)} écrivent…`;
    });

    readonly isConnected = this.wsService.isConnected;
    readonly connectionState = this.wsService.connectionState;

    @ViewChild('messagesContainer') messagesContainer!: ElementRef<HTMLDivElement>;
    @ViewChild('messageInput') messageInput!: ElementRef<HTMLInputElement>;

    private readonly typingSubject = new Subject<void>();
    private typingTimeouts = new Map<string, ReturnType<typeof setTimeout>>();
    private subscription = new Subscription();

    private readonly http = inject(HttpClient);


    constructor() {
        effect(() => {
            this.messages();

            queueMicrotask(() => {
            this.scrollToBottom();
            });
        });
    }

    ngOnInit(): void {
        this.subscription.add(
        this.wsService.messages$.subscribe((msg) => this.handleIncoming(msg))
        );

        this.subscription.add(
        this.typingSubject.pipe(debounceTime(300)).subscribe(() => {
            if (this.isJoined()) {
            this.wsService.sendTyping(this.roomId(), this.username());
            }
        })
        );
    }

    ngOnDestroy(): void {
        if (this.isJoined()) {
        this.wsService.disconnect(this.roomId(), this.username());
        }
        this.subscription.unsubscribe();
        this.typingSubject.complete();
    }

    joinRoom(): void {
        const name = this.username().trim();
        const room = this.roomId().trim();
        if (!name || !room) return;

        this.http.get<ChatMessage[]>(`/api/chat/${room}/history`).subscribe({
            next: (history) => this.messages.set(history),
            error: () => {}
        });

        this.wsService.connect(room, name);
        this.isJoined.set(true);
    }

    sendMessage(): void {
        const content = this.messageText().trim();
        if (!content || !this.isJoined()) return;

        this.wsService.sendMessage(this.roomId(), content, this.username());
        this.messageText.set('');
    }

    onTyping(): void {
        this.typingSubject.next();
    }

    onKeydown(event: KeyboardEvent): void {
        if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        this.sendMessage();
        }
    }

    private handleIncoming(msg: ChatMessage): void {
        switch (msg.type) {
        case 'CHAT':
        case 'JOIN':
        case 'LEAVE':
            this.messages.update((list) => [...list, msg]);
            break;

        case 'TYPING':
            this.typingUsers.update((set) => new Set([...set, msg.sender]));

            this.clearTypingTimeout(msg.sender);
            this.typingTimeouts.set(
            msg.sender,
            setTimeout(() => {
                this.typingUsers.update((set) => {
                const next = new Set(set);
                next.delete(msg.sender);
                return next;
                });
            }, 3000)
            );
            break;
        }
    }

    private clearTypingTimeout(username: string): void {
        const existing = this.typingTimeouts.get(username);
        if (existing) {
        clearTimeout(existing);
        this.typingTimeouts.delete(username);
        }
    }

    private scrollToBottom(): void {
        const el = this.messagesContainer?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
    }


    isOwnMessage(msg: ChatMessage): boolean {
        return msg.sender === this.username();
    }

    formatTime(timestamp?: string): string {
        if (!timestamp) return '';
        return new Date(timestamp).toLocaleTimeString('fr-FR', {
        hour: '2-digit',
        minute: '2-digit',
        });
    }
}