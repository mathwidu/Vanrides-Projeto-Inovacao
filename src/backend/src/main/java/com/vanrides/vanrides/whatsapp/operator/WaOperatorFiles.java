package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

/** Reserves a private result before database effects; COMMITTED atomically replaces its own PREPARED file. */
final class WaOperatorFiles implements AutoCloseable {
    @FunctionalInterface
    interface FinalWrite { void write(FileChannel channel, byte[] bytes) throws IOException; }
    private static final Set<java.nio.file.attribute.PosixFilePermission> PRIVATE = PosixFilePermissions.fromString("rw-------");
    private final Path path;
    private final FileChannel output;
    private final Object destinationKey;
    private final FinalWrite finalWrite;
    private ObjectNode prepared;
    private boolean finalized;

    private WaOperatorFiles(Path path, FileChannel output, Object destinationKey, FinalWrite finalWrite) {
        this.path = path; this.output = output; this.destinationKey = destinationKey; this.finalWrite = finalWrite;
    }

    static byte[] read(Path input) {
        try {
            requirePrivate(input);
            try (FileChannel channel = FileChannel.open(input, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                long size = channel.size();
                if (size < 1 || size > 16384) throw new WaOperatorFailure();
                ByteBuffer buffer = ByteBuffer.allocate((int) size + 1);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
                if (buffer.position() != size || channel.size() != size) throw new WaOperatorFailure();
                byte[] result = new byte[(int) size]; buffer.flip(); buffer.get(result);
                requirePrivate(input);
                return result;
            }
        } catch (IOException | RuntimeException failure) { throw new WaOperatorFailure(); }
    }

    static WaOperatorFiles create(Path destination) { return create(destination, WaOperatorFiles::writeFully); }

    static WaOperatorFiles create(Path destination, FinalWrite finalWrite) {
        FileChannel channel = null;
        try {
            if (!destination.isAbsolute() || destination.getParent() == null || Files.isSymbolicLink(destination) || finalWrite == null)
                throw new WaOperatorFailure();
            channel = FileChannel.open(destination,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PRIVATE));
            return new WaOperatorFiles(destination, channel, privateFileKey(destination), finalWrite);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) try { channel.close(); } catch (IOException ignored) { }
            throw new WaOperatorFailure();
        }
    }

    void prepare(ObjectNode result) {
        if (!"PREPARED".equals(result.path("status").asText()) || prepared != null) throw new WaOperatorFailure();
        try {
            requireOwnDestination();
            writeFully(output, bytes(result));
            output.force(true);
            forceDirectory(path.getParent());
            requireOwnDestination();
            prepared = result.deepCopy();
        } catch (IOException | RuntimeException failure) { throw new WaOperatorFailure(); }
    }

    void committed() {
        if (prepared == null || finalized) throw new WaOperatorFailure();
        Path temporary = null;
        try {
            requireOwnDestination();
            ObjectNode completed = prepared.deepCopy().put("status", "COMMITTED");
            temporary = Files.createTempFile(path.getParent(), ".wa-operator-", ".tmp", PosixFilePermissions.asFileAttribute(PRIVATE));
            Object temporaryKey = privateFileKey(temporary);
            try (FileChannel replacement = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                finalWrite.write(replacement, bytes(completed));
                replacement.force(true);
            }
            if (!temporaryKey.equals(privateFileKey(temporary))) throw new WaOperatorFailure();
            requireOwnDestination();
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            finalized = true;
            forceDirectory(path.getParent());
        } catch (IOException | RuntimeException failure) { throw new WaOperatorFailure(); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private void requireOwnDestination() throws IOException {
        if (!destinationKey.equals(privateFileKey(path))) throw new WaOperatorFailure();
    }
    private static Object privateFileKey(Path path) throws IOException {
        requirePrivate(path);
        Object key = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        if (key == null) throw new WaOperatorFailure();
        return key;
    }
    private static byte[] bytes(ObjectNode result) {
        byte[] bytes = (WaJson.write(result) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 32768) throw new WaOperatorFailure();
        return bytes;
    }
    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
    private static void requirePrivate(Path path) throws IOException {
        if (!path.isAbsolute() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || !PRIVATE.equals(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)))
            throw new WaOperatorFailure();
        UserPrincipal current = path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (!current.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))) throw new WaOperatorFailure();
    }

    @Override public void close() {
        prepared = null;
        try { output.close(); } catch (IOException failure) { throw new WaOperatorFailure(); }
    }
}
