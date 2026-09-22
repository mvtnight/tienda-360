import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AuthStateService } from '../../services/auth-state.service';
import { CreatePedidoRequest, EstadoPedido, Pedido, PedidosService } from '../../services/pedidos.service';

@Component({
  selector: 'app-pedidos',
  imports: [FormsModule, CurrencyPipe],
  standalone: true,
  template: `
    <h2>Pedidos</h2>

    @if (error) {
      <div class="banner error">
        <span>{{ error }}</span>
        <button class="btn link" (click)="cargar()">Reintentar</button>
      </div>
    }
    @if (mensaje) {
      <div class="banner ok">
        <span>{{ mensaje }}</span>
        <button class="btn link" (click)="mensaje = ''">×</button>
      </div>
    }

    @if (canCreate) {
      <section class="card">
        <h3>Nuevo pedido</h3>
        <div class="grid-2">
          <label>
            Cliente
            <input [(ngModel)]="form.cliente" name="cliente" required placeholder="Nombre del cliente" />
          </label>
          <label>
            Email
            <input [(ngModel)]="form.email" name="email" required placeholder="cliente@correo.com" />
          </label>
        </div>
        <label>
          Items (uno por línea)
          <textarea [(ngModel)]="formItems" name="items" rows="3" placeholder="Laptop Pro 16&#10;Teclado mecánico"></textarea>
        </label>
        <label>
          Total
          <input [(ngModel)]="form.total" name="total" type="number" min="0" step="0.01" required placeholder="0.00" />
        </label>
        <button class="btn primary" (click)="crear()" [disabled]="creating || !formCargado">
          {{ creating ? 'Creando…' : 'Crear pedido' }}
        </button>
        <p class="hint">Requiere rol PEDIDOS_ADMIN o PEDIDOS_VENDEDOR (el backend valida con @PreAuthorize).</p>
      </section>
    }

    <section class="card">
      <h3>Listado ({{ pedidos.length }} pedidos)</h3>
      @if (canAdmin) {
        <p class="hint">Rol PEDIDOS_ADMIN: puedes cambiar estado (PATCH) y eliminar (DELETE).</p>
      } @else {
        <p class="hint">Modo lectura: tu rol no permite cambiar estado ni eliminar (403).</p>
      }

      @if (loading) {
        <p>Cargando…</p>
      } @else if (pedidos.length === 0) {
        <p>No hay pedidos.</p>
      } @else {
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Cliente</th>
                <th>Email</th>
                <th>Items</th>
                <th>Estado</th>
                <th>Total</th>
                @if (canAdmin) {
                  <th>Acciones</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (p of pedidos; track p.id) {
                <tr>
                  <td>{{ p.id }}</td>
                  <td>{{ p.cliente }}</td>
                  <td>{{ p.email }}</td>
                  <td class="items">{{ p.items.join(', ') }}</td>
                  <td><span class="estado {{ p.estado }}">{{ p.estado }}</span></td>
                  <td>{{ p.total | currency }}</td>
                  @if (canAdmin) {
                    <td class="actions">
                      <select
                        class="estado-select"
                        [value]="estadoSel(p.id)"
                        (change)="setEstado(p.id, $any($event.target).value)"
                      >
                        @for (e of estados; track e) {
                          <option [value]="e">{{ e }}</option>
                        }
                      </select>
                      <button class="btn" (click)="cambiarEstado(p)" [disabled]="accionId === p.id">
                        {{ accionId === p.id ? 'Guardando…' : 'Guardar' }}
                      </button>
                      <button class="btn danger" (click)="eliminar(p)" [disabled]="accionId === p.id">Eliminar</button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
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
      .grid-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
      }
      label {
        display: block;
        margin-bottom: 12px;
        font-weight: 600;
        color: #334155;
      }
      input,
      textarea,
      select {
        width: 100%;
        margin-top: 4px;
        padding: 8px;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        box-sizing: border-box;
        font: inherit;
      }
      textarea {
        resize: vertical;
      }
      input:focus,
      textarea:focus,
      select:focus {
        outline: 2px solid #4cc2ff;
        border-color: #0e7fb8;
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
      .btn.danger {
        background: #c62828;
      }
      .btn.link {
        background: transparent;
        color: #0e7fb8;
        padding: 0 8px;
        text-decoration: underline;
      }
      .btn:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      .table-wrap {
        overflow-x: auto;
      }
      table {
        width: 100%;
        border-collapse: collapse;
      }
      th,
      td {
        text-align: left;
        padding: 10px 8px;
        border-bottom: 1px solid #eef2f7;
        vertical-align: top;
      }
      th {
        color: #475569;
        font-size: 0.8rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
      }
      .items {
        color: #475569;
        font-size: 0.85rem;
        max-width: 260px;
      }
      .actions {
        display: flex;
        gap: 6px;
        white-space: nowrap;
      }
      .estado-select {
        width: auto;
        margin: 0;
        padding: 6px;
      }
      .estado {
        display: inline-block;
        border-radius: 999px;
        padding: 2px 10px;
        font-size: 0.72rem;
        font-weight: 700;
        background: #eef2f7;
        color: #334155;
      }
      .banner {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 10px 14px;
        border-radius: 8px;
        margin-bottom: 14px;
        font-weight: 600;
      }
      .banner.error {
        color: #8f1d1d;
        background: #ffe9ed;
        border: 1px solid #f5c6cb;
      }
      .banner.ok {
        color: #14532d;
        background: #dff5e4;
        border: 1px solid #a7d7b1;
      }
      .hint {
        color: #64748b;
        font-size: 0.8rem;
        margin-top: 10px;
      }
    `
  ]
})
export class PedidosComponent implements OnInit {
  pedidos: Pedido[] = [];
  loading = false;
  creating = false;
  error = '';
  mensaje = '';
  formCargado = false;
  canCreate = false;
  canAdmin = false;
  accionId: number | null = null;

