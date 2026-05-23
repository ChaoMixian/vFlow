#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  rotate_sign_apks.sh --apk-dir DIR --out-dir DIR --lineage FILE [options]

Required options:
  --apk-dir DIR                  Directory containing release APKs to rotate-sign
  --out-dir DIR                  Output directory for rotated APKs and verification logs
  --lineage FILE                 SigningCertificateLineage file
  --old-keystore FILE            Old signer keystore path
  --old-alias NAME               Old signer key alias
  --old-store-pass PASS          Old signer keystore password
  --old-key-pass PASS            Old signer key password
  --new-keystore FILE            New signer keystore path
  --new-alias NAME               New signer key alias
  --new-store-pass PASS          New signer keystore password
  --new-key-pass PASS            New signer key password

Optional:
  --apksigner PATH               Explicit apksigner path
  --rotation-min-sdk API         Passed to apksigner --rotation-min-sdk-version
  --skip-lineage-create          Reuse the provided lineage file as-is
  --verify-min-sdk API           Extra verify pass with --min-sdk-version
  --in-place                     Keep the original APK filename in the output directory
EOF
}

require_value() {
  local name="$1"
  local value="${2:-}"
  if [[ -z "${value}" ]]; then
    echo "Missing required value: ${name}" >&2
    usage >&2
    exit 1
  fi
}

find_apksigner() {
  if [[ -n "${APKSIGNER_PATH:-}" ]]; then
    printf '%s\n' "${APKSIGNER_PATH}"
    return
  fi

  local candidates=()
  local default_sdk_dir="${HOME}/Library/Android/sdk"
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    while IFS= read -r path; do
      candidates+=("${path}")
    done < <(find "${ANDROID_HOME}/build-tools" -type f -name apksigner 2>/dev/null | sort -V)
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    while IFS= read -r path; do
      candidates+=("${path}")
    done < <(find "${ANDROID_SDK_ROOT}/build-tools" -type f -name apksigner 2>/dev/null | sort -V)
  fi
  if [[ -d "${default_sdk_dir}/build-tools" ]]; then
    while IFS= read -r path; do
      candidates+=("${path}")
    done < <(find "${default_sdk_dir}/build-tools" -type f -name apksigner 2>/dev/null | sort -V)
  fi

  if [[ ${#candidates[@]} -eq 0 ]]; then
    echo "Unable to find apksigner. Set --apksigner or ANDROID_HOME/ANDROID_SDK_ROOT." >&2
    exit 1
  fi

  local last_index=$(( ${#candidates[@]} - 1 ))
  printf '%s\n' "${candidates[${last_index}]}"
}

APK_DIR=""
OUT_DIR=""
LINEAGE_FILE=""
OLD_KEYSTORE=""
OLD_ALIAS=""
OLD_STORE_PASS=""
OLD_KEY_PASS=""
NEW_KEYSTORE=""
NEW_ALIAS=""
NEW_STORE_PASS=""
NEW_KEY_PASS=""
APKSIGNER_PATH="${APKSIGNER_PATH:-}"
ROTATION_MIN_SDK=""
SKIP_LINEAGE_CREATE="false"
VERIFY_MIN_SDK=""
IN_PLACE_OUTPUT="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk-dir)
      APK_DIR="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --lineage)
      LINEAGE_FILE="$2"
      shift 2
      ;;
    --old-keystore)
      OLD_KEYSTORE="$2"
      shift 2
      ;;
    --old-alias)
      OLD_ALIAS="$2"
      shift 2
      ;;
    --old-store-pass)
      OLD_STORE_PASS="$2"
      shift 2
      ;;
    --old-key-pass)
      OLD_KEY_PASS="$2"
      shift 2
      ;;
    --new-keystore)
      NEW_KEYSTORE="$2"
      shift 2
      ;;
    --new-alias)
      NEW_ALIAS="$2"
      shift 2
      ;;
    --new-store-pass)
      NEW_STORE_PASS="$2"
      shift 2
      ;;
    --new-key-pass)
      NEW_KEY_PASS="$2"
      shift 2
      ;;
    --apksigner)
      APKSIGNER_PATH="$2"
      shift 2
      ;;
    --rotation-min-sdk)
      ROTATION_MIN_SDK="$2"
      shift 2
      ;;
    --skip-lineage-create)
      SKIP_LINEAGE_CREATE="true"
      shift
      ;;
    --verify-min-sdk)
      VERIFY_MIN_SDK="$2"
      shift 2
      ;;
    --in-place)
      IN_PLACE_OUTPUT="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_value "--apk-dir" "${APK_DIR}"
