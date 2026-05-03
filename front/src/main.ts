(window as any).global = window;

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './config/app.config';
import { AppComponent } from './components/app.component';

bootstrapApplication(AppComponent, appConfig).catch((err) =>
    console.error(err)
);