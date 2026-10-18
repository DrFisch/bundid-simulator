/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.controller;

import de.ba.oiam.bundidsim.model.SamlRequestValues;
import de.ba.oiam.bundidsim.services.UserDefinitionService;
import de.ba.oiam.bundidsim.utils.AuthLevelTools;
import de.ba.oiam.bundidsim.utils.SchnellAnmeldungTools;
import org.springframework.ui.Model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Daten für die Seiten mit Personenauswahl (Auswahlseite, Bearbeiten, Anmeldung am Postfach) an einer Stelle:
 * Testpersonen, Identifizierungsmittel mit dem Niveau, das sie erreichen, Karte „Schnellanmeldung“ (John Doe) und auf
 * der Auswahlseite, was der Dienst im AuthnRequest verlangt hat (Issuer, Niveau, ForceAuthn). Vorher verteilt auf
 * SamlController und SelectViewController – der Fehlerfall der Auswahl zeigte deshalb weniger als der erste Aufruf.
 */
public final class AuswahlSeite {

    private AuswahlSeite() {
    }

    /** Alles für select_view.html (erster Aufruf, Fehlerfall, Rückweg aus „Angaben ändern“). */
    public static void auswahl(Model model, UserDefinitionService users, SamlRequestValues anfrage) {
        model.addAttribute("userlist", users.getUserList());
        mittel(model, anfrage.getReqAuthnLevel());
        schnellanmeldung(model, users, anfrage.getReqAuthnLevel());
        model.addAttribute("anfrage", anfrage);
        model.addAttribute("anfrageNiveau", niveauName(AuthLevelTools.rank(anfrage.getReqAuthnLevel())));
    }

    /** Für das geforderte Niveau zugelassene Identifizierungsmittel und je Mittel sein Niveau (für die Anzeige). */
    public static String[][] mittel(Model model, String reqAuthnLevel) {
        String[][] list = AuthLevelTools.createIdentificationWithList(reqAuthnLevel);
        model.addAttribute("identWithList", list);
        model.addAttribute("mittelNiveau", niveauJeMittel(list));
        return list;
    }

    /** Daten für die Karte „Schnellanmeldung“ (John Doe, Mittel passend zum geforderten Niveau). */
    public static void schnellanmeldung(Model model, UserDefinitionService users, String reqAuthnLevel) {
        String[] identification = SchnellAnmeldungTools.identifikation(reqAuthnLevel);
        model.addAttribute("schnellPerson", users.getUserById(SchnellAnmeldungTools.USER_ID));
        model.addAttribute("schnellIdent", identification[1]);
        model.addAttribute("schnellNiveau", SchnellAnmeldungTools.niveauName(identification[0]));
    }

    /** Identifizierungsmittel → Niveau, wie die BundID es nennt; bei eIDAS hängt es vom gewählten LoA ab. */
    static Map<String, String> niveauJeMittel(String[][] list) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] item : list) {
            map.put(item[0], AuthLevelTools.IDENTIFICATION_EIDAS.equals(item[0])
                    ? "je nach eIDAS-Niveau" : SchnellAnmeldungTools.niveauName(item[0]));
        }
        return map;
    }

    /** Rang eines Niveaus (1, 3, 4) so, wie die BundID es nennt. */
    public static String niveauName(int rank) {
        return switch (rank) {
            case 4 -> "hoch";
            case 3 -> "substanziell";
            default -> "normal";
        };
    }
}
