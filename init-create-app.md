# MQ Client Utility — Initialization & Creation Strategy

## 1. Goal

Build a **reusable Java Maven JAR** (`mq-client-util-core`) that lets any Maven-based application (web app, scheduler, batch job, etc.) connect to **different message queue providers** using a single configuration file: `mq-config.json`.

The JAR does **not** ship with `mq-config.json`. Each consuming application places its own `mq-config.json` on the classpath (or provides an explicit file path) and depends on this utility.

### Two core capabilities

| Capability | Purpose | Payload formats |
|------------|---------|-----------------|
| **`MqSender`** | Publish/send a message **to** a queue | **JSON** or **XML** (raw string or Java object) |
| **`MqListener`** | Listen/consume messages **from** a queue (e.g. responses) | **JSON** or **XML** (raw string or bind to Java type) |

Both capabilities use the same `mq-config.json` queue definitions and provider adapters. The application chooses format per call or via queue-level defaults in config.

### Target MQ providers (v1)

| Provider | `type` value | Role |
|----------|--------------|------|
| **IBM MQ** | `IBM_MQ` | **Primary** — main enterprise broker |
| **RabbitMQ** | `RABBITMQ` | **Secondary** — same `MqSender` / `MqListener` API; swap via config only |

One `mq-config.json` can define **multiple connections** (e.g. one IBM MQ, one RabbitMQ). Each logical queue points at the connection it uses. Application code stays identical:

```java
factory.sender("orderRequest").sendJson(payload);   // works for IBM_MQ or RABBITMQ
factory.listener("orderResponse").onXml(OrderResponse.class, handler);
```

### Target environment (constraints)

| Constraint | Value | Impact on design |
|------------|-------|------------------|
| **JDK** | **1.8** | Compile with `source`/`target` 1.8; no Java 9+ APIs (`var`, `List.of`, modules, etc.) |
| **Spring Boot** | **1.4** and **2.6.5** | Core JAR must be **Spring-free**; optional thin Spring module for bean wiring |
| **Servlet API** | `javax.*` (not Jakarta) | Both Boot versions use `javax`; safe for this stack |

The utility must run unchanged in legacy Boot 1.4 apps and newer Boot 2.6.5 apps. **Version alignment of transitive deps (Jackson, SLF4J) is delegated to the consumer’s Spring Boot BOM** where possible.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Consuming Application (web / scheduler / batch)            │
│  ├── src/main/resources/mq-config.json   ← app-owned config │
│  └── depends on mq-client-util.jar                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  mq-client-util (this project)                              │
│  ├── Config loader (read & validate mq-config.json)         │
│  ├── MqSender        → send JSON/XML to queue                 │
│  ├── MqListener      → listen for JSON/XML from queue         │
│  ├── Message codec   → JSON (Jackson) + XML (Jackson XML)   │
│  └── Provider adapters                                      │
│       ├── IBM MQ      (primary — JMS / MQ client)          │
│       └── RabbitMQ    (secondary — AMQP)                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
              ┌────────────┴────────────┐
              │   Message Queue Broker   │
              └─────────────────────────┘
