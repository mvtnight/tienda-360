import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export type EstadoPedido = 'RECIBIDO' | 'EN_PREPARACION' | 'ENVIADO' | 'ENTREGADO' | 'CANCELADO';

export interface Pedido {
  id: number;
  cliente: string;
  email: string;
  items: string[];
  total: number;
  estado: EstadoPedido;
  creador?: string;
  createdAt: string;
}

export interface CreatePedidoRequest {
  cliente: string;
  email: string;
  items: string[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class PedidosService {
  constructor(private readonly http: HttpClient) {}

  // Local: SPA -> BFF (/bff/pedidos). Público: SPA -> API Manager (/api/pedidos)
  private readonly apiBase = environment.apiGateway.enabled
    ? environment.apiGateway.baseUrl
    : `${environment.bffUrl}/bff`;

  list(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${this.apiBase}/pedidos`);
  }

  get(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(`${this.apiBase}/pedidos/${id}`);
  }

  create(body: CreatePedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${this.apiBase}/pedidos`, body);
  }

  actualizarEstado(id: number, estado: EstadoPedido): Observable<Pedido> {
    return this.http.patch<Pedido>(`${this.apiBase}/pedidos/${id}/estado`, { estado });
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/pedidos/${id}`);
  }
}