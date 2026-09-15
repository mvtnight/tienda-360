import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { firstValueFrom } from 'rxjs';

import { AuthStateService } from '../../services/auth-state.service';
import { CreatePedidoRequest, Pedido, PedidosService } from '../../services/pedidos.service';

@Component({
  selector: 'app-pedidos',
  imports: [FormsModule, CurrencyPipe],
  standalone: true,
  template: `
    <h2>Pedidos</h2>

    @if (error) {
      <p class="error">Error: {{ error }}</p>
    }

    @if (canCreate) {
      <section class="card">
        <h3>Nuevo pedido</h3>
        <label>
          Cliente
          <input [(ngModel)]="form.cliente" name="cliente" required />
        </label>
        <label>
          Email
          <input [(ngModel)]="form.email" name="email" required />
        </label>
        <label>
          Detalle
          <input [(ngModel)]="form.detalle" name="detalle" required />
        </label>
        <label>
          Total
          <input [(ngModel)]="form.total" name="total" type="number" min="0" step="0.01" required />
        </label>
        <button class="btn primary" (click)="crear()" [disabled]="creating || !formCargado">
          {{ creating ? 'Creando…' : 'Crear pedido' }}
        </button>
        <p class="hint">Requiere rol PEDIDOS_ADMIN o PEDIDOS_VENDEDOR.</p>
      </section>
    }

    <section class="card">
      <h3>Listado (leído a través del BFF)</h3>
      @if (loading) {
        <p>Cargando…</p>
      } @else if (pedidos.length === 0) {
        <p>No hay pedidos.</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>#</th>
              <th>Cliente</th>
              <th>Email</th>
              <th>Estado</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            @for (p of pedidos; track p.id) {
              <tr>
                <td>{{ p.id }}</td>
                <td>{{ p.cliente }}</td>
                <td>{{ p.email }}</td>
                <td>{{ p.estado }}</td>
                <td>{{ p.total | currency }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: [
    `
      .card {
        border: 1px solid #ddd;
        border-radius: 10px;
        padding: 20px;
        margin-bottom: 20px;
      }
      label {
        display: block;
        margin-bottom: 10px;
      }
      input {
        width: 100%;
        padding: 8px;
        border: 1px solid #ccc;
        border-radius: 6px;
        box-sizing: border-box;
      }
      .btn {
        background: #0e7fb8;
        color: #fff;
        border: none;
        border-radius: 6px;
        padding: 8px 16px;
        cursor: pointer;
      }
      table {
        width: 100%;
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: 8px;
        border-bottom: 1px solid #eee;
      }
      .error {
        color: #b00020;
        background: #ffe9ed;
        padding: 10px;
        border-radius: 6px;
      }
      .hint {
        color: #777;
        font-size: 0.8rem;
      }
    `
  ]
})
export class PedidosComponent implements OnInit {
  pedidos: Pedido[] = [];
  loading = false;
  creating = false;
  error = '';
  formCargado = false;
  canCreate = false;

  form: CreatePedidoRequest = { cliente: '', email: '', detalle: '', total: 0 };

  constructor(
    private readonly pedidosService: PedidosService,
    private readonly authState: AuthStateService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.authState.refreshRoles();
    this.canCreate =
      this.authState.hasRole('PEDIDOS_ADMIN') || this.authState.hasRole('PEDIDOS_VENDEDOR');
    this.formCargado = true;
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.loading = true;
    this.error = '';
    try {
      this.pedidos = await firstValueFrom(this.pedidosService.list());
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.loading = false;
    }
  }

  async crear(): Promise<void> {
    this.creating = true;
    this.error = '';
    try {
      await firstValueFrom(this.pedidosService.create(this.form));
      this.form = { cliente: '', email: '', detalle: '', total: 0 };
      await this.cargar();
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.creating = false;
    }
  }
}

function mensajeError(e: unknown): string {
  if (e instanceof Error) {
    const http = (e as unknown) as { status?: number; message?: string };
    if (http.status === 401) {
      return 'No autorizado (401): la sesión o el token no son válidos.';
    }
    if (http.status === 403) {
      return 'Prohibido (403): tu usuario no tiene el rol necesario.';
    }
    return http.message ?? String(e);
  }
  return String(e);
}