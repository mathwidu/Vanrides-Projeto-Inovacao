package com.vanrides.vanrides.service;

import java.util.ArrayList;
import java.util.List;

final class GooglePolylineCodec {

    private GooglePolylineCodec() {
    }

    static String merge(List<String> polylines) {
        List<Point> mergedPoints = new ArrayList<>();
        for (String polyline : polylines) {
            if (polyline == null || polyline.isBlank()) {
                continue;
            }
            List<Point> decodedPoints = decode(polyline);
            for (Point point : decodedPoints) {
                if (mergedPoints.isEmpty() || !sameCoordinate(mergedPoints.get(mergedPoints.size() - 1), point)) {
                    mergedPoints.add(point);
                }
            }
        }

        if (mergedPoints.size() < 2) {
            return null;
        }

        return encode(mergedPoints);
    }

    static List<Point> decode(String encoded) {
        List<Point> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;

        while (index < encoded.length()) {
            int[] latitudeDecode = decodeValue(encoded, index);
            latitude += latitudeDecode[0];
            index = latitudeDecode[1];

            int[] longitudeDecode = decodeValue(encoded, index);
            longitude += longitudeDecode[0];
            index = longitudeDecode[1];

            points.add(new Point(latitude / 1e5d, longitude / 1e5d));
        }
        return points;
    }

    private static int[] decodeValue(String encoded, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;
        int chunk;

        do {
            chunk = encoded.charAt(index++) - 63;
            result |= (chunk & 0x1f) << shift;
            shift += 5;
        } while (chunk >= 0x20 && index < encoded.length());

        int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{delta, index};
    }

    static String encode(List<Point> points) {
        StringBuilder encoded = new StringBuilder();
        long lastLat = 0;
        long lastLng = 0;

        for (Point point : points) {
            long lat = Math.round(point.latitude() * 1e5);
            long lng = Math.round(point.longitude() * 1e5);
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

    private static boolean sameCoordinate(Point left, Point right) {
        return Double.compare(left.latitude(), right.latitude()) == 0
                && Double.compare(left.longitude(), right.longitude()) == 0;
    }

    record Point(double latitude, double longitude) {
    }
}
