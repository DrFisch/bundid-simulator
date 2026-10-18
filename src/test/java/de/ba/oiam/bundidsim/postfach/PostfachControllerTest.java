/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostfachControllerTest {

    @Test
    void returnTargetStaysInsideThePostfach() {
        assertThat(PostfachController.sicheresZiel("/postfach")).isEqualTo("/postfach");
        assertThat(PostfachController.sicheresZiel("/postfach/nachricht/0f5c-4d1e")).isEqualTo("/postfach/nachricht/0f5c-4d1e");
        assertThat(PostfachController.sicheresZiel("https://boese.example/")).isEqualTo("/postfach");
        assertThat(PostfachController.sicheresZiel("//boese.example/postfach")).isEqualTo("/postfach");
        assertThat(PostfachController.sicheresZiel("/postfach/../saml")).isEqualTo("/postfach");
        assertThat(PostfachController.sicheresZiel(null)).isEqualTo("/postfach");
    }

    @Test
    void offersNoEidasBecauseTheAccountLoginHasNoCountryOrLoaFields() {
        assertThat(schluessel(PostfachController.mittel(AuthLevelTools.STORK_1)))
                .contains(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG, AuthLevelTools.IDENTIFICATION_EID)
                .doesNotContain(AuthLevelTools.IDENTIFICATION_EIDAS);
        assertThat(schluessel(PostfachController.mittel(AuthLevelTools.STORK_3)))
                .doesNotContain(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG, AuthLevelTools.IDENTIFICATION_EIDAS);
    }

    private static List<String> schluessel(String[][] mittel) {
        return Arrays.stream(mittel).map(item -> item[0]).toList();
    }

    @Test
    void mapsLevels() {
        assertThat(PostfachController.storkLevel(null)).isEqualTo(AuthLevelTools.STORK_1);
        assertThat(PostfachController.storkLevel(3)).isEqualTo(AuthLevelTools.STORK_3);
        assertThat(PostfachController.storkLevel(4)).isEqualTo(AuthLevelTools.STORK_4);
        assertThat(PostfachController.levelName(3)).isEqualTo("substanziell");
        assertThat(PostfachController.levelName(1)).isEqualTo("normal");
    }
}
