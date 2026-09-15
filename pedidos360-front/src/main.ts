import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { msalInstance } from './app/auth/msal.config';

// Flujo Redirect (Authorization Code + PKCE):
// si Azure AD nos devuelve a la app tras el login/logout, primero
// procesamos la respuesta y recién después inicializamos Angular.
msalInstance
  .initialize()
  .then(() => msalInstance.handleRedirectPromise())
  .then(() => bootstrapApplication(App, appConfig))
  .catch((err) => console.error('MSAL initialization error', err));