import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:vanrides_frontend_dev/app/theme/vanrides_design_tokens.dart';
import 'package:vanrides_frontend_dev/app/widgets/vanrides_liquid_surface.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';
import 'package:vanrides_frontend_dev/services/auth_headers.dart';
import 'package:vanrides_frontend_dev/services/location_gateway.dart';
import 'package:vanrides_frontend_dev/services/location_sharing_use_case.dart';
import 'package:vanrides_frontend_dev/services/websocket_service.dart';

class CompartilharLocalizacaoScreen extends StatefulWidget {
  final int motoristaId;
  final String email;
  final String senha;
  final String? accessToken;
  final LocationGateway locationGateway;
  final WebSocketServiceBase webSocketService;

  static const iniciarCompartilhamentoButtonKey = Key(
    'motorista.localizacao.iniciar',
  );
  static const pararCompartilhamentoButtonKey = Key(
    'motorista.localizacao.parar',
  );

  CompartilharLocalizacaoScreen({
    super.key,
    required this.motoristaId,
    required this.email,
    required this.senha,
    this.accessToken,
    LocationGateway? locationGateway,
    WebSocketServiceBase? webSocketService,
  }) : locationGateway = locationGateway ?? GeolocatorLocationGateway(),
       webSocketService = webSocketService ?? WebSocketService();

  @override
  State<CompartilharLocalizacaoScreen> createState() =>
      _CompartilharLocalizacaoScreenState();
}

