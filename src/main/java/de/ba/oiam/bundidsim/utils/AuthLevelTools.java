/*
 * Copyright 2025. IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ba.oiam.bundidsim.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utils für den Umgang mit Vertrauensniveaus
 *
 * Geändert 2026 (Fork bpsim/standardkonform, siehe FORK.md): Normalisierung des angeforderten Niveaus und
 * Ausgabe als absolute eIDAS-LoA-URI ergänzt.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class AuthLevelTools {

    public static final String STORK_1 = "STORK-QAA-Level-1";   // Vertrauensniveau: NIEDRIG
    public static final String STORK_3 = "STORK-QAA-Level-3";   // Vertrauensniveau: SUBSTANTIELL
    public static final String STORK_4 = "STORK-QAA-Level-4";   // Vertrauensniveau HOCH

    public static final String LOA_HIGH = "HIGH";
    public static final String LOA_SUBSTANTIAL = "SUBSTANTIAL";
    public static final String LOA_LOW = "LOW";

    public static final String IDENTIFICATION_BENUTZERKENNUNG = "Benutzername";
    public static final String IDENTIFICATION_EID = "eID";
    public static final String IDENTIFICATION_SMARTEID = "Smart-eID";
    public static final String IDENTIFICATION_ELSTER = "Elster";
    public static final String IDENTIFICATION_EIDAS = "eIDAS";
    public static final String IDENTIFICATION_FINK = "FINK";

    /**
     * liefert eine Liste mit key/Value für die UI-Darstellung des Identifizierungsmittels in
     * Abhängigkeit des angeforderten Vertrauensniveaus.
     *
     * @param minLevel
     * @return
     */
    public static String[][] createIdentificationWithList(String minLevel) {
        List<String[]> resultList = new ArrayList<>();

        if (!StringUtils.hasText(minLevel) || STORK_1.equalsIgnoreCase(minLevel)) {
            // Kein Vertrauensniveau angegeben
            resultList.add(new String[]{IDENTIFICATION_BENUTZERKENNUNG, "Benutzername"});
            resultList.add(new String[]{IDENTIFICATION_ELSTER, "Elster"});
            resultList.add(new String[]{IDENTIFICATION_SMARTEID, "Smart eID"});
        } else if (STORK_3.equalsIgnoreCase(minLevel)) {
            resultList.add(new String[]{IDENTIFICATION_ELSTER, "Elster"});
            resultList.add(new String[]{IDENTIFICATION_SMARTEID, "Smart eID"});
        }

        resultList.add(new String[]{IDENTIFICATION_EID, "EID (Ausweis)"});
        resultList.add(new String[]{IDENTIFICATION_EIDAS, "eIDAS"});
        resultList.add(new String[]{IDENTIFICATION_FINK, "FINK"});

        return resultList.toArray(new String[resultList.size()][2]);
    }

    public static String getAuthnLevelByIdentificationMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return STORK_1;
        }

        switch (method) {
            case IDENTIFICATION_EIDAS, IDENTIFICATION_EID, IDENTIFICATION_FINK -> {
                return STORK_4;
            }
            case IDENTIFICATION_SMARTEID, IDENTIFICATION_ELSTER -> {
                return STORK_3;
            }
            case IDENTIFICATION_BENUTZERKENNUNG -> {
                return STORK_1;
            }
        }
        return STORK_1;
    }

    public static String getAuthnLevelByLoa(String loa) {
        return switch (loa) {
            case LOA_HIGH -> STORK_4;
            case LOA_SUBSTANTIAL -> STORK_3;
            default -> STORK_1;
        };
    }

    // Fork bpsim/standardkonform (siehe FORK.md) ************************************************************

    public static final String LOA_FORMAT_STORK = "stork";   // Original: "STORK-QAA-Level-n" (keine absolute URI)
    public static final String LOA_FORMAT_EIDAS = "eidas";   // absolute eIDAS-LoA-URIs (SAML-Core 1.3.2)

    public static final String EIDAS_LOA_LOW = "http://eidas.europa.eu/LoA/low";
    public static final String EIDAS_LOA_SUBSTANTIAL = "http://eidas.europa.eu/LoA/substantial";
    public static final String EIDAS_LOA_HIGH = "http://eidas.europa.eu/LoA/high";

    /**
     * normalisiert das angeforderte Niveau aus dem AuthnRequest: getrimmt; eIDAS-LoA-URIs werden auf die
     * STORK-Bezeichner abgebildet. Andere Werte bleiben unverändert (Originalverhalten).
     */
    /**
     * Fork: Rang eines (normalisierten) Niveaus für Vergleiche: STORK-QAA-Level-1 → 1, -3 → 3, -4 → 4,
     * fehlend → 1 (Mindestniveau), unbekannt → 4 (wie createIdentificationWithList: nur Mittel mit hohem Niveau).
     */
    public static int rank(String storkLevel) {
        if (!StringUtils.hasText(storkLevel)) {
            return 1;
        }
        return switch (normalizeRequestedLevel(storkLevel)) {
            case STORK_1 -> 1;
            case STORK_3 -> 3;
            default -> 4;
        };
    }

    public static String normalizeRequestedLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return level;
        }
        String trimmed = level.trim();
        return switch (trimmed) {
            case EIDAS_LOA_HIGH -> STORK_4;
            case EIDAS_LOA_SUBSTANTIAL -> STORK_3;
            case EIDAS_LOA_LOW -> STORK_1;
            default -> trimmed;
        };
    }

    /**
     * liefert den Wert für das AuthnContextClassRef der Response im gewünschten Format.
     * Format "eidas": STORK-QAA-Level-4 → high, -3 → substantial, -1 → low (Konvention dieses Forks: die
     * BundID-Stufe "normal/Basis" hat keine exakte eIDAS-Entsprechung).
     */
    public static String toAuthnContextClassRef(String storkLevel, String format) {
        if (!LOA_FORMAT_EIDAS.equalsIgnoreCase(format) || !StringUtils.hasText(storkLevel)) {
            return storkLevel;
        }
        return switch (storkLevel) {
            case STORK_4 -> EIDAS_LOA_HIGH;
            case STORK_3 -> EIDAS_LOA_SUBSTANTIAL;
            default -> EIDAS_LOA_LOW;
        };
    }

}
