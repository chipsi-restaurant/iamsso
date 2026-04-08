#!/bin/bash
POLICY_URL="http://localhost:8082/api/v1/policies"

echo "Seeding policies..."

curl -s -X POST $POLICY_URL -H "Content-Type: application/json" \
  -d '{"name":"Admin full access","role":"admin","effect":"ALLOW","action":"*","resourcePattern":"*","priority":100}'
echo ""

curl -s -X POST $POLICY_URL -H "Content-Type: application/json" \
  -d '{"name":"User read users","role":"user","effect":"ALLOW","action":"READ","resourcePattern":"users/*"}'
echo ""

curl -s -X POST $POLICY_URL -H "Content-Type: application/json" \
  -d '{"name":"User read policies","role":"user","effect":"ALLOW","action":"READ","resourcePattern":"policies/*"}'
echo ""

curl -s -X POST $POLICY_URL -H "Content-Type: application/json" \
  -d '{"name":"User read roles","role":"user","effect":"ALLOW","action":"READ","resourcePattern":"roles/*"}'
echo ""

echo "Done."
