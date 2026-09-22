import { Component, OnDestroy, OnInit } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

import { AuthStateService } from '../../services/auth-state.service';

interface ClaimRow {
  claim: string;
  value: string;
}

function decodeExp(token: string): number {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return Number(payload['exp'] ?? 0);
  } catch {
    return 0;
  }
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <h2>Dashboard</h2>

    @if (!loaded) {
      <p>Cargando perfil…</p>
    } @else {
      <section class="card">
        <h3>Perfil (claims)</h3>
        @for (row of claims; track row.claim) {
          <div class="claim">
            <span class="claim-name">{{ row.claim }}</span>
            <span class="claim-value">{{ row.value }}</span>
          </div>
        }
      </section>

      <section class="card">
        <h3>Access token (PKCE)</h3>
        <div class="row">
          <span class="claim-name">Validez</span>
          <span class="claim-value {{ tokenVence ? 'vencido' : '' }}">
            {{ tokenVence ? 'VENCIDO — vuelve a iniciar sesión' : 'Vence en ' + minutosRestantes + ' min' }}
          </span>
        </div>
        <div class="row">
          <span class="claim-name">Expira</span>
          <span class="claim-value">{{ expira ? expira.toLocaleTimeString() : '—' }}</span>
        </div>
        <div class="btn-row">
          <button class="btn" (click)="refresh()">Refrescar roles</button>
          <button class="btn" (click)="renovar()" [disabled]="renovando">
            {{ renovando ? 'Renovando…' : 'Renovar access token' }}
          </button>
          <button class="btn" (click)="copyToken()" [disabled]="copiando">
            {{ copiando ? 'Copiando…' : 'Copiar access_token (PKCE)' }}
          </button>
        </div>
        @if (copiado) {
          <p class="hint ok">Token copiado al portapapeles. Úsalo en los curls del API Manager.</p>
        }
        @if (error) {
          <p class="hint error">{{ error }}</p>
        }
      </section>
    }
  `,
  styles: [
    `
      .card {
        background: #fff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 20px;
        margin-bottom: 20px;
        box-shadow: 0 1px 3px rgba(13, 43, 69, 0.08);
      }
      .card h3 {
        margin-top: 0;
        color: #123a56;
      }
      .claim,
      .row {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        padding: 8px 0;
        border-bottom: 1px solid #eef2f7;
      }
      .claim-name {
        font-weight: 600;
        color: #123a56;
      }
      .claim-value {
        color: #334155;
        word-break: break-all;
        text-align: right;
      }
      .btn-row {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 16px;
      }
      .btn {
        background: #0e7fb8;
        color: #fff;
        border: none;
        border-radius: 6px;
        padding: 8px 14px;
        cursor: pointer;
        font: inherit;
      }
      .btn:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .hint {
        margin-top: 12px;
        font-size: 0.8rem;
      }
      .hint.ok {
        color: #14532d;
      }
      .hint.error {
        color: #8f1d1d;
      }
      .vencido {
        color: #c62828;
        font-weight: 700;
      }
    `
  ]
})
export class DashboardComponent implements OnInit, OnDestroy {
  claims: ClaimRow[] = [];
  loaded = false;
  token = '';
  expira: Date | null = null;
  minutosRestantes = 0;
  tokenVence = false;

  private timer: ReturnType<typeof setInterval> | undefined;

  constructor(
    private readonly msal: MsalService,
    private readonly authState: AuthStateService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.cargarToken();
    this.timer = setInterval(() => this.actualizarCuenta(), 5000);
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  private async cargarToken(): Promise<void> {
    await this.authState.refreshRoles();
    const account = this.msal.instance.getAllAccounts()[0];
    const idClaims = (account?.idTokenClaims ?? {}) as Record<string, unknown>;
    const name = String(idClaims['name'] ?? '');
    const email = String(idClaims['email'] ?? idClaims['preferred_username'] ?? '');
    const oid = String(idClaims['oid'] ?? '');
    const roles = this.authState.roles();

    this.token = '';
    try {
      this.token = await this.authState.getAccessToken();
    } catch {
      this.token = '';
    }
    if (this.token) {
      const exp = decodeExp(this.token);
      this.expira = exp ? new Date(exp * 1000) : null;
    } else {
      this.expira = null;
    }
    this.actualizarCuenta();

    this.claims = [
      { claim: 'nombre', value: name },
      { claim: 'email', value: email },
      { claim: 'oid (subject)', value: oid },
      { claim: 'roles (app role en access_token)', value: roles.length ? roles.join(', ') : '(ninguno)' }
    ];
    this.loaded = true;
  }

  private actualizarCuenta(): void {
    if (!this.expira) {
      this.tokenVence = true;
      this.minutosRestantes = 0;
      return;
    }
    this.minutosRestantes = Math.max(0, Math.floor((this.expira.getTime() - Date.now()) / 60000));
    this.tokenVence = this.minutosRestantes <= 0;
  }

  copiando = false;
  copiado = false;
  renovando = false;
  error = '';

  async refresh(): Promise<void> {
    await this.authState.refreshRoles();
    const roles = this.authState.roles();
    const row = this.claims.find((c) => c.claim.startsWith('roles'));
    if (row) {
      row.value = roles.length ? roles.join(', ') : '(ninguno)';
    }
    await this.cargarToken();
  }

  async renovar(): Promise<void> {
    this.renovando = true;
    this.error = '';
    this.copiado = false;
    try {
      await this.authState.getAccessToken();
      await this.cargarToken();
      if (this.token) {
        this.error = '';
      } else {
        this.error = 'No se pudo renovar el token. Comprueba la sesión en Azure AD.';
      }
    } catch {
      this.error = 'No se pudo renovar en silencio. Inicia sesión de nuevo si el token venció.';
    } finally {
      this.renovando = false;
    }
  }

  async copyToken(): Promise<void> {
    this.copiando = true;
    this.copiado = false;
    this.error = '';
    try {
      const token = this.token || (await this.authState.getAccessToken());
      await navigator.clipboard.writeText(token);
      this.copiado = true;
    } catch {
      this.error = 'No se pudo copiar el token.';
    } finally {
      this.copiando = false;
    }
  }
}