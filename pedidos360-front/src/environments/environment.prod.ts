// Build PÚBLICO (production): se sirve por CloudFront y llama directo al
// API Manager (API Gateway) con el token PKCE del navegador.
export const environment = {
  production: true,

  azure: {
    tenantId: '0b4bca41-b3f5-427c-aeac-2dbcd055f91d',
    spaClientId: '54b906c8-47fb-4066-a49a-b3aa7f05427e',
    apiClientId: '6da3f8f4-905c-4c76-abf0-c711d0dd3926',
    authority: 'https://login.microsoftonline.com/0b4bca41-b3f5-427c-aeac-2dbcd055f91d',
    redirectUri: 'https://d2u1dalj9nm2b1.cloudfront.net',
    postLogoutRedirectUri: 'https://d2u1dalj9nm2b1.cloudfront.net'
  },

  apiScope: 'api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user',

  bffUrl: 'https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod',

  apiGateway: {
    enabled: true,
    baseUrl: 'https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod/api'
  }
};