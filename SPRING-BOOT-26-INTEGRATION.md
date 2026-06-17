# Using `mq-client-util-core` in Spring Boot 2.6.5

This guide shows how to add IBM MQ send/receive to any **Spring Boot 2.6.5** application using the single library artifact `mq-client-util-core`.

**Requirements**

- Java 8+
- Spring Boot **2.6.5**
- IBM MQ queue manager reachable from the app
- `mq-config.json` describing connections and queues

**Reference implementation:** `sample-boot26-webapp` in this repository.

---

## Step 1 — Install or publish the library

From the `mq-client-util` repository root:

```cmd
mvn clean install
```

This installs `com.yourorg.mq:mq-client-util-core:1.0.1-SNAPSHOT` into your local Maven repository (`~/.m2`).

For shared teams, publish the JAR to your corporate Maven repository and use a release version instead of `SNAPSHOT`.

---

## Step 2 — Add Maven dependencies

In your Spring Boot 2.6.5 application `pom.xml`:

```xml
<properties>
  <java.version>1.8</java.version>
  <mq-client-util.version>1.0.1-SNAPSHOT</mq-client-util.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>2.6.5</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- your existing starters, e.g. web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <!-- Jackson is 'provided' in mq-client-util-core; Boot BOM supplies it at runtime -->
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>

  <!-- single MQ library (includes IBM MQ client + Spring helpers) -->
  <dependency>
    <groupId>com.yourorg.mq</groupId>
    <artifactId>mq-client-util-core</artifactId>
    <version>${mq-client-util.version}</version>
  </dependency>
</dependencies>
```

No separate IBM MQ or `mq-client-util-ibm-mq` dependency is required — they are bundled transitively inside `mq-client-util-core`.

---

## Step 3 — Add `mq-config.json`

Place `src/main/resources/mq-config.json` on the classpath (or point to an external file in Step 4).

**Plain TCP + username/password (local dev example):**

```json
{
  "defaultConnection": "ibmVe",
  "connections": {
    "ibmVe": {
      "type": "IBM_MQ",
      "queueManager": "QM.SU000423",
      "channel": "DBTAX.VE.SVRCONN",
      "connectionName": "localhost(1423)",
      "ssl": false,
      "username": "app",
      "password": "passw0rd",
      "useMqCspAuthentication": false,
      "connectionTimeoutMs": 30000
    }
  },
  "queues": {
    "responseQ": {
      "connection": "ibmVe",
      "qname": "PWM.VE.RESPONSE.QUEUE",
      "queueType": "ibm.mq",
      "defaultContentType": "XML",
      "listen": true
    }
  }
}
```

| Field | Purpose |
|-------|---------|
| `connections.*.type` | Must be `IBM_MQ` |
| `connectionName` | Host/port as `host(port)` — parsed to host + port |
| `ssl` / `sslCipherSpec` | Set `ssl: true` and cipher for TLS channels |
| `username` / `password` | Optional when using SSL client-auth only |
| `queues.*.qname` | Physical IBM MQ queue name |
| `queues.*.listen` | `true` = start a background listener at app startup |

**SSL example:**

```json
"ssl": true,
"sslCipherSpec": "ECDHE_RSA_AES_256_GCM_SHA384",
"sslTrustStore": "C:/path/to/truststore.jks",
"sslTrustStorePassword": "changeit"
```

See `docker/mq-config.ssl-with-auth.json` and `docker/mq-config.ssl-no-auth.json` in this repo for full examples.

---

## Step 4 — Configure Spring Boot

In `src/main/resources/application.properties`:

```properties
mq.client.config-location=mq-config.json
logging.level.mq-client.flow=INFO
```

**Config location formats:**

| Value | Meaning |
|-------|---------|
| `mq-config.json` | Classpath root (default) |
| `classpath:config/mq-config.json` | Classpath path |
| `file:/opt/app/config/mq-config.json` | External file |

---

## Step 5 — Enable the MQ client

Add `@EnableMqClient` to your main application class:

