package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Private host journal, outside database backups. A partial write blocks instead of losing an erasure. */
public final class WaPrivacyFileJournal implements WaPrivacyJournal {
    private static final ObjectMapper JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final String ZERO = "0".repeat(64);
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RECORDS = 10000;
    private static final Object PROCESS_LOCK = new Object();
    private final Path directory;
    private final Path data;
    private final Path anchor;
    private final Durability durability;
    @FunctionalInterface interface Durability { void directory(Path directory) throws IOException; }
    public record VerifiedEntry(Entry entry, Ack acknowledgement) { }
    public record Snapshot(long sequence, String digest, List<VerifiedEntry> records) {
        public Snapshot { records = List.copyOf(records); }
    }
    private record Row(long sequence, String previous, Entry entry, String digest) { }
    private record Head(long sequence, String digest) { }
    private record State(List<Row> rows, Head head) { }

    public WaPrivacyFileJournal(Path directory) {
        this(directory, WaPrivacyFileJournal::forceDirectory);
    }

    WaPrivacyFileJournal(Path directory, Durability durability) {
        this.directory = directory.toAbsolutePath().normalize();
        this.durability = durability;
        data = this.directory.resolve("erasures.jsonl");
        anchor = this.directory.resolve("head.json");
        try { requireDirectory(); } catch (IOException failure) { throw unavailable(failure); }
    }

