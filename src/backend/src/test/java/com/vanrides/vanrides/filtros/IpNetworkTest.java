package com.vanrides.vanrides.filtros;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpNetworkTest {

    @Test
    void shouldMatchExactIpv4CidrBoundaries() {
        IpNetwork network = IpNetwork.parse("10.20.30.0/24");

        assertThat(network.contains(IpNetwork.parseAddress("10.20.30.0"))).isTrue();
        assertThat(network.contains(IpNetwork.parseAddress("10.20.30.255"))).isTrue();
        assertThat(network.contains(IpNetwork.parseAddress("10.20.31.0"))).isFalse();
    }

    @Test
    void shouldMatchExactIpv6CidrBoundaries() {
        IpNetwork network = IpNetwork.parse("2001:db8:abcd::/48");

        assertThat(network.contains(IpNetwork.parseAddress("2001:db8:abcd::1"))).isTrue();
        assertThat(network.contains(IpNetwork.parseAddress("2001:db8:abce::1"))).isFalse();
        assertThat(network.contains(IpNetwork.parseAddress("10.20.30.1"))).isFalse();
    }

    @Test
    void shouldNormalizeIpv4MappedIpv6Address() {
        assertThat(IpNetwork.parseAddress("::ffff:192.0.2.15"))
                .containsExactly(IpNetwork.parseAddress("192.0.2.15"));
        assertThat(IpNetwork.parseAddress("0:0:0:0:0:ffff:c000:20f"))
                .containsExactly(IpNetwork.parseAddress("192.0.2.15"));
    }

    @Test
    void shouldAggregateOnlyIpv6RateIdentityToSlash64() {
        assertThat(IpNetwork.rateIdentityAddress(IpNetwork.parseAddress("2001:db8:1:2::1")))
                .containsExactly(IpNetwork.rateIdentityAddress(IpNetwork.parseAddress("2001:db8:1:2:ffff::9")));
        assertThat(IpNetwork.rateIdentityAddress(IpNetwork.parseAddress("192.0.2.1")))
                .isNotEqualTo(IpNetwork.rateIdentityAddress(IpNetwork.parseAddress("192.0.2.2")));
    }

    @Test
    void shouldTreatBlankCidrConfigurationAsTrustNobody() {
        assertThat(IpNetwork.parseCsv(null)).isEmpty();
        assertThat(IpNetwork.parseCsv("")).isEmpty();
        assertThat(IpNetwork.parseCsv("   ")).isEmpty();
    }

    @Test
    void shouldParseMultipleExactCidrs() {
        List<IpNetwork> networks = IpNetwork.parseCsv("10.0.0.1/32, 2001:db8::/32");

        assertThat(networks).hasSize(2);
        assertThat(networks.get(0).contains(IpNetwork.parseAddress("10.0.0.1"))).isTrue();
        assertThat(networks.get(1).contains(IpNetwork.parseAddress("2001:db8::5"))).isTrue();
    }

    @Test
    void shouldRejectZeroPrefixAndNonCanonicalNetwork() {
        assertThatThrownBy(() -> IpNetwork.parse("0.0.0.0/0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("::/0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("10.0.0.1/24"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("2001:db8::1/64"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCidrConfiguration() {
        assertThatThrownBy(() -> IpNetwork.parseCsv("10.0.0.1/32,"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("10.0.0.0/033"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parse("::ffff:192.0.2.0/120"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAnythingOtherThanStrictNumericLiterals() {
        assertThatThrownBy(() -> IpNetwork.parseAddress("localhost"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parseAddress("127.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parseAddress("010.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parseAddress("[2001:db8::1]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parseAddress("2001:db8::1%eth0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpNetwork.parseAddress("192.0.2.1:8080"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