```java
package com.example.myapp;

import com.db.pwmus.mqclient.spring.EnableMqClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMqClient
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

`@EnableMqClient` registers:

| Bean | Type | Purpose |
|------|------|---------|
| `mqClientFactory` | `MqClientFactory` | Low-level factory from `mq-config.json` |
| `mqClient` | `MqClient` | Facade for send/receive by logical queue name |
| `mqListenerRegistry` | `MqListenerRegistry` | Manages background listeners |
| `MqListenerLifecycle` | — | Starts listeners for `"listen": true` queues on startup |

REST endpoints are **not** part of the library — add your own `@RestController` in the application (see Step 8).

---

## Step 6 — Send and receive in your code

Inject `MqClient` into any `@Service`, `@Component`, or `@RestController`:

```java
import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.core.MqClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class OrderService {
    private final MqClient mqClient;

    public OrderService(MqClient mqClient) {
        this.mqClient = mqClient;
    }

    public void publishOrder(String xmlPayload) {
        mqClient.sendXml("responseQ", xmlPayload);
    }

    public void publishJson(Object orderDto) {
        mqClient.sendJson("responseQ", orderDto);
    }

    public String pollResponse() {
        MqMessage msg = mqClient.receive("responseQ", 10, TimeUnit.SECONDS);
        return msg == null ? null : msg.getBody();
    }
}
```

`responseQ` is the **logical** queue name from `mq-config.json`, not the physical IBM MQ name (`qname`).

---

## Step 7 — Background listeners (optional)

Queues with `"listen": true` are started automatically when the application starts.

**Default behaviour:** messages are logged via `LoggingMqMessageHandler`.

**Custom handler:** implement `MqQueueMessageHandler` and register as a Spring bean:

```java
import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.listener.MqQueueMessageHandler;
import org.springframework.stereotype.Component;

@Component
public class ResponseQueueHandler implements MqQueueMessageHandler {

    @Override
    public String getLogicalQueueName() {
        return "responseQ";
    }

    @Override
    public void onMessage(MqMessage message) {
        // business logic here
    }
}
```

Spring collects all `MqQueueMessageHandler` beans and registers them before listeners start.

---

## Step 8 — REST endpoints (in your application)

The library does not ship a `@RestController`. Add one in your app if you need HTTP test APIs. Inject `MqClient`:

```java
@RestController
public class MqController {
    private final MqClient mqClient;

    public MqController(MqClient mqClient) {
        this.mqClient = mqClient;
    }

    @PostMapping("/mq/sendXml")
    public String sendXml(@RequestParam("queue") String logicalQueue, @RequestBody String body) {
        mqClient.sendXml(logicalQueue, body);
        return "OK";
    }

    @GetMapping("/mq/receive")
    public String receive(@RequestParam("queue") String logicalQueue,
                          @RequestParam(value = "timeoutSec", defaultValue = "5") long timeoutSec) {
        MqMessage msg = mqClient.receive(logicalQueue, timeoutSec, TimeUnit.SECONDS);
        return msg == null ? "" : msg.getBody();
    }
}
```

Full example: `sample-boot26-webapp/.../MqController.java`.

**Send XML example:**

```cmd
curl -X POST "http://localhost:8080/mq/sendXml?queue=responseQ" ^
  -H "Content-Type: application/xml" ^
  -d "<order><id>123</id></order>"
```

**Receive example:**

```cmd
curl "http://localhost:8080/mq/receive?queue=responseQ&timeoutSec=5"
```

Remove or secure these endpoints in production if they are not needed.

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/mq/sendJson?queue=responseQ&body={"id":1}` | Send JSON (small payloads) |
| `POST` | `/mq/sendJson?queue=responseQ` | Send JSON body |
| `POST` | `/mq/sendXml?queue=responseQ` | Send XML body |
| `GET` | `/mq/receive?queue=responseQ&timeoutSec=5` | Synchronous receive |

---

## Step 9 — Build and run

```cmd
mvn clean package
java -jar target/my-application.jar
```

Or:

```cmd
mvn spring-boot:run
```

On startup you should see flow logs under logger `mq-client.flow` and listener startup messages for queues with `"listen": true`.

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| `No provider found for type: IBM_MQ` | `mq-client-util-core` JAR on classpath (SPI inside the JAR) |
| `Unknown queue logical name` | Logical name in code matches a key under `queues` in `mq-config.json` |
| `2035 NOT_AUTHORIZED` | MQ channel auth, username/password, CHLAUTH on queue manager |
| `sslCipherSpec is required when ssl is true` | Add `sslCipherSpec` matching the MQ channel cipher |
| No messages in listener | `"listen": true` on queue; check `logging.level.mq-client.flow=DEBUG` |
| Jackson errors | Ensure `jackson-databind` is on the classpath (Boot starter usually provides it) |

---

## Architecture summary

```
@SpringBootApplication + @EnableMqClient
        │
        ├── MqClient (inject in your services)
        │       └── sendJson / sendXml / receive(logicalQueue, …)
        │
        ├── MqListenerLifecycle
        │       └── starts listeners for listen:true queues
        │
        └── Your @RestController (application-owned, optional)
```

**Package root:** `com.db.pwmus.mqclient`

For non-Spring usage or advanced SPI/extension details, see `LIBRARY-USAGE.md`.