```

### Design principles

| Principle | Rationale |
|-----------|-----------|
| **Provider abstraction** | One interface; swap brokers via config, not code changes |
| **Config-driven** | No hard-coded broker URLs or queue names in the JAR |
| **Thin consumer integration** | Add Maven dependency + `mq-config.json` + a few lines of code |
| **Optional provider dependencies** | Keep core JAR small; heavy client libs loaded only when needed |
| **Fail fast** | Validate config at startup; clear errors for missing/invalid fields |
| **Spring-free core** | Core JAR has zero Spring dependencies so one artifact works on Boot 1.4 and 2.6.5 |
| **Java 8 bytecode** | Single compiled JAR deployable to all target runtimes |
| **Format-agnostic wire API** | Same sender/listener API for JSON and XML; format is explicit or config-driven |

---

## 3. `mq-config.json` — Proposed Schema

The consuming application owns this file. Suggested structure:

```json
{
  "defaultConnection": "ibmPrimary",
  "connections": {
    "ibmPrimary": {
      "type": "IBM_MQ",
      "host": "mq.example.com",
      "port": 1414,
      "queueManager": "QM1",
      "channel": "DEV.APP.SVRCONN",
      "username": "appuser",
      "password": "${IBM_MQ_PASSWORD}",
      "ssl": false,
      "useMqCspAuthentication": false,
      "connectionTimeoutMs": 30000
    },
    "rabbitBackup": {
      "type": "RABBITMQ",
      "host": "localhost",
      "port": 5672,
      "username": "guest",
      "password": "guest",
      "virtualHost": "/",
      "ssl": false,
      "connectionTimeoutMs": 30000
    }
  },
  "queues": {
    "orderRequest": {
      "connection": "ibmPrimary",
      "name": "ORDER.REQUEST",
      "defaultContentType": "JSON",
      "replyTo": "orderResponse",
      "ibmMq": {
        "targetClient": 1,
        "persistence": true
      }
    },
    "orderResponse": {
      "connection": "ibmPrimary",
      "name": "ORDER.RESPONSE",
      "defaultContentType": "XML",
      "ibmMq": {
        "browse": false
      }
    },
    "auditEvent": {
      "connection": "rabbitBackup",
      "name": "audit.events",
      "exchange": "audit",
      "routingKey": "audit.event",
      "durable": true,
      "defaultContentType": "JSON"
    }
  }
}
```

### Field conventions

- **`type`** — `IBM_MQ` or `RABBITMQ` (extensible later via SPI).
- **`connections`** — Named broker profiles. IBM MQ and RabbitMQ fields differ (see below).
- **`queues`** — Logical names for app code (`factory.sender("orderRequest")`). **`name`** is the physical queue name on both providers.
- **`defaultConnection`** — Fallback when a queue omits `connection`.
- **`defaultContentType`** — `JSON` or `XML` when format is not specified on send/listen.
- **`replyTo`** — Logical queue name for response messages (request/reply).
- **`ibmMq`** — (Optional) IBM MQ–only queue options; ignored by RabbitMQ adapter.
- **`exchange` / `routingKey`** — RabbitMQ-only; ignored by IBM MQ adapter.

### Connection fields by provider

| Field | IBM MQ | RabbitMQ |
|-------|--------|----------|
| `host` | Queue manager host | Broker host |
| `port` | Default **1414** | Default **5672** |
| `queueManager` | **Required** (e.g. `QM1`) | — |
| `channel` | **Required** SVRCONN channel (e.g. `DEV.APP.SVRCONN`) | — |
| `virtualHost` | — | Default `/` |
| `username` / `password` | MQ user (or blank if QM uses MCA) | Broker credentials |
| `useMqCspAuthentication` | Set `false` if QM expects compatibility-mode auth (common on older QMs) | — |
| `ssl` / `sslCipherSuite` | TLS to queue manager | TLS to broker |

### Queue fields by provider

| Field | IBM MQ | RabbitMQ |
|-------|--------|----------|
| `name` | MQ queue name (e.g. `ORDER.REQUEST`) | Queue name |
| `replyTo` | Resolved to reply queue `name`; sets **JMSReplyTo** / MQMD `ReplyToQ` | Sets AMQP `reply-to` property |
| Correlation | **JMSCorrelationID** / MQMD `CorrelId` | AMQP `correlation-id` property |
| `exchange` | Ignored | Optional exchange for publish |
| `routingKey` | Ignored | Routing key when publishing to exchange |
| `ibmMq.persistence` | Persistent messages (default true) | — |
| `ibmMq.targetClient` | `1` = JMS non-JMS MQ client | — |

### Config loading rules

1. **Classpath first** — `classpath:mq-config.json` (default for Spring/non-Spring apps).
2. **Explicit path** — Allow `MqClientFactory.init("/path/to/mq-config.json")` for externalized config.
3. **Environment overrides** — Optional `${ENV_VAR}` substitution for secrets (passwords, API keys).
4. **Validation** — JSON schema or programmatic validation before any broker connection is opened.

---

## 4. Core API — `MqSender` & `MqListener`

The public API has **two entry points** resolved from `MqClientFactory`:

```
MqClientFactory
    ├── sender("logicalQueueName")   → MqSender
    └── listener("logicalQueueName") → MqListener
```

### 4.1 Bootstrap

```java
MqClientFactory factory = MqClientFactory.fromClasspath("mq-config.json");
MqSender sender = factory.sender("orderRequest");
MqListener listener = factory.listener("orderResponse");
```

### 4.2 `MqSender` — send JSON or XML to queue

```java
// --- Raw payload (caller builds JSON/XML string) ---
sender.sendJson("{\"orderId\": 123}");
sender.sendXml("<order><id>123</id></order>");

// --- Object payload (utility serializes) ---
OrderRequest request = new OrderRequest(123, "ABC");
sender.sendJson(request);
sender.sendXml(request);

