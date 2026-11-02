package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;

public class ViagemRotaGeograficaResponse {

    private final String provider;
    private final String polyline;
    private final ViagemNavegacaoResponse navegacao;
    private final ViagemNavegacaoResponse navegacaoRestante;
    private final List<ViagemRotaWaypointResponse> waypoints;
    private final List<ViagemRotaLegResponse> legs;
    private final Integer filaTotalParadas;
    private final Integer posicaoPassageiroNaFila;
    private final Integer paradasAntesDoPassageiro;
    private final LocalDateTime calculadaEm;
    private final boolean snapshotOperacional;
    private final String snapshotKey;
    private final String routeInputHash;
    private final RouteSnapshotStatus routeSnapshotStatus;
    private final RouteRecomputeReason routeRecomputeReason;
    private final LocalDateTime routeRecomputeAllowedAfter;
    private final NavigationRoutePlanResponse navigationRoutePlan;

    public ViagemRotaGeograficaResponse(String provider,
                                        String polyline,
                                        ViagemNavegacaoResponse navegacao,
                                        List<ViagemRotaWaypointResponse> waypoints,
                                        List<ViagemRotaLegResponse> legs,
                                        Integer filaTotalParadas,
                                        Integer posicaoPassageiroNaFila,
                                        Integer paradasAntesDoPassageiro) {
        this(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiroNaFila,
                paradasAntesDoPassageiro,
                navegacao,
                null,
                false
        );
    }

    public ViagemRotaGeograficaResponse(String provider,
                                        String polyline,
                                        ViagemNavegacaoResponse navegacao,
                                        List<ViagemRotaWaypointResponse> waypoints,
                                        List<ViagemRotaLegResponse> legs,
                                        Integer filaTotalParadas,
                                        Integer posicaoPassageiroNaFila,
                                        Integer paradasAntesDoPassageiro,
                                        LocalDateTime calculadaEm,
                                        boolean snapshotOperacional) {
        this(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiroNaFila,
                paradasAntesDoPassageiro,
                navegacao,
                calculadaEm,
                snapshotOperacional
        );
    }

    public ViagemRotaGeograficaResponse(String provider,
                                        String polyline,
                                        ViagemNavegacaoResponse navegacao,
                                        List<ViagemRotaWaypointResponse> waypoints,
                                        List<ViagemRotaLegResponse> legs,
                                        Integer filaTotalParadas,
                                        Integer posicaoPassageiroNaFila,
                                        Integer paradasAntesDoPassageiro,
                                        ViagemNavegacaoResponse navegacaoRestante,
                                        LocalDateTime calculadaEm,
                                        boolean snapshotOperacional) {
        this(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiroNaFila,
                paradasAntesDoPassageiro,
                navegacaoRestante,
                calculadaEm,
                snapshotOperacional,
                null,
                null,
                resolveSnapshotStatus(snapshotOperacional, polyline, navegacao, navegacaoRestante, waypoints, legs),
                snapshotOperacional ? RouteRecomputeReason.TRIP_STARTED : RouteRecomputeReason.NONE,
                snapshotOperacional && calculadaEm != null ? calculadaEm.plusMinutes(2) : null
        );
    }

    public ViagemRotaGeograficaResponse(String provider,
                                        String polyline,
                                        ViagemNavegacaoResponse navegacao,
                                        List<ViagemRotaWaypointResponse> waypoints,
                                        List<ViagemRotaLegResponse> legs,
                                        Integer filaTotalParadas,
                                        Integer posicaoPassageiroNaFila,
                                        Integer paradasAntesDoPassageiro,
                                        ViagemNavegacaoResponse navegacaoRestante,
                                        LocalDateTime calculadaEm,
                                        boolean snapshotOperacional,
                                        String snapshotKey,
                                        String routeInputHash,
                                        RouteSnapshotStatus routeSnapshotStatus,
                                        RouteRecomputeReason routeRecomputeReason,
                                        LocalDateTime routeRecomputeAllowedAfter) {
        this(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiroNaFila,
                paradasAntesDoPassageiro,
                navegacaoRestante,
                calculadaEm,
                snapshotOperacional,
                snapshotKey,
                routeInputHash,
                routeSnapshotStatus,
                routeRecomputeReason,
                routeRecomputeAllowedAfter,
                null
        );
    }

