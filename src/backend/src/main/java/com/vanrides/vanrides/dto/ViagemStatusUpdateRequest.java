package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemStatus;

public class ViagemStatusUpdateRequest {

    private ViagemStatus status;

    public ViagemStatus getStatus() {
        return status;
    }

    public void setStatus(ViagemStatus status) {
        this.status = status;
    }
}
