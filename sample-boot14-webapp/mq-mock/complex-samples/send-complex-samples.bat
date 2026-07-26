@echo off
setlocal
set BASE=http://localhost:8084
set HERE=%~dp0

echo === Complex POSITIVE ACK (large order CREATE) ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%HERE%request-positive-complex-order.xml"
echo.
echo Wait autoReplyDelayMs, then check listener logs for AckType=POSITIVE
echo.

echo === Complex NEGATIVE ACK (compliance failure) ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%HERE%request-negative-complex-compliance.xml"
echo.
echo.

echo === Complex NEGATIVE ACK (risk limit breach) ===
curl -s -X POST "%BASE%/mq/sendXml?queue=requestQ" -H "Content-Type: application/xml" --data-binary @"%HERE%request-negative-complex-risk.xml"
echo.
echo.
echo Done. Complex samples folder: %HERE%
endlocal
