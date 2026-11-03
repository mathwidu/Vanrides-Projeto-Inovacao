package com.vanrides.vanrides.dto;

public class NavigationRoutePlanInstructionResponse {

    private final int index;
    private final String text;
    private final String voiceText;
    private final Integer distanceMeters;
    private final String maneuver;

    public NavigationRoutePlanInstructionResponse(int index,
                                                  String text,
                                                  String voiceText,
                                                  Integer distanceMeters,
                                                  String maneuver) {
        this.index = index;
        this.text = text;
        this.voiceText = voiceText;
        this.distanceMeters = distanceMeters;
        this.maneuver = maneuver;
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }

    public String getVoiceText() {
        return voiceText;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public String getManeuver() {
        return maneuver;
    }
}
