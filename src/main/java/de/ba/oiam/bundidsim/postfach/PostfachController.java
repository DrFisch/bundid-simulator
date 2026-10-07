/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import de.ba.oiam.bundidsim.model.BundIdUser;
import de.ba.oiam.bundidsim.services.SsoSessionService;
import de.ba.oiam.bundidsim.services.UserDefinitionService;
import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import de.ba.oiam.bundidsim.utils.PersonTools;
import de.ba.oiam.bundidsim.utils.PostkorbHandleTools;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * „Mein BundID-Postfach“ im Simulator: Wie bei der echten BundID ist das Postfach Teil des BundID-Kontos. Angemeldet
 * ist, wer eine Anmeldesitzung (SSO) beim Simulator hat – nach der Anmeldung an einem Dienst (z. B. dem BürgerPortal)
 * oder direkt hier über die Personenauswahl. Angezeigt werden die Nachrichten an das Postkorb-Handle dieser Person
 * (bPK2 inkl. fachlichem Kontext). Das Handle kommt nur aus der Sitzung, nie aus der URL.
 * Nachrichten mit höherem Vertrauensniveau als die Sitzung sind erst nach erneuter Anmeldung lesbar (Step-up).
 */
@Controller
@Slf4j
public class PostfachController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter ANZEIGE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Autowired
    private SsoSessionService ssoSessionService;

    @Autowired
    private PostfachStore store;

    @Autowired
    private UserDefinitionService userService;

    @org.springframework.beans.factory.annotation.Value("${app.postfach.enabled:false}")
    private boolean enabled;

    @org.springframework.beans.factory.annotation.Value("${app.basepath:}")
    private String basePath;

    @org.springframework.beans.factory.annotation.Value("${app.postfach.portal-url:}")
    private String portalUrl;

    @org.springframework.beans.factory.annotation.Value("${app.postfach.portal-name:}")
    private String portalName;

    /** Zeile der Nachrichtenliste (Klasse mit Gettern, damit Thymeleaf/SpEL die Werte lesen kann). */
    @Value
    public static class Eintrag {
        String id;
        String title;
        String sender;
        String service;
        String received;
        boolean unread;
        int level;
        String levelName;
        boolean requiresHigherLevel;
    }

    /** Für alle Seiten: Link „Zurück zu …“ (optional) und Name/Niveau der Sitzung. */
    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        if (StringUtils.hasText(portalUrl)) {
            model.addAttribute("portalUrl", portalUrl);
            model.addAttribute("portalName", StringUtils.hasText(portalName) ? portalName : "Dienst");
        }
        ssoSessionService.find(request).ifPresent(s -> {
            model.addAttribute("sessionName", s.displayName());
            model.addAttribute("sessionLevel", levelName(AuthLevelTools.rank(s.level())));
        });
    }

    @GetMapping(path = {"/postfach", "/postfach/"})
    public String liste(Model model, HttpServletRequest request) {
        ensureEnabled();
        Optional<SsoSessionService.SsoState> sso = ssoSessionService.find(request);
        if (sso.isEmpty()) {
            return "redirect:" + basePath + "/postfach/anmelden";
        }
        int sessionLevel = AuthLevelTools.rank(sso.get().level());
        List<Eintrag> eintraege = store.list(handle(sso.get())).stream()
                .map(m -> new Eintrag(m.getId(), m.getTitle(), m.getSender(), m.getService(), anzeige(m.getCreatedUtc()),
                        m.getReadUtc() == null, m.getStorkQaaLevel(), levelName(m.getStorkQaaLevel()),
                        m.getStorkQaaLevel() > sessionLevel))
                .toList();
        model.addAttribute("eintraege", eintraege);
        model.addAttribute("ungelesen", eintraege.stream().filter(Eintrag::isUnread).count());
        return "postfach/liste";
    }

    @GetMapping(path = "/postfach/nachricht/{id}")
    public String nachricht(@PathVariable String id, Model model, HttpServletRequest request) {
        ensureEnabled();
        Optional<SsoSessionService.SsoState> sso = ssoSessionService.find(request);
        if (sso.isEmpty()) {
            return "redirect:" + basePath + "/postfach/anmelden";
        }
        String handle = handle(sso.get());
        // Nur im eigenen Postfach suchen: fremde IDs liefern 404 und verraten nicht, ob es sie gibt.
        PostfachMessage message = store.find(handle, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int sessionLevel = AuthLevelTools.rank(sso.get().level());
        boolean readable = message.getStorkQaaLevel() <= sessionLevel;
        // Als gelesen gilt eine Nachricht erst, wenn ihr Inhalt angezeigt wurde.
        if (readable && message.getReadUtc() == null) {
            store.markRead(handle, id);
            message.setReadUtc(Instant.now());
        }
        model.addAttribute("nachricht", message);
        model.addAttribute("empfangen", anzeige(message.getCreatedUtc()));
        model.addAttribute("gelesen", message.getReadUtc() == null ? null : anzeige(message.getReadUtc()));
        model.addAttribute("lesbar", readable);
        model.addAttribute("levelName", levelName(message.getStorkQaaLevel()));
        return "postfach/nachricht";
    }

    /** Anmeldung am BundID-Konto (Personenauswahl wie im Simulator); niveau/ziel für den Step-up einer Nachricht. */
    @GetMapping(path = "/postfach/anmelden")
    public String anmelden(@RequestParam(required = false) Integer niveau, @RequestParam(required = false) String ziel,
                           @RequestParam(required = false) String abgemeldet, Model model, HttpServletRequest request) {
        ensureEnabled();
        String minLevel = storkLevel(niveau);
        String[][] identWithList = AuthLevelTools.createIdentificationWithList(minLevel);
        List<BundIdUser> users = userService.getUserList();
        String userId = users.getFirst().getId();
        String domainContext = "";
        String identification = AuthLevelTools.IDENTIFICATION_EID;
        // Bestehende Sitzung (Step-up): Person und fachlichen Kontext vorbelegen
        Optional<SsoSessionService.SsoState> sso = ssoSessionService.find(request);
        if (sso.isPresent()) {
            userId = sso.get().userId();
            domainContext = sso.get().domainContext() == null ? "" : sso.get().domainContext();
            String current = sso.get().identification();
            if (Arrays.stream(identWithList).anyMatch(item -> item[0].equals(current))) {
                identification = current;
            }
        }
        model.addAttribute("userlist", users);
        model.addAttribute("identWithList", identWithList);
        model.addAttribute("userId", userId);
        model.addAttribute("domainContext", domainContext);
        model.addAttribute("identification", identification);
        model.addAttribute("niveau", niveau);
        model.addAttribute("niveauName", niveau == null ? null : levelName(niveau));
        model.addAttribute("ziel", sicheresZiel(ziel));
        model.addAttribute("abgemeldet", abgemeldet != null);
        model.addAttribute("ssoAus", !ssoSessionService.isEnabled());
        return "postfach/anmelden";
    }

    @PostMapping(path = "/postfach/anmelden")
    public String anmeldenAbsenden(@RequestParam String userId, @RequestParam String identifikationWith,
                                   @RequestParam(required = false) String domainContext,
                                   @RequestParam(required = false) Integer niveau,
                                   @RequestParam(required = false) String ziel, HttpServletRequest request) {
        ensureEnabled();
        BundIdUser user = userService.getUserById(userId);
        String[][] erlaubt = AuthLevelTools.createIdentificationWithList(storkLevel(niveau));
        if (user == null || Arrays.stream(erlaubt).noneMatch(item -> item[0].equals(identifikationWith))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Person oder Identifizierungsmittel ungültig");
        }
        String kontext = domainContext == null ? "" : domainContext.trim();
        PersonTools.applySelection(user, identifikationWith, null, null, kontext);
        ssoSessionService.remember(request, user, userId, kontext);
        log.debug("Postfach: Anmeldung als [{}] mit [{}]", userId, identifikationWith);
        return "redirect:" + basePath + sicheresZiel(ziel);
    }

    /** Abmelden beendet die Anmeldesitzung der BundID (wie das Abmelden im BundID-Konto). */
    @PostMapping(path = "/postfach/abmelden")
    public String abmelden(HttpServletRequest request) {
        ensureEnabled();
        ssoSessionService.end(request);
        return "redirect:" + basePath + "/postfach/anmelden?abgemeldet";
    }

    // ---------------------------------------------------------------------------------------------------------------

    private void ensureEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private String handle(SsoSessionService.SsoState state) {
        return PostkorbHandleTools.handleOf(ssoSessionService.user(state));
    }

    /** Rücksprung nur innerhalb des Postfachs (kein offener Redirect). */
    static String sicheresZiel(String ziel) {
        return ziel != null && ziel.matches("/postfach(/nachricht/[A-Za-z0-9-]{1,64})?") ? ziel : "/postfach";
    }

    static String storkLevel(Integer niveau) {
        if (niveau == null || niveau <= 1) {
            return AuthLevelTools.STORK_1;
        }
        return niveau == 3 ? AuthLevelTools.STORK_3 : AuthLevelTools.STORK_4;
    }

    static String levelName(int rank) {
        return switch (rank) {
            case 4 -> "hoch";
            case 3 -> "substanziell";
            default -> "normal";
        };
    }

    private static String anzeige(Instant instant) {
        return instant == null ? "" : ANZEIGE.format(instant.atZone(ZONE));
    }
}
