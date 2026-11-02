package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemParadaStatus;

public class ViagemParadaStatusUpdateRequest {

    private ViagemParadaStatus status;

    public ViagemParadaStatus getStatus() {
        return status;
    }

    public void setStatus(ViagemParadaStatus status) {
        this.status = status;
    }
}
