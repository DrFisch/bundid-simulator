/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.services;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.model.SamlRequestValues;
import de.ba.oiam.bundidsim.model.SamlResponseValues;
import de.ba.oiam.bundidsim.model.Status;
import de.ba.oiam.bundidsim.utils.ObjectStringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Erzeugt die SAML-Response und die Seite, die sie per HTTP-POST an den Assertion Consumer Service schickt.
 * Vorher als gleichlautende private Methode in SelectViewController und EditViewController; zusammengeführt, weil
 * auch die Anmeldesitzung (SSO) direkt aus SamlController antwortet.
 */
@Service
@Slf4j
public class AuthResponseService {

    @Autowired
    private SamlResponseGeneratorService samlResponseGeneratorService;

    public String prepareSamlResponse(Model model, String samlRequest, Status samlStatus, BundIdUser user,
                                      String authnLevel) {

        SamlRequestValues requestParams = ObjectStringConverter.decodeAndDeserialize(samlRequest, SamlRequestValues.class);

        SamlResponseValues responseParams =
                SamlResponseValues.builder()
                        .id(UUID.randomUUID().toString())
                        .assertionId(UUID.randomUUID().toString())
                        .requestId(requestParams.getId())
                        .spEntityId(requestParams.getIssuer())
                        .idpId(requestParams.getIssuer())
                        .created(Instant.now().truncatedTo(ChronoUnit.SECONDS))
                        .ascUrl(requestParams.getAscUrl())
                        .nameId(user != null ? user.getBpk2() : null)
                        .userAuthnLevel(authnLevel)
                        .build();
        log.debug("ResponseParams; [{}]", responseParams);

        String samlResponseAsString =
                samlResponseGeneratorService.generateSamlResponse(
                        samlStatus, user, responseParams);

        model.addAttribute("saml_response", samlResponseAsString);
        model.addAttribute("relay_state", requestParams.getRelayState());
        model.addAttribute("post_url", requestParams.getAscUrl());
        return "auth_response";
    }
}
