/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.utils;

import java.security.SecureRandom;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Schnellanmeldung mit der fiktiven Person John Doe (Hof, Bayern): ein Klick und eine Bestätigung statt Person,
 * Identifizierungsmittel und fachlichen Kontext von Hand zu wählen.
 *
 * <ul>
 *   <li>Identifizierungsmittel: das erste, das für das geforderte Niveau zugelassen ist (normal: Benutzername,
 *       substanziell: Elster, hoch: Online-Ausweis) – so bleibt ein Step-up des Dienstes vorführbar.</li>
 *   <li>Fachlicher Kontext je Gerät: {@code -jd} plus eine zufällige Kennung aus dem Cookie {@value #COOKIE}. Auf
 *       einem Gerät bleibt es dasselbe Demo-Konto (gleiche bPK2), verschiedene Geräte teilen sich keins.</li>
 * </ul>
 */
public final class SchnellAnmeldungTools {

    /** Id der Person in users.yml. */
    public static final String USER_ID = "U20";

    /** Cookie mit der Kennung des Geräts (ein Jahr, nur für den Simulator). */
    public static final String COOKIE = "BUNDIDSIM_SCHNELL";

    private static final Pattern KENNUNG = Pattern.compile("[a-z0-9]{8}");
    private static final String ZEICHEN = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom ZUFALL = new SecureRandom();

    private SchnellAnmeldungTools() {
    }

    /** Erstes für das geforderte Niveau zugelassenes Identifizierungsmittel: {Schlüssel, Anzeigename}. */
    public static String[] identifikation(String reqAuthnLevel) {
        return AuthLevelTools.createIdentificationWithList(reqAuthnLevel)[0];
    }

    /** Vorhandene Kennung behalten, wenn sie gültig ist; sonst eine neue erzeugen. */
    public static String kennung(String cookieValue, Supplier<String> neu) {
        return cookieValue != null && KENNUNG.matcher(cookieValue).matches() ? cookieValue : neu.get();
    }

    /** Neue zufällige Kennung (8 Zeichen a–z, 0–9). */
    public static String neueKennung() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ZEICHEN.charAt(ZUFALL.nextInt(ZEICHEN.length())));
        }
        return sb.toString();
    }

    /** Fachlicher Kontext für bPK2 und E-Mail-Adresse, z. B. {@code -jd4k2m9x7q}. */
    public static String kontext(String kennung) {
        return "-jd" + kennung;
    }

    /** Vertrauensniveau zum Identifizierungsmittel, wie es die BundID nennt. */
    public static String niveauName(String identification) {
        String level = AuthLevelTools.getAuthnLevelByIdentificationMethod(identification);
        return switch (level) {
            case AuthLevelTools.STORK_4 -> "hoch";
            case AuthLevelTools.STORK_3 -> "substanziell";
            default -> "normal";
        };
    }
}
