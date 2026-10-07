/*
 * Neu 2026 im Fork bpsim/standardkonform (siehe FORK.md). Lizenz wie das Gesamtprojekt: Apache License 2.0.
 */
package de.ba.oiam.bundidsim.postfach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostfachStoreTest {

    private static final String HANDLE = "11b2dc8f-3831-3b26-afde-aa0be42bd79b";

    @TempDir
    Path dir;

    private static CreateMessageRequest request(String title, int level) {
        CreateMessageRequest r = new CreateMessageRequest();
        r.setMailboxUuid(UUID.fromString(HANDLE));
        r.setTitle(" " + title + " ");
        r.setContent("Inhalt\nmit Zeilen");
        r.setSender("Bürgerbüro Musterstadt");
        r.setService("Terminvereinbarung");
        r.setStorkQaaLevel(level);
        return r;
    }

    @Test
    void storesMessagesPerHandleNewestFirstAndSurvivesRestart() {
        PostfachStore store = new PostfachStore(dir, Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneOffset.UTC));
        store.add(request("Erste", 1));
        PostfachStore later = new PostfachStore(dir, Clock.fixed(Instant.parse("2026-10-07T09:00:00Z"), ZoneOffset.UTC));
        String id = later.add(request("Zweite", 3));

        PostfachStore restarted = new PostfachStore(dir, Clock.systemUTC());
        assertThat(restarted.list(HANDLE)).extracting(PostfachMessage::getTitle).containsExactly("Zweite", "Erste");
        assertThat(restarted.find(HANDLE, id)).get().satisfies(m -> {
            assertThat(m.getStorkQaaLevel()).isEqualTo(3);
            assertThat(m.getContent()).isEqualTo("Inhalt\nmit Zeilen");
            assertThat(m.getReadUtc()).isNull();
        });
        assertThat(restarted.list(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void foreignIdIsNotFoundAndMarkReadSetsTimeOnce() {
        PostfachStore store = new PostfachStore(dir, Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneOffset.UTC));
        String id = store.add(request("Nachricht", 1));

        assertThat(store.find(UUID.randomUUID().toString(), id)).isEmpty();
        store.markRead(HANDLE, id);
        Instant first = store.find(HANDLE, id).orElseThrow().getReadUtc();
        new PostfachStore(dir, Clock.fixed(Instant.parse("2026-10-08T08:00:00Z"), ZoneOffset.UTC)).markRead(HANDLE, id);

        assertThat(first).isEqualTo(Instant.parse("2026-10-07T08:00:00Z"));
        assertThat(store.find(HANDLE, id).orElseThrow().getReadUtc()).isEqualTo(first);
    }

    @Test
    void apiKeyComparison() {
        assertThat(PostfachApiController.matches("geheim-123", "geheim-123")).isTrue();
        assertThat(PostfachApiController.matches("falsch", "geheim-123")).isFalse();
        assertThat(PostfachApiController.matches("", "")).isFalse();
        assertThat(PostfachApiController.matches(null, "geheim-123")).isFalse();
    }
}
