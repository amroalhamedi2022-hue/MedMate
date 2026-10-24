#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/com/medmate/personal/brain/BrainRequestIdentity.kt \
  app/src/main/java/com/medmate/personal/brain/GatewayPolicy.kt \
  app/src/main/java/com/medmate/personal/brain/CloudTransferConsent.kt \
  scripts/brain/CloudConsentSmoke.kt -include-runtime -d "$out/cloud-consent.jar"
java -jar "$out/cloud-consent.jar"
