package com.vanrides.vanrides.service;

import java.util.List;

final class PolylineEncoder {
    private PolylineEncoder() {
    }

    static String encode(List<List<Double>> points) {
        StringBuilder encoded = new StringBuilder();
        long lastLat = 0;
        long lastLng = 0;
        for (List<Double> point : points) {
            long lat = Math.round(point.get(0) * 1e5);
            long lng = Math.round(point.get(1) * 1e5);
            encodeValue(lat - lastLat, encoded);
            encodeValue(lng - lastLng, encoded);
            lastLat = lat;
            lastLng = lng;
        }
        return encoded.toString();
    }

    private static void encodeValue(long value, StringBuilder encoded) {
        long shifted = value < 0 ? ~(value << 1) : (value << 1);
        while (shifted >= 0x20) {
            encoded.append((char) ((0x20 | (shifted & 0x1f)) + 63));
            shifted >>= 5;
        }
        encoded.append((char) (shifted + 63));
    }
}
