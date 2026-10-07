/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.utils;

import de.ba.oiam.bundidsim.model.BundIdUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostkorbHandleToolsTest {

    @Test
    void derivesStableHandleFromBpk2IncludingContext() {
        // Wert aus der Analyse (doku/02-konzept/postkorb.md): BUNDIDSIM-U02 mit fachlichem Kontext "-bp"
        assertThat(PostkorbHandleTools.deriveFromBpk2("BUNDIDSIM-U02-bp"))
                .isEqualTo("11b2dc8f-3831-3b26-afde-aa0be42bd79b");
        assertThat(PostkorbHandleTools.deriveFromBpk2("BUNDIDSIM-U02-anders"))
                .isNotEqualTo("11b2dc8f-3831-3b26-afde-aa0be42bd79b");
    }

    @Test
    void prefersConfiguredHandle() {
        BundIdUser user = new BundIdUser();
        user.setBpk2("BUNDIDSIM-U02-bp");
        user.setPostkorbHandle("d0996589-0000-4000-8000-000000000001");
        assertThat(PostkorbHandleTools.handleOf(user)).isEqualTo("d0996589-0000-4000-8000-000000000001");
        assertThat(PostkorbHandleTools.handleOf(null)).isNull();
    }

    @Test
    void appliesSelectionLikeTheSelectView() {
        BundIdUser user = new BundIdUser();
        user.setBpk2("BUNDIDSIM-U02");
        user.setMail("m-schmidt@example.com");

        PersonTools.applySelection(user, AuthLevelTools.IDENTIFICATION_ELSTER, null, null, "-bp");

        assertThat(user.getBpk2()).isEqualTo("BUNDIDSIM-U02-bp");
        assertThat(user.getMail()).isEqualTo("m-schmidt-bp@example.com");
        assertThat(user.getEidCitizenQaaLevel()).isEqualTo(AuthLevelTools.STORK_3);
        assertThat(user.getAssertionProvedBy()).isEqualTo("Elster");
    }
}