class _CompartilharLocalizacaoScreenState
    extends State<CompartilharLocalizacaoScreen> {
  bool compartilhando = false;
  StreamSubscription<Position>? _positionSubscription;
  late final LocationSharingUseCase _useCase;

  @override
  void initState() {
    super.initState();
    _useCase = LocationSharingUseCase(gateway: widget.locationGateway);
    final companyId = extractCompanyIdFromAccessToken(widget.accessToken);
    if (companyId == null) {
      debugPrint(
        '[DEBUG FLUTTER] ❌ accessToken sem companyId. WebSocket não será conectado.',
      );
      return;
    }

    widget.webSocketService.connect(
      authorizationHeader: buildAuthorizationHeader(
        accessToken: widget.accessToken,
        email: widget.email,
        senha: widget.senha,
      ),
      companyId: companyId,
    );
  }

  @override
  void dispose() {
    _positionSubscription?.cancel();
    _positionSubscription = null;
    widget.webSocketService.disconnect();
    super.dispose();
  }

  Future<void> startSharingLocation() async {
    try {
      final result = await _useCase.ensurePermissions();

      if (result is LocationSharingServiceDisabled) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.porFavorAtiveLocalizacaoNoDispositivo,
            ),
          ),
        );
        await widget.locationGateway.openLocationSettings();
        return;
      }

      if (result is LocationSharingPermissionDenied) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppStrings.motorista.permissaoDeLocalizacaoNegada),
          ),
        );
        return;
      }

      if (result is LocationSharingPermissionDeniedForever) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.permissaoLocalizacaoPermanentementeNegada,
            ),
            action: SnackBarAction(
              label: AppStrings.motorista.abrirAjustes,
              onPressed: () {
                widget.locationGateway.openAppSettings();
              },
            ),
          ),
        );
        return;
      }

      if (result is LocationSharingPermissionUnableToDetermine) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppStrings.motorista.unableDetermineLocationPermission,
            ),
          ),
        );
        return;
      }

      final allowBackground =
          (result as LocationSharingPermissionGranted).allowBackground;
      if (!allowBackground) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppStrings.motorista.localizacaoFuncionaComAppAberto),
            action: SnackBarAction(
              label: AppStrings.motorista.abrirAjustes,
              onPressed: () {
                widget.locationGateway.openAppSettings();
              },
            ),
          ),
        );
      }

      final currentSubscription = _positionSubscription;
      if (currentSubscription != null) {
        await currentSubscription.cancel();
      }
      final currentPosition = await widget.locationGateway.getCurrentPosition();
      widget.webSocketService.sendLocation(
        currentPosition.latitude,
        currentPosition.longitude,
        widget.motoristaId,
      );
      _positionSubscription = widget.locationGateway
          .getPositionStream(allowBackground: allowBackground)
          .listen(
            (Position position) {
              widget.webSocketService.sendLocation(
                position.latitude,
                position.longitude,
                widget.motoristaId,
              );
            },
            onError: (Object error, StackTrace stackTrace) {
              debugPrint(
                '[DEBUG FLUTTER] ❌ Erro no stream de localização: $error',
              );
              debugPrintStack(stackTrace: stackTrace);
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    AppStrings.motorista.erroAoObterLocalizacao(error),
                  ),
                ),
              );
              setState(() {
                compartilhando = false;
              });
            },
          );

      if (!mounted) return;
      setState(() {
        compartilhando = true;
      });
    } catch (e, st) {
      debugPrint(
        '[DEBUG FLUTTER] ❌ Falha ao iniciar compartilhamento de localização: $e',
      );
      debugPrintStack(stackTrace: st);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppStrings.motorista.falhaAoIniciarLocalizacao(e)),
        ),
      );
    }
  }

  void stopSharingLocation() {
    _positionSubscription?.cancel();
    _positionSubscription = null;
    setState(() {
      compartilhando = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final accent =
        compartilhando ? MapFirstPalette.green : MapFirstPalette.amber;
    final icon =
        compartilhando ? Icons.location_on_rounded : Icons.location_off_rounded;
    final message =
        compartilhando
            ? AppStrings.motorista.compartilhandoLocalizacaoTempoReal
            : AppStrings.motorista.localizacaoNaoEstaSendoCompartilhada;

    return Scaffold(
      backgroundColor: MapFirstPalette.background,
      appBar: AppBar(
        title: Text(AppStrings.motorista.compartilharLocalizacao),
        backgroundColor: Colors.transparent,
        foregroundColor: MapFirstPalette.textStrong,
        elevation: 0,
        surfaceTintColor: Colors.transparent,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: VanRidesLiquidSurface.card(
                semanticLabel: AppStrings.motorista.compartilharLocalizacao,
                borderRadius: 30,
                padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 96,
                      height: 96,
                      decoration: BoxDecoration(
                        color: accent.withValues(alpha: 0.14),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: accent.withValues(alpha: 0.28),
                        ),
                      ),
                      child: Icon(icon, size: 48, color: accent),
                    ),
                    const SizedBox(height: 22),
                    Text(
                      message,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        color: VanRidesDesignTokens.textPrimary,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      AppStrings.motorista.compartilharLocalizacao,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: VanRidesDesignTokens.textMuted,
                        height: 1.35,
                      ),
                    ),
                    const SizedBox(height: 24),
                    SizedBox(
                      width: double.infinity,
                      child:
                          compartilhando
                              ? OutlinedButton.icon(
                                key:
                                    CompartilharLocalizacaoScreen
                                        .pararCompartilhamentoButtonKey,
                                onPressed: stopSharingLocation,
                                icon: const Icon(Icons.stop_circle_outlined),
                                label: Text(
                                  AppStrings.motorista.pararCompartilhamento,
                                ),
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: MapFirstPalette.red,
                                  side: BorderSide(
                                    color: MapFirstPalette.red.withValues(
                                      alpha: 0.28,
                                    ),
                                  ),
                                  backgroundColor: MapFirstPalette.surfaceRaised
                                      .withValues(alpha: 0.72),
                                  minimumSize: const Size.fromHeight(54),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(18),
                                  ),
                                ),
                              )
                              : FilledButton.icon(
                                key:
                                    CompartilharLocalizacaoScreen
                                        .iniciarCompartilhamentoButtonKey,
                                onPressed: startSharingLocation,
                                icon: const Icon(Icons.near_me_outlined),
                                label: Text(
                                  AppStrings.motorista.iniciarCompartilhamento,
                                ),
                                style: FilledButton.styleFrom(
                                  backgroundColor: MapFirstPalette.green,
                                  foregroundColor:
                                      VanRidesDesignTokens.onAccent,
                                  minimumSize: const Size.fromHeight(56),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(18),
                                  ),
                                  textStyle: Theme.of(context)
                                      .textTheme
                                      .titleSmall
                                      ?.copyWith(fontWeight: FontWeight.w800),
                                ),
                              ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
