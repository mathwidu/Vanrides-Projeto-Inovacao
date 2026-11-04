package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Bounded private-file CLI plan. Every step retains the single-request transaction and receipt. */
final class WaOperatorBatch {
    private static final Set<WaOperatorRequest.Operation> ALLOWED = Set.of(
            WaOperatorRequest.Operation.CREATE_COHORT, WaOperatorRequest.Operation.PROVISION_CREDENTIAL,
            WaOperatorRequest.Operation.RESET, WaOperatorRequest.Operation.REVOKE_CREDENTIAL);
    private final List<Step> steps;
    private final Path cancelFile;

    private record Step(WaOperatorRequest request, Path output) {
        @Override public String toString() { return "WaOperatorBatch.Step[redacted]"; }
    }

    private WaOperatorBatch(List<Step> steps, Path cancelFile) {
        this.steps = List.copyOf(steps); this.cancelFile = cancelFile;
    }

    static boolean selected(ObjectNode input) { return input.has("batchVersion") || input.has("operations"); }

    static WaOperatorBatch parse(byte[] bytes, Path input) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > 16384) throw new WaOperatorFailure();
            ObjectNode envelope = WaJson.object(bytes);
            WaJson.fields(envelope, envelope.has("cancelFile")
                    ? new String[] {"batchVersion", "operations", "cancelFile"}
                    : new String[] {"batchVersion", "operations"});
            var version = envelope.get("batchVersion");
            var operations = envelope.get("operations");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1
                    || !operations.isArray() || operations.isEmpty() || operations.size() > 16)
                throw new WaOperatorFailure();
            Path directory = privateDirectory(input);
            List<Step> steps = new ArrayList<>();
            Set<Path> destinations = new HashSet<>();
            Path cancelFile = envelope.has("cancelFile") ? newDestination(envelope, "cancelFile", directory) : null;
            if (cancelFile != null) destinations.add(cancelFile);
            WaOperatorRequest scope = null;
            for (var operation : operations) {
                WaJson.fields(operation, "request", "outputFile");
                WaOperatorRequest request = WaOperatorRequest.parse(
                        WaJson.write(operation.get("request")).getBytes(StandardCharsets.UTF_8));
                if (!ALLOWED.contains(request.operation())) throw new WaOperatorFailure();
                if (scope == null) scope = request;
                else if (!scope.environment().equals(request.environment()) || !scope.batchId().equals(request.batchId())
                        || !WaCrypto.same(scope.operatorCredential(), request.operatorCredential()))
                    throw new WaOperatorFailure();
                Path destination = newDestination(operation, "outputFile", directory);
                if (!destinations.add(destination)) throw new WaOperatorFailure();
                steps.add(new Step(request, destination));
            }
            return new WaOperatorBatch(steps, cancelFile);
        } catch (IOException | RuntimeException failure) { throw new WaOperatorFailure(); }
    }

    private static Path newDestination(JsonNode node, String field, Path directory) {
        Path path = Path.of(WaJson.text(node, field, 1, 4096));
        if (!path.isAbsolute() || !path.equals(path.normalize()) || !directory.equals(path.getParent())
                || !Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) throw new WaOperatorFailure();
        return path;
    }

    private static Path privateDirectory(Path input) throws IOException {
        if (!input.isAbsolute() || !input.equals(input.normalize()) || input.getParent() == null)
            throw new WaOperatorFailure();
        Path directory = input.getParent();
        if (!directory.equals(directory.toRealPath()) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rwx------"))) throw new WaOperatorFailure();
        var current = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (!current.equals(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS))) throw new WaOperatorFailure();
        return directory;
    }

    void execute(WaOperatorService service, Function<Path, WaOperatorFiles> filesFactory) {
        List<WaOperatorFiles> outputs = new ArrayList<>();
        try {
            // Reserve every unique destination before the first database effect.
            for (Step step : steps) outputs.add(filesFactory.apply(step.output()));
            for (int index = 0; index < steps.size(); index++) {
                // A current authorized transaction finishes; cancellation fences the next operation.
                if (cancelFile != null && !Files.notExists(cancelFile, LinkOption.NOFOLLOW_LINKS))
                    throw new WaOperatorFailure();
                WaOperatorFiles files = outputs.get(index);
                service.execute(steps.get(index).request(), files::prepare);
                files.committed(); // The proxied per-operation transaction has returned successfully.
            }
        } finally {
            WaOperatorFailure closeFailure = null;
            for (WaOperatorFiles files : outputs) {
                try { files.close(); } catch (RuntimeException failure) { closeFailure = new WaOperatorFailure(); }
            }
            if (closeFailure != null) throw closeFailure;
        }
    }

    @Override public String toString() { return "WaOperatorBatch[redacted]"; }
}