  estados: EstadoPedido[] = ['RECIBIDO', 'EN_PREPARACION', 'ENVIADO', 'ENTREGADO', 'CANCELADO'];
  private sel = new Map<number, EstadoPedido>();

  form: CreatePedidoRequest = { cliente: '', email: '', items: [], total: 0 };
  formItems = '';

  constructor(
    private readonly pedidosService: PedidosService,
    private readonly authState: AuthStateService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.authState.refreshRoles();
    this.canCreate =
      this.authState.hasRole('PEDIDOS_ADMIN') || this.authState.hasRole('PEDIDOS_VENDEDOR');
    this.canAdmin = this.authState.hasRole('PEDIDOS_ADMIN');
    this.formCargado = true;
    await this.cargar();
  }

  estadoSel(id: number): EstadoPedido {
    return this.sel.get(id) ?? this.pedidos.find((p) => p.id === id)?.estado ?? 'RECIBIDO';
  }

  setEstado(id: number, estado: EstadoPedido): void {
    this.sel.set(id, estado);
  }

  async cargar(): Promise<void> {
    this.loading = true;
    this.error = '';
    this.mensaje = '';
    try {
      this.pedidos = await firstValueFrom(this.pedidosService.list());
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.loading = false;
    }
  }

  async crear(): Promise<void> {
    const items = this.formItems.split('\n').map((s) => s.trim()).filter(Boolean);
    if (!this.form.cliente.trim() || items.length === 0 || this.form.total <= 0) {
      this.mensaje = '';
      this.error = 'Completa cliente, al menos un item y un total mayor a 0.';
      return;
    }
    this.creating = true;
    this.error = '';
    this.mensaje = '';
    try {
      await firstValueFrom(this.pedidosService.create({ ...this.form, items }));
      this.form = { cliente: '', email: '', items: [], total: 0 };
      this.formItems = '';
      this.mensaje = 'Pedido creado (201).';
      await this.cargar();
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.creating = false;
    }
  }

  async cambiarEstado(p: Pedido): Promise<void> {
    this.accionId = p.id;
    this.error = '';
    this.mensaje = '';
    try {
      await firstValueFrom(this.pedidosService.actualizarEstado(p.id, this.estadoSel(p.id)));
      this.mensaje = `Pedido ${p.id} actualizado a ${this.estadoSel(p.id)} (200).`;
      await this.cargar();
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.accionId = null;
    }
  }

  async eliminar(p: Pedido): Promise<void> {
    if (!confirm(`¿Eliminar el pedido #${p.id} de ${p.cliente}?`)) {
      return;
    }
    this.accionId = p.id;
    this.error = '';
    this.mensaje = '';
    try {
      await firstValueFrom(this.pedidosService.eliminar(p.id));
      this.mensaje = `Pedido ${p.id} eliminado (204).`;
      await this.cargar();
    } catch (e) {
      this.error = mensajeError(e);
    } finally {
      this.accionId = null;
    }
  }
}

function mensajeError(e: unknown): string {
  console.error('pedidos360 error:', e);
  if (e instanceof HttpErrorResponse) {
    if (e.status === 401) {
      return 'No autorizado (401): no se adjuntó un token válido. Vuelve a iniciar sesión (Salir → Ingresar).';
    }
    if (e.status === 403) {
      return 'Prohibido (403): tu usuario no tiene el rol necesario para esta operación.';
    }
    if (e.status === 400) {
      return 'Solicitud inválida (400): revisa que todos los campos estén completos.';
    }
    if (e.status === 0) {
      return 'Sin conexión (0): el navegador bloqueó la petición (revisa CORS) o no hay internet.';
    }
    return `Error HTTP ${e.status}: ${e.statusText}`;
  }
  if (e instanceof Error) {
    return e.message && e.message.trim() ? e.message : String(e);
  }
  return 'Error inesperado al contactar el servidor.';
}