require_value "--out-dir" "${OUT_DIR}"
require_value "--lineage" "${LINEAGE_FILE}"
require_value "--old-keystore" "${OLD_KEYSTORE}"
require_value "--old-alias" "${OLD_ALIAS}"
require_value "--old-store-pass" "${OLD_STORE_PASS}"
require_value "--old-key-pass" "${OLD_KEY_PASS}"
require_value "--new-keystore" "${NEW_KEYSTORE}"
require_value "--new-alias" "${NEW_ALIAS}"
require_value "--new-store-pass" "${NEW_STORE_PASS}"
require_value "--new-key-pass" "${NEW_KEY_PASS}"

APKSIGNER_PATH="$(find_apksigner)"

if [[ ! -d "${APK_DIR}" ]]; then
  echo "APK directory does not exist: ${APK_DIR}" >&2
  exit 1
fi

if [[ ! -f "${OLD_KEYSTORE}" ]]; then
  echo "Old keystore does not exist: ${OLD_KEYSTORE}" >&2
  exit 1
fi

if [[ ! -f "${NEW_KEYSTORE}" ]]; then
  echo "New keystore does not exist: ${NEW_KEYSTORE}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

if [[ "${SKIP_LINEAGE_CREATE}" != "true" ]]; then
  mkdir -p "$(dirname "${LINEAGE_FILE}")"
  rm -f "${LINEAGE_FILE}"
  "${APKSIGNER_PATH}" rotate \
    --out "${LINEAGE_FILE}" \
    --old-signer \
      --ks "${OLD_KEYSTORE}" \
      --ks-key-alias "${OLD_ALIAS}" \
      --ks-pass "pass:${OLD_STORE_PASS}" \
      --key-pass "pass:${OLD_KEY_PASS}" \
      --set-installed-data true \
      --set-shared-uid true \
      --set-permission true \
      --set-auth true \
    --new-signer \
      --ks "${NEW_KEYSTORE}" \
      --ks-key-alias "${NEW_ALIAS}" \
      --ks-pass "pass:${NEW_STORE_PASS}" \
      --key-pass "pass:${NEW_KEY_PASS}"
fi

if [[ ! -f "${LINEAGE_FILE}" ]]; then
  echo "Lineage file was not created: ${LINEAGE_FILE}" >&2
  exit 1
fi

apks=()
while IFS= read -r apk; do
  apks+=("${apk}")
done < <(find "${APK_DIR}" -maxdepth 1 -type f -name "*.apk" ! -name "*-androidTest.apk" | sort)
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "No APKs found in ${APK_DIR}" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  base_name="$(basename "${apk}")"
  signed_apk="${OUT_DIR}/${base_name%.apk}-rotated.apk"
  if [[ "${IN_PLACE_OUTPUT}" == "true" ]]; then
    signed_apk="${OUT_DIR}/${base_name}"
  fi
  verify_log="${OUT_DIR}/${base_name%.apk}-verify.txt"

  sign_cmd=(
    "${APKSIGNER_PATH}" sign
    --in "${apk}"
    --out "${signed_apk}"
    --ks "${OLD_KEYSTORE}"
    --ks-key-alias "${OLD_ALIAS}"
    --ks-pass "pass:${OLD_STORE_PASS}"
    --key-pass "pass:${OLD_KEY_PASS}"
    --next-signer
    --ks "${NEW_KEYSTORE}"
    --ks-key-alias "${NEW_ALIAS}"
    --ks-pass "pass:${NEW_STORE_PASS}"
    --key-pass "pass:${NEW_KEY_PASS}"
    --lineage "${LINEAGE_FILE}"
  )

  if [[ -n "${ROTATION_MIN_SDK}" ]]; then
    sign_cmd+=(--rotation-min-sdk-version "${ROTATION_MIN_SDK}")
  fi

  "${sign_cmd[@]}"

  {
    echo "# verify"
    "${APKSIGNER_PATH}" verify --verbose --print-certs "${signed_apk}"
    if [[ -n "${VERIFY_MIN_SDK}" ]]; then
      echo
      echo "# verify min sdk ${VERIFY_MIN_SDK}"
      "${APKSIGNER_PATH}" verify --verbose --min-sdk-version "${VERIFY_MIN_SDK}" --print-certs "${signed_apk}"
    fi
  } > "${verify_log}"
done

cp "${LINEAGE_FILE}" "${OUT_DIR}/signing-lineage.bin"

echo "Rotated APKs written to ${OUT_DIR}"
