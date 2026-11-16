package com.vanrides.vanrides.whatsapp.privacy;

import static org.junit.jupiter.api.Assertions.*;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs({OS.LINUX, OS.MAC})
class WaPrivacyFileJournalTest {
    @TempDir Path temporary;

    private Path directory() throws Exception {
        Path path = temporary.resolve("journal");
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        return path.toRealPath();
    }

    private WaPrivacyJournal.Entry entry(long sequence) {
        UUID event = UUID.randomUUID(), subject = UUID.randomUUID();
        String payload = "{\"policy\":\"wa-privacy-v1\",\"eventId\":\"" + event + "\",\"subjectId\":\"" + subject
            + "\",\"environment\":\"vanrides-mvp-hml\",\"instanceId\":\"synthetic\","
            + "\"closedAt\":\"2026-01-01T00:00:00Z\",\"eraseAfter\":\"2026-01-31T00:00:00Z\"}";
        return new WaPrivacyJournal.Entry(sequence, event, subject, "vanrides-mvp-hml", "synthetic", payload, WaCrypto.digest(payload));
    }

    @Test void explicitInitializationAndMissingJournalFailClosed() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path);
        var item = entry(1);
        assertThrows(IllegalStateException.class, () -> journal.append(item));
        assertThrows(IllegalStateException.class, journal::entries);
        journal.initializeEmpty();
        assertEquals(List.of(), journal.entries());
        assertThrows(IllegalStateException.class, journal::initializeEmpty);
        var ack = journal.append(item);
        Files.delete(path.resolve("erasures.jsonl"));
        assertFalse(journal.verify(item, ack));
        assertThrows(IllegalStateException.class, () -> journal.append(item));
        assertThrows(IllegalStateException.class, journal::initializeEmpty);
    }

    @Test void durableReopenIdempotencyAndOriginalAcknowledgementSurviveLaterAppend() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var first = entry(2); var second = entry(1);
        var ack = journal.append(first);
        assertEquals(ack, journal.append(first));
        var next = journal.append(second);
        assertEquals(1, ack.journalSequence()); assertEquals(2, next.journalSequence());
        var reopened = new WaPrivacyFileJournal(path);
        assertTrue(reopened.verify(first, ack)); assertTrue(reopened.verify(second, next));
        assertEquals(List.of(first, second), reopened.entries());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path.resolve("erasures.jsonl")));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(path.resolve("head.json")));
    }

    @Test void eventConflictCannotReplaceAnExistingRecord() throws Exception {
        var journal = new WaPrivacyFileJournal(directory()); journal.initializeEmpty();
        var first = entry(1); var ack = journal.append(first);
        var conflict = new WaPrivacyJournal.Entry(2, first.eventId(), first.subjectId(), first.environment(),
            first.instanceId(), first.canonicalPayload(), first.payloadHash());
        assertThrows(IllegalStateException.class, () -> journal.append(conflict));
        assertTrue(journal.verify(first, ack)); assertEquals(List.of(first), journal.entries());
    }

    @Test void payloadTamperAndPartialAppendBlockWithoutRepair() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var first = entry(1); var ack = journal.append(first);
        byte[] original = Files.readAllBytes(path.resolve("erasures.jsonl"));
        Files.writeString(path.resolve("erasures.jsonl"), "{\"partial\":", StandardOpenOption.APPEND);
        assertFalse(journal.verify(first, ack));
        assertThrows(IllegalStateException.class, () -> journal.append(entry(2)));
        Files.write(path.resolve("erasures.jsonl"), original);
        Files.writeString(path.resolve("erasures.jsonl"), new String(original, java.nio.charset.StandardCharsets.UTF_8).replace("synthetic", "different"));
        assertFalse(journal.verify(first, ack));
        assertThrows(IllegalStateException.class, journal::entries);
    }

    @Test void truncatedTailOrMissingHeadCannotBeAcceptedAsOlderValidState() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var first = entry(1); var ack = journal.append(first);
        byte[] old = Files.readAllBytes(path.resolve("erasures.jsonl"));
        journal.append(entry(2));
        Files.write(path.resolve("erasures.jsonl"), old);
        assertFalse(journal.verify(first, ack));
        assertThrows(IllegalStateException.class, journal::entries);
        Files.delete(path.resolve("head.json"));
        assertThrows(IllegalStateException.class, () -> journal.append(entry(3)));
    }

    @Test void insecurePermissionsAndSymlinkAreRejected() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        Files.setPosixFilePermissions(path.resolve("head.json"), PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalStateException.class, journal::entries);
        Files.setPosixFilePermissions(path.resolve("head.json"), PosixFilePermissions.fromString("rw-------"));
        Path moved = temporary.resolve("outside"); Files.move(path.resolve("erasures.jsonl"), moved);
        Files.createSymbolicLink(path.resolve("erasures.jsonl"), moved);
        assertThrows(IllegalStateException.class, journal::entries);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThrows(IllegalStateException.class, () -> new WaPrivacyFileJournal(path));
    }

    @Test void concurrentAdaptersKeepOneContiguousChainAndIdempotency() throws Exception {
        Path path = directory();
        var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var other = new WaPrivacyFileJournal(path); var shared = entry(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> journal.append(shared));
            var b = pool.submit(() -> other.append(shared));
            assertEquals(a.get(), b.get()); assertEquals(List.of(shared), journal.entries());
            var c = pool.submit(() -> journal.append(entry(2)));
            var d = pool.submit(() -> other.append(entry(3)));
            assertNotEquals(c.get().journalSequence(), d.get().journalSequence());
            assertEquals(3, journal.entries().size());
        } finally { pool.shutdownNow(); }
    }

    @Test void forgedAcknowledgementAndUnmappedPayloadAreRejected() throws Exception {
        var journal = new WaPrivacyFileJournal(directory()); journal.initializeEmpty();
        var item = entry(1); var ack = journal.append(item);
        assertFalse(journal.verify(item, new WaPrivacyJournal.Ack(ack.eventId(), ack.payloadHash(), 2, ack.chainDigest())));
        assertFalse(journal.verify(item, null));
        var mismatch = new WaPrivacyJournal.Entry(1, UUID.randomUUID(), item.subjectId(), item.environment(),
            item.instanceId(), item.canonicalPayload(), item.payloadHash());
        assertThrows(IllegalStateException.class, () -> journal.append(mismatch));
        assertEquals(List.of(item), journal.entries());
    }

    @Test void retryAfterAtomicHeadMoveMustRepeatDirectorySynchronizationBeforeAck() throws Exception {
        Path path = directory(); new WaPrivacyFileJournal(path).initializeEmpty();
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var journal = new WaPrivacyFileJournal(path, directory -> {
            if (attempts.incrementAndGet() <= 2) throw new java.io.IOException("synthetic-directory-force-failure");
            try (var parent = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) { parent.force(true); }
        });
        var item = entry(1);
        assertThrows(IllegalStateException.class, () -> journal.append(item));
        // The moved head is visible, but visibility alone is not a durable ACK.
        assertThrows(IllegalStateException.class, () -> journal.append(item));
        var ack = journal.append(item);
        assertEquals(3, attempts.get()); assertEquals(1, ack.journalSequence());
        assertTrue(journal.verify(item, ack)); assertEquals(List.of(item), journal.entries());
    }

    @Test void trailingJsonInHeadAndInLineIsRejected() throws Exception {
        Path path = directory(); var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var item = entry(1); var ack = journal.append(item);
        byte[] originalHead = Files.readAllBytes(path.resolve("head.json"));
        Files.writeString(path.resolve("head.json"), " {}", StandardOpenOption.APPEND);
        assertFalse(journal.verify(item, ack));
        Files.write(path.resolve("head.json"), originalHead);
        String original = Files.readString(path.resolve("erasures.jsonl"));
        Files.writeString(path.resolve("erasures.jsonl"), original.stripTrailing() + " {}\n");
        assertFalse(journal.verify(item, ack)); assertThrows(IllegalStateException.class, journal::entries);
    }

    @Test void trailingPayloadAndInvalidDatesAreRejectedEvenWithMatchingHash() throws Exception {
        var journal = new WaPrivacyFileJournal(directory()); journal.initializeEmpty();
        var item = entry(1);
        for (String payload : List.of(item.canonicalPayload() + " {}",
                item.canonicalPayload().replace("2026-01-01T00:00:00Z", "invalid"),
                item.canonicalPayload().replace("2026-01-31T00:00:00Z", "2026-01-01T00:00:00Z"),
                item.canonicalPayload().replace("2026-01-31T00:00:00Z", "2026-01-30T00:00:00Z"))) {
            var invalid = new WaPrivacyJournal.Entry(1, item.eventId(), item.subjectId(), item.environment(),
                item.instanceId(), payload, WaCrypto.digest(payload));
            assertThrows(IllegalStateException.class, () -> journal.append(invalid));
        }
        assertEquals(List.of(), journal.entries());
    }

    @Test void snapshotAtomicallyBindsHeadEntriesAndOriginalAcks() throws Exception {
        var journal = new WaPrivacyFileJournal(directory()); journal.initializeEmpty();
        var first = entry(1); var a = journal.append(first); var second = entry(2); var b = journal.append(second);
        var snapshot = journal.snapshot();
        assertEquals(2, snapshot.sequence()); assertEquals(b.chainDigest(), snapshot.digest());
        assertEquals(List.of(new WaPrivacyFileJournal.VerifiedEntry(first, a), new WaPrivacyFileJournal.VerifiedEntry(second, b)), snapshot.records());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.records().clear());
    }

    @Test void missingNullCoercedAndDuplicateHeadFieldsAreRejected() throws Exception {
        Path path = directory(); var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        String digest = "\"digest\":\"" + "0".repeat(64) + "\"";
        for (String invalid : List.of("{" + digest + "}", "{\"sequence\":null," + digest + "}",
                "{\"sequence\":\"0\"," + digest + "}", "{\"sequence\":0.0," + digest + "}",
                "{\"sequence\":0,\"sequence\":0," + digest + "}")) {
            Files.writeString(path.resolve("head.json"), invalid);
            assertThrows(IllegalStateException.class, journal::entries);
        }
    }

    @Test void extraBlankLinesAndDuplicateOrNonTextualPayloadAreRejected() throws Exception {
        Path path = directory(); var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        var item = entry(1); var ack = journal.append(item);
        byte[] original = Files.readAllBytes(path.resolve("erasures.jsonl"));
        Files.writeString(path.resolve("erasures.jsonl"), "\n", StandardOpenOption.APPEND);
        assertFalse(journal.verify(item, ack));
        Files.write(path.resolve("erasures.jsonl"), original);
        for (String payload : List.of(item.canonicalPayload().replace("\"policy\":", "\"policy\":\"wa-privacy-v1\",\"policy\":"),
                item.canonicalPayload().replace("\"closedAt\":\"2026-01-01T00:00:00Z\"", "\"closedAt\":123"))) {
            var invalid = new WaPrivacyJournal.Entry(1, item.eventId(), item.subjectId(), item.environment(),
                item.instanceId(), payload, WaCrypto.digest(payload));
            assertThrows(IllegalStateException.class, () -> journal.append(invalid));
        }
        assertTrue(journal.verify(item, ack));
    }

    @Test void anotherJvmMustWaitForTheOperatingSystemFileLock() throws Exception {
        Path path = directory(); var journal = new WaPrivacyFileJournal(path); journal.initializeEmpty();
        Path ready = temporary.resolve("ready"), release = temporary.resolve("release");
        Process holder = child("hold", path, ready, release), writer = null;
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while (!Files.exists(ready) && holder.isAlive() && System.nanoTime() < deadline) Thread.sleep(20);
            assertTrue(Files.exists(ready), "child did not acquire the journal lock");
            writer = child("append", path, ready, release);
            assertFalse(writer.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS), "another process bypassed the held file lock");
            Files.writeString(release, "release");
            assertTrue(holder.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, holder.exitValue());
            assertTrue(writer.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, writer.exitValue());
            assertEquals(1, journal.entries().size());
        } finally {
            Files.writeString(release, "release"); holder.destroyForcibly(); if (writer != null) writer.destroyForcibly();
        }
    }

    private Process child(String mode, Path path, Path ready, Path release) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xmx96m", "-XX:+UseSerialGC", "-cp", classpath, ProcessAppender.class.getName(), mode,
            path.toString(), ready.toString(), release.toString())
            .redirectErrorStream(true).redirectOutput(temporary.resolve("child-" + mode + ".log").toFile()).start();
    }

    public static final class ProcessAppender {
        public static void main(String[] args) throws Exception {
            Path path = Path.of(args[1]);
            if (args[0].equals("append")) {
                new WaPrivacyFileJournal(path).append(new WaPrivacyFileJournalTest().entry(1)); return;
            }
            try (var channel = java.nio.channels.FileChannel.open(path.resolve("journal.lock"), StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                Files.writeString(Path.of(args[2]), "ready");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(Path.of(args[3])) && System.nanoTime() < deadline) Thread.sleep(20);
                if (!Files.exists(Path.of(args[3]))) throw new IllegalStateException("JOURNAL_PROCESS_BARRIER_TIMEOUT");
            }
        }
    }
}
