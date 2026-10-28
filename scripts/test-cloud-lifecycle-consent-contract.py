#!/usr/bin/env python3
"""Source contract: cloud approval and ephemeral credentials are revoked on app stop."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
brain = (root / "app/src/main/java/com/medmate/personal/ui/BrainPanel.kt").read_text()
gateway = (root / "app/src/main/java/com/medmate/personal/brain/CloudBrainGateway.kt").read_text()

assert "import androidx.lifecycle.LifecycleEventObserver" in brain
assert "import androidx.lifecycle.compose.LocalLifecycleOwner" in brain
assert 'var sessionToken by remember { mutableStateOf("") }' in brain, (
    "the bearer token must remain ordinary in-memory Compose state, not saved state"
)
start = brain.index("val lifecycleOwner = LocalLifecycleOwner.current")
end = brain.index("var lastQuestion", start)
observer = brain[start:end]
assert "DisposableEffect(lifecycleOwner)" in observer
assert "Lifecycle.Event.ON_STOP" in observer
for statement in (
    "cloudConsent = null",
    "explicitConsent = false",
    'sessionToken = ""',
    "cloudPreviewReviewed = false",
    "cloudPreviewExpanded = false",
):
    assert statement in observer, f"background revocation missing: {statement}"
assert "lifecycleOwner.lifecycle.addObserver(observer)" in observer
assert "lifecycleOwner.lifecycle.removeObserver(observer)" in observer
assert "Lifecycle.Event.ON_START" not in observer, "foregrounding must not silently grant consent"

# The production outbound boundary remains one-shot and redirect-safe.
consume = gateway.index("consent.consume(gatewayUrl, model, requestIdentity, evidenceSeal)")
open_connection = gateway.index("connectionFactory(URL(safeUrl))")
assert consume < open_connection, "approval must be consumed before opening the network connection"
assert "connection.instanceFollowRedirects = false" in gateway
assert "connection.disconnect()" in gateway
print("PASS: background revocation, ephemeral token and outbound consent contract")
