#!/bin/sh
set -e

trap exit TERM

while :; do
  echo "[$(date)] Running certbot renew..."
  certbot renew \
    --non-interactive \
    --preferred-challenges dns \
    --authenticator dns-duckdns \
    --dns-duckdns-token "$DUCKDNS_TOKEN" \
    --dns-duckdns-propagation-seconds 60 || echo "Renew failed, will retry"
  echo "[$(date)] Sleeping 12h..."
  sleep 12h &
  wait $!
done