#!/bin/bash

# Script to test API endpoints
# Make sure application is running on localhost:8080

BASE_URL="http://localhost:8080/api/v1"
TENANT_ID="test-tenant-1"
USER_ID="test-user-1"

# Generate token using curl with OpenAPI (simplified for testing)
# For production, replace with your auth system
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXItMSIsInRlbmFudElkIjoidGVzdC10ZW5hbnQtMSIsImlhdCI6MTcxODEwMDAwMCwiZXhwIjoyMDAwMDAwMDAwfQ.dummy"

echo "=== Testing Trouble Ticket API ==="
echo ""

# 1. Create trouble ticket
echo "1. Creating trouble ticket..."
CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/troubleTicket" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "externalId": "TEST-'$(date +%s)'",
    "serviceId": 987654321,
    "description": "Test issue from script",
    "status": "new",
    "note": "Created via test script"
  }')

echo "$CREATE_RESPONSE" | jq .
TICKET_ID=$(echo "$CREATE_RESPONSE" | jq -r '.id')
echo "Created ticket: $TICKET_ID"
echo ""

# 2. List tickets
echo "2. Listing trouble tickets..."
curl -s -X GET "$BASE_URL/troubleTicket" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# 3. Get ticket details
echo "3. Getting ticket details for $TICKET_ID..."
curl -s -X GET "$BASE_URL/troubleTicket/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .
echo ""

# 4. Add note
echo "4. Adding note to ticket..."
curl -s -X POST "$BASE_URL/troubleTicket/$TICKET_ID/note" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Note added via test script"
  }' | jq .
echo ""

# 5. Close ticket
echo "5. Closing ticket..."
curl -s -X PATCH "$BASE_URL/troubleTicket/$TICKET_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "closed"
  }' | jq .
echo ""

echo "=== Test Complete ==="

