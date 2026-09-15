import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';

import { HomeComponent } from './pages/home/home.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { PedidosComponent } from './pages/pedidos/pedidos.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [MsalGuard]
  },
  {
    path: 'pedidos',
    component: PedidosComponent,
    canActivate: [MsalGuard]
  },
  { path: '**', redirectTo: '' }
];