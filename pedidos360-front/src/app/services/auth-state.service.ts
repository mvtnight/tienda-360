import { Injectable, signal } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

import { environment } from '../../environments/environment';

function decodeRolesFromAccessToken(token: string): string[] {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    const roles: unknown = payload['roles'];
    return Array.isArray(roles) ? (roles as string[]) : [];
  } catch {
    return [];
  }
}

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly isAuthenticated = signal(false);
  private readonly userRoles = signal<string[]>([]);

  constructor(private readonly msal: MsalService) {
    const account = this.msal.instance.getAllAccounts()[0];
    if (account) {
      this.isAuthenticated.set(true);
      void this.refreshRoles();
    }
  }

  authenticated = this.isAuthenticated.asReadonly();
  roles = this.userRoles.asReadonly();

  /**
   * Obtiene en silencio un access_token para el BFF y extrae el claim
   * `roles` (app roles de Azure AD). Si falla (token expirado o permiso
   * no concedido) deja vacío el set de roles.
   */
  async refreshRoles(): Promise<void> {
    const account = this.msal.instance.getAllAccounts()[0];
    if (!account) {
      this.isAuthenticated.set(false);
      this.userRoles.set([]);
      return;
    }
    this.isAuthenticated.set(true);
    try {
      const response = await this.msal.instance.acquireTokenSilent({
        account,
        scopes: [environment.apiScope]
      });
      this.userRoles.set(decodeRolesFromAccessToken(response.accessToken));
    } catch {
      this.userRoles.set([]);
    }
  }

  hasRole(role: string): boolean {
    return this.userRoles().includes(role);
  }
}