    @Override public Ack append(Entry entry) {
        validate(entry);
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = lockChannel(); FileLock lock = channel.lock()) {
                State state = read();
                for (Row row : state.rows()) {
                    if (!row.entry().eventId().equals(entry.eventId())) continue;
                    require(row.entry().equals(entry));
                    forceSnapshot();
                    return ack(row);
                }
                require(state.rows().size() < MAX_RECORDS);
                long sequence = state.head().sequence() + 1;
                Row row = new Row(sequence, state.head().digest(), entry,
                    digest(sequence, state.head().digest(), entry));
                byte[] bytes = (JSON.writeValueAsString(row) + "\n").getBytes(StandardCharsets.UTF_8);
                require(bytes.length <= 32000 && (!Files.exists(data, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(data) + bytes.length <= MAX_BYTES));
                if (!Files.exists(data, LinkOption.NOFOLLOW_LINKS)) createPrivate(data);
                requirePrivateFile(data);
                try (FileChannel output = FileChannel.open(data, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) output.write(buffer);
                    output.force(true);
                }
                writeAnchor(new Head(sequence, row.digest()));
                State durable = read();
                require(durable.head().equals(new Head(sequence, row.digest())));
                return ack(row);
            } catch (IOException failure) { throw unavailable(failure); }
        }
    }

    @Override public boolean verify(Entry entry, Ack acknowledgement) {
        if (acknowledgement == null) return false;
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = lockChannel(); FileLock lock = channel.lock()) {
                State state = read(); forceSnapshot();
                return state.rows().stream().anyMatch(row -> row.entry().equals(entry)
                    && ack(row).equals(acknowledgement));
            } catch (IOException | RuntimeException failure) { return false; }
        }
    }

    /** Restore must preserve the complete current journal and its independently retained head. */
    public List<Entry> entries() {
        return snapshot().records().stream().map(VerifiedEntry::entry).toList();
    }

    /** Head, entries and their original ACKs are captured under the same file lock. */
    public Snapshot snapshot() {
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = lockChannel(); FileLock lock = channel.lock()) {
                State state = read(); forceSnapshot();
                return new Snapshot(state.head().sequence(), state.head().digest(),
                    state.rows().stream().map(row -> new VerifiedEntry(row.entry(), ack(row))).toList());
            } catch (IOException failure) { throw unavailable(failure); }
        }
    }

    /** Explicit first provisioning only; never use this as recovery from a missing journal. */
    public void initializeEmpty() {
        synchronized (PROCESS_LOCK) {
            try (FileChannel channel = lockChannel(); FileLock lock = channel.lock()) {
                require(!Files.exists(data, LinkOption.NOFOLLOW_LINKS) && !Files.exists(anchor, LinkOption.NOFOLLOW_LINKS));
                createPrivate(data);
                try (FileChannel output = FileChannel.open(data, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { output.force(true); }
                writeAnchor(new Head(0, ZERO));
                require(read().head().sequence() == 0);
            } catch (IOException failure) { throw unavailable(failure); }
        }
    }

    private State read() throws IOException {
        requireDirectory();
        boolean hasData = Files.exists(data, LinkOption.NOFOLLOW_LINKS);
        boolean hasHead = Files.exists(anchor, LinkOption.NOFOLLOW_LINKS);
        require(hasData && hasHead);
        requirePrivateFile(data); requirePrivateFile(anchor);
        require(Files.size(data) <= MAX_BYTES && Files.size(anchor) <= 256);
        byte[] bytes = Files.readAllBytes(data);
        Head head = JSON.readValue(Files.readAllBytes(anchor), Head.class);
        require(head != null);
        if (bytes.length == 0) {
            require(head.sequence() == 0 && ZERO.equals(head.digest()));
            return new State(List.of(), head);
        }
        require(bytes[bytes.length - 1] == '\n');
        String[] lines = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().split("\n", -1);
        require(lines.length > 1 && lines.length <= MAX_RECORDS + 1 && lines[lines.length - 1].isEmpty());
        List<Row> rows = new ArrayList<>();
        HashSet<UUID> events = new HashSet<>();
        String previous = ZERO;
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index];
            require(!line.isBlank() && line.length() <= 32000);
            Row row = JSON.readValue(line, Row.class);
            require(row != null);
            validate(row.entry());
            require(row.sequence() == rows.size() + 1 && previous.equals(row.previous())
                && events.add(row.entry().eventId())
                && digest(row.sequence(), previous, row.entry()).equals(row.digest()));
            rows.add(row); previous = row.digest();
        }
        require(head.sequence() == rows.size() && previous.equals(head.digest()));
        return new State(List.copyOf(rows), head);
    }

    private void writeAnchor(Head head) throws IOException {
        Path temporary = directory.resolve("head-" + UUID.randomUUID() + ".tmp");
        createPrivate(temporary);
        try {
            byte[] bytes = JSON.writeValueAsBytes(head);
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            if (Files.exists(anchor, LinkOption.NOFOLLOW_LINKS)) requirePrivateFile(anchor);
            Files.move(temporary, anchor, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            durability.directory(directory);
        } finally { Files.deleteIfExists(temporary); }
    }

    private void forceSnapshot() throws IOException {
        for (Path path : List.of(data, anchor)) {
            requirePrivateFile(path);
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
        }
        durability.directory(directory);
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) { parent.force(true); }
    }

    private FileChannel lockChannel() throws IOException {
        requireDirectory();
        Path path = directory.resolve("journal.lock");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            try { createPrivate(path); } catch (java.nio.file.FileAlreadyExistsException raced) { /* verify below */ }
        }
        requirePrivateFile(path);
        return FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    private void requireDirectory() throws IOException {
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
            && directory.toRealPath().equals(directory)
            && Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------")));
    }

    private static void requirePrivateFile(Path path) throws IOException {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            && Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
            && Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(Files.getOwner(path.getParent())));
    }

    private static void createPrivate(Path path) throws IOException {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private static void validate(Entry entry) {
        require(entry != null && entry.databaseSequence() > 0 && entry.eventId() != null && entry.subjectId() != null
            && entry.environment() != null && !entry.environment().isBlank() && entry.environment().length() <= 100
            && entry.instanceId() != null && !entry.instanceId().isBlank() && entry.instanceId().length() <= 100
            && entry.canonicalPayload() != null && entry.canonicalPayload().length() <= 16000
            && WaCrypto.digest(entry.canonicalPayload()).equals(entry.payloadHash()));
        try {
            var payload = JSON.readTree(entry.canonicalPayload());
            require(payload != null && payload.isObject());
            for (String field : List.of("policy", "eventId", "subjectId", "environment", "instanceId", "closedAt", "eraseAfter"))
                require(payload.path(field).isTextual());
            require(payload.isObject() && payload.size() == 7 && payload.path("policy").asText().equals("wa-privacy-v1")
                && payload.path("eventId").asText().equals(entry.eventId().toString())
                && payload.path("subjectId").asText().equals(entry.subjectId().toString())
                && payload.path("environment").asText().equals(entry.environment())
                && payload.path("instanceId").asText().equals(entry.instanceId())
                && !payload.path("closedAt").asText().isBlank() && !payload.path("eraseAfter").asText().isBlank());
            java.time.Instant closed = java.time.OffsetDateTime.parse(payload.path("closedAt").asText()).toInstant();
            java.time.Instant after = java.time.OffsetDateTime.parse(payload.path("eraseAfter").asText()).toInstant();
            require(after.equals(closed.plus(java.time.Duration.ofDays(30))));
        } catch (IOException | java.time.DateTimeException failure) { throw unavailable(failure); }
    }

    private static String digest(long sequence, String previous, Entry entry) throws IOException {
        return WaCrypto.digest(sequence + "\n" + previous + "\n" + JSON.writeValueAsString(entry));
    }
    private static Ack ack(Row row) { return new Ack(row.entry().eventId(), row.entry().payloadHash(), row.sequence(), row.digest()); }
    private static void require(boolean condition) { if (!condition) throw unavailable(null); }
    private static IllegalStateException unavailable(Exception ignored) { return new IllegalStateException("PRIVACY_JOURNAL_UNAVAILABLE"); }
}
