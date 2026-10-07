/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Einlieferung von Nachrichten für Behörden-Dienste nach dem Vorbild von ZBP „CreateMessage“:
 * POST /api/v1/messages mit Header X-Api-Key, Antwort 201 {"id": …}. Nur bei app.postfach.enabled=true; im Betrieb
 * nur im internen Container-Netz erreichbar (der Reverse Proxy sperrt /api von außen).
 * Vereinfachung gegenüber dem echten ZBP: gemeinsamer API-Schlüssel statt BPKI-Zertifikat und Signatur über den Inhalt.
 */
@RestController
@Slf4j
public class PostfachApiController {

    public static final String API_KEY_HEADER = "X-Api-Key";

    @Autowired
    private PostfachStore store;

    @Value("${app.postfach.enabled:false}")
    private boolean enabled;

    @Value("${app.postfach.api-key:}")
    private String apiKey;

    @PostMapping(path = "/api/v1/messages")
    public ResponseEntity<Map<String, String>> create(
            @RequestHeader(value = API_KEY_HEADER, required = false) String providedKey,
            @Valid @RequestBody CreateMessageRequest request) {
        if (!enabled) {
            return ResponseEntity.notFound().build();
        }
        if (!matches(providedKey, apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("title", "API-Schlüssel fehlt oder ist ungültig."));
        }
        String id = store.add(request);
        // Kein Inhalt und kein Postkorb-Handle im Log.
        log.info("Postfach: Nachricht {} von {} ({}) angenommen.", id, request.getSender(), request.getService());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        if (!enabled) {
            return ResponseEntity.notFound().build();
        }
        Map<String, String> errors = new java.util.TreeMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of("title", "Ungültige Nachricht.", "errors", errors));
    }

    /** Vergleich über die Hashwerte in konstanter Zeit: verrät weder Inhalt noch Länge des Schlüssels. */
    static boolean matches(String provided, String expected) {
        if (provided == null || expected == null || provided.isEmpty() || expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(sha256(provided), sha256(expected));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
