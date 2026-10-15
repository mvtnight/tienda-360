# Guía de Implementación y Guion de Presentación (100% Rúbrica)
**Asignatura:** Desarrollo Cloud Native I (DSY1107)  
**Evaluaciones:** EP3 (Encargo - 12%) & EP4 (Presentación - 18%)  
**Institución:** Duoc UC  

---

## ÍNDICE
1. [Resumen Estratégico para Puntuación Máxima](#1-resumen-estratégico-para-puntuación-máxima)
2. [Arquitectura del Sistema](#2-arquitectura-del-sistema)
3. [Implementación Backend (Spring Boot & RabbitMQ)](#3-implementación-backend-spring-boot--rabbitmq)
   - [3.1 Configuración Centralizada (application.yml)](#31-configuración-centralizada-applicationyml)
   - [3.2 Beans de Configuración (Queues, Exchanges, Bindings y DLQs)](#32-beans-de-configuración-queues-exchanges-bindings-y-dlqs)
   - [3.3 Desacoplamiento de Lógica de Negocio](#33-desacoplamiento-de-lógica-de-negocio)
   - [3.4 Consumidores por Dominio (@RabbitListener)](#34-consumidores-por-dominio-rabbitlistener)
   - [3.5 Control de Errores, ACK Manual y DLQ](#35-control-de-errores-ack-manual-y-dlq)
   - [3.6 Microservicio Administrador de RabbitMQ (REST + Validaciones)](#36-microservicio-administrador-de-rabbitmq-rest--validaciones)
4. [Infraestructura Cloud, IDaaS y API Manager](#4-infraestructura-cloud-idaas-y-api-manager)
   - [4.1 Clúster de RabbitMQ (2 Nodos con Docker Compose)](#41-clúster-de-rabbitmq-2-nodos-con-docker-compose)
   - [4.2 IDaaS (OAuth 2.0 / OpenID Connect) & Frontend Angular](#42-idaas-oauth-20--openid-connect--frontend-angular)
   - [4.3 API Manager & Validación JWT](#43-api-manager--validación-jwt)
5. [Guion Paso a Paso para la Presentación EP4 (5 a 10 Minutos)](#5-guion-paso-a-paso-para-la-presentación-ep4-5-a-10-minutos)
6. [Checklist Final de Verificación (100% Rúbrica)](#6-checklist-final-de-verificación-100-rúbrica)

---

## 1. Resumen Estratégico para Puntuación Máxima

Para obtener el **100% ("Muy buen desempeño")** en ambas evaluaciones, la implementación y la defensa deben cumplir con los siguientes pilares de la rúbrica:

| Criterio Rúbrica | Ponderación | Clave para el 100% |
| :--- | :---: | :--- |
| **Configuración Centralizada** | 12% | Todo nombre de cola, exchange o binding DEBE estar en `application.yml` o clase de constantes. Cero cadenas de texto explícitas (*hardcoded*) en clases de servicio/controladores. |
| **Declaración de Beans RabbitMQ** | 13% | Definir explícitamente Beans Java de `Queue`, `DirectExchange`, `TopicExchange`, `Binding` y sus equivalentes `DLQ` para cada caso de uso. |
| **Aislamiento de Capas** | 10% | La configuración de mensajería vive estrictamente en el paquete `config`. La capa `@Service` de negocio solo inyecta `RabbitTemplate` o interfaces personalizadas. |
| **Consumidores por Dominio** | 15% | Uso de `@RabbitListener`, agrupados en paquetes por contexto delimitado (ej: `notification.listener`, `ticket.listener`). |
| **Manejo de ACK y DLQs** | 20% | Confirmación explícita (Manual ACK/NACK/REJECT), políticas de reintento, captura de excepciones y enrutamiento automático a Dead Letter Queues con registro en Logs. |
| **API REST Administradora** | 13% | Microservicio dedicado con verbos REST claros (`POST /api/v1/queues`, `DELETE /api/v1/queues/{name}`, etc.). |
| **Encapsulación AdminService** | 10% | Toda la manipulación dinámica de colas/exchanges se delega a `RabbitAdmin`. El controlador solo invoca la interfaz del servicio. |
| **Validación de Entradas** | 7% | Uso de DTOs con `@Valid`, `@NotBlank`, `@Pattern` en las peticiones del Microservicio Administrador para impedir valores nulos o inválidos. |

---

## 2. Arquitectura del Sistema

```
                         +-----------------------------------+
                         |    IDaaS (Auth0 / Keycloak)       |
                         |  (Tenant + OAuth 2.0 / OIDC)      |
                         +-----------------+-----------------+
                                           |
                                      JWT  | Validación JWT
                                           v
+-------------------+           +-------------------+          +------------------------+
| Angular Frontend  |  ------>  |    API Manager    | -------> | Microservicios Backend |
| (OAuth2 / OIDC)   |           | (Filtro JWT Auth) |          | (Spring Boot REST)     |
+-------------------+           +-------------------+          +-----------+------------+
                                                                           |
                                                                  Productor / Consumidor
                                                                           v
                                                            +-------------------------------+
                                                            |  RabbitMQ Cluster (2 Nodos)   |
                                                            |  - Exchanges (Direct, Topic)  |
                                                            |  - 3 Queues + 3 DLQs          |
                                                            +-------------------------------+
```

---

## 3. Implementación Backend (Spring Boot & RabbitMQ)

### 3.1 Configuración Centralizada (`application.yml`)

Ninguna cadena de mensajería debe escribirse directamente en clases Java.

```yaml
spring:
  application:
    name: notification-service
  rabbitmq:
    addresses: rabbitmq-node1:5672,rabbitmq-node2:5672
    username: admin
    password: adminpassword
    listener:
      simple:
        acknowledge-mode: manual # Manejo explícito de ACK/NACK
        prefetch: 1

app:
  rabbitmq:
    exchanges:
      direct: app.direct.exchange
      topic: app.topic.exchange
      dead-letter: app.dlx.exchange
    queues:
      notifications:
        name: queue.notifications
        routing-key: notification.key
        dlq-name: queue.notifications.dlq
        dlq-routing-key: notification.dlq.key
      tickets:
        name: queue.tickets
        routing-key: ticket.*
        dlq-name: queue.tickets.dlq
        dlq-routing-key: ticket.dlq.key
      documents:
        name: queue.documents
        routing-key: document.generate
        dlq-name: queue.documents.dlq
        dlq-routing-key: document.dlq.key
```

---

### 3.2 Beans de Configuración (Queues, Exchanges, Bindings y DLQs)

Ubicación: `com.duoc.system.config.RabbitMQConfig`

```java
package com.duoc.system.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchanges.direct}")
    private String directExchangeName;

    @Value("${app.rabbitmq.exchanges.topic}")
    private String topicExchangeName;

    @Value("${app.rabbitmq.exchanges.dead-letter}")
    private String dlxExchangeName;

    @Value("${app.rabbitmq.queues.notifications.name}")
    private String notificationQueueName;

    @Value("${app.rabbitmq.queues.notifications.routing-key}")
    private String notificationRoutingKey;

    @Value("${app.rabbitmq.queues.notifications.dlq-name}")
    private String notificationDlqName;

    @Value("${app.rabbitmq.queues.notifications.dlq-routing-key}")
    private String notificationDlqRoutingKey;

    // 1. DLX Exchange y DLQ
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName);
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(notificationDlqName).build();
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlq())
                .to(deadLetterExchange())
                .with(notificationDlqRoutingKey);
    }

    // 2. Main Exchange, Main Queue vinculada a su DLQ
    @Bean
    public DirectExchange mainDirectExchange() {
        return new DirectExchange(directExchangeName);
    }

    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", dlxExchangeName);
        args.put("x-dead-letter-routing-key", notificationDlqRoutingKey);
        return QueueBuilder.durable(notificationQueueName)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(mainDirectExchange())
                .with(notificationRoutingKey);
    }

    @Bean
    public TopicExchange mainTopicExchange() {
        return new TopicExchange(topicExchangeName);
    }
}
```

---

### 3.3 Desacoplamiento de Lógica de Negocio

La capa de negocio **no** configura colas, solo publica eventos mediante una abstracción o `RabbitTemplate`.

```java
package com.duoc.system.service.impl;

import com.duoc.system.dto.NotificationEventDto;
import com.duoc.system.service.NotificationPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationPublisherServiceImpl implements NotificationPublisherService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherServiceImpl.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchanges.direct}")
    private String directExchange;

    @Value("${app.rabbitmq.queues.notifications.routing-key}")
    private String notificationRoutingKey;

    public NotificationPublisherServiceImpl(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishNotification(NotificationEventDto event) {
        log.info("Publicando evento de notificación para el usuario: {}", event.getUserId());
        rabbitTemplate.convertAndSend(directExchange, notificationRoutingKey, event);
    }
}
```

---

### 3.4 Consumidores por Dominio (`@RabbitListener`)

Ubicación organizada por dominio funcional (ej: `com.duoc.system.consumer.notification`).

```java
package com.duoc.system.consumer.notification;

import com.duoc.system.dto.NotificationEventDto;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @RabbitListener(queues = "${app.rabbitmq.queues.notifications.name}")
    public void receiveNotification(
            @Payload NotificationEventDto event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        log.info("Procesando mensaje de notificación en Worker. Payload: {}", event);

        try {
            // Lógica de procesamiento
            processNotification(event);

            // Confirmación explícita (ACK)
            channel.basicAck(tag, false);
            log.info("Mensaje procesado y ACK enviado correctamente. Tag: {}", tag);

        } catch (Exception e) {
            log.error("Falla en el procesamiento de la notificación. Enviando NACK para redirigir a DLQ. Error: {}", e.getMessage());
            
            // Rejection manual sin requeue -> El mensaje viaja a la DLQ
            channel.basicNack(tag, false, false);
        }
    }

    private void processNotification(NotificationEventDto event) {
        if (event.getPayload() == null || event.getPayload().isBlank()) {
            throw new IllegalArgumentException("Payload inválido. Desencadenando flujo de error.");
        }
        // Procesamiento exitoso simulación
    }
}
```

---

### 3.5 Control de Errores, ACK Manual y DLQ

Criterios demostrados para el 100%:
1. **Modo de confirmación**: `acknowledge-mode: manual`.
2. **ACK Explícito**: `channel.basicAck(tag, false)` tras procesamiento exitoso.
3. **NACK con `requeue=false`**: `channel.basicNack(tag, false, false)` ante fallo irreparable, obligando al broker a desviar el mensaje a la DLQ asociada vía `x-dead-letter-exchange`.
4. **Logs estructurados**: Registro formal del evento en cada ciclo de vida de la mensajería.

---

### 3.6 Microservicio Administrador de RabbitMQ (REST + Validaciones)

#### DTO con Validaciones (`QueueRequestDto.java`)
```java
package com.duoc.system.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class QueueRequestDto {

    @NotBlank(message = "El nombre de la cola es obligatorio.")
    @Size(min = 3, max = 50, message = "El nombre de la cola debe tener entre 3 y 50 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre contiene caracteres no permitidos.")
    private String queueName;

    private boolean durable = true;
    private boolean autoDelete = false;

    // Getters y Setters
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public boolean isDurable() { return durable; }
    public void setDurable(boolean durable) { this.durable = durable; }
    public boolean isAutoDelete() { return autoDelete; }
    public void setAutoDelete(boolean autoDelete) { this.autoDelete = autoDelete; }
}
```

#### Servicio de Administración (`RabbitAdminService.java`)
```java
package com.duoc.system.admin.service;

import com.duoc.system.admin.dto.QueueRequestDto;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;

import org.springframework.stereotype.Service;

@Service
public class RabbitAdminService {

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    public void createQueue(QueueRequestDto dto) {
        Queue queue = new Queue(dto.getQueueName(), dto.isDurable(), false, dto.isAutoDelete());
        amqpAdmin.declareQueue(queue);
    }

    public boolean deleteQueue(String queueName) {
        return amqpAdmin.deleteQueue(queueName);
    }
}
```

#### Controlador REST (`RabbitAdminController.java`)
```java
package com.duoc.system.admin.controller;

import com.duoc.system.admin.dto.QueueRequestDto;
import com.duoc.system.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/queues")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping
    public ResponseEntity<String> createQueue(@Valid @RequestBody QueueRequestDto request) {
        rabbitAdminService.createQueue(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Cola '" + request.getQueueName() + "' creada exitosamente.");
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<String> deleteQueue(@PathVariable("name") String queueName) {
        boolean deleted = rabbitAdminService.deleteQueue(queueName);
        if (deleted) {
            return ResponseEntity.ok("Cola '" + queueName + "' eliminada exitosamente.");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("La cola no existe.");
    }
}
```

---

## 4. Infraestructura Cloud, IDaaS y API Manager

### 4.1 Clúster de RabbitMQ (2 Nodos con Docker Compose)

Para sustentar la exigencia de la presentación: "mostrar la existencia de dos nodos RabbitMQ configurados y su integración en un clúster".

```yaml
version: '3.8'

networks:
  rabbitmq-cluster-net:
    driver: bridge

services:
  rabbitmq-node1:
    image: rabbitmq:3-management
    container_name: rabbitmq-node1
    hostname: rabbitmq-node1
    environment:
      RABBITMQ_ERLANG_COOKIE: 'DUOC_SECRET_COOKIE_100'
      RABBITMQ_DEFAULT_USER: 'admin'
      RABBITMQ_DEFAULT_PASS: 'adminpassword'
    ports:
      - "5672:5672"
      - "15672:15672"
    networks:
      - rabbitmq-cluster-net

  rabbitmq-node2:
    image: rabbitmq:3-management
    container_name: rabbitmq-node2
    hostname: rabbitmq-node2
    environment:
      RABBITMQ_ERLANG_COOKIE: 'DUOC_SECRET_COOKIE_100'
    depends_on:
      - rabbitmq-node1
    ports:
      - "5673:5672"
      - "15673:15672"
    networks:
      - rabbitmq-cluster-net
```

---

### 4.2 IDaaS (OAuth 2.0 / OpenID Connect) & Frontend Angular

1. **Configuración Tenant IDaaS** (Auth0 / Keycloak):
   - Tenant configurado con `Issuer URI` y `Audience`.
   - Usuarios de prueba creados.
2. **Frontend Angular**:
   - Integración con librería `angular-oauth2-oidc`.
   - Obtención de `access_token` JWT.
   - Envio automático del encabezado HTTP: `Authorization: Bearer <JWT>`.

---

### 4.3 API Manager & Validación JWT

1. **Rutas Configuradas**: El API Manager expone `/api/v1/*` redirigiendo al backend en la nube.
2. **Política de Seguridad**: Validación de firma JWT contra el endpoint PKKS (`/.well-known/jwks.json`) del IDaaS.
3. **Comportamiento Requerido**:
   - Sin Token / Token Expirado / Firma Inválida $\rightarrow$ `HTTP 401 Unauthorized` / `HTTP 403 Forbidden`.
   - Token Válido $\rightarrow$ Reenvío exitoso al backend con `HTTP 200/201`.

---

## 5. Guion Paso a Paso para la Presentación EP4 (5 a 10 Minutos)

Este guion está optimizado temporalmente para cubrir **el 100% de los 11 puntos requeridos** en las instrucciones de la presentación.

---

### **Minuto 0:00 - 01:30 | Introducción y Demostración Cloud / API Manager**

> **Estudiante:** "Buenas tardes Profesor/a. A continuación presentaré la solución Cloud Native desarrollada para la Evaluación Parcial N°3 y N°4. 
> 
> Comienzo mostrando nuestra consola Cloud donde la instancia del **API Manager** se encuentra activa y en ejecución [Mostrar pantalla de API Manager]. 
>
> Aquí podemos observar la configuración de rutas donde el API Manager mapea las solicitudes entrantes hacia los microservicios Backend desplegados. Procederé a realizar una prueba desde Postman sin JWT para demostrar que el API Manager valida la autenticidad del Token: al enviar la petición sin token recibimos un error **401 Unauthorized**.
>
> Ahora, enviamos la misma solicitud incluyendo un JWT válido emitido por nuestro IDaaS y vemos que el API Manager autoriza la petición e invoca el backend respondiendo exitosamente con un **200 OK**."

---

### **Minuto 01:30 - 03:30 | Autenticación IDaaS y Frontend Angular**

> **Estudiante:** "A nivel de identidad, muestro la consola de nuestro IDaaS [Auth0 / Keycloak] donde tenemos configurado el Tenant del proyecto con los usuarios registrados para las pruebas.
>
> Pasamos ahora al **Frontend Angular** desplegado en la nube. Iniciamos el flujo de autenticación utilizando el estándar **OAuth 2.0 / OpenID Connect**. Hacemos clic en 'Iniciar Sesión', nos redirige a la pantalla centralizada del IDaaS, ingresamos las credenciales y volvemos a la aplicación.
>
> Abro la consola del navegador en la pestaña 'Red' (Network) para demostrar que Angular adjunta de forma automática el Bearer JWT en las cabeceras HTTP en cada consumo de endpoint del backend a través del API Manager."

---

### **Minuto 03:30 - 06:30 | Infraestructura RabbitMQ en Clúster y Consola de Gestión**

> **Estudiante:** "Pasamos a la arquitectura de mensajería asíncrona. En nuestro entorno cloud/contenedorizado ejecutamos `docker-compose` levantando un **Clúster de RabbitMQ compuesto por 2 Nodos** [Mostrar terminal o consola Docker].
>
> Ingresamos a la Management UI de RabbitMQ y demostramos lo siguiente:
> 1. En la pestaña **Overview / Nodes** se visualizan los 2 nodos integrados y sincronizados correctamente en el clúster (`rabbitmq-node1` y `rabbitmq-node2`).
> 2. En la pestaña **Exchanges** evidenciamos la presencia de los exchanges de tipo `Direct` (para tareas puntuales) y `Topic` (para eventos con claves de enrutamiento flexibles).
> 3. En la pestaña **Queues** tenemos operativas las 3 colas funcionales del sistema (`queue.notifications`, `queue.tickets`, `queue.documents`), cada una asociada explícitamente con su respectiva **Dead Letter Queue (DLQ)**."

---

### **Minuto 06:30 - 08:30 | Demostración de Código Backend, Manual ACK, Manejo de Errores y API Admin**

> **Estudiante:** "Mostramos rápidamente la estructura del código del Backend en Spring Boot:
>
> - **Configuración Centralizada:** Como exige la rúbrica, todos los nombres de colas, exchanges y bindings están centralizados en `application.yml` y mapeados en clases `@Configuration` mediante Beans Java. No existe ninguna cadena hardcodeada.
> - **Separación de Responsabilidades:** La lógica de negocio no conoce detalles de infraestructura de mensajería; solo inyecta nuestro servicio publicador.
> - **Consumidores y ACK:** En la clase `NotificationConsumer`, utilizamos la anotación `@RabbitListener`. Implementamos **ACK Manual** (`channel.basicAck`).
> - **Prueba de DLQ:** Provocamos una falla intencional con un payload malformado. Observamos en los logs cómo se captura la excepción, se envía un `basicNack` con requeue en `false` y el mensaje es inmediatamente derivado a la DLQ correspondiente sin perder información.
> - **API Administradora:** Mostramos el microservicio administrador donde exponemos endpoints REST (`POST /api/v1/admin/queues`, `DELETE /api/v1/admin/queues/{name}`). La lógica para crear/eliminar colas está encapsulada en `RabbitAdminService` y los datos de entrada se validan mediante DTOs con anotaciones Bean Validation (`@Valid`, `@NotBlank`)."

---

### **Minuto 08:30 - 09:30 | Conclusión y Cierre**

> **Estudiante:** "Con esto demostramos que la solución satisface el 100% de los requerimientos técnicos y formales: mensajería desacoplada con alta disponibilidad en clúster, gestión explícita de errores vía DLQ, autenticación IDaaS/JWT vía API Manager y un diseño Backend limpio basado en buenas prácticas Cloud Native. Quedo atento a sus preguntas. Muchas gracias."

---

## 6. Checklist Final de Verificación (100% Rúbrica)

Antes de entregar en AVA / Correo y presentar, verifica cada ítem:

### Backend & RabbitMQ (EP3 - Encargo)
- [ ] Archivo `.gitignore` subido correctamente en Frontend y Backend (sin carpetas `target/`, `node_modules/`, ni `.env` expuestos).
- [ ] Nombres de colas/exchanges centralizados en `application.yml`.
- [ ] Clase de configuración `@Configuration` con Beans explícitos para 3 Queues + 3 DLQs + Exchanges (`Direct` y `Topic`) + Bindings.
- [ ] Inexistencia de configuraciones de RabbitMQ incrustadas dentro de los `@Service` de negocio.
- [ ] Consumidores agrupados por dominio utilizando `@RabbitListener`.
- [ ] Confirmación manual de mensajes (`acknowledge-mode: manual`), envío explicito de ACK y NACK a DLQ en caso de fallo, con logs.
- [ ] Microservicio Administrador con controladores REST estandarizados (`POST`, `DELETE`).
- [ ] Lógica de administración delegada completamente en un `RabbitAdminService`.
- [ ] DTOs de entrada del servicio Administrador validados con `@Valid` y Bean Validation.

### Presentación & Demostración Cloud (EP4 - Presentación)
- [ ] API Manager en funcionamiento en la nube con rutas backend asociadas.
- [ ] Prueba de rechazo de solicitud en API Manager sin JWT (401) y aceptación con JWT (200).
- [ ] Tenant IDaaS configurado con usuarios de prueba.
- [ ] Frontend Angular autenticando mediante OAuth 2.0 / OIDC y enviando JWT en las peticiones.
- [ ] Despliegue Cloud en vivo de Frontend y Backend.
- [ ] Clúster de 2 Nodos de RabbitMQ mediante `docker-compose`.
- [ ] Consola RabbitMQ mostrando 3 colas + 3 DLQs y Exchanges de tipo `Direct` y `Topic`.
- [ ] Presentación preparada para una duración entre 5 y 10 minutos con lenguaje técnico apropiado.