/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.utils;

import de.ba.oiam.bundidsim.model.BundIdUser;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Postkorb-Handle einer Person: Wert aus users.yml oder deterministisch aus der bPK2 (inkl. fachlichem Kontext)
 * abgeleitete UUID (Version 3). So erhält dieselbe Person immer dasselbe Handle – in der SAML-Assertion wie im
 * Postfach des Simulators.
 */
public final class PostkorbHandleTools {

    private PostkorbHandleTools() {
    }

    /** Handle der Person oder null, wenn weder ein Wert noch eine bPK2 vorliegt. */
    public static String handleOf(BundIdUser user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getPostkorbHandle())) {
            return user.getPostkorbHandle();
        }
        return StringUtils.hasText(user.getBpk2()) ? deriveFromBpk2(user.getBpk2()) : null;
    }

    public static String deriveFromBpk2(String bpk2) {
        return UUID.nameUUIDFromBytes(("bundid-simulator-postkorb:" + bpk2).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