// --- Optional headers (correlationId, replyTo, etc.) ---
Map<String, String> headers = new HashMap<String, String>();
headers.put("correlationId", "corr-001");
sender.sendJson(request, headers);
```

**Send contract**

- Sets message property/header `contentType` to `application/json` or `application/xml`.
- Uses queue `defaultContentType` from config only when using generic `send(Object)` overload (optional convenience).
- Throws `MqSendException` on serialization or broker errors.

### 4.3 `MqListener` — listen for JSON or XML responses

Supports **async callback** (primary for long-running apps) and **sync poll** (for schedulers / request-reply).

```java
// --- Async: register listener (JSON) ---
listener.onJson(OrderResponse.class, new MqMessageCallback<OrderResponse>() {
    @Override
    public void onMessage(OrderResponse response, MqMessageContext context) {
        // handle response
    }

    @Override
    public void onError(Exception e, MqMessageContext context) {
        // handle bad message or deserialization failure
    }
});
// Java 8 lambda: listener.onJson(OrderResponse.class, (response, ctx) -> handle(response));

// --- Async: register listener (XML) ---
listener.onXml(OrderResponse.class, (response, ctx) -> handle(response));

// --- Raw string (format unknown or pass-through) ---
listener.onMessage(new MqRawMessageCallback() {
    @Override
    public void onMessage(String body, String contentType, MqMessageContext context) {
        if ("application/xml".equals(contentType)) { /* parse XML */ }
        else { /* parse JSON */ }
    }
});

// --- Sync: wait for one response (e.g. after send) ---
MqMessage raw = listener.receive(30, TimeUnit.SECONDS);
OrderResponse response = raw.asJson(OrderResponse.class);
OrderResponse responseXml = raw.asXml(OrderResponse.class);

// --- Lifecycle ---
listener.start();   // begin consuming (if not auto-started)
listener.stop();    // stop consuming
```

**Listen contract**

- Reads `contentType` from message headers/properties to pick JSON vs XML decoder.
- Falls back to queue `defaultContentType` if header missing.
- `MqMessage.asJson(Class)` / `asXml(Class)` for explicit parsing.
- Deserialization errors invoke `onError` (async) or throw `MqMessageException` (sync).
- Ack/nack behavior configurable per queue in `mq-config.json` (`autoAck`: true/false).

### 4.4 Request / reply pattern (typical flow)

```java
String correlationId = UUID.randomUUID().toString();

Map<String, String> headers = new HashMap<String, String>();
headers.put("correlationId", correlationId);

factory.sender("orderRequest").sendJson(orderRequest, headers);

// Option A: blocking wait on response queue
MqMessage responseMsg = factory.listener("orderResponse")
    .receiveWhere(correlationId, 30, TimeUnit.SECONDS);
OrderResponse response = responseMsg.asJson(OrderResponse.class);

