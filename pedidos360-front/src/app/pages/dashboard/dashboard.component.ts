import { Component, OnInit } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

import { AuthStateService } from '../../services/auth-state.service';

interface ClaimRow {
  claim: string;
  value: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <h2>Dashboard</h2>

    @if (!loaded) {
      <p>Cargando perfil…</p>
    } @else {
      @for (row of claims; track row.claim) {
        <div class="claim">
          <span class="claim-name">{{ row.claim }}</span>
          <span class="claim-value">{{ row.value }}</span>
        </div>
      }
      <button class="btn" (click)="refresh()">Refrescar roles</button>
      <button class="btn" (click)="copyToken()" [disabled]="copiando">
        {{ copiando ? 'Copiando…' : 'Copiar access_token (PKCE)' }}
      </button>
      @if (copiado) {
        <p class="hint">Token copiado al portapapeles. Úsalo en los curls del API Manager (vence en ~1 hora).</p>
      }
    }
  `,
  styles: [
    `
      .claim {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        padding: 8px 0;
        border-bottom: 1px solid #eee;
      }
      .claim-name {
        font-weight: 600;
        color: #123a56;
      }
      .claim-value {
        color: #333;
        word-break: break-all;
      }
      .btn {
        margin-top: 16px;
        background: #0e7fb8;
        color: #fff;
        border: none;
        border-radius: 6px;
        padding: 8px 16px;
        cursor: pointer;
      }
      .btn + .btn {
        margin-left: 12px;
      }
      .hint {
        color: #777;
        font-size: 0.8rem;
      }
    `
  ]
})
export class DashboardComponent implements OnInit {
  claims: ClaimRow[] = [];
  loaded = false;

  constructor(
    private readonly msal: MsalService,
    private readonly authState: AuthStateService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.authState.refreshRoles();
    const account = this.msal.instance.getAllAccounts()[0];
    const idClaims = (account?.idTokenClaims ?? {}) as Record<string, unknown>;
    const name = String(idClaims['name'] ?? '');
    const email = String(idClaims['email'] ?? idClaims['preferred_username'] ?? '');
    const oid = String(idClaims['oid'] ?? '');
    const roles = this.authState.roles();
    this.claims = [
      { claim: 'nombre', value: name },
      { claim: 'email', value: email },
      { claim: 'oid (subject)', value: oid },
      { claim: 'roles (app role en access_token)', value: roles.length ? roles.join(', ') : '(ninguno)' }
    ];
    this.loaded = true;
  }

  async refresh(): Promise<void> {
    await this.authState.refreshRoles();
    const roles = this.authState.roles();
    const row = this.claims.find((c) => c.claim.startsWith('roles'));
    if (row) {
      row.value = roles.length ? roles.join(', ') : '(ninguno)';
    }
  }

  copiando = false;
  copiado = false;

  async copyToken(): Promise<void> {
    this.copiando = true;
    this.copiado = false;
    try {
      const token = await this.authState.getAccessToken();
      await navigator.clipboard.writeText(token);
      this.copiado = true;
    } catch {
      this.copiado = false;
    } finally {
      this.copiando = false;
    }
  }
}