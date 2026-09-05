#!/bin/sh
set -e

KEYS_DIR="user-service/src/main/resources/keys"
PRIVATE_KEY="$KEYS_DIR/private.pem"
PUBLIC_KEY="$KEYS_DIR/public.pem"

if [ -f "$PRIVATE_KEY" ] && [ -f "$PUBLIC_KEY" ]; then
  echo "Keys already exist, skipping."
  exit 0
fi

mkdir -p "$KEYS_DIR"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/private-pkcs1.pem"
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in "$KEYS_DIR/private-pkcs1.pem" -out "$PRIVATE_KEY"
openssl rsa -pubout -in "$KEYS_DIR/private-pkcs1.pem" -out "$PUBLIC_KEY"
rm "$KEYS_DIR/private-pkcs1.pem"

echo "Generated keypair in $KEYS_DIR"