// Option B: async handler already registered on orderResponse listener
```

### 4.5 `MqMessage` model

| Method | Description |
|--------|-------------|
| `getBody()` | Raw payload as `String` |
| `getContentType()` | `application/json`, `application/xml`, or custom |
| `getHeaders()` | Correlation ID, reply-to, custom properties |
| `asJson(Class<T>)` | Deserialize body as JSON |
| `asXml(Class<T>)` | Deserialize body as XML |

### 4.6 Message format handling (JSON & XML)

| Format | Serializer | Content-Type header |
|--------|------------|---------------------|
| JSON | Jackson `ObjectMapper` | `application/json` |
| XML | Jackson `XmlMapper` (`jackson-dataformat-xml`) | `application/xml` |

- **Java 8:** use Jackson 2.9.x; XML module does not require JAXB on JDK 8 (JAXB is available on JDK 8 but Jackson XML keeps JSON/XML consistent).
- **POJOs:** same Java class can be used for both formats if field names align; otherwise use separate DTOs.
- **Validation:** optional lightweight check (starts with `{`/`[` for JSON, `<` for XML) before deserialize; fail with clear error.

### 4.7 Factory lifecycle

```java
factory.close(); // closes all senders, listeners, and broker connections
```

### Internal packages (suggested)

```
com.example.mq
├── api/
│   ├── MqClientFactory
│   ├── MqSender
│   ├── MqListener
│   ├── MqMessage
│   ├── MqMessageContext
│   ├── MqMessageCallback
│   └── MessageFormat          (enum: JSON, XML)
├── codec/
│   ├── MessageSerializer
│   ├── JsonMessageSerializer
│   └── XmlMessageSerializer
├── config/
├── spi/
│   └── MqProvider             (creates sender/listener per provider)
├── provider/
│   ├── ibmmq/                 IbmMqSender, IbmMqListener, IbmMqProvider
│   └── rabbitmq/              RabbitMqSender, RabbitMqListener, RabbitMqProvider
└── exception/
    ├── MqConfigException
    ├── MqSendException
    └── MqMessageException
```

---

## 5. Maven Project Structure

```
mq-client-util/                          (parent POM, packaging pom)
├── mq-client-util-core/                 ← API, config, codec, SPI (no Spring, no MQ clients)
├── mq-client-util-ibm-mq/               ← IBM MQ adapter (depends on core)
├── mq-client-util-rabbitmq/             ← RabbitMQ adapter (depends on core)
├── mq-client-util-all/                  ← optional aggregator JAR (core + both providers)
├── mq-client-util-spring/               ← optional Spring @Configuration (Boot 1.4 + 2.6)
├── init-create-app.md
└── README.md
```

**Recommended for consumers**

| Use case | Maven artifacts |
|----------|-----------------|
| IBM MQ only | `mq-client-util-core` + `mq-client-util-ibm-mq` |
| RabbitMQ only | `mq-client-util-core` + `mq-client-util-rabbitmq` |
| Both brokers | `mq-client-util-all` (or add both adapter modules) |

### Parent `pom.xml` essentials

| Setting | Value |
|---------|-------|
| `packaging` | `jar` (core module) |
| `groupId` | e.g. `com.yourorg.mq` |
| `artifactId` | `mq-client-util` (or `mq-client-util-core`) |
| **Java version** | **1.8** (`maven.compiler.source` / `target` = `1.8`) |
| **Boot compatibility** | Core: no Spring; optional spring module: `provided` scope |
| Dependencies | Jackson, SLF4J (see version matrix below), provider clients |

### Java 8 compiler settings

```xml
<properties>
  <java.version>1.8</java.version>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

### Dependency version matrix (Java 8 + Boot 1.4 / 2.6.5)

Use versions that compile on Java 8 and do not force a newer Spring/Jackson than the consumer app.

| Dependency | Suggested version | Scope | Notes |
|------------|-------------------|-------|-------|
| `jackson-databind` | **2.9.10** (minimum) | `provided` | JSON send/listen; consumer BOM wins at runtime |
| `jackson-dataformat-xml` | **2.9.10** (minimum) | `provided` | XML send/listen; keep version aligned with `jackson-databind` |
| `slf4j-api` | **1.7.36** | `provided` | Matches both Boot 1.4 and 2.6 |
| `com.ibm.mq:com.ibm.mq.allclient` | **9.3.3.0** (or **9.0.4.0**+) | compile in `ibm-mq` module | **Java 8 / `javax.jms`** — do **not** use `com.ibm.mq.jakarta.client` |
| `javax.jms:javax.jms-api` | **2.0.1** | `provided` | IBM JMS API; already bundled in `allclient` at runtime |
| `com.rabbitmq:amqp-client` | **5.7.3** | compile in `rabbitmq` module | Java 8 compatible |
| `junit` | **4.13.2** | test | JUnit 4 avoids extra Surefire config on legacy CI |
| `mockito-core` | **2.28.2** | test | Last Mockito 2.x; runs on Java 8 |

**Rule:** Do **not** pull in `spring-boot-starter` or `spring-context` in the **core** module. That keeps one JAR valid for Boot 1.4 and 2.6.5 without classpath conflicts.

### Optional Spring module (`mq-client-util-spring`)

Thin wrapper only — depends on `mq-client-util-core` + `spring-context` (`provided`):

```java
@Configuration
public class MqClientSpringConfiguration {

    @Bean(destroyMethod = "close")
    public MqClientFactory mqClientFactory() {
        return MqClientFactory.fromClasspath("mq-config.json");
    }
}
```

| Boot version | How to enable |
|--------------|---------------|
| **1.4.x** | `@Import(MqClientSpringConfiguration.class)` on `@SpringBootApplication` or `@Configuration` |
| **2.6.5** | Same `@Import`, or register via `META-INF/spring.factories` (see below) |

**Auto-configuration (optional, v1.1):** Both Boot 1.4 and 2.6 read `META-INF/spring.factories`:

```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.mq.spring.MqClientAutoConfiguration
```

Keep auto-config class minimal: `@ConditionalOnClass(MqClientFactory.class)` and `@ConditionalOnMissingBean`. Test against **both** Boot versions in separate sample modules or CI jobs.

**Do not** use `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — that is Boot 3+ only.

### Provider module strategy (IBM MQ + RabbitMQ)

**Recommended: modular artifacts (Option B)**

- `mq-client-util-core` — config, codec, `MqSender`/`MqListener` interfaces, SPI
- `mq-client-util-ibm-mq` — `IbmMqProvider`, JMS connection factory, put/get
- `mq-client-util-rabbitmq` — `RabbitMqProvider`, AMQP channel publish/consume
- `mq-client-util-all` — depends on both adapters; convenience for apps using either broker

Provider JARs register via **`META-INF/services/com.example.mq.spi.MqProvider`** (Java `ServiceLoader`). Core discovers only providers present on the classpath — an IBM-only app never loads RabbitMQ classes.

For a quick start you may combine adapters in one repo build; still keep **separate Maven modules** so consumers are not forced to ship both client libraries.

---

## 6. How Consuming Applications Use the JAR

### 6.1 Maven dependency

**IBM MQ application**

```xml
<dependency>
  <groupId>com.yourorg.mq</groupId>
  <artifactId>mq-client-util-all</artifactId>
  <version>1.0.0</version>
</dependency>
```

Or pick modules explicitly:

```xml
<dependency>
  <groupId>com.yourorg.mq</groupId>
  <artifactId>mq-client-util-core</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>com.yourorg.mq</groupId>
  <artifactId>mq-client-util-ibm-mq</artifactId>
  <version>1.0.0</version>
</dependency>
```

### 6.2 Place config in the consumer app

```
consumer-app/
└── src/main/resources/
    └── mq-config.json
```

### 6.3 Minimal usage (scheduler — send JSON, listen for XML response)

```java
MqClientFactory mq = MqClientFactory.fromClasspath("mq-config.json");
try {
    MqSender sender = mq.sender("orderRequest");
    MqListener listener = mq.listener("orderResponse");

    String correlationId = UUID.randomUUID().toString();
    Map<String, String> headers = new HashMap<String, String>();
    headers.put("correlationId", correlationId);

    sender.sendJson(new OrderRequest(123));
    MqMessage response = listener.receiveWhere(correlationId, 30, TimeUnit.SECONDS);
    OrderResponse order = response.asXml(OrderResponse.class);
} finally {
    mq.close();
}
```

### 6.3b Web app — async listener (JSON responses)

```java
MqListener listener = mq.listener("orderResponse");
listener.onJson(OrderResponse.class, (response, ctx) -> {
    orderService.process(response);
});
listener.start();
```

### 6.4 Web application considerations (Spring Boot 1.4 & 2.6.5)

**Option A — Spring module (recommended for Boot apps)**

```xml
<dependency>
  <groupId>com.yourorg.mq</groupId>
  <artifactId>mq-client-util-spring</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
@SpringBootApplication
@Import(MqClientSpringConfiguration.class)
public class Application { }
```

Inject in services:

```java
@Service
public class OrderService {
    private final MqClientFactory mqClientFactory;

    @Autowired
    public OrderService(MqClientFactory mqClientFactory) {
        this.mqClientFactory = mqClientFactory;
    }
}
```

**Option B — Manual wiring (no Spring module)**

```java
@Configuration
public class MqConfig {
    @Bean(destroyMethod = "close")
    public MqClientFactory mqClientFactory() {
        return MqClientFactory.fromClasspath("mq-config.json");
    }
}
```

Works identically on **Boot 1.4** (`@EnableAutoConfiguration` + component scan) and **Boot 2.6.5**.

**Lifecycle rules**

- Initialize `MqClientFactory` once (singleton bean).
- Use `destroyMethod = "close"` or `@PreDestroy` so connections close on shutdown.
- Do **not** create a new factory per HTTP request.
- Boot 1.4: prefer `@Autowired` constructor injection (same as 2.6).

### 6.5 Config outside the JAR

- Dev: `src/main/resources/mq-config.json`
- Prod: mount file or set `MqClientFactory.fromFile(Paths.get(System.getenv("MQ_CONFIG_PATH")))`

---

## 7. Provider Adapter Pattern — IBM MQ & RabbitMQ

Each provider implements the same SPI. The **sender/listener API is identical**; only connection and queue mapping differ.

```java
public interface MqProvider {
    String type();  // "IBM_MQ" or "RABBITMQ"
    MqSender createSender(ConnectionConfig conn, QueueConfig queue, MessageSerializer codec);
    MqListener createListener(ConnectionConfig conn, QueueConfig queue, MessageSerializer codec);
    void testConnection(ConnectionConfig conn);
}
```

`ProviderRegistry` loads implementations via `ServiceLoader` and maps `type` → provider.

### 7.1 IBM MQ adapter (primary)

**Client:** `com.ibm.mq:com.ibm.mq.allclient` with **JMS** (`javax.jms`).

| Concern | Implementation |
|---------|----------------|
| Connection | `MQQueueConnectionFactory` — host, port, queue manager, channel, user/password |
| Send | `QueueSession` → `QueueSender` → `TextMessage` (JSON/XML string body) |
| Listen | `QueueReceiver` with `MessageListener` (async) or `receive(timeout)` (sync) |
| Request/reply | `setJMSReplyTo(replyQueue)` + `setJMSCorrelationID` on send; listener filters by correlation ID |
| Content type | JMS custom property `contentType` = `application/json` or `application/xml` |
| Auth | Honor `useMqCspAuthentication: false` when QM uses compatibility mode (avoids JMSCC0003 / 2035 on older QMs) |
| Close | Close session, connection, `MQQueueConnectionFactory` |

**IBM MQ–specific notes**

- Queue names are usually uppercase (e.g. `ORDER.REQUEST`); match your QM object definitions.
- Ensure SVRCONN channel and MQ user have **connect**, **inq**, **get**, **put** authority on the queues.
- For local dev, use [IBM MQ container](https://developer.ibm.com/tutorials/mq-running-a-queue-manager-on-containers/) or a shared dev queue manager.
- Use **`com.ibm.mq.allclient`** only (Java 8). **`com.ibm.mq.jakarta.client`** is for Jakarta/JDK 17+ — not compatible with this project.

### 7.2 RabbitMQ adapter (secondary)

**Client:** `com.rabbitmq:amqp-client`.

| Concern | Implementation |
|---------|----------------|
| Connection | `ConnectionFactory` — host, port, virtual host, credentials |
| Send | `channel.basicPublish(exchange, routingKey, props, body)` |
| Listen | `channel.basicConsume(queue, …)` with manual/auto ack from config |
| Request/reply | AMQP properties `correlationId`, `replyTo` |
| Content type | `AMQP.BasicProperties.contentType` |
| Close | Close channel and connection |

### 7.3 Unified mapping (same API, different wire)

```
                    MqSender.sendJson() / sendXml()
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
       IbmMqSender                      RabbitMqSender
       TextMessage body                 AMQP body bytes
       JMSCorrelationID                correlation-id
       JMSReplyTo                       reply-to
       prop: contentType                prop: content-type
              │                               │
              └───────────────┬───────────────┘
                              ▼
                    MqListener.onJson / onXml / receiveWhere
```

### 7.4 Provider comparison

| Feature | IBM MQ | RabbitMQ |
|---------|--------|----------|
| Default port | 1414 | 5672 |
| Addressing | Queue manager + queue name | Virtual host + queue (+ exchange) |
| Request/reply | Native JMS / MQMD | AMQP `reply-to` + `correlation-id` |
| Message format | JSON/XML in `TextMessage` | JSON/XML in message body |
| Config `type` | `IBM_MQ` | `RABBITMQ` |

### 7.5 v1 provider priority

| Phase | Provider | Client library |
|-------|----------|----------------|
| **Phase 5** | **IBM MQ** | `com.ibm.mq:com.ibm.mq.allclient` |
| **Phase 6** | **RabbitMQ** | `com.rabbitmq:amqp-client` |

---

## 8. Error Handling & Observability

- **Config errors** — `MqConfigException` with field path (e.g. `connections.primary.port`).
- **Connection errors** — `MqConnectionException`; support retry with backoff (configurable).
- **Send errors** — `MqSendException` (serialization or broker failure).
- **Listen/deserialize errors** — `MqMessageException` with queue name and content type in message.
- **Correlation timeout** — `MqReceiveTimeoutException` when `receiveWhere(correlationId, …)` finds no match.
- **Logging** — SLF4J only; never log passwords. Consumer app chooses Logback/Log4j2.
- **Health check** — Optional `factory.healthCheck()` for actuator / readiness probes.

---

## 9. Testing Strategy

| Layer | Approach |
|-------|----------|
| Config parsing | Unit tests with valid/invalid `mq-config.json` fixtures |
| JSON/XML codec | Round-trip POJO tests for `sendJson`/`sendXml` and `asJson`/`asXml` |
| Sender | Mock provider: assert payload bytes + `contentType` header |
| Listener | Mock provider: push sample JSON/XML frames; verify callbacks |
| IBM MQ adapter | IBM MQ container or shared dev QM; round-trip JSON send + XML reply |
| RabbitMQ adapter | Testcontainers RabbitMQ **1.17.x** or embedded broker |
| Integration | `test-mq-config.json` + round-trip send/receive |
| Boot 1.4 contract | `sample-boot14-app` module — verifies core + spring module |
| Boot 2.6 contract | `sample-boot26-app` module — verifies core + spring module |

---

## 10. Build, Versioning & Distribution

1. **Version** — Semantic versioning (`1.0.0-SNAPSHOT` during development).
2. **Build** — `mvn clean package` → `target/mq-client-util-1.0.0.jar`
3. **Publish** — Internal Nexus/Artifactory or GitHub Packages (`mvn deploy`).
4. **Consumer pin** — Apps should pin explicit version, not `LATEST`.

---

## 11. Step-by-Step Creation Checklist

Use this as the implementation order for building the project.

### Phase 1 — Project bootstrap

- [ ] **1.1** Create Maven **multi-module** parent (`core`, `ibm-mq`, `rabbitmq`, `all`).
- [ ] **1.2** Set `groupId`, `artifactId`, **Java 1.8**, encoding UTF-8.
- [ ] **1.3** Add dependencies: Jackson (`provided`), SLF4J (`provided`), JUnit 4, Mockito 2.
- [ ] **1.6** Enforce Java 8 API only (no `module-info.java`, no Java 9+ APIs).
- [ ] **1.4** Create package structure under `com.example.mq` (adjust group to your org).
- [ ] **1.5** Add `.gitignore` (target/, IDE files, `*.iml`).

### Phase 2 — Configuration model

- [ ] **2.1** Define Java POJOs: `MqConfig`, `ConnectionConfig`, `QueueConfig`.
- [ ] **2.2** Implement `ConfigLoader` (classpath + file path).
- [ ] **2.3** Add JSON deserialization with Jackson.
- [ ] **2.4** Implement validation per `type` (`IBM_MQ` requires `queueManager` + `channel`; `RABBITMQ` requires `virtualHost`).
- [ ] **2.5** (Optional) Add `mq-config.schema.json` for IDE autocomplete in consumer apps.
- [ ] **2.6** Unit tests for config loading and validation errors.

### Phase 3 — Message codec (JSON & XML)

- [ ] **3.1** Define `MessageFormat` enum (`JSON`, `XML`).
- [ ] **3.2** Implement `JsonMessageSerializer` (Jackson `ObjectMapper`).
- [ ] **3.3** Implement `XmlMessageSerializer` (Jackson `XmlMapper`).
- [ ] **3.4** Implement `MessageSerializer` facade used by sender/listener.
- [ ] **3.5** Unit tests: serialize/deserialize sample POJOs; invalid payload errors.

### Phase 4 — Core API & SPI

- [ ] **4.1** Define `MqMessage`, `MqMessageContext`, callback interfaces.
- [ ] **4.2** Define `MqSender` (sendJson, sendXml, raw + object overloads).
- [ ] **4.3** Define `MqListener` (onJson, onXml, onMessage, receive, receiveWhere).
- [ ] **4.4** Define `MqProvider` SPI, `ProviderRegistry`, and `META-INF/services` registration.
- [ ] **4.5** Implement `MqClientFactory` (`sender()`, `listener()`, `close()`).
- [ ] **4.6** Unit tests for factory routing (mock providers).

### Phase 5 — IBM MQ provider (primary)

- [ ] **5.1** Create `mq-client-util-ibm-mq` module; add `com.ibm.mq.allclient` (**javax**, not jakarta).
- [ ] **5.2** Implement `IbmMqConnectionFactory` (host, port, QM, channel, SSL, MQCSP flag).
- [ ] **5.3** Implement `IbmMqSender` — JMS `TextMessage`, `JMSCorrelationID`, `JMSReplyTo`, `contentType` property.
- [ ] **5.4** Implement `IbmMqListener` — async `MessageListener` + sync `receive` + `receiveWhere(correlationId)`.
- [ ] **5.5** Register `IbmMqProvider` via `ServiceLoader`.
- [ ] **5.6** Integration test against IBM MQ (container or dev QM): JSON request → XML response.
- [ ] **5.7** Document IBM MQ `mq-config.json` fields and QM authority requirements.

### Phase 6 — RabbitMQ provider (secondary)

- [ ] **6.1** Create `mq-client-util-rabbitmq` module; add `amqp-client`.
- [ ] **6.2** Implement `RabbitMqSender` (exchange, routing key, correlationId, replyTo, contentType).
- [ ] **6.3** Implement `RabbitMqListener` (consumer, ack/nack, correlation filter).
- [ ] **6.4** Register `RabbitMqProvider` via `ServiceLoader`.
- [ ] **6.5** Create `mq-client-util-all` aggregator module.
- [ ] **6.6** Integration test: send JSON → receive JSON/XML on RabbitMQ.
- [ ] **6.7** Verify same application code works when switching `connection.type` in config.

### Phase 7 — Spring integration (Boot 1.4 + 2.6.5) — optional module

- [ ] **7.1** Create `mq-client-util-spring` module (`spring-context` scope `provided`).
- [ ] **7.2** Add `MqClientSpringConfiguration` with `@Bean(destroyMethod = "close")`.
- [ ] **7.3** (Optional) Add `MqClientAutoConfiguration` + `spring.factories` entry.
- [ ] **7.4** Sample app: **Spring Boot 1.4.x** — send JSON / listen XML smoke test.
- [ ] **7.5** Sample app: **Spring Boot 2.6.5** — same steps; confirm no Jackson conflicts.

### Phase 8 — Polish & release

- [ ] **8.1** Add `README.md` (sender/listener examples for JSON and XML).
- [ ] **8.2** Add example `mq-config.json` in README (not bundled in JAR).
- [ ] **8.3** Configure `maven-source-plugin` and `maven-javadoc-plugin` for IDE support.
- [ ] **8.4** Run `mvn clean verify` on **JDK 8** and fix issues.
- [ ] **8.5** Tag release `v1.0.0` and publish to artifact repository.
- [ ] **8.6** Document tested matrix: JDK 8 + Boot 1.4.x + Boot 2.6.5.

---

## 12. Security Considerations

- Store IBM MQ and RabbitMQ passwords in environment variables; reference via `${IBM_MQ_PASSWORD}` etc.
- Enable SSL/TLS per connection (`ssl`, `sslCipherSuite` for IBM MQ).
- Set `useMqCspAuthentication: false` when connecting to queue managers that expect compatibility-mode credentials.
- Avoid logging message bodies that may contain PII in production (configurable log level).
- Validate message size limits where the broker allows configuration.

---

## 13. Future Enhancements (Post v1)

- Dedicated Boot 3+ starter (Jakarta EE) when you migrate off Java 8.
- Async API via `CompletableFuture` (Java 8 native).
- Reactive API (Project Reactor) only if consumers move to Boot 2.x WebFlux.
- Apache Kafka, ActiveMQ, and other brokers via SPI.
- Dead-letter queue configuration in `mq-config.json`.
- Metrics (Micrometer) for send/receive counts and latency.
- SPI-based plugin loading for custom providers without recompiling core.

---

## 14. Success Criteria

The utility is ready when:

1. A Maven consumer can send **JSON or XML** to a queue via `MqSender`.
2. A Maven consumer can listen for **JSON or XML** responses via `MqListener` (async or sync).
3. Switching between **IBM MQ** and **RabbitMQ** requires only `mq-config.json` changes (no app code changes).
4. Invalid config or deserialization failures produce actionable error messages.
5. All tests pass in CI (`mvn verify`) on **JDK 8**.
6. IBM MQ and RabbitMQ integration tests pass (or documented manual test against dev brokers).
7. JAR modules published and installable from your artifact repository.

---

## 15. Compatibility Quick Reference

```
┌──────────────────┐     ┌─────────────────────────┐
│  Boot 1.4.x app  │────▶│  mq-client-util-core    │  (no Spring dep)
└──────────────────┘     └───────────┬─────────────┘
                                     │
┌──────────────────┐     ┌───────────▼─────────────┐
│  Boot 2.6.5 app  │────▶│  mq-client-util-spring  │  (optional, @Import)
└──────────────────┘     └─────────────────────────┘
```

| Check | Boot 1.4 | Boot 2.6.5 |
|-------|----------|------------|
| Java | 8 | 8 |
| `javax.*` | Yes | Yes |
| `@Import` configuration | Yes | Yes |
| `spring.factories` auto-config | Yes | Yes |
| Jackson conflict risk | Low if Jackson is `provided` in core | Low if Jackson is `provided` in core |

---

## 16. Next Action

After reviewing this strategy, proceed with **Phase 1** (Maven multi-module bootstrap), **Phase 2** (config model with IBM MQ + RabbitMQ fields), and **Phase 3** (JSON/XML codec).
