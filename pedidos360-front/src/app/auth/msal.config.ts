import { PublicClientApplication, InteractionType } from '@azure/msal-browser';
import type { MsalGuardConfiguration, MsalInterceptorConfiguration } from '@azure/msal-angular';
import { environment } from '../../environments/environment';

export const msalConfig = {
  auth: {
    clientId: environment.azure.spaClientId,
    authority: environment.azure.authority,
    redirectUri: environment.azure.redirectUri,
    postLogoutRedirectUri: environment.azure.postLogoutRedirectUri
  },
  cache: {
    cacheLocation: 'localStorage' as const,
    storeAuthStateInCookie: false
  }
};

/**
 * Instancia única de MSAL: se usa tanto en main.ts (para procesar la
 * redirección del login) como en app.config (providers de la app).
 */
export const msalInstance = new PublicClientApplication(msalConfig);

/**
 * El interceptor agrega automáticamente el access_token a las llamadas
 * HTTP cuyo origen coincide con el BFF (mismo matcher que el API Manager).
 */
export function MSALInterceptorConfigFactory(): MsalInterceptorConfiguration {
  // El interceptor agrega el access_token a las llamadas cuyo origen coincide
  // con la API: local -> BFF, público -> API Manager (/api).
  const apiBase = environment.apiGateway.enabled
    ? environment.apiGateway.baseUrl
    : environment.bffUrl;
  const protectedResourceMap = new Map<string, Array<string>>([
    [apiBase, [environment.apiScope]]
  ]);
  // strictMatching:false = matching por prefijo (la clave .../api cubre
  // .../api/pedidos). Con strictMatching (default en msal-angular v5) la
  // URL se ancla completa y el interceptor no adjunta el token.
  return { interactionType: InteractionType.Redirect, protectedResourceMap, strictMatching: false };
}

export function MSALGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: { scopes: ['openid', 'profile', environment.apiScope] }
  };
}