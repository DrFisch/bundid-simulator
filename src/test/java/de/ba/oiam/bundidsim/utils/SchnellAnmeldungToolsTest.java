/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.utils;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.services.UserDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {UserDefinitionService.class})
class SchnellAnmeldungToolsTest {

    @Autowired
    private UserDefinitionService userService;

    @Test
    void choosesWeakestIdentificationAllowedForTheRequestedLevel() {
        // ohne Vorgabe bzw. "normal": Benutzername – ein späterer Step-up bleibt vorführbar
        assertThat(SchnellAnmeldungTools.identifikation(null)[0]).isEqualTo(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG);
        assertThat(SchnellAnmeldungTools.identifikation(AuthLevelTools.STORK_1)[0]).isEqualTo(AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG);
        assertThat(SchnellAnmeldungTools.identifikation(AuthLevelTools.STORK_3)[0]).isEqualTo(AuthLevelTools.IDENTIFICATION_ELSTER);
        assertThat(SchnellAnmeldungTools.identifikation(AuthLevelTools.STORK_4)[0]).isEqualTo(AuthLevelTools.IDENTIFICATION_EID);
        assertThat(SchnellAnmeldungTools.niveauName(AuthLevelTools.IDENTIFICATION_ELSTER)).isEqualTo("substanziell");
    }

    @Test
    void keepsValidDeviceIdAndReplacesInvalidOnes() {
        assertThat(SchnellAnmeldungTools.kennung("abc123xy", () -> "neu00000")).isEqualTo("abc123xy");
        assertThat(SchnellAnmeldungTools.kennung(null, () -> "neu00000")).isEqualTo("neu00000");
        assertThat(SchnellAnmeldungTools.kennung("<script>", () -> "neu00000")).isEqualTo("neu00000");
        assertThat(SchnellAnmeldungTools.neueKennung()).matches("[a-z0-9]{8}");
        assertThat(SchnellAnmeldungTools.neueKennung()).isNotEqualTo(SchnellAnmeldungTools.neueKennung());
    }

    @Test
    void johnDoeFromBavariaGetsAnOwnAccountPerDevice() {
        BundIdUser user = userService.getUserById(SchnellAnmeldungTools.USER_ID);
        assertThat(user.getGivenname()).isEqualTo("John");
        assertThat(user.getSurname()).isEqualTo("Doe");
        assertThat(user.getLocalityName()).isEqualTo("Hof");

        String kontext = SchnellAnmeldungTools.kontext("abc123xy");
        PersonTools.applySelection(user, AuthLevelTools.IDENTIFICATION_BENUTZERKENNUNG, null, null, kontext);
        assertThat(user.getBpk2()).isEqualTo("BUNDIDSIM-JD-jdabc123xy");
        assertThat(user.getMail()).isEqualTo("john.doe-jdabc123xy@example.com");
        assertThat(user.getEidCitizenQaaLevel()).isEqualTo(AuthLevelTools.STORK_1);
    }
}
