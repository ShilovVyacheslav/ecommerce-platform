#!/bin/bash
set -e

NAMESPACE=ecommerce

create_or_update() {
  kubectl create secret generic "$1" -n "$NAMESPACE" "${@:2}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

create_or_update postgres-user-credentials \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=POSTGRES_DB=user_service

create_or_update postgres-payment-credentials \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=POSTGRES_DB=payment_service

create_or_update postgres-order-credentials \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  --from-literal=POSTGRES_DB=order_service

create_or_update redis-credentials \
  --from-literal=REDIS_PASSWORD="$(openssl rand -hex 16)"

create_or_update internal-api-credentials \
  --from-literal=INTERNAL_API_KEY="$(openssl rand -hex 32)"

create_or_update mongo-credentials \
  --from-literal=MONGO_ROOT_USER=root \
  --from-literal=MONGO_ROOT_PASSWORD="$(openssl rand -hex 16)"

if ! kubectl get secret mongo-keyfile -n "$NAMESPACE" &>/dev/null; then
  TMP_KEYFILE=$(mktemp)
  openssl rand -base64 756 > "$TMP_KEYFILE"
  kubectl create secret generic mongo-keyfile -n "$NAMESPACE" \
    --from-file=mongodb-keyfile="$TMP_KEYFILE"
  rm -f "$TMP_KEYFILE"
else
  echo "mongo-keyfile already exists, skipping (rotating would require reinitializing the whole cluster)"
fi

if ! kubectl get secret jwt-keys -n "$NAMESPACE" &>/dev/null; then
  ./scripts/generate-jwt-keys.sh
  kubectl create secret generic jwt-keys -n "$NAMESPACE" \
    --from-file=private.pem=user-service/src/main/resources/keys/private.pem \
    --from-file=public.pem=user-service/src/main/resources/keys/public.pem
else
  echo "jwt-keys already exists, skipping"
fi

echo "Secrets created/updated in namespace $NAMESPACE"