/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.services;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.utils.ObjectStringConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Optional;

/**
 * Anmeldesitzung (Single Sign-on) des simulierten Identity Providers.
 *
 * Die echte BundID hält nach der Identifizierung eine Sitzung: Ein weiterer Dienst – etwa das Postfach der BundID –
 * erhält dann ohne erneute Identifizierung eine Antwort. Der Original-Simulator kennt keine Sitzung und zeigt bei
 * jeder Anfrage die Personenauswahl. Mit app.sso.enabled=true merkt sich der Simulator nach einer erfolgreichen
 * Auswahl die Person (als Kopie, inklusive fachlichem Kontext bzw. bearbeiteter Daten) in der Server-Sitzung.
 * Standard: aus (Originalverhalten).
 */
@Service
@Slf4j
public class SsoSessionService {

    private static final String SESSION_ATTRIBUTE = "bpsim.sso";

    @Value("${app.sso.enabled:false}")
    private boolean enabled;

    /** Was sich die Sitzung merkt: Auswahl für die Vorbelegung und die vollständige Person für Antworten. */
    public record SsoState(String userId, String domainContext, String identification, String displayName,
                           String level, String serializedUser) implements Serializable {
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Nach einer erfolgreichen Anmeldung aufrufen, bevor die Response erzeugt wird (die Erzeugung ergänzt den
     * Datensatz). Die Sitzungs-ID wird erneuert, damit eine vorher bekannte ID nicht übernommen werden kann.
     */
    public void remember(HttpServletRequest request, BundIdUser user, String userId, String domainContext) {
        if (!enabled || user == null) {
            return;
        }
        request.getSession(true);
        request.changeSessionId();
        String displayName = (user.getGivenname() + " " + user.getSurname()).trim();
        request.getSession().setAttribute(SESSION_ATTRIBUTE, new SsoState(userId, domainContext,
                user.getAssertionProvedBy(), displayName, user.getEidCitizenQaaLevel(),
                ObjectStringConverter.serializeAndEncode(user)));
        log.debug("SSO-Sitzung für [{}] mit Niveau [{}]", displayName, user.getEidCitizenQaaLevel());
    }

    /** Bestehende Sitzung, ohne eine neue anzulegen. */
    public Optional<SsoState> find(HttpServletRequest request) {
        if (!enabled) {
            return Optional.empty();
        }
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_ATTRIBUTE) instanceof SsoState state)) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    /** Frische Kopie der gemerkten Person für eine neue Response. */
    public BundIdUser user(SsoState state) {
        return ObjectStringConverter.decodeAndDeserialize(state.serializedUser(), BundIdUser.class);
    }

    public void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
