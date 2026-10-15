import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MsalService } from '@azure/msal-angular';

import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  standalone: true,
  template: `
    <section class="hero">
      <span class="eyebrow">Desarrollo Cloud Native · DSY1107</span>
      <h1>Pedidos360</h1>
      <p>
        SPA protegida con Azure AD (MSAL). El frontend sólo habla con el
        <strong>BFF</strong>; el BFF y el API Gateway validan el token antes de
        llegar al backend de pedidos.
      </p>

      @if (authState.authenticated()) {
        <p class="estado-ok">Sesión iniciada. Todo listo para operar.</p>
        <a class="btn primary" routerLink="/dashboard">Ir al Dashboard</a>
      } @else {
        <button class="btn primary" (click)="login()">Iniciar sesión con la cuenta del curso</button>
      }
    </section>

    <section class="features">
      <article class="card">
        <h3>Autenticación</h3>
        <p>OAuth 2.0 / OpenID Connect con Authorization Code + PKCE vía MSAL v5.</p>
      </article>
      <article class="card">
        <h3>API Manager</h3>
        <p>JWT validado en todas las rutas del API Gateway antes de tocar el backend.</p>
      </article>
      <article class="card">
        <h3>Mensajería</h3>
        <p>Eventos desacoplados sobre RabbitMQ con reintentos y Dead Letter Queues.</p>
      </article>
    </section>
  `,
  styles: [
    `
      .eyebrow {
        display: inline-block;
        text-transform: uppercase;
        letter-spacing: 0.14em;
        font-size: 0.72rem;
        font-weight: 700;
        color: var(--brand-600);
        background: var(--bg-alt);
        border-radius: 999px;
        padding: 4px 12px;
        margin-bottom: 14px;
      }
      .estado-ok {
        color: var(--ok);
        font-weight: 600;
      }
      .features {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--gap);
      }
      .features .card h3 {
        margin-top: 0;
      }
      .features .card p {
        margin: 0;
        color: var(--muted);
      }
    `
  ]
})
export class HomeComponent {
  constructor(
    protected readonly authState: AuthStateService,
    private readonly msal: MsalService
  ) {}

  login(): void {
    this.msal.loginRedirect();
  }
}
