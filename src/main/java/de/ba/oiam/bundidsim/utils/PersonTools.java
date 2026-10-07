/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 * Inhalt aus SelectViewController.addDataToUser/changeMailAddress des Originals übernommen (unverändert), damit auch
 * die Anmeldung am Postfach eine Person genauso aufbaut.
 */
package de.ba.oiam.bundidsim.utils;

import de.ba.oiam.bundidsim.model.BundIdUser;
import org.springframework.util.StringUtils;

/** Vervollständigt eine ausgewählte Testperson um Identifizierungsmittel, Niveau und fachlichen Kontext. */
public final class PersonTools {

    private PersonTools() {
    }

    public static BundIdUser applySelection(BundIdUser user, String identification, String eidasCountry,
                                            String eidasLoa, String domainContext) {
        // User-Daten vervollständigen
        user.setAssertionProvedBy(identification); // Identifizierungsmittel
        user.setEidCitizenQaaLevel(AuthLevelTools.getAuthnLevelByIdentificationMethod(identification));
        user.setVersion("2021.7.1");
        if (AuthLevelTools.IDENTIFICATION_EIDAS.equalsIgnoreCase(identification)) {
            // Speziell für eIDAS-Identifikation
            user.setEidasIssuingCountry(eidasCountry);
            user.setEidCitizenQaaLevel(AuthLevelTools.getAuthnLevelByLoa(eidasLoa));
        }

        if (StringUtils.hasText(domainContext)) {
            // Suffix an die bpk2 anfügen
            user.setBpk2(user.getBpk2() + domainContext);
            user.setMail(changeMailAddress(user.getMail(), domainContext));
        }
        return user;
    }

    /**
     * Manipulation einer Email-Adresse: Gegeben: email: "test@online.de", context: "-team" Ergebnis:
     * "test-team@online.de"
     */
    static String changeMailAddress(String email, String context) {
        String[] emailParts = email.split("@");
        return emailParts[0] + context + "@" + emailParts[1];
    }
}
