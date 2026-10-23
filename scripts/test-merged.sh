#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for f in test-brain.sh test-drafts.sh test-domain.sh test-study-filter.sh test-export.sh test-backup-lines.sh; do
    bash "scripts/$f"
done
python3 scripts/test-database-contract.py
python3 scripts/test-migrations.py
python3 scripts/validate-merged-contracts.py
