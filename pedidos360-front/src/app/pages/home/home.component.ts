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
      <h1>Pedidos360</h1>
      <p>
        SPA protegida con Azure AD (MSAL). El frontend sólo habla con el
        <strong>BFF</strong>; el BFF y el API Gateway validan el token antes de
        llegar al backend de pedidos.
      </p>

      @if (authState.authenticated()) {
        <p>Ya iniciaste sesión.</p>
        <a class="btn primary" routerLink="/dashboard">Ir al Dashboard</a>
      } @else {
        <button class="btn primary" (click)="login()">Iniciar sesión con la cuenta del curso</button>
      }
    </section>
  `
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