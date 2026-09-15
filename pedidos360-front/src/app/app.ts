import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MsalService } from '@azure/msal-angular';

import { AuthStateService } from './services/auth-state.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor(
    private readonly msal: MsalService,
    readonly authState: AuthStateService
  ) {}

  login(): void {
    this.msal.loginRedirect();
  }

  logout(): void {
    this.msal.logoutRedirect();
  }

  get userName(): string {
    const account = this.msal.instance.getAllAccounts()[0];
    return account?.name ?? account?.username ?? '';
  }
}