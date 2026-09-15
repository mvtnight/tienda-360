export const environment = {
  production: false,

  // ============================================================
  // Azure AD (Entra ID) del curso — completá estos valores.
  // La app registration "SPA" expone clientId; la "API de Pedidos"
  // (pedidos-api) expone el scope api://<client-id-api>/access_as_user
  // y el SPA debe tener permiso sobre ese scope.
  // ============================================================
  azure: {
    tenantId: '00000000-0000-0000-0000-000000000000',
    spaClientId: '00000000-0000-0000-0000-000000000000',
    apiClientId: '00000000-0000-0000-0000-000000000000',
    authority: 'https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000',
    redirectUri: 'http://localhost:4200',
    postLogoutRedirectUri: 'http://localhost:4200'
  },

  // Scope del access_token que el BFF/API Manager validan
  apiScope: 'api://00000000-0000-0000-0000-000000000000/access_as_user',

  // Backend for Frontend (local o endpoint del API Gateway en AWS)
  bffUrl: 'http://localhost:8085'
};