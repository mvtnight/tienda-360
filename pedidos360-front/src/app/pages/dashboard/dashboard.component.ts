import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MsalService } from '@azure/msal-angular';
import { firstValueFrom } from 'rxjs';

import { AuthStateService } from '../../services/auth-state.service';
import { PedidosService } from '../../services/pedidos.service';
import { environment } from '../../../environments/environment';

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

function tokenShort(token: string): string {
  return token.length > 56 ? `${token.slice(0, 56)}…` : token;
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

      <section class="card">
        <h3>Diagnóstico API Gateway (interceptor)</h3>
        <p class="hint">Verifica que el interceptor adjunte el token: HEADERS que envía la SPA:</p>
        <div class="mono">GET {{ apiProbeUrl }}</div>
        <div class="mono">Authorization: Bearer {{ tokenPreview }}</div>
        <div class="btn-row">
          <button class="btn" (click)="probarApi()" [disabled]="probando">
            {{ probando ? 'Probando…' : 'Probar GET /api/pedidos (con token)' }}
          </button>
          <button class="btn" (click)="copiarHeader()">Copiar header de ejemplo</button>
        </div>
        @if (headerCopiado) {
          <p class="hint ok">Header copiado. Pégalo en DevTools/Postman o en la evidencia.</p>
        }
        @if (diag) {
          <div class="diag">
            <div class="diag-line">
              <span class="badge-static {{ diag.ok ? 'b-ok' : 'b-err' }}">HTTP {{ diag.status }}</span>
              <span>{{ diag.ok ? 'OK: token válido y adjuntado por el interceptor' : 'Falló: el token no se adjuntó o no es válido' }}</span>
            </div>
            <pre>{{ diag.body }}</pre>
            <p class="hint ok">Respuesta real del backend vía API Manager · {{ diag.at }}</p>
          </div>
        }
        @if (diagError) {
          <p class="hint error">{{ diagError }}</p>
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
      .mono {
        font-family: Consolas, monospace;
        font-size: 0.75rem;
        background: #0f172a;
        color: #e2e8f0;
        padding: 8px 12px;
        border-radius: 6px;
        margin: 6px 0;
        overflow-wrap: anywhere;
        white-space: normal;
      }
      .diag {
        margin-top: 14px;
        border-top: 1px dashed #cbd5e1;
        padding-top: 12px;
      }
      .diag-line {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 8px;
      }
      .badge-static {
        padding: 3px 10px;
        border-radius: 999px;
        font-weight: 700;
        font-size: 0.85rem;
        color: #fff;
      }
      .b-ok {
        background: #1b7a43;
      }
      .b-err {
        background: #b31b1b;
      }
      .diag pre {
        font-family: Consolas, monospace;
        font-size: 0.72rem;
        background: #f1f5f9;
        color: #0f172a;
        padding: 10px 12px;
        border-radius: 6px;
        overflow: auto;
        max-height: 260px;
        white-space: pre-wrap;
      }
    `
  ]
})
export class DashboardComponent implements OnInit, OnDestroy {
  claims: ClaimRow[] = [];
  loaded = false;
  token = '';
  tokenPreview = '(sin token)';
  expira: Date | null = null;
  minutosRestantes = 0;
  tokenVence = false;

  apiProbeUrl = `${environment.apiGateway.enabled ? environment.apiGateway.baseUrl : `${environment.bffUrl}/bff`}/pedidos`;
  diag: { status: number; ok: boolean; body: string; at: string } | null = null;
  diagError = '';
  probando = false;
  headerCopiado = false;

  private timer: ReturnType<typeof setInterval> | undefined;

  constructor(
    private readonly msal: MsalService,
    private readonly authState: AuthStateService,
    private readonly pedidos: PedidosService
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
      this.tokenPreview = tokenShort(this.token);
    } else {
      this.expira = null;
      this.tokenPreview = '(sin token)';
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

  async probarApi(): Promise<void> {
    this.probando = true;
    this.diag = null;
    this.diagError = '';
    this.headerCopiado = false;
    try {
      const token = this.token || (await this.authState.getAccessToken());
      this.tokenPreview = token ? tokenShort(token) : '(sin token)';
      const pedidos = await firstValueFrom(this.pedidos.list());
      this.diag = {
        status: 200,
        ok: true,
        body: JSON.stringify(pedidos, null, 2).slice(0, 900),
        at: new Date().toLocaleTimeString()
      };
    } catch (e) {
      const http = e as { status?: number };
      const status = http.status ?? 0;
      this.diag = {
        status,
        ok: false,
        body: this.cuerpoDiagnostico(e),
        at: new Date().toLocaleTimeString()
      };
    } finally {
      this.probando = false;
    }
  }

  private cuerpoDiagnostico(e: unknown): string {
    if (e instanceof HttpErrorResponse) {
      const dato =
        typeof e.error === 'string'
          ? e.error
          : e.error
            ? JSON.stringify(e.error, null, 2).slice(0, 400)
            : e.message ?? '';
      return `status ${e.status}\n${dato}`;
    }
    return String(e);
  }

  async copiarHeader(): Promise<void> {
    this.headerCopiado = false;
    try {
      const token = this.token || (await this.authState.getAccessToken());
      const texto = `GET ${this.apiProbeUrl}\nAuthorization: Bearer ${token}`;
      await navigator.clipboard.writeText(texto);
      this.headerCopiado = true;
    } catch {
      this.diagError = 'No se pudo copiar el header.';
    }
  }
}