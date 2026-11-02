package com.vanrides.vanrides.filtros;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class IpNetwork {

    private final byte[] networkAddress;
    private final int prefixLength;

    private IpNetwork(byte[] networkAddress, int prefixLength) {
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    static IpNetwork parse(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("CIDR de proxy confiavel invalido.");
        }

        int separator = value.indexOf('/');
        if (separator <= 0 || separator != value.lastIndexOf('/') || separator == value.length() - 1) {
            throw new IllegalArgumentException("CIDR de proxy confiavel invalido.");
        }

        String addressLiteral = value.substring(0, separator);
        byte[] address = parseAddress(addressLiteral);
        if (addressLiteral.indexOf(':') >= 0 && address.length == 4) {
            throw new IllegalArgumentException("CIDR IPv4-mapped e ambiguo.");
        }

        int prefix = parsePrefix(value.substring(separator + 1), address.length * Byte.SIZE);
        byte[] masked = mask(address, prefix);
        if (!Arrays.equals(address, masked)) {
            throw new IllegalArgumentException("CIDR deve informar o endereco exato da rede.");
        }
        return new IpNetwork(masked, prefix);
    }

    static List<IpNetwork> parseCsv(String configuredCidrs) {
        if (configuredCidrs == null || configuredCidrs.isBlank()) {
            return List.of();
        }

        String[] values = configuredCidrs.split(",", -1);
        List<IpNetwork> networks = new ArrayList<>(values.length);
        for (String value : values) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Lista de proxies confiaveis invalida.");
            }
            networks.add(parse(trimmed));
        }
        return List.copyOf(networks);
    }

    boolean contains(byte[] address) {
        return address != null
                && address.length == networkAddress.length
                && Arrays.equals(mask(address, prefixLength), networkAddress);
    }

    static byte[] parseAddress(String literal) {
        if (literal == null || literal.isEmpty() || !literal.equals(literal.trim())) {
            throw new IllegalArgumentException("Endereco IP invalido.");
        }
        if (literal.indexOf(':') >= 0) {
            return normalizeMappedIpv4(parseIpv6(literal));
        }
        return parseIpv4(literal);
    }

    static byte[] rateIdentityAddress(byte[] address) {
        if (address == null || (address.length != 4 && address.length != 16)) {
            throw new IllegalArgumentException("Endereco IP invalido.");
        }
        byte[] normalized = address.clone();
        if (normalized.length == 16) {
            Arrays.fill(normalized, 8, normalized.length, (byte) 0);
        }
        return normalized;
    }

    private static int parsePrefix(String value, int addressBits) {
        if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
            throw new IllegalArgumentException("Prefixo CIDR invalido.");
        }
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("Prefixo CIDR invalido.");
            }
        }
        try {
            int prefix = Integer.parseInt(value);
            if (prefix <= 0 || prefix > addressBits) {
                throw new IllegalArgumentException("Prefixo CIDR invalido.");
            }
            return prefix;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Prefixo CIDR invalido.", exception);
        }
    }

    private static byte[] mask(byte[] address, int prefixLength) {
        byte[] masked = address.clone();
        int wholeBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        if (remainingBits != 0) {
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            masked[wholeBytes] = (byte) (masked[wholeBytes] & mask);
            wholeBytes++;
        }
        Arrays.fill(masked, wholeBytes, masked.length, (byte) 0);
        return masked;
    }

    private static byte[] parseIpv4(String literal) {
        String[] octets = literal.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Endereco IPv4 invalido.");
        }

        byte[] result = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3 || (octet.length() > 1 && octet.charAt(0) == '0')) {
                throw new IllegalArgumentException("Endereco IPv4 invalido.");
            }
            int value = 0;
            for (int character = 0; character < octet.length(); character++) {
                char digit = octet.charAt(character);
                if (digit < '0' || digit > '9') {
                    throw new IllegalArgumentException("Endereco IPv4 invalido.");
                }
                value = value * 10 + digit - '0';
            }
            if (value > 255) {
                throw new IllegalArgumentException("Endereco IPv4 invalido.");
            }
            result[index] = (byte) value;
        }
        return result;
    }

    private static byte[] parseIpv6(String literal) {
        if (!isIpv6CharacterSet(literal) || literal.indexOf('%') >= 0) {
            throw new IllegalArgumentException("Endereco IPv6 invalido.");
        }

        int compression = literal.indexOf("::");
        if (compression >= 0 && literal.indexOf("::", compression + 2) >= 0) {
            throw new IllegalArgumentException("Endereco IPv6 invalido.");
        }

        List<Integer> groups = new ArrayList<>(8);
        if (compression < 0) {
            appendGroups(literal, true, groups);
            if (groups.size() != 8) {
                throw new IllegalArgumentException("Endereco IPv6 invalido.");
            }
        } else {
            String head = literal.substring(0, compression);
            String tail = literal.substring(compression + 2);
            appendGroups(head, false, groups);
            List<Integer> tailGroups = new ArrayList<>(8);
            appendGroups(tail, true, tailGroups);
            int omitted = 8 - groups.size() - tailGroups.size();
            if (omitted < 1) {
                throw new IllegalArgumentException("Endereco IPv6 invalido.");
            }
            for (int index = 0; index < omitted; index++) {
                groups.add(0);
            }
            groups.addAll(tailGroups);
        }

        byte[] result = new byte[16];
        for (int index = 0; index < groups.size(); index++) {
            int group = groups.get(index);
            result[index * 2] = (byte) (group >>> 8);
            result[index * 2 + 1] = (byte) group;
        }
        return result;
    }

    private static void appendGroups(String value, boolean allowIpv4AtEnd, List<Integer> destination) {
        if (value.isEmpty()) {
            return;
        }
        String[] parts = value.split(":", -1);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Endereco IPv6 invalido.");
            }
            if (part.indexOf('.') >= 0) {
                if (!allowIpv4AtEnd || index != parts.length - 1) {
                    throw new IllegalArgumentException("Endereco IPv6 invalido.");
                }
                byte[] ipv4 = parseIpv4(part);
                destination.add(((ipv4[0] & 0xFF) << 8) | (ipv4[1] & 0xFF));
                destination.add(((ipv4[2] & 0xFF) << 8) | (ipv4[3] & 0xFF));
            } else {
                destination.add(parseHexGroup(part));
            }
        }
    }

    private static int parseHexGroup(String value) {
        if (value.isEmpty() || value.length() > 4) {
            throw new IllegalArgumentException("Endereco IPv6 invalido.");
        }
        int result = 0;
        for (int index = 0; index < value.length(); index++) {
            int digit = asciiHexDigit(value.charAt(index));
            if (digit < 0) {
                throw new IllegalArgumentException("Endereco IPv6 invalido.");
            }
            result = (result << 4) | digit;
        }
        return result;
    }

    private static boolean isIpv6CharacterSet(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != ':' && character != '.' && asciiHexDigit(character) < 0) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private static int asciiHexDigit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        return -1;
    }

    private static byte[] normalizeMappedIpv4(byte[] address) {
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return address;
            }
        }
        if (address[10] != (byte) 0xFF || address[11] != (byte) 0xFF) {
            return address;
        }
        return Arrays.copyOfRange(address, 12, 16);
    }
}
