#!/bin/bash

# Script to generate test JWT token
# Usage: ./generate-token.sh <tenantId> <userId>

TENANT_ID=${1:-"test-tenant-1"}
USER_ID=${2:-"test-user-1"}
SECRET="your-super-secret-key-that-should-be-changed-in-production-at-least-32-chars"
EXPIRATION=$(($(date +%s) + 86400)) # 24 hours

# Create JWT token manually using Python
python3 << EOF
import json
import base64
import hmac
import hashlib
from datetime import datetime, timedelta

# Header
header = {
    "alg": "HS256",
    "typ": "JWT"
}

# Payload
payload = {
    "sub": "$USER_ID",
    "tenantId": "$TENANT_ID",
    "iat": int(datetime.now().timestamp()),
    "exp": int((datetime.now() + timedelta(hours=24)).timestamp())
}

# Secret
secret = "$SECRET"

# Create signing input
header_encoded = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip('=')
payload_encoded = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')

signing_input = f"{header_encoded}.{payload_encoded}"

# Create signature
signature = base64.urlsafe_b64encode(
    hmac.new(
        secret.encode(),
        signing_input.encode(),
        hashlib.sha256
    ).digest()
).decode().rstrip('=')

# Create token
token = f"{signing_input}.{signature}"
print(token)
EOF

