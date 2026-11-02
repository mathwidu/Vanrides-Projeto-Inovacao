package com.vanrides.vanrides.whatsapp.commands;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public record WaProfileState(UUID identityId, String role, String status, String name,
                             String noticeVersion, boolean driverEnabled, long version) {
    public static WaProfileState absent(UUID identity) { return new WaProfileState(identity, "UNREGISTERED", "ONBOARDING", null, null, false, 0); }
    public static WaProfileState read(ResultSet rs, int row) throws SQLException {
        return new WaProfileState(rs.getObject("identity_id", UUID.class), rs.getString("role"), rs.getString("status"),
            rs.getString("display_name"), rs.getString("notice_version"), rs.getBoolean("driver_enabled"), rs.getLong("version"));
    }
    public boolean blocked() { return "SUSPENDED".equals(status) || "CLOSED".equals(status); }
}
