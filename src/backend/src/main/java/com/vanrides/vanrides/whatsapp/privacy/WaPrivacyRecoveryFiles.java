package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Only private files in a dedicated lab directory. No URL, subject, SQL or clock input. */
final class WaPrivacyRecoveryFiles {
    enum Operation { PREPARE, CLOSE_PURGE, REAPPLY_PURGE }
    record Request(Operation operation, String nonce, String password, String credential,
                   String fixtureSha256, String retainedHeadSha256) {
        @Override public String toString() { return "WaPrivacyRecoveryRequest[redacted]"; }
    }
    final Path directory;
    final Request request;
    private final long uid;

    WaPrivacyRecoveryFiles(Path input) throws IOException {
        require(input.isAbsolute() && input.equals(input.normalize()) && input.getParent()!=null);
        directory=input.getParent();
        require(directory.toRealPath().equals(directory) && Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)
            && Files.getPosixFilePermissions(directory,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------")));
        Path probe=Files.createTempFile(directory,".owner-probe-",".private",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Object createdKey=Files.readAttributes(probe,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        try { uid=((Number)Files.getAttribute(probe,"unix:uid",LinkOption.NOFOLLOW_LINKS)).longValue(); }
        finally {
            var current=Files.readAttributes(probe,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            require(createdKey!=null && current.isRegularFile() && current.size()==0 && Objects.equals(createdKey,current.fileKey()));
            Files.delete(probe);
        }
        privateDirectory(directory);
        ObjectNode body=WaJson.object(read(input));
        Operation operation=Operation.valueOf(WaJson.text(body,"operation",1,20));
        var fields=new ArrayList<>(List.of("schemaVersion","operation","environment","nonce","password","credential"));
        if(operation!=Operation.PREPARE) fields.add("fixtureSha256");
        if(operation==Operation.REAPPLY_PURGE) fields.add("retainedHeadSha256");
        WaJson.fields(body,fields.toArray(String[]::new));
        require(body.path("schemaVersion").isIntegralNumber() && body.path("schemaVersion").canConvertToInt() && body.path("schemaVersion").intValue()==1
            && WaJson.text(body,"environment",1,32).equals("wa070-local")
            && input.getFileName().toString().equals(operation.name().toLowerCase(Locale.ROOT)+".request.json"));
        String nonce=WaJson.text(body,"nonce",32,32); require(nonce.matches("[0-9a-f]{32}"));
        String password=token(body,"password"),credential=token(body,"credential");
        request=new Request(operation,nonce,password,credential,
            operation==Operation.PREPARE ? null : hash(body,"fixtureSha256"),
            operation==Operation.REAPPLY_PURGE ? hash(body,"retainedHeadSha256") : null);
        require(!Files.exists(result(),LinkOption.NOFOLLOW_LINKS));
    }
    Path result() { return directory.resolve(request.operation().name().toLowerCase(Locale.ROOT)+".result.json"); }
    Path journal() throws IOException { Path path=directory.resolve("journal"); privateDirectory(path); return path; }

    ObjectNode fixture() throws IOException {
        byte[] raw=read(directory.resolve("fixture.json"));
        require(WaCrypto.digest(new String(raw,StandardCharsets.UTF_8)).equals(request.fixtureSha256()));
        ObjectNode value=WaJson.object(raw);
        WaJson.fields(value,"schemaVersion","nonce","baseAt","coreDigest","betaDigest");
        require(value.path("schemaVersion").isIntegralNumber() && value.path("schemaVersion").canConvertToInt() && value.path("schemaVersion").intValue()==1
            && request.nonce().equals(value.path("nonce").asText()));
        return value;
    }
    ObjectNode retainedHead() throws IOException {
        byte[] raw=read(directory.resolve("retained-head.json"));
        require(WaCrypto.digest(new String(raw,StandardCharsets.UTF_8)).equals(request.retainedHeadSha256()));
        ObjectNode value=WaJson.object(raw); WaJson.fields(value,"sequence","digest");
        require(value.path("sequence").isIntegralNumber() && value.path("sequence").canConvertToLong()
            && value.path("sequence").longValue()>0); hash(value,"digest"); return value;
    }
    byte[] read(Path path) throws IOException {
        require(path.getParent().equals(directory)); privateFile(path);
        try(var file=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            require(file.size()>0 && file.size()<=64000); ByteBuffer bytes=ByteBuffer.allocate((int)file.size());
            while(bytes.hasRemaining()) require(file.read(bytes)>0);
            require(file.size()==bytes.capacity()); privateFile(path); return bytes.array();
        }
    }
    void write(String name,ObjectNode value) throws IOException {
        require(Set.of("fixture.json","prepare.result.json","close_purge.result.json","reapply_purge.result.json").contains(name));
        privateDirectory(directory); byte[] raw=(WaJson.canonical(value)+"\n").getBytes(StandardCharsets.UTF_8);
        require(raw.length<=64000);
        try(var file=FileChannel.open(directory.resolve(name),Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            ByteBuffer bytes=ByteBuffer.wrap(raw); while(bytes.hasRemaining()) file.write(bytes); file.force(true);
        }
        try(var parent=FileChannel.open(directory,StandardOpenOption.READ)) { parent.force(true); }
    }
    void emptyJournal() throws IOException {
        try(var entries=Files.list(journal())) { require(entries.findAny().isEmpty()); }
    }
    private void privateDirectory(Path path) throws IOException {
        require(uid!=0 && path.toRealPath().equals(path) && Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)
            && Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
            && ((Number)Files.getAttribute(path,"unix:uid",LinkOption.NOFOLLOW_LINKS)).longValue()==uid);
    }
    private void privateFile(Path path) throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
            && Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
            && ((Number)Files.getAttribute(path,"unix:uid",LinkOption.NOFOLLOW_LINKS)).longValue()==uid);
    }
    private static String token(ObjectNode body,String name) {
        String value=WaJson.text(body,name,43,43);
        require(value.matches("[A-Za-z0-9_-]{43}") && java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(java.util.Base64.getUrlDecoder().decode(value)).equals(value)); return value;
    }
    private static String hash(ObjectNode body,String name) {
        String value=WaJson.text(body,name,64,64); require(value.matches("[a-f0-9]{64}")); return value;
    }
    static void require(boolean condition) { if(!condition) throw new IllegalStateException("PRIVACY_RECOVERY_BLOCKED"); }
}
