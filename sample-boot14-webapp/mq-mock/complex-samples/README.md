# Complex mock request / response samples

Large XML messages for local mock MQ testing (no real IBM MQ).

## Folder

```text
sample-boot14-webapp/mq-mock/complex-samples/
```

## Files

| File | Purpose |
|------|---------|
| `request-positive-complex-order.xml` | Large multi-party equity order → **POSITIVE** ACK |
| `request-negative-complex-compliance.xml` | Compliance / sanctions failure → **NEGATIVE** ACK |
| `request-negative-complex-risk.xml` | Pre-trade risk / collateral breach → **NEGATIVE** ACK |
| `expected-response-positive-complex.xml` | Response body returned for positive case |
| `expected-response-negative-compliance.xml` | Response body for compliance NACK |
| `expected-response-negative-risk.xml` | Response body for risk NACK |
| `send-complex-samples.bat` | Curl helper to send all three requests |

Match keys inside requests (`MessageProfile`):

- `COMPLEX_POS_CREATE`
- `COMPLEX_NEG_COMPLIANCE`
- `COMPLEX_NEG_RISK`

Wired in `../responseQ-replies.json` via `bodyFile` pointing at the expected-response XML files.

## How to run

1. App running with `mq-config-mock.json` (`mode=mock`, `autoReply=true`).
2. Rebuild connector if you just pulled `bodyFile` support:

```bat
cd D:\project\AI\mq-connector-util
mvn clean install -DskipTests
```

3. Send samples:

```bat
D:\project\AI\mq-client-util\sample-boot14-webapp\mq-mock\complex-samples\send-complex-samples.bat
```

Or one request:

```bat
curl -X POST "http://localhost:8084/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"D:\project\AI\mq-client-util\sample-boot14-webapp\mq-mock\complex-samples\request-positive-complex-order.xml"
```

4. After `autoReplyDelayMs`, check `ResponseQueueListener` logs for complex ACK XML and headers `AckType=POSITIVE` / `NEGATIVE`.
