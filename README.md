# banquito-clearinghouse-service

> Nota de arquitectura actual: este microservicio usa Google Cloud Pub/Sub como broker de mensajes administrado. Cualquier referencia historica a RabbitMQ en este README corresponde al diseno anterior y debe interpretarse como obsoleta.

## Descripción General

**banquito-clearinghouse-service** es el microservicio responsable de gestionar las transacciones **Off-Us** (pagos dirigidos a entidades financieras distintas de BanQuito) dentro del Switch de Pagos Masivos BanQuito V2.

Su principal responsabilidad es recibir las transacciones Off-Us enviadas por el **routing-service**, generar el archivo de compensación bancaria que será utilizado para el proceso de intercambio interbancario, registrar el asiento contable correspondiente mediante el Core Bancario y exponer servicios de consulta sobre los archivos generados.

---

# Responsabilidades del Microservicio

Este microservicio realiza las siguientes funciones:

* Consumir transacciones Off-Us desde Google Cloud Pub/Sub.
* Almacenar transacciones Off-Us en MongoDB.
* Generar archivos de compensación bancaria.
* Registrar asientos contables asociados a las compensaciones.
* Mantener trazabilidad de los archivos generados.
* Permitir la consulta de archivos de compensación por lote.
* Exponer información para monitoreo y observabilidad.

---

# Flujo Funcional

## Flujo General del Sistema

```text
Tesorero
   │
   ▼
Portal Empresas
   │
   ▼
Kong Switch
   │
   ▼
file-reception-service
   │
RabbitMQ
(payment.lines.queue)
   │
   ▼
routing-service
   │
   ├────────────── On-Us ──────────────► account-core-service
   │
   └────────────── Off-Us ─────────────► clearing.outbound.queue
                                             │
                                             ▼
                              banquito-clearinghouse-service
                                             │
                        ┌────────────────────┴───────────────────┐
                        │                                        │
                        ▼                                        ▼
               Archivo TXT                         Asiento Contable
               Compensación                        accounting-service
```

---

# Flujo Interno del Microservicio

```text
RabbitMQ
(clearing.outbound.queue)
        │
        ▼
ClearingQueueListener
        │
        ▼
OffUsConsumerService
        │
        ▼
MongoDB
(offus_payment)
        │
        ▼
CompensationFileService
        │
        ├────────► Generar TXT
        │
        ├────────► Guardar Metadata
        │
        └────────► AccountingService
                            │
                            ▼
                 /api/v2/accounting/entries
```

---

# Arquitectura del Proyecto

```text
src/main/java
│
├── config
│   ├── RabbitMQConfig
│   └── WebClientConfig
│
├── controller
│   └── ClearingController
│
├── dto
│   ├── OffUsPaymentMessage
│   ├── AccountingEntryRequest
│   ├── AccountingEntryResponse
│   └── ClearingFileResponse
│
├── model
│   ├── OffUsPayment
│   └── CompensationFile
│
├── enums
│   ├── PaymentStatus
│   └── FileStatus
│
├── repository
│   ├── OffUsPaymentRepository
│   └── CompensationFileRepository
│
├── listener
│   └── ClearingQueueListener
│
├── service
│   ├── OffUsConsumerService
│   ├── CompensationFileService
│   ├── AccountingService
│   └── ClearingQueryService
│
├── provider
│   └── AccountingProvider
│
├── exception
│   ├── BatchNotFoundException
│   ├── FileGenerationException
│   ├── AccountingException
│   └── GlobalExceptionHandler
│
└── BanquitoClearinghouseServiceApplication
```

---

# Componentes Principales

## ClearingQueueListener

Escucha continuamente la cola RabbitMQ:

```text
clearing.outbound.queue
```

Recibe transacciones Off-Us enviadas por el routing-service.

---

## OffUsConsumerService

Responsable de:

* Validar mensajes recibidos.
* Convertir DTO a entidad.
* Persistir información en MongoDB.

Colección:

```text
offus_payment
```

---

## CompensationFileService

Responsable de:

* Recuperar todas las transacciones del lote.
* Generar el archivo TXT de compensación.
* Calcular totales.
* Registrar metadatos del archivo.

