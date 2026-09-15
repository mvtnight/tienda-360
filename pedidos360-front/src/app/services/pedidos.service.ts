import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';

export type EstadoPedido = 'CREADO' | 'EN_PREPARACION' | 'ENVIADO' | 'ENTREGADO' | 'CANCELADO';

export interface Pedido {
  id: number;
  cliente: string;
  email: string;
  estado: EstadoPedido;
  detalle: string;
  total: number;
  fechaCreacion: string;
}

export interface CreatePedidoRequest {
  cliente: string;
  email: string;
  detalle: string;
  total: number;
}

@Injectable({ providedIn: 'root' })
export class PedidosService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(`${environment.bffUrl}/bff/pedidos`);
  }

  get(id: number): Observable<Pedido> {
    return this.http.get<Pedido>(`${environment.bffUrl}/bff/pedidos/${id}`);
  }

  create(body: CreatePedidoRequest): Observable<Pedido> {
    return this.http.post<Pedido>(`${environment.bffUrl}/bff/pedidos`, body);
  }
}