package com.vanrides.vanrides.dto;

import java.util.List;

public class AdminPassageiroServicosUpdateRequest {

    private List<Long> servicoIds;

    public List<Long> getServicoIds() {
        return servicoIds;
    }

    public void setServicoIds(List<Long> servicoIds) {
        this.servicoIds = servicoIds;
    }
}
