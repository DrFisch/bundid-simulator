/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuswahlSeiteTest {

    @Test
    void namesTheLevelEachIdentificationReaches() {
        Map<String, String> normal = AuswahlSeite.niveauJeMittel(AuthLevelTools.createIdentificationWithList(AuthLevelTools.STORK_1));
        assertThat(normal).containsEntry(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG, "normal")
                .containsEntry(AuthLevelTools.IDENTIFICATION_ELSTER, "substanziell")
                .containsEntry(AuthLevelTools.IDENTIFICATION_EID, "hoch")
                .containsEntry(AuthLevelTools.IDENTIFICATION_EIDAS, "je nach eIDAS-Niveau");

        // Step-up auf substanziell: Benutzername steht nicht mehr zur Wahl
        Map<String, String> substanziell = AuswahlSeite.niveauJeMittel(AuthLevelTools.createIdentificationWithList(AuthLevelTools.STORK_3));
        assertThat(substanziell).doesNotContainKey(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG)
                .containsKeys(AuthLevelTools.IDENTIFICATION_ELSTER, AuthLevelTools.IDENTIFICATION_SMARTEID);

        assertThat(AuswahlSeite.niveauName(1)).isEqualTo("normal");
        assertThat(AuswahlSeite.niveauName(3)).isEqualTo("substanziell");
        assertThat(AuswahlSeite.niveauName(4)).isEqualTo("hoch");
    }
}
