# ms-vidasalud-rabbit-admin

Microservicio de **administración en caliente de RabbitMQ** para la plataforma VidaSalud. Expone una API REST para crear, consultar, purgar y eliminar **colas**, **exchanges** y **bindings** sin entrar a la consola del broker.

Spring Boot 4.1 · Java 21 · Spring AMQP · puerto **8084**

---

## Diseño

```
controller/   ColaController, ExchangeController, BindingController
                └── solo reciben HTTP, validan el DTO y llaman a métodos de alto nivel
service/      RabbitAdminService
                └── TODA la lógica de administración: es la única clase que habla con RabbitMQ
dto/          CrearColaRequest, CrearExchangeRequest, BindingRequest, ColaResponse, ExchangeResponse
exception/    GlobalExceptionHandler + excepciones de dominio (404, 409, 403)
config/       RabbitAdminConfig (RabbitAdmin + RestClient), NombresRabbit (nombres centralizados)
```

- Los controladores **no importan ninguna clase de RabbitMQ**.
- `RabbitAdminService` usa dos vías:
  - **AMQP (`RabbitAdmin`)** para declarar, purgar, eliminar y enlazar.
  - **API HTTP de administración (puerto 15672)** para listar, ya que AMQP no permite listados globales, y para eliminar bindings.

---

## Endpoints

### Colas — `/api/rabbit/colas`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/rabbit/colas` | Lista las colas del vhost con mensajes y consumidores | `200` |
| `GET` | `/api/rabbit/colas/{nombre}` | Detalle de una cola | `200`, `404` |
| `POST` | `/api/rabbit/colas` | Crea una cola durable; opcionalmente con DLQ y binding inicial | `201`, `400`, `404`, `409` |
| `DELETE` | `/api/rabbit/colas/{nombre}/mensajes` | Purga los mensajes de la cola | `200`, `403`, `404` |
| `DELETE` | `/api/rabbit/colas/{nombre}` | Elimina la cola | `204`, `403`, `404` |

Cuerpo de `POST /api/rabbit/colas`:

```json
{
  "nombre": "vidasalud.pruebas",
  "durable": true,
  "conDlq": true,
  "exchange": "cmd.topic",
  "routingKey": "pruebas.*"
}
```

- `nombre`: obligatorio, entre 3 y 255 caracteres, solo letras, números, `.`, `_` y `-`.
- `conDlq: true` crea además `<nombre>.dlq` enlazada a `cmd.dead.dlx` y configura la cola principal para desviar ahí lo rechazado.
- `exchange` y `routingKey` son opcionales; si vienen, se crea el binding. Si el exchange no existe, responde `404` **sin crear nada**.

### Exchanges — `/api/rabbit/exchanges`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/rabbit/exchanges` | Lista los exchanges del vhost | `200` |
| `POST` | `/api/rabbit/exchanges` | Crea un exchange `direct`, `topic` o `fanout` | `201`, `400`, `409` |
| `DELETE` | `/api/rabbit/exchanges/{nombre}` | Elimina el exchange | `204`, `403`, `404` |

```json
{ "nombre": "vidasalud.eventos", "tipo": "topic", "durable": true }
```

`tipo` se valida contra un enum: cualquier valor distinto de `direct`, `topic` o `fanout` responde `400`.

### Bindings — `/api/rabbit/bindings`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/rabbit/bindings` | Lista los bindings del vhost | `200` |
| `POST` | `/api/rabbit/bindings` | Enlaza una cola con un exchange | `201`, `400`, `404` |
| `DELETE` | `/api/rabbit/bindings` | Elimina el binding indicado en el cuerpo | `204`, `404` |

```json
{ "cola": "vidasalud.pruebas", "exchange": "cmd.topic", "routingKey": "pruebas.*" }
```

Los tres campos son obligatorios. El `DELETE` recibe el binding en el cuerpo porque un binding no tiene un identificador propio: se identifica por la combinación de los tres valores.

---

## Reglas de negocio

| Situación | Respuesta |
|---|---|
| Recurso creado | `201` con el recurso |
| Entrada inválida (nombre vacío, caracteres no permitidos, tipo de exchange desconocido) | `400` con el campo y el motivo |
| Eliminar o purgar un **recurso protegido** | `403` |
| Cola, exchange o binding inexistente | `404` |
| Crear algo que ya existe | `409` |
| RabbitMQ no responde | `502` con mensaje claro; el detalle técnico queda en el log |

**Recursos protegidos** (no se pueden eliminar ni purgar): las colas `vidasalud.notificaciones`, `vidasalud.tickets`, `vidasalud.auditoria` y sus tres `.dlq`, y los exchanges `cmd.topic`, `cmd.direct` y `cmd.dead.dlx`.

Ninguna respuesta de error incluye trazas. Formato:

```json
{ "timestamp": "2026-10-18T17:40:41Z", "estado": 403, "mensaje": "No se permite eliminar la cola protegida: vidasalud.notificaciones" }
```

Errores de validación:

```json
{
  "timestamp": "...",
  "estado": 400,
  "mensaje": "Parametros de entrada invalidos",
  "errores": [ { "campo": "nombre", "mensaje": "..." } ]
}
```

---

## Configuración

Variables de entorno (sin valores por defecto para la clave: si falta, el servicio no arranca):

| Variable | Ejemplo | Uso |
|---|---|---|
| `RABBITMQ_ADDRESSES` | `rabbit1:5672,rabbit2:5672` | Nodos del clúster (AMQP) |
| `RABBITMQ_USER` | `vidasalud` | Usuario |
| `RABBITMQ_PASSWORD` | — | Clave (obligatoria) |
| `RABBITMQ_MGMT_URL` | `http://rabbit1:15672` | API HTTP de administración |
| `RABBITMQ_VHOST` | `/` | Virtual host |

Salud: `GET /actuator/health`

---

## Ejecutar

**Con Docker Compose** (despliegue real): ver el repositorio `vidasalud-deploy`. El puerto 8084 se publica solo en `127.0.0.1` del servidor y se usa por túnel SSH, porque la API no tiene autenticación propia.

**Local:**

```powershell
$env:RABBITMQ_USER='vidasalud'
$env:RABBITMQ_PASSWORD='<clave>'
.\mvnw spring-boot:run
```

**Tests** (11, con Mockito, sin broker real):

```powershell
.\mvnw clean test
```