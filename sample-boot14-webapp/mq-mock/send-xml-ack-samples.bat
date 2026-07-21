@echo off
setlocal
set BASE=http://localhost:8084
set SAMPLES=%~dp0samples

echo === Positive ACK: CREATE ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-positive-create.xml"
echo.
echo Wait for autoReplyDelayMs, then check ResponseQueueListener logs / responseQ-inbox
echo.

echo === Positive ACK: UPDATE ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-positive-update.xml"
echo.
echo.

echo === Positive ACK: CANCEL ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-positive-cancel.xml"
echo.
echo.

echo === Negative ACK: REJECT action ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-negative-reject.xml"
echo.
echo.

echo === Negative ACK: invalid amount ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-negative-invalid-amount.xml"
echo.
echo.

echo === Negative ACK: missing OrderId ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%SAMPLES%\request-negative-missing-orderid.xml"
echo.
echo.
echo Done. Watch app logs for AckType=POSITIVE or NEGATIVE after the configured delay.
endlocal
