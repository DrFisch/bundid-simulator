# Fork „bpsim/standardkonform“ des BundID-Simulators

Dieser Branch ist eine **geänderte Fassung** des BundID-Simulators der Bundesagentur für Arbeit
(https://github.com/ba-itsys/bundid-simulator, Basis: Commit `f1ba593` nach Release `v1.1.5`,
Lizenz Apache License 2.0 – siehe `LICENSE`). Geändert 2026 von Lukas Sponsel für eine Studienarbeit
(Hochschule Hof, BundID-Anbindung eines Bürgerportals). Geänderte Dateien tragen einen entsprechenden Hinweis.

## Ziel

Der Original-Simulator antwortet in mehreren Punkten nicht standardkonform (unsigniert, Issuer = EntityID des
SP, Request-Parsing über feste Präfixe, `Conditions` an falscher Stelle, Leerzeichen in OIDs, keine Metadaten).
Gängige SAML-Bibliotheken (z. B. ITfoxtec.Identity.Saml2) lehnen solche Responses zu Recht ab. Der Fork
behebt das mit möglichst kleinen Änderungen, damit eine Bibliothek mit **voller Validierung** arbeiten kann.
Außerdem kennt der Original-Simulator keine **Anmeldesitzung**: Jeder Dienst (z. B. das Postfach der BundID) zeigt
erneut die Personenauswahl. Der Fork ergänzt eine abschaltbare Sitzung (Single Sign-on) wie bei der echten BundID.

**Alle neuen Funktionen sind abschaltbar; ohne Konfiguration verhält sich der Simulator wie das Original**
(ausgenommen die Fehlerbehebungen in Abschnitt „Immer aktiv“).

## Konfiguration (neu)

