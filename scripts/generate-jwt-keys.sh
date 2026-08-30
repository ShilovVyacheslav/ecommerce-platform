#!/bin/sh
set -e

ENV_NAME="$1"
if [ -z "$ENV_NAME" ]; then
  echo "Usage: $0 <local|docker>"
  exit 1
fi

KEYS_DIR="user-service/src/main/resources/keys"
PRIVATE_KEY="$KEYS_DIR/${ENV_NAME}-private.pem"
PUBLIC_KEY="$KEYS_DIR/${ENV_NAME}-public.pem"

if [ -f "$PRIVATE_KEY" ] && [ -f "$PUBLIC_KEY" ]; then
  echo "Keys for '$ENV_NAME' already exist, skipping."
  exit 0
fi

mkdir -p "$KEYS_DIR"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEYS_DIR/${ENV_NAME}-private-pkcs1.pem"
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in "$KEYS_DIR/${ENV_NAME}-private-pkcs1.pem" -out "$PRIVATE_KEY"
openssl rsa -pubout -in "$KEYS_DIR/${ENV_NAME}-private-pkcs1.pem" -out "$PUBLIC_KEY"
rm "$KEYS_DIR/${ENV_NAME}-private-pkcs1.pem"

echo "Generated $ENV_NAME keypair in $KEYS_DIR"