# Signing rotation scripts

## Purpose

This directory contains the local and CI entrypoint for one specific migration:

- old signer: debug keystore
- new signer: release keystore

## Main script

`rotate_sign_apks.sh`

What it does:

- creates or reuses a `SigningCertificateLineage`
- re-signs every release APK in a directory with debug signer + release signer
- writes verification logs for each rotated APK

## Local example

```bash
./scripts/signing/rotate_sign_apks.sh \
  --apk-dir app/build/outputs/apk/release \
  --out-dir app/build/outputs/apk/rotated-release \
  --lineage signing/signing-lineage.bin \
  --old-keystore debug.keystore \
  --old-alias androiddebugkey \
  --old-store-pass 'android' \
  --old-key-pass 'android' \
  --new-keystore vFlow.jks \
  --new-alias release_alias \
  --new-store-pass 'release_store_password' \
  --new-key-pass 'release_key_password' \
  --rotation-min-sdk 29 \
  --verify-min-sdk 29
```

## CI contract

The GitHub Actions workflow expects these existing release secrets:

- `KEYSTORE`
- `KEYSTORE_PASSWORD`
- `KEYSTORE_ALIAS`
- `KEY_PASSWORD`

It also uses:

- `DEBUG_KEYSTORE`

Optional debug secrets when the debug keystore is not the default Android debug key:

- `DEBUG_KEYSTORE_PASSWORD`
- `DEBUG_KEYSTORE_ALIAS`
- `DEBUG_KEY_PASSWORD`
