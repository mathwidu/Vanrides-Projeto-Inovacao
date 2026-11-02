package com.vanrides.vanrides.dto;

import java.util.List;

public class AdminServicoPassageirosUpdateRequest {

    private List<Long> passageiroIds;

    public List<Long> getPassageiroIds() {
        return passageiroIds;
    }

    public void setPassageiroIds(List<Long> passageiroIds) {
        this.passageiroIds = passageiroIds;
    }
}