---

## AccountingService

Responsable de:

* Construir el request contable.
* Invocar el Core Bancario.
* Registrar el asiento contable asociado al archivo generado.

Endpoint consumido:

```http
POST /api/v2/accounting/entries
```

---

## ClearingQueryService

Responsable de:

* Consultar archivos generados.
* Construir respuestas para los consumidores REST.

---

# Base de Datos

## MongoDB

Base:

```text
clearingdb
```

---

## Colección: offus_payment

Almacena todas las transacciones Off-Us.

Campos principales:

```text
id
batchId
transactionId
routingCode
originAccount
destinationAccount
amount
currency
concept
valueDate
status
createdAt
```

---

## Colección: compensation_file

Almacena metadatos de archivos generados.

Campos principales:

```text
id
batchId
fileName
filePath
offUsRecords
totalAmount
status
generatedAt
```

---

# RabbitMQ

## Cola Consumida

```text
clearing.outbound.queue
```

---

## Ejemplo de Mensaje

```json
{
  "batchId":"550e8400-e29b-41d4-a716-446655440000",
  "transactionId":"5a1cb527-9d1f-42f5-a8a5-c02ea654ec7e",
  "routingCode":"002",
  "originAccount":"2100000001",
  "destinationAccount":"2200000002",
  "amount":1500.00,
  "currency":"USD",
  "concept":"NOMINA MAYO",
  "valueDate":"2026-06-03"
}
```

---

# API REST

## Consultar Archivo de Compensación

### Request

```http
GET /api/v2/clearing/batches/{batchId}/file
```

---

### Response

```json
{
  "batchId":"550e8400-e29b-41d4-a716-446655440000",
  "fileName":"COMPENSACION_20260603_550e8400.txt",
  "filePath":"/compensacion/outbound/",
  "offUsRecords":28,
  "totalOffUsAmount":23800.00,
  "status":"GENERATED",
  "generatedAt":"2026-06-03T14:03:00"
}
```

---

# Configuración

## application.properties

```properties
spring.application.name=banquito-clearinghouse-service

server.port=8087

spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=clearingdb

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

rabbitmq.clearing.queue=clearing.outbound.queue

core.accounting.url=http://localhost:8010/api/v2/accounting/entries

management.endpoints.web.exposure.include=health,info
```

---

# Ejecución Local

## Prerrequisitos

* Java 21
* Maven 3.9+
* MongoDB
* RabbitMQ

---

## Compilar

```bash
mvn clean install
```

---

## Ejecutar

```bash
mvn spring-boot:run
```

o

```bash
java -jar target/banquito-clearinghouse-service.jar
```

---

# Pruebas Funcionales

## Prueba 1: Recepción de Transacción Off-Us

### Acción

Publicar mensaje en:

```text
clearing.outbound.queue
```

---

### Resultado Esperado

Registro creado en:

```text
offus_payment
```

---

## Prueba 2: Generación de Archivo

### Acción

Invocar servicio de generación.

---

### Resultado Esperado

Archivo:

```text
COMPENSACION_YYYYMMDD_BATCHID.txt
```

creado correctamente.

---

## Prueba 3: Registro Contable

### Acción

Generar archivo exitosamente.

---

### Resultado Esperado

Invocación:

```http
POST /api/v2/accounting/entries
```

---

### Resultado Esperado

Respuesta 200 OK del Core.

---

## Prueba 4: Consulta de Archivo

### Request

```http
GET /api/v2/clearing/batches/{batchId}/file
```

---

### Resultado Esperado

```http
200 OK
```

con información del archivo.

---

## Prueba 5: Batch Inexistente

### Request

```http
GET /api/v2/clearing/batches/00000000-0000-0000-0000-000000000000/file
```

---

### Resultado Esperado

```http
404 NOT FOUND
```

---

# Monitoreo

## Health Check

```http
GET /actuator/health
```

---

## Respuesta Esperada

```json
{
  "status":"UP"
}
```

---

# Autor

**Johan Alomía**

Microservicio desarrollado para el proyecto académico:

**Switch de Pagos Masivos BanQuito V2**
