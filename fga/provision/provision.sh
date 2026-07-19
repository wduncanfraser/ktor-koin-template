#!/bin/sh
# Idempotently provisions the OpenFGA "todo" store + authorization model, so repeated
# initializations of OpenFGA neither creates duplicate stores nor silently ignores model edits:
#   - store absent  -> create the store and write the model
#   - store present -> write a NEW model version only if /model/authorization-model.fga differs
#                      from the deployed latest
# OpenFGA models are immutable and versioned (every write creates a new version), so we compare the
# canonical DSL of both sides and skip when unchanged — avoiding a duplicate version on every
# restart. Comparing canonical DSL (not raw JSON) is robust: the server materializes empty defaults
# the local file omits, and it ignores comment/whitespace-only edits. Existing tuples are untouched.
set -e

API="${OPENFGA_API_URL:-http://openfga:8080}"
MODEL_FILE=/model/authorization-model.fga

store_id=$(fga store list --api-url "$API" | jq -r '.stores[]? | select(.name == "todo") | .id' | head -n1)

if [ -z "$store_id" ]; then
  echo "Creating OpenFGA 'todo' store and authorization model..."
  fga store create --api-url "$API" --name todo --model "$MODEL_FILE"
  exit 0
fi

echo "OpenFGA 'todo' store ($store_id) already exists — checking the authorization model..."
deployed=$(fga model get --api-url "$API" --store-id "$store_id" --format fga --field model)
desired=$(fga model transform --file "$MODEL_FILE" --output-format fga)

if [ "$deployed" = "$desired" ]; then
  echo "Authorization model is up to date — nothing to do."
else
  echo "Authorization model changed — writing a new version..."
  fga model write --api-url "$API" --store-id "$store_id" --file "$MODEL_FILE"
fi