| Property | Umgebungsvariable | Standard | Wirkung |
|---|---|---|---|
| `app.saml.idp-entity-id` | `APP_SAML_IDPENTITYID` | leer | EntityID des simulierten IdP; wird `Issuer` von Response und Assertion. Leer = Original (Issuer = EntityID des SP) |
| `app.saml.sso-url` | `APP_SAML_SSOURL` | leer | öffentliche URL von `POST /saml`, steht in den Metadaten |
| `app.saml.loa-format` | `APP_SAML_LOAFORMAT` | `stork` | `stork` = `STORK-QAA-Level-n` (Original); `eidas` = absolute URIs `http://eidas.europa.eu/LoA/{low,substantial,high}` im `AuthnContextClassRef` (SAML-Core 1.3.2 verlangt absolute URIs). Das Attribut `EID-CITIZEN-QAA-LEVEL` bleibt immer im STORK-Format |
| `app.saml.postkorb-handle.enabled` | `APP_SAML_POSTKORBHANDLE_ENABLED` | `false` | liefert das Attribut `PostkorbHandle` (`urn:oid:2.5.4.18`, **Annahme**, siehe unten). Wert aus `users.yml` (`postkorbHandle`) oder als UUID (Version 3) deterministisch aus der bPK2 abgeleitet |
| `app.saml.signing.enabled` | `APP_SAML_SIGNING_ENABLED` | `false` | signiert die Assertion (enveloped, RSA-SHA256, Exclusive C14N, Zertifikat im `KeyInfo`) |
| `app.saml.signing.keystore` | `APP_SAML_SIGNING_KEYSTORE` | leer | PKCS12-Keystore; im Container erzeugt der Entrypoint beim ersten Start einen selbstsignierten Test-Schlüssel, falls die Datei fehlt |
| `app.saml.signing.password` | `APP_SAML_SIGNING_PASSWORD` | leer | Keystore-Passwort (mind. 6 Zeichen) |
| `app.saml.signing.alias` | `APP_SAML_SIGNING_ALIAS` | `idp-signing` | Alias des Schlüssels |
| `app.sso.enabled` | `APP_SSO_ENABLED` | `false` | Anmeldesitzung (Single Sign-on): Nach einer erfolgreichen Auswahl merkt sich der Simulator Person, fachlichen Kontext bzw. bearbeitete Daten und Identifizierungsmittel in der Server-Sitzung (Cookie `BUNDIDSIM_SSO`, 30 min). Ein weiterer AuthnRequest wird **ohne Personenauswahl** beantwortet, wenn das Niveau der Sitzung reicht und der Request kein `ForceAuthn="true"` trägt; sonst erscheint die Auswahl mit vorbelegter Person |
| `server.servlet.session.cookie.secure` | `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `false` | hinter einem TLS-Proxy auf `true` setzen |

Neuer Endpunkt: `GET /saml/metadata` – IdP-Metadaten (EntityID, Signaturzertifikat, SSO-Endpunkt mit
HTTP-POST-Binding); nur wenn `idp-entity-id` und `sso-url` gesetzt sind, sonst 404.

Neue Endpunkte der Anmeldesitzung: `GET /sso` zeigt die bestehende Sitzung, `GET /sso/logout` beendet sie.
Die Sitzung gilt nur, wenn der Browser das Cookie mitsendet: bei Diensten unter derselben Website (z. B.
`*.example.de`) bzw. lokal unter `localhost`; bei Cross-Site-POSTs von fremden Websites (SameSite=Lax) zeigt der
Simulator wie bisher die Auswahl.

## Immer aktiv (Fehlerbehebungen)

- AuthnRequest wird **namespace-bewusst** gelesen (beliebige Präfixe wie `saml2:`/`saml2p:`), Werte getrimmt,
  eIDAS-LoA-URIs im Request werden verstanden; DTDs werden abgelehnt (XXE-Schutz).
- Response: `Conditions` direkt nach `Subject` (Schema-Reihenfolge) mit `NotBefore`/`NotOnOrAfter`;
  `xsi:type="xs:string"` mit deklariertem Präfix; alle eingesetzten Werte XML-maskiert; Response als UTF-8.
- `field_definitions.yml`: Leerzeichen in zwei OIDs entfernt (`personalTitle`, `EID-CITIZEN-QAA-LEVEL`).
- Docker: Container läuft als Benutzer `simulator` (UID 10001) statt root; Datenverzeichnis `/app/data`.

## Tests

Die `.dockerignore` des Originalprojekts schließt `src/test` vom Image-Build aus (dort: „No tests to run“). Tests
deshalb separat, z. B.:
`docker run --rm -v "$PWD":/app -v bpsim-maven-repo:/root/.m2 -w /app maven:3.9.16-eclipse-temurin-25 mvn -q test`
(Berichte unter `target/surefire-reports`; Stand: 21 Tests grün).

## Annahmen

- Der Attributname des Postkorb-Handles der echten BundID ist nicht öffentlich dokumentiert.
  `urn:oid:2.5.4.18` (X.520 `postOfficeBox`) erscheint als Beispiel in der README der BA-Keycloak-Erweiterung
  (https://github.com/ba-itsys/keycloak-extension-bundid) und wird hier verwendet.
- Die Abbildung der BundID-Stufe „normal“ (`STORK-QAA-Level-1`) auf `http://eidas.europa.eu/LoA/low` ist eine
  Konvention dieses Forks; eine exakte eIDAS-Entsprechung gibt es nicht.

## Beispiel (Docker)

```
docker build -t bpsim-bundid-simulator:dev .
docker run -p 8090:8080 -v bpsim-simulator-data:/app/data \
  -e APP_SAML_IDPENTITYID=http://localhost:8090/saml/metadata -e APP_SAML_SSOURL=http://localhost:8090/saml \
  -e APP_SAML_LOAFORMAT=eidas -e APP_SAML_POSTKORBHANDLE_ENABLED=true \
  -e APP_SAML_SIGNING_ENABLED=true -e APP_SAML_SIGNING_KEYSTORE=/app/data/idp-signing.p12 -e APP_SSO_ENABLED=true \
  -e APP_SAML_SIGNING_PASSWORD=<passwort> bpsim-bundid-simulator:dev
```

Unter Git-Bash (Windows) `MSYS_NO_PATHCONV=1` voranstellen, sonst werden `/app/...`-Pfade umgeschrieben.
