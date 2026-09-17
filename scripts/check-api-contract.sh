#!/usr/bin/env bash
set -euo pipefail

source_value="${1:-https://crucible.lbl.gov/api/v3/openapi.json}"
contract_file="$source_value"
temporary_file=""

if [[ "$source_value" == http://* || "$source_value" == https://* ]]; then
    temporary_file="$(mktemp)"
    trap 'rm -f "$temporary_file"' EXIT
    curl -fsS "$source_value" -o "$temporary_file"
    contract_file="$temporary_file"
fi

jq -e '
    .info.version == "3.0.0" and
    (.paths["/samples"].get.parameters | any(.name == "owner_id" and ((.deprecated // false) == false))) and
    (.paths["/samples"].get.parameters | any(.name == "owner_orcid" and .deprecated == true)) and
    (.paths["/samples"].get.parameters | any(.name == "anchor_mfid")) and
    (.paths["/datasets"].get.parameters | any(.name == "anchor_mfid")) and
    (.paths["/users/search"].get.parameters | any(.name == "is_service_account")) and
    (.paths["/samples/facets"].get != null) and
    (.paths["/datasets/facets"].get != null) and
    (.paths["/datasets/{dsid}/instrument"].put != null) and
    (.paths["/datasets/{dsid}/thumbnails/{thumbnail_id}"].patch != null) and
    (.paths["/service_accounts"].get != null) and
    (.paths["/service_accounts"].post != null) and
    (.paths["/service_accounts/{unique_id}"].patch != null) and
    (.paths["/service_accounts/{unique_id}/rotate_key"].post != null) and
    (.components.schemas.AccountCapabilities.properties.can_manage_service_accounts != null) and
    (.components.schemas.ReadinessResponse.properties.database != null)
' "$contract_file" >/dev/null

echo "Crucible API contract is compatible"
