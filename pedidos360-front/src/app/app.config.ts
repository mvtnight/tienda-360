import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import {
  MsalBroadcastService,
  MsalGuard,
  MsalInterceptor,
  MSAL_GUARD_CONFIG,
  MSAL_INSTANCE,
  MSAL_INTERCEPTOR_CONFIG,
  MsalService
} from '@azure/msal-angular';

import { routes } from './app.routes';
import {
  msalInstance,
  MSALGuardConfigFactory,
  MSALInterceptorConfigFactory
} from './auth/msal.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),

    // MSAL: misma instancia que main.ts para no perder la sesión
    { provide: MSAL_INSTANCE, useValue: msalInstance },
    { provide: MSAL_GUARD_CONFIG, useFactory: MSALGuardConfigFactory },
    { provide: MSAL_INTERCEPTOR_CONFIG, useFactory: MSALInterceptorConfigFactory },
    { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },
    MsalService,
    MsalGuard,
    MsalBroadcastService
  ]
};