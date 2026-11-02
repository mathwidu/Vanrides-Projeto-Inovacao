import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_base_widgets.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverGuidedRouteCard extends StatelessWidget {
  const DriverGuidedRouteCard({super.key, required this.trip});

  final DriverTripRecord trip;

  @override
  Widget build(BuildContext context) {
    final nextStop = trip.rotaGeografica?.nextStop;
    final navigation = trip.rotaGeografica?.navegacaoPreferencial;
    final queueLength =
        trip.rotaGeografica?.filaTotalParadas ?? trip.totalParadas;
    final routeLabel = presentRouteGeometryLabel(trip.rotaGeografica);
    final routeHelper = presentRouteGeometryHelper(trip.rotaGeografica);
    final navigationSummary = presentNavigationSummary(navigation);

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: MapFirstPalette.surface,
        borderRadius: BorderRadius.circular(30),
        border: Border.all(color: MapFirstPalette.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.28),
            blurRadius: 18,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 12,
            runSpacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Text(
                  trip.nomeOperacional,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              DriverRouteBadge(label: routeLabel),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            routeHelper,
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.4,
            ),
          ),
          const SizedBox(height: 18),
          LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 720;
              final tileWidth =
                  compact
                      ? constraints.maxWidth
                      : (constraints.maxWidth - 12) / 2;

              return Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  SizedBox(
                    width: tileWidth,
                    child: _GuidedMetricTile(
                      icon: Icons.flag_outlined,
                      label: AppStrings.motorista.proximaParada,
                      value:
                          nextStop == null
                              ? AppStrings
                                  .motorista
                                  .aguardandoDefinicaoOperacional
                              : AppStrings.motorista.stopRouteSummary(
                                nextStop.ordem,
                                nextStop.nome,
                              ),
                    ),
                  ),
                  SizedBox(
                    width: tileWidth,
                    child: _GuidedMetricTile(
                      icon: Icons.schedule_outlined,
                      label: AppStrings.motorista.etaAtual,
                      value: presentEtaLabel(trip.etaMinutosAteProximaParada),
                    ),
                  ),
                  SizedBox(
                    width: tileWidth,
                    child: _GuidedMetricTile(
                      icon: Icons.list_alt_outlined,
                      label: AppStrings.motorista.filaDeHoje,
                      value: AppStrings.motorista.queueSummary(
                        queueLength,
                        trip.confirmadosCount,
                        trip.pendentesCount,
                      ),
                    ),
                  ),
                  SizedBox(
                    width: tileWidth,
                    child: _GuidedMetricTile(
                      icon: Icons.near_me_outlined,
                      label: AppStrings.motorista.navegacao,
                      value: navigationSummary,
                    ),
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _GuidedMetricTile extends StatelessWidget {
  const _GuidedMetricTile({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 18,
            backgroundColor: MapFirstPalette.blueSoft,
            child: Icon(icon, size: 18, color: MapFirstPalette.blue),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  value,
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w700,
                    height: 1.25,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class DriverOperationalAlertCard extends StatelessWidget {
  const DriverOperationalAlertCard({
    super.key,
    required this.title,
    required this.description,
    required this.color,
  });

  final String title;
  final String description;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: color,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            description,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }
}

class DriverRoutePlanningBanner extends StatelessWidget {
  const DriverRoutePlanningBanner({super.key, required this.trip});

  final DriverTripRecord trip;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: MapFirstPalette.blueSoft,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.motorista.modoDeEdicaoDaRota,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: MapFirstPalette.blue,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            AppStrings.motorista.routeEditVersionDescription(
              presentRouteVersionLabel(trip.rotaVersao),
            ),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: MapFirstPalette.textMuted,
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }
}

class DriverNextStopSheet extends StatelessWidget {
  const DriverNextStopSheet({
    super.key,
    required this.stop,
    required this.planningMode,
    required this.sharingLocation,
    required this.etaLabel,
  });

  final DriverTripStopRecord stop;
  final bool planningMode;
  final bool sharingLocation;
  final String etaLabel;

  @override
  Widget build(BuildContext context) {
    final avatarLabel =
        stop.passageiroNome.trim().isEmpty
            ? '?'
            : stop.passageiroNome
                .trim()
                .split(RegExp(r'\s+'))
                .take(2)
                .map((chunk) => chunk.substring(0, 1).toUpperCase())
                .join();

    final title =
        planningMode
            ? AppStrings.motorista.primeiraColeta
            : AppStrings.motorista.proximoPassageiro;
    final subtitle =
        planningMode
            ? AppStrings.motorista.iniciarTripMapDescription
            : sharingLocation
            ? AppStrings.motorista.veiculoVisivelNoMapa
            : AppStrings.motorista.sinalAoVivoAindaNaoAtivo;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color:
            planningMode
                ? MapFirstPalette.surfaceRaised.withValues(alpha: 0.78)
                : MapFirstPalette.blueSoft,
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: MapFirstPalette.border),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 360;

          final summary = Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: MapFirstPalette.textMuted,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  stop.passageiroNome,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: MapFirstPalette.textStrong,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  AppStrings.motorista.nextStopSubtitle(stop.ordem, subtitle),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: MapFirstPalette.textMuted,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          );

          final etaBadge = Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: MapFirstPalette.surfaceRaised.withValues(alpha: 0.84),
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: MapFirstPalette.border),
            ),
            child: Text(
              etaLabel,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: MapFirstPalette.blue,
                fontWeight: FontWeight.w800,
              ),
            ),
          );

          if (compact) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    CircleAvatar(
                      radius: 24,
                      backgroundColor: MapFirstPalette.surface,
                      child: Text(
                        avatarLabel,
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          color: MapFirstPalette.blue,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    summary,
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    etaBadge,
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        subtitle,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: MapFirstPalette.textMuted,
                          height: 1.35,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            );
          }

          return Row(
            children: [
              CircleAvatar(
                radius: 28,
                backgroundColor: MapFirstPalette.surface,
                child: Text(
                  avatarLabel,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: MapFirstPalette.blue,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const SizedBox(width: 14),
              summary,
              const SizedBox(width: 12),
              etaBadge,
            ],
          );
        },
      ),
    );
  }
}
