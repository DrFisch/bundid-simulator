#!/bin/sh
# Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md).
# Erzeugt beim ersten Start einen selbstsignierten TEST-Signaturschluessel, falls die Signatur aktiv ist und
# unter APP_SAML_SIGNING_KEYSTORE noch kein Keystore liegt (Verzeichnis als Volume einbinden, damit das
# Zertifikat einen Neustart uebersteht). Passwort: APP_SAML_SIGNING_PASSWORD (mind. 6 Zeichen).
set -e
if [ "${APP_SAML_SIGNING_ENABLED:-false}" = "true" ] && [ -n "${APP_SAML_SIGNING_KEYSTORE:-}" ] \
   && [ ! -f "$APP_SAML_SIGNING_KEYSTORE" ]; then
  echo "Erzeuge Test-Signaturschluessel: $APP_SAML_SIGNING_KEYSTORE"
  keytool -genkeypair -alias "${APP_SAML_SIGNING_ALIAS:-idp-signing}" -keyalg RSA -keysize 3072 \
    -sigalg SHA256withRSA -validity 3650 -dname "CN=BundID-Simulator Test-IdP" -storetype PKCS12 \
    -keystore "$APP_SAML_SIGNING_KEYSTORE" \
    -storepass "$APP_SAML_SIGNING_PASSWORD" -keypass "$APP_SAML_SIGNING_PASSWORD"
fi
exec "$@"
