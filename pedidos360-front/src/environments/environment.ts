export const environment = {
  production: false,

  // ============================================================
  // Azure AD (Entra ID) del curso — completá estos valores.
  // La app registration "SPA" expone clientId; la "API de Pedidos"
  // (pedidos-api) expone el scope api://<client-id-api>/access_as_user
  // y el SPA debe tener permiso sobre ese scope.
  // ============================================================
  azure: {
    tenantId: '0b4bca41-b3f5-427c-aeac-2dbcd055f91d',
    spaClientId: '54b906c8-47fb-4066-a49a-b3aa7f05427e',
    apiClientId: '6da3f8f4-905c-4c76-abf0-c711d0dd3926',
    authority: 'https://login.microsoftonline.com/0b4bca41-b3f5-427c-aeac-2dbcd055f91d',
    redirectUri: 'http://localhost:4200',
    postLogoutRedirectUri: 'http://localhost:4200'
  },

  // Scope del access_token que el BFF/API Manager validan
  apiScope: 'api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user',

  // Backend for Frontend (local o endpoint del API Gateway en AWS)
  bffUrl: 'http://localhost:8085'
};