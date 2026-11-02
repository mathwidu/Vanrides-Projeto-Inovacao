package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.NavigationRoutePlanResponse;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;

import java.util.List;

record MapsPlatformPlannedRoute(
        String provider,
        String polyline,
        List<ViagemRotaLegResponse> legs,
        NavigationRoutePlanResponse navigationRoutePlan
) {
    ViagemRotaGeograficaResponse toRotaGeograficaResponse(ViagemNavegacaoResponse navegacao,
                                                          List<ViagemRotaWaypointResponse> waypoints,
                                                          int filaTotalParadas,
                                                          Integer posicaoPassageiro,
                                                          ViagemNavegacaoResponse navegacaoRestante) {
        return new ViagemRotaGeograficaResponse(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiro,
                posicaoPassageiro == null ? null : Math.max(posicaoPassageiro - 1, 0),
                navegacaoRestante,
                null,
                false
        ).withNavigationRoutePlan(navigationRoutePlan);
    }
}
