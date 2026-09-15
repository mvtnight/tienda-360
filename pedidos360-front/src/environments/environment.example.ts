// Copiá este archivo a environment.ts y completá los valores.
// El access_token se solicita contra el scope de la API (pedidos-api) que
// el SPA debe tener aprobado. El BFF y el API Gateway validan: firma (JWKS),
// issuer, audience (api://<client-id>), exp y nbf.
export const environment = {
  production: false,

  // Azure AD / Entra ID del curso
  azure: {
    tenantId: 'TU_TENANT_ID',
    spaClientId: 'TU_CLIENT_ID_SPA',
    apiClientId: 'TU_CLIENT_ID_API',
    authority: 'https://login.microsoftonline.com/TU_TENANT_ID',
    redirectUri: 'http://localhost:4200',
    postLogoutRedirectUri: 'http://localhost:4200'
  },

  apiScope: 'api://TU_CLIENT_ID_API/access_as_user',
  bffUrl: 'http://localhost:8085'
};