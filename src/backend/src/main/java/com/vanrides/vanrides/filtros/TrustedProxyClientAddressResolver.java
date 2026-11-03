package com.vanrides.vanrides.filtros;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;

@Component
public final class TrustedProxyClientAddressResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int DIGEST_BYTES = 16;
    private static final int RANDOM_SALT_BYTES = 32;

    private final List<IpNetwork> trustedProxies;
    private final int maxForwardedBytes;
    private final int maxForwardedHops;
    private final byte[] salt;

    @Autowired
    public TrustedProxyClientAddressResolver(
            @Value("${vanrides.rate-limit.trusted-proxy-cidrs:}") String configuredCidrs,
            @Value("${vanrides.rate-limit.forwarded-max-bytes:1024}") int maxForwardedBytes,
            @Value("${vanrides.rate-limit.forwarded-max-hops:10}") int maxForwardedHops
    ) {
        this(configuredCidrs, maxForwardedBytes, maxForwardedHops, randomSalt());
    }

    TrustedProxyClientAddressResolver(
            String configuredCidrs,
            int maxForwardedBytes,
            int maxForwardedHops,
            byte[] salt
    ) {
        this.trustedProxies = IpNetwork.parseCsv(configuredCidrs);
        if (maxForwardedBytes < 1 || maxForwardedHops < 1) {
            throw new IllegalArgumentException("Limites de forwarded headers devem ser positivos.");
        }
        if (salt == null || salt.length < DIGEST_BYTES) {
            throw new IllegalArgumentException("Salt de identidade deve ter pelo menos 16 bytes.");
        }
        this.maxForwardedBytes = maxForwardedBytes;
        this.maxForwardedHops = maxForwardedHops;
        this.salt = salt.clone();
        newMac();
    }

    public ClientIdentity resolve(HttpServletRequest request) {
        if (request == null) {
            throw rejected(RejectionReason.INVALID_REMOTE_ADDRESS);
        }

        byte[] peerAddress = parseRemoteAddress(request.getRemoteAddr());
        if (!isTrusted(peerAddress)) {
            return identity(peerAddress, Source.DIRECT);
        }

        String forwarded = singleForwardedHeader(request);
        if (forwarded == null) {
            return identity(peerAddress, Source.PROXY_PEER);
        }
        List<byte[]> chain = parseForwardedChain(forwarded);
        byte[] resolvedAddress = resolveRightToLeft(peerAddress, chain);
        return identity(resolvedAddress, Source.TRUSTED_PROXY);
    }

    private byte[] parseRemoteAddress(String remoteAddress) {
        try {
            return IpNetwork.parseAddress(remoteAddress);
        } catch (IllegalArgumentException exception) {
            throw rejected(RejectionReason.INVALID_REMOTE_ADDRESS);
        }
    }

    private String singleForwardedHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders(FORWARDED_FOR);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        if (values.hasMoreElements()) {
            throw rejected(RejectionReason.MULTIPLE_FORWARDED_HEADERS);
        }
        return value;
    }

    private List<byte[]> parseForwardedChain(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            throw rejected(RejectionReason.EMPTY_FORWARDED_HEADER);
        }
        if (forwarded.getBytes(StandardCharsets.UTF_8).length > maxForwardedBytes) {
            throw rejected(RejectionReason.FORWARDED_HEADER_TOO_LARGE);
        }

        String[] hops = forwarded.split(",", -1);
        if (hops.length > maxForwardedHops) {
            throw rejected(RejectionReason.TOO_MANY_FORWARDED_HOPS);
        }

        List<byte[]> addresses = new ArrayList<>(hops.length);
        for (String hop : hops) {
            String literal = hop.trim();
            if (literal.isEmpty()) {
                throw rejected(RejectionReason.EMPTY_FORWARDED_HOP);
            }
            try {
                addresses.add(IpNetwork.parseAddress(literal));
            } catch (IllegalArgumentException exception) {
                throw rejected(RejectionReason.INVALID_FORWARDED_ADDRESS);
            }
        }
        return addresses;
    }

    private byte[] resolveRightToLeft(byte[] peerAddress, List<byte[]> chain) {
        byte[] current = peerAddress;
        for (int index = chain.size() - 1; index >= 0 && isTrusted(current); index--) {
            current = chain.get(index);
        }
        return current;
    }

    private boolean isTrusted(byte[] address) {
        return trustedProxies.stream().anyMatch(network -> network.contains(address));
    }

    private ClientIdentity identity(byte[] address, Source source) {
        byte[] normalized = IpNetwork.rateIdentityAddress(address);
        Mac mac = newMac();
        mac.update((byte) normalized.length);
        byte[] digest = Arrays.copyOf(mac.doFinal(normalized), DIGEST_BYTES);
        return new ClientIdentity(HexFormat.of().formatHex(digest), source);
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 indisponivel.", exception);
        }
    }

    private static byte[] randomSalt() {
        byte[] result = new byte[RANDOM_SALT_BYTES];
        new SecureRandom().nextBytes(result);
        return result;
    }

    private static ResolutionException rejected(RejectionReason reason) {
        return new ResolutionException(reason);
    }

    public record ClientIdentity(String digest, Source source) {
        public ClientIdentity {
            if (digest == null || digest.length() != DIGEST_BYTES * 2 || source == null) {
                throw new IllegalArgumentException("Identidade de cliente invalida.");
            }
        }
    }

    public enum Source {
        DIRECT,
        TRUSTED_PROXY,
        PROXY_PEER
    }

    public enum RejectionReason {
        INVALID_REMOTE_ADDRESS,
        MULTIPLE_FORWARDED_HEADERS,
        EMPTY_FORWARDED_HEADER,
        FORWARDED_HEADER_TOO_LARGE,
        TOO_MANY_FORWARDED_HOPS,
        EMPTY_FORWARDED_HOP,
        INVALID_FORWARDED_ADDRESS
    }

    public static final class ResolutionException extends RuntimeException {
        private final RejectionReason reason;

        private ResolutionException(RejectionReason reason) {
            super("Endereco de cliente encaminhado invalido.");
            this.reason = reason;
        }

        public RejectionReason reason() {
            return reason;
        }
    }
}
