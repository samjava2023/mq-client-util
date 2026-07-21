#!/bin/bash
# Configures TLS on the running ibmmq-dev container (run via: docker exec ibmmq-dev bash /mnt/mqm/ssl/configure-qm-ssl.sh)

set -e

QMGR="${MQ_QMGR_NAME:-QM.SU000423}"
SSLDIR="/mnt/mqm/ssl"
PW="changeit"
LABEL="qmgrssl"

if [ ! -f "$SSLDIR/server.p12" ]; then
  echo "Missing $SSLDIR/server.p12 - run docker/generate-ssl-certs.bat on the host first"
  exit 1
fi

echo "Creating IBM MQ key database at $SSLDIR/qmgr ..."
if [ ! -f "$SSLDIR/qmgr.kdb" ]; then
  runmqakm -keydb -create -type cms -db "$SSLDIR/qmgr.kdb" -pw "$PW" -stash
fi

runmqakm -cert -delete -db "$SSLDIR/qmgr.kdb" -pw "$PW" -label "$LABEL" 2>/dev/null || true
runmqakm -cert -import \
  -file "$SSLDIR/server.p12" -pw "$PW" -type pkcs12 \
  -target "$SSLDIR/qmgr.kdb" -target_pw "$PW" -target_type cms \
  -label "$LABEL"

echo "Applying MQSC for TLS channel ..."
runmqsc "$QMGR" <<EOF
ALTER QMGR SSLKEYR('$SSLDIR/qmgr') CERTLABL('$LABEL')
REFRESH SECURITY TYPE(SSL)
ALTER CHANNEL('DBTAX.VE.SVRCONN') CHLTYPE(SVRCONN) TRPTYPE(TCP) +
  SSLCIPH(ECDHE_RSA_AES_256_GCM_SHA384) SSLCAUTH(OPTIONAL)
DISPLAY CHANNEL('DBTAX.VE.SVRCONN') SSLCIPH SSLCAUTH
EOF

echo "TLS configuration applied."
