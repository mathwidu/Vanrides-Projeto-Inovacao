package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GooglePolylineCodecTest {

    @Test
    void shouldDecodeKnownGooglePolyline() {
        List<GooglePolylineCodec.Point> points = GooglePolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");

        assertThat(points).hasSize(3);
        assertThat(points.get(0).latitude()).isCloseTo(38.5, within(0.00001));
        assertThat(points.get(0).longitude()).isCloseTo(-120.2, within(0.00001));
        assertThat(points.get(1).latitude()).isCloseTo(40.7, within(0.00001));
        assertThat(points.get(1).longitude()).isCloseTo(-120.95, within(0.00001));
        assertThat(points.get(2).latitude()).isCloseTo(43.252, within(0.00001));
        assertThat(points.get(2).longitude()).isCloseTo(-126.453, within(0.00001));
    }

    @Test
    void shouldEncodeAndDecodeRoundTrip() {
        List<GooglePolylineCodec.Point> originalPoints = List.of(
                new GooglePolylineCodec.Point(-29.57280, -50.79150),
                new GooglePolylineCodec.Point(-29.57550, -50.78690),
                new GooglePolylineCodec.Point(-29.56890, -50.78180)
        );

        String encoded = GooglePolylineCodec.encode(originalPoints);
        List<GooglePolylineCodec.Point> decodedPoints = GooglePolylineCodec.decode(encoded);

        assertThat(decodedPoints).containsExactlyElementsOf(originalPoints);
    }

    @Test
    void shouldMergePolylinesWithoutDuplicatingAdjacentPoints() {
        GooglePolylineCodec.Point first = new GooglePolylineCodec.Point(-29.57280, -50.79150);
        GooglePolylineCodec.Point shared = new GooglePolylineCodec.Point(-29.57550, -50.78690);
        GooglePolylineCodec.Point last = new GooglePolylineCodec.Point(-29.56890, -50.78180);
        String firstLeg = GooglePolylineCodec.encode(List.of(first, shared));
        String secondLeg = GooglePolylineCodec.encode(List.of(shared, last));

        String merged = GooglePolylineCodec.merge(List.of(firstLeg, secondLeg));

        assertThat(GooglePolylineCodec.decode(merged)).containsExactly(first, shared, last);
    }

    @Test
    void shouldReturnNullWhenMergeDoesNotHaveEnoughPoints() {
        String singlePointPolyline = GooglePolylineCodec.encode(List.of(
                new GooglePolylineCodec.Point(-29.57280, -50.79150)
        ));

        String merged = GooglePolylineCodec.merge(List.of("", singlePointPolyline));

        assertThat(merged).isNull();
    }
}