    private ViagemRotaGeograficaResponse(String provider,
                                         String polyline,
                                         ViagemNavegacaoResponse navegacao,
                                         List<ViagemRotaWaypointResponse> waypoints,
                                         List<ViagemRotaLegResponse> legs,
                                         Integer filaTotalParadas,
                                         Integer posicaoPassageiroNaFila,
                                         Integer paradasAntesDoPassageiro,
                                         ViagemNavegacaoResponse navegacaoRestante,
                                         LocalDateTime calculadaEm,
                                         boolean snapshotOperacional,
                                         String snapshotKey,
                                         String routeInputHash,
                                         RouteSnapshotStatus routeSnapshotStatus,
                                         RouteRecomputeReason routeRecomputeReason,
                                         LocalDateTime routeRecomputeAllowedAfter,
                                         NavigationRoutePlanResponse navigationRoutePlan) {
        this.provider = provider;
        this.polyline = polyline;
        this.navegacao = navegacao;
        this.navegacaoRestante = navegacaoRestante;
        this.waypoints = waypoints;
        this.legs = legs;
        this.filaTotalParadas = filaTotalParadas;
        this.posicaoPassageiroNaFila = posicaoPassageiroNaFila;
        this.paradasAntesDoPassageiro = paradasAntesDoPassageiro;
        this.calculadaEm = calculadaEm;
        this.snapshotOperacional = snapshotOperacional;
        this.snapshotKey = snapshotKey;
        this.routeInputHash = routeInputHash;
        this.routeSnapshotStatus = routeSnapshotStatus == null
                ? resolveSnapshotStatus(snapshotOperacional, polyline, navegacao, navegacaoRestante, waypoints, legs)
                : routeSnapshotStatus;
        this.routeRecomputeReason = routeRecomputeReason == null
                ? RouteRecomputeReason.NONE
                : routeRecomputeReason;
        this.routeRecomputeAllowedAfter = routeRecomputeAllowedAfter;
        this.navigationRoutePlan = navigationRoutePlan;
    }

    public ViagemRotaGeograficaResponse withNavigationRoutePlan(NavigationRoutePlanResponse navigationRoutePlan) {
        return new ViagemRotaGeograficaResponse(
                provider,
                polyline,
                navegacao,
                waypoints,
                legs,
                filaTotalParadas,
                posicaoPassageiroNaFila,
                paradasAntesDoPassageiro,
                navegacaoRestante,
                calculadaEm,
                snapshotOperacional,
                snapshotKey,
                routeInputHash,
                routeSnapshotStatus,
                routeRecomputeReason,
                routeRecomputeAllowedAfter,
                navigationRoutePlan
        );
    }

    public String getProvider() {
        return provider;
    }

    public String getPolyline() {
        return polyline;
    }

    public ViagemNavegacaoResponse getNavegacao() {
        return navegacao;
    }

    public ViagemNavegacaoResponse getNavegacaoRestante() {
        return navegacaoRestante;
    }

    public List<ViagemRotaWaypointResponse> getWaypoints() {
        return waypoints;
    }

    public List<ViagemRotaLegResponse> getLegs() {
        return legs;
    }

    public Integer getFilaTotalParadas() {
        return filaTotalParadas;
    }

    public Integer getPosicaoPassageiroNaFila() {
        return posicaoPassageiroNaFila;
    }

    public Integer getParadasAntesDoPassageiro() {
        return paradasAntesDoPassageiro;
    }

    public LocalDateTime getCalculadaEm() {
        return calculadaEm;
    }

    public boolean isSnapshotOperacional() {
        return snapshotOperacional;
    }

    public String getSnapshotKey() {
        return snapshotKey;
    }

    public String getRouteInputHash() {
        return routeInputHash;
    }

    public RouteSnapshotStatus getRouteSnapshotStatus() {
        return routeSnapshotStatus;
    }

    public RouteRecomputeReason getRouteRecomputeReason() {
        return routeRecomputeReason;
    }

    public LocalDateTime getRouteRecomputeAllowedAfter() {
        return routeRecomputeAllowedAfter;
    }

    @JsonIgnore
    public NavigationRoutePlanResponse getNavigationRoutePlan() {
        return navigationRoutePlan;
    }

    private static RouteSnapshotStatus resolveSnapshotStatus(boolean snapshotOperacional,
                                                            String polyline,
                                                            ViagemNavegacaoResponse navegacao,
                                                            ViagemNavegacaoResponse navegacaoRestante,
                                                            List<ViagemRotaWaypointResponse> waypoints,
                                                            List<ViagemRotaLegResponse> legs) {
        if (snapshotOperacional) {
            return RouteSnapshotStatus.MATERIALIZED;
        }
        if (polyline != null
                || navegacao != null
                || navegacaoRestante != null
                || (waypoints != null && !waypoints.isEmpty())
                || (legs != null && !legs.isEmpty())) {
            return RouteSnapshotStatus.PLANNED;
        }
        return RouteSnapshotStatus.UNAVAILABLE;
    }
}
