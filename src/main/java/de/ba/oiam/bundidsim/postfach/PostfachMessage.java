/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Nachricht im Postfach der simulierten BundID. Die Felder folgen „CreateMessage“ des Zentralen Bürgerpostfachs (ZBP),
 * soweit öffentlich dokumentiert (FIT-Connect, https://docs.fitko.de/fit-connect/docs/zbp/) – wie im eigenständigen
 * Postfach-Dienst des BürgerPortals. Nicht nachgebaut: Anhänge, Abrufbestätigung, BPKI-Signatur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostfachMessage {

    private String id;

    /** Postkorb-Handle der Person (ZBP: mailboxUuid). */
    private String mailboxUuid;

    private String title;

    /** Nachrichtentext (Klartext). */
    private String content;

    /** Absendende Stelle, z. B. "Bürgerbüro Musterstadt". */
    private String sender;

    /** Verwaltungsleistung, z. B. "Terminvereinbarung". */
    private String service;

    private String replyAddress;

    /** Vertrauensniveau der Nachricht (STORK-QAA-Level 1, 3 oder 4; ZBP: stork_qaa_level). */
    private int storkQaaLevel;

    private Instant createdUtc;

    /** Zeitpunkt des ersten Öffnens; null = ungelesen. */
    private Instant readUtc;
}
