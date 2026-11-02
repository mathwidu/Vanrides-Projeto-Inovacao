package com.vanrides.vanrides.filtros;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.EMPTY_FORWARDED_HEADER;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.EMPTY_FORWARDED_HOP;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.FORWARDED_HEADER_TOO_LARGE;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.INVALID_FORWARDED_ADDRESS;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.INVALID_REMOTE_ADDRESS;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.MULTIPLE_FORWARDED_HEADERS;
import static com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver.RejectionReason.TOO_MANY_FORWARDED_HOPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedProxyClientAddressResolverTest {

    private static final byte[] SALT = new byte[] {
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31
    };

    @Test
    void shouldIgnoreForwardedHeaderFromDirectClient() {
        TrustedProxyClientAddressResolver resolver = resolver("");
        MockHttpServletRequest request = request("198.51.100.10");
        request.addHeader("X-Forwarded-For", "localhost, invalid");

        TrustedProxyClientAddressResolver.ClientIdentity actual = resolver.resolve(request);

        assertThat(actual.source()).isEqualTo(TrustedProxyClientAddressResolver.Source.DIRECT);
        assertThat(actual.digest()).isEqualTo(digestFor(resolver, "198.51.100.10"));
    }

    @Test
    void shouldNotReadForwardedHeadersUnlessPeerIsTrusted() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public Enumeration<String> getHeaders(String name) {
                throw new AssertionError("XFF nao deveria ser lido");
            }
        };
        request.setRemoteAddr("198.51.100.10");

        assertThat(resolver.resolve(request).source())
                .isEqualTo(TrustedProxyClientAddressResolver.Source.DIRECT);
    }

    @Test
    void shouldUseTrustedPeerWhenForwardedHeaderIsAbsent() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");

        TrustedProxyClientAddressResolver.ClientIdentity actual = resolver.resolve(request("10.0.0.5"));

        assertThat(actual.source()).isEqualTo(TrustedProxyClientAddressResolver.Source.PROXY_PEER);
        assertThat(actual.digest()).isEqualTo(digestFor(resolver(""), "10.0.0.5"));
    }

    @Test
    void shouldResolveSingleClientFromTrustedProxy() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");
        MockHttpServletRequest request = request("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        TrustedProxyClientAddressResolver.ClientIdentity actual = resolver.resolve(request);

        assertThat(actual.source()).isEqualTo(TrustedProxyClientAddressResolver.Source.TRUSTED_PROXY);
        assertThat(actual.digest()).isEqualTo(digestFor(resolver(""), "203.0.113.9"));
        assertThat(actual.digest()).doesNotContain("203.0.113.9").hasSize(32);
    }

    @Test
    void shouldWalkTrustedChainRightToLeftAndIgnoreSpoofedPrefix() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24,192.0.2.0/24");
        MockHttpServletRequest first = request("10.0.0.5");
        first.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.9, 192.0.2.4");
        MockHttpServletRequest second = request("10.0.0.5");
        second.addHeader("X-Forwarded-For", "198.51.100.250, 203.0.113.9, 192.0.2.4");

        assertThat(resolver.resolve(first).digest()).isEqualTo(resolver.resolve(second).digest());
        assertThat(resolver.resolve(first).digest()).isEqualTo(digestFor(resolver(""), "203.0.113.9"));
    }

    @Test
    void shouldAggregateIpv6ClientsBySlash64ButKeepIpv4AtSlash32() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");

        assertThat(resolveForwarded(resolver, "2001:db8:1:2::1").digest())
                .isEqualTo(resolveForwarded(resolver, "2001:db8:1:2:ffff::9").digest());
        assertThat(resolveForwarded(resolver, "2001:db8:1:3::1").digest())
                .isNotEqualTo(resolveForwarded(resolver, "2001:db8:1:2::1").digest());
        assertThat(resolveForwarded(resolver, "198.51.100.1").digest())
                .isNotEqualTo(resolveForwarded(resolver, "198.51.100.2").digest());
    }

    @Test
    void shouldNormalizeIpv4MappedAddressToIpv4Identity() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");

        assertThat(resolveForwarded(resolver, "::ffff:198.51.100.8").digest())
                .isEqualTo(resolveForwarded(resolver, "198.51.100.8").digest());
    }

    @Test
    void shouldRejectMultipleForwardedHeaderInstances() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");
        HttpServletRequest request = requestWithHeaders("10.0.0.5", List.of("203.0.113.1", "203.0.113.2"));

        assertRejected(resolver, request, MULTIPLE_FORWARDED_HEADERS);
    }

    @Test
    void shouldRejectEmptyHeaderAndEmptyHop() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");
        assertRejected(resolver, requestWithHeaders("10.0.0.5", List.of("   ")), EMPTY_FORWARDED_HEADER);
        assertRejected(resolver, requestWithHeaders("10.0.0.5", List.of("203.0.113.1,,192.0.2.1")), EMPTY_FORWARDED_HOP);
    }

    @Test
    void shouldRejectOversizedHeaderBeforeParsing() {
        TrustedProxyClientAddressResolver resolver = new TrustedProxyClientAddressResolver(
                "10.0.0.0/24", 8, 10, SALT
        );
        assertRejected(
                resolver,
                requestWithHeaders("10.0.0.5", List.of("203.0.113.1")),
                FORWARDED_HEADER_TOO_LARGE
        );
    }

    @Test
    void shouldRejectTooManyForwardedHops() {
        TrustedProxyClientAddressResolver resolver = new TrustedProxyClientAddressResolver(
                "10.0.0.0/24", 1024, 2, SALT
        );
        assertRejected(
                resolver,
                requestWithHeaders("10.0.0.5", List.of("203.0.113.1, 192.0.2.1, 192.0.2.2")),
                TOO_MANY_FORWARDED_HOPS
        );
    }

    @Test
    void shouldRejectInvalidNumericAddressWithoutIncludingItInException() {
        TrustedProxyClientAddressResolver resolver = resolver("10.0.0.0/24");
        HttpServletRequest request = requestWithHeaders("10.0.0.5", List.of("attacker.example"));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOfSatisfying(
                        TrustedProxyClientAddressResolver.ResolutionException.class,
                        exception -> {
                            assertThat(exception.reason()).isEqualTo(INVALID_FORWARDED_ADDRESS);
                            assertThat(exception.getMessage()).doesNotContain("attacker.example");
                        }
                );
    }

    @Test
    void shouldRejectInvalidRemoteAddressWithTypedReason() {
        assertRejected(resolver(""), request("localhost"), INVALID_REMOTE_ADDRESS);
    }

    @Test
    void shouldRejectInvalidConfigurationAndWeakSalt() {
        assertThatThrownBy(() -> resolver("0.0.0.0/0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustedProxyClientAddressResolver("", 0, 10, SALT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustedProxyClientAddressResolver("", 1024, 0, SALT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrustedProxyClientAddressResolver("", 1024, 10, new byte[8]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepTelemetryReasonsClosedAndStable() {
        assertThat(TrustedProxyClientAddressResolver.RejectionReason.values()).containsExactly(
                TrustedProxyClientAddressResolver.RejectionReason.INVALID_REMOTE_ADDRESS,
                TrustedProxyClientAddressResolver.RejectionReason.MULTIPLE_FORWARDED_HEADERS,
                TrustedProxyClientAddressResolver.RejectionReason.EMPTY_FORWARDED_HEADER,
                TrustedProxyClientAddressResolver.RejectionReason.FORWARDED_HEADER_TOO_LARGE,
                TrustedProxyClientAddressResolver.RejectionReason.TOO_MANY_FORWARDED_HOPS,
                TrustedProxyClientAddressResolver.RejectionReason.EMPTY_FORWARDED_HOP,
                TrustedProxyClientAddressResolver.RejectionReason.INVALID_FORWARDED_ADDRESS
        );
    }

    private static TrustedProxyClientAddressResolver resolver(String trustedCidrs) {
        return new TrustedProxyClientAddressResolver(trustedCidrs, 1024, 10, SALT);
    }

    private static MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static HttpServletRequest requestWithHeaders(String remoteAddress, List<String> headers) {
        return new MockHttpServletRequest() {
            @Override
            public String getRemoteAddr() {
                return remoteAddress;
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return "X-Forwarded-For".equalsIgnoreCase(name)
                        ? Collections.enumeration(headers)
                        : Collections.emptyEnumeration();
            }
        };
    }

    private static TrustedProxyClientAddressResolver.ClientIdentity resolveForwarded(
            TrustedProxyClientAddressResolver resolver,
            String forwarded
    ) {
        return resolver.resolve(requestWithHeaders("10.0.0.5", List.of(forwarded)));
    }

    private static String digestFor(TrustedProxyClientAddressResolver resolver, String remoteAddress) {
        return resolver.resolve(request(remoteAddress)).digest();
    }

    private static void assertRejected(
            TrustedProxyClientAddressResolver resolver,
            HttpServletRequest request,
            TrustedProxyClientAddressResolver.RejectionReason reason
    ) {
        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOfSatisfying(
                        TrustedProxyClientAddressResolver.ResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason)
                );
    }
}
