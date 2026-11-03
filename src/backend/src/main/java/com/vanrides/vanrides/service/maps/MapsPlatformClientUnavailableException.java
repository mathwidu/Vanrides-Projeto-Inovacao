package com.vanrides.vanrides.service.maps;

public class MapsPlatformClientUnavailableException extends RuntimeException {

    public MapsPlatformClientUnavailableException(String message) {
        super(message);
    }

    public MapsPlatformClientUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
