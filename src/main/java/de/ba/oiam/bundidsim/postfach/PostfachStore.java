/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Nachrichten des Simulator-Postfachs: eine JSON-Datei je Postkorb-Handle im Datenverzeichnis (im Container das Volume
 * /app/data, das auch den Signaturschlüssel hält). Bewusst ohne Datenbank – der Simulator soll klein bleiben.
 * Schreibzugriffe sind serialisiert und ersetzen die Datei atomar.
 */
@Service
@Slf4j
public class PostfachStore {

    /** Höchstens so viele Nachrichten je Postfach werden angezeigt (die neuesten). */
    public static final int MAX_LISTED = 200;

    private static final TypeReference<List<PostfachMessage>> LIST = new TypeReference<>() {
    };

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Path directory;
    private final Clock clock;

    @Autowired
    public PostfachStore(@Value("${app.postfach.storage-dir:/app/data/postfach}") String directory) {
        this(Path.of(directory), Clock.systemUTC());
    }

    PostfachStore(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    /** Nimmt eine Nachricht an und liefert ihre ID. */
    public synchronized String add(CreateMessageRequest request) {
        String handle = request.getMailboxUuid().toString();
        List<PostfachMessage> messages = new ArrayList<>(read(handle));
        PostfachMessage message = PostfachMessage.builder()
                .id(UUID.randomUUID().toString())
                .mailboxUuid(handle)
                .title(request.getTitle().trim())
                .content(request.getContent())
                .sender(request.getSender().trim())
                .service(request.getService().trim())
                .replyAddress(request.getReplyAddress() == null || request.getReplyAddress().isBlank()
                        ? null : request.getReplyAddress().trim())
                .storkQaaLevel(request.getStorkQaaLevel())
                .createdUtc(clock.instant())
                .build();
        messages.add(message);
        write(handle, messages);
        return message.getId();
    }

    /** Neueste zuerst, höchstens {@link #MAX_LISTED}. */
    public synchronized List<PostfachMessage> list(String handle) {
        return read(handle).stream()
                .sorted(Comparator.comparing(PostfachMessage::getCreatedUtc).reversed())
                .limit(MAX_LISTED)
                .toList();
    }

    /** Nur im eigenen Postfach: eine fremde ID ergibt „nicht gefunden“ und verrät nicht, ob es sie gibt. */
    public synchronized Optional<PostfachMessage> find(String handle, String id) {
        return read(handle).stream().filter(m -> m.getId().equals(id)).findFirst();
    }

    /** Als gelesen markieren (erstes Öffnen des Inhalts). */
    public synchronized void markRead(String handle, String id) {
        List<PostfachMessage> messages = new ArrayList<>(read(handle));
        boolean changed = false;
        for (PostfachMessage m : messages) {
            if (m.getId().equals(id) && m.getReadUtc() == null) {
                m.setReadUtc(clock.instant());
                changed = true;
            }
        }
        if (changed) {
            write(handle, messages);
        }
    }

    private Path file(String handle) {
        // Das Handle ist eine UUID (geprüft beim Einliefern bzw. aus der Sitzung) – kein Pfad aus Benutzereingaben.
        return directory.resolve(UUID.fromString(handle) + ".json");
    }

    private List<PostfachMessage> read(String handle) {
        Path file = file(handle);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return mapper.readValue(Files.readString(file), LIST);
        } catch (IOException e) {
            throw new UncheckedIOException("Postfach nicht lesbar", e);
        }
    }

    private void write(String handle, List<PostfachMessage> messages) {
        try {
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, "postfach", ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(messages));
            try {
                Files.move(tmp, file(handle), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file(handle), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Postfach nicht schreibbar", e);
        }
    }
}
