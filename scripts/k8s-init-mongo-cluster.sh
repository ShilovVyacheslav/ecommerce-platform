#!/bin/bash
set -e

NAMESPACE=ecommerce
MONGO_ROOT_USER=$(kubectl get secret mongo-credentials -n "$NAMESPACE" -o jsonpath='{.data.MONGO_ROOT_USER}' | base64 -d)
MONGO_ROOT_PASSWORD=$(kubectl get secret mongo-credentials -n "$NAMESPACE" -o jsonpath='{.data.MONGO_ROOT_PASSWORD}' | base64 -d)

echo "Initiating config server replica set (via localhost exception)..."
kubectl exec mongo-configsvr-0 -n "$NAMESPACE" -- mongo --port 27019 --eval '
  rs.initiate({
    _id: "csrs",
    configsvr: true,
    members: [{ _id: 0, host: "mongo-configsvr-0.mongo-configsvr.ecommerce.svc.cluster.local:27019" }]
  })
' || echo "csrs already initiated, skipping"

echo "Initiating shard1 replica set..."
kubectl exec mongo-shard1-0 -n "$NAMESPACE" -- mongo --port 27018 --eval '
  rs.initiate({
    _id: "shard1",
    members: [{ _id: 0, host: "mongo-shard1-0.mongo-shard1.ecommerce.svc.cluster.local:27018" }]
  })
' || echo "shard1 already initiated, skipping"

echo "Initiating shard2 replica set..."
kubectl exec mongo-shard2-0 -n "$NAMESPACE" -- mongo --port 27018 --eval '
  rs.initiate({
    _id: "shard2",
    members: [{ _id: 0, host: "mongo-shard2-0.mongo-shard2.ecommerce.svc.cluster.local:27018" }]
  })
' || echo "shard2 already initiated, skipping"

echo "Waiting for replica sets to elect a PRIMARY (needed before mongos can use them)..."
sleep 15

MONGOS_POD=$(kubectl get pods -n "$NAMESPACE" -l app=mongos -o jsonpath='{.items[0].metadata.name}')

echo "Adding shards to cluster (via mongos localhost)..."
kubectl exec "$MONGOS_POD" -n "$NAMESPACE" -- mongo --eval '
  sh.addShard("shard1/mongo-shard1-0.mongo-shard1.ecommerce.svc.cluster.local:27018");
  sh.addShard("shard2/mongo-shard2-0.mongo-shard2.ecommerce.svc.cluster.local:27018");
'

echo "Creating root user (last step allowed under the localhost exception)..."
kubectl exec "$MONGOS_POD" -n "$NAMESPACE" -- mongo --eval '
  db.getSiblingDB("admin").createUser({
    user: "'"$MONGO_ROOT_USER"'",
    pwd: "'"$MONGO_ROOT_PASSWORD"'",
    roles: [{ role: "root", db: "admin" }]
  })
' || echo "root user already exists, skipping"

echo "Enabling sharding on product_service database (now authenticated — exception is closed)..."
kubectl exec "$MONGOS_POD" -n "$NAMESPACE" -- mongo -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --eval '
  sh.enableSharding("product_service");
  sh.shardCollection("product_service.products", { _id: "hashed" });
  sh.shardCollection("product_service.stock_reservations", { orderId: "hashed" });
'

echo "Mongo cluster initialized successfully."