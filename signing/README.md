# Stable debug signing key

`medmate-debug.keystore` signs development APKs so a later debug build can update an earlier one without uninstalling it and losing app-private study data.

This is a **debug-only** key. Its standard debug credentials are declared in `app/build.gradle.kts`. Keep this file with private source backups; never use it to sign a public release.
