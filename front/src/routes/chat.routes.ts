import { Routes } from '@angular/router';
import { ChatComponent } from 'src/components/chat/chat.component';

export const CHAT_ROUTES: Routes = [
    {
        path: '',
        component: ChatComponent,
        title: 'YCYW — Tchat',
    },
];