/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Eingehende Nachricht an POST /api/v1/messages, nach dem Vorbild von ZBP „CreateMessage“. Gleiches JSON wie beim
 * eigenständigen Postfach-Dienst des BürgerPortals (mailboxUuid, title, content, sender, service, replyAddress,
 * storkQaaLevel) – ein Dienst kann ohne Änderung an das eine oder das andere Postfach liefern.
 * Abweichung vom ZBP: ein JSON-Objekt mit den Feldern direkt statt „content“ + Signatur in einer Hülle.
 */
@Data
public class CreateMessageRequest {

    // Belegt ist nur „content max. 1 MB“; die übrigen Längen sind großzügige Grenzen der Simulation.
    public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    @NotNull
    private UUID mailboxUuid;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = MAX_CONTENT_LENGTH)
    private String content;

    @NotBlank
    @Size(max = 255)
    private String sender;

    @NotBlank
    @Size(max = 255)
    private String service;

    @Email
    @Size(max = 320)
    private String replyAddress;

    // STORK-QAA-Level 1 bis 4; die BundID nutzt 1 (normal), 3 (substanziell) und 4 (hoch).
    @Min(1)
    @Max(4)
    private int storkQaaLevel = 1;
}
