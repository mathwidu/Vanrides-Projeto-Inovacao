package com.vanrides.vanrides.dto;

import java.util.List;

public class ViagemRotaUpdateRequest {

    private List<Long> paradaIds;

    public List<Long> getParadaIds() {
        return paradaIds;
    }

    public void setParadaIds(List<Long> paradaIds) {
        this.paradaIds = paradaIds;
    }
}
