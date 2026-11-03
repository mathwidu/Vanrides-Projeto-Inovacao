part of 'home_motorista_screen.dart';

extension _HomeMotoristaTabs on _HomeMotoristaScreenState {
  Widget _buildMapTab(BuildContext context, DriverTripRecord? trip) {
    return DriverFullMapTab(
      isLoading: _loading,
      errorMessage: _errorMessage,
      trip: trip,
      motoristaId: widget.motoristaId,
      email: widget.email,
      senha: widget.senha,
      accessToken: widget.accessToken,
      liveLocationGateway: widget.liveLocationGateway,
      liveLocationWebSocketService: widget.liveLocationWebSocketService,
      mapRenderMode: widget.mapRenderMode,
      driverLiveLocation: _driverLiveLocation,
      onRefresh: _loadTrip,
    );
  }

  Widget _buildStopsTab(BuildContext context, DriverTripRecord? trip) {
    return DriverStopsTab(
      isLoading: _loading,
      errorMessage: _errorMessage,
      trip: trip,
      onRefresh: _loadTrip,
    );
  }

  Widget _buildAccountTab(BuildContext context) {
    return DriverAccountTab(
      nome: widget.nome,
      email: widget.email,
      enabled: _currentTabIndex == 3,
      accountProfileGateway:
          widget.accountProfileGateway ??
          AccountProfileService(
            email: widget.email,
            senha: widget.senha,
            accessToken: widget.accessToken,
          ),
      trip: _trip,
      dayTrips: _dayTrips,
      onLogout: () => _logout(context),
    );
  }
}
