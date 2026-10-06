/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.services.SsoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * GET /sso zeigt die bestehende Anmeldesitzung, GET /sso/logout beendet sie. Danach zeigt der Simulator bei der
 * nächsten Anfrage wieder die Personenauswahl.
 */
@RestController
public class SsoController {

    @Autowired
    private SsoSessionService ssoSessionService;

    @GetMapping(path = "/sso", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String status(HttpServletRequest request) {
        String text = ssoSessionService.find(request)
                .map(s -> "Angemeldet als <b>" + HtmlUtils.htmlEscape(s.displayName()) + "</b> (Identifizierung: "
                        + HtmlUtils.htmlEscape(s.identification()) + ", Niveau " + HtmlUtils.htmlEscape(s.level())
                        + "). <a href=\"sso/logout\">Sitzung beenden</a>")
                .orElse(ssoSessionService.isEnabled() ? "Keine Anmeldesitzung." : "Anmeldesitzung (SSO) ist abgeschaltet.");
        return page(text);
    }

    @GetMapping(path = "/sso/logout", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String logout(HttpServletRequest request) {
        ssoSessionService.end(request);
        return page("Die Anmeldesitzung beim BundID-Simulator ist beendet. Die nächste Anmeldung zeigt wieder die "
                + "Personenauswahl.");
    }

    private static String page(String body) {
        return "<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>BundID-Simulator – Sitzung</title>"
                + "</head><body style=\"font-family:sans-serif;margin:2rem\"><h1>BundID-Simulator</h1><p>" + body
                + "</p></body></html>";
    }
}
