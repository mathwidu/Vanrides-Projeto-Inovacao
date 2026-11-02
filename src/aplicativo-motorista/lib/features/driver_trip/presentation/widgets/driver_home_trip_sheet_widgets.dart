import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_trip_action_focus_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/driver_trip_progress_presenter.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_active_operation_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_next_action_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_navigation_panel.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_location_signal_hint.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_trip_agenda_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_home_today_readiness_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_premium_chip.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_action_focus_panel.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_overview_widgets.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/map_first_v2_components.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_offline_continuity_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/trip_tracking_presenter.dart';
import 'package:vanrides_frontend_dev/features/tracking/presentation/widgets/vanrides_map_tokens.dart';
import 'package:vanrides_frontend_dev/l10n/app_strings.dart';

class DriverHomePlanningSheet extends StatelessWidget {
  const DriverHomePlanningSheet({
    super.key,
    required this.trip,
    required this.nextStop,
    required this.dayTrips,
    required this.selectedTripId,
    required this.agendaKey,
    required this.tripKeyBuilder,
    required this.startTripKey,
    required this.detailsKey,
    required this.mapKey,
    required this.editRouteKey,
    required this.runningLifecycleAction,
    required this.onSelectTrip,
    required this.onStartTrip,
    required this.onOpenStops,
    required this.onOpenMap,
    required this.onEditRoute,
  });

  final DriverTripRecord trip;
  final DriverTripStopRecord? nextStop;
  final List<DriverTripRecord> dayTrips;
  final int? selectedTripId;
  final Key agendaKey;
  final DriverTripKeyBuilder tripKeyBuilder;
  final Key startTripKey;
  final Key detailsKey;
  final Key mapKey;
  final Key editRouteKey;
  final bool runningLifecycleAction;
  final ValueChanged<DriverTripRecord> onSelectTrip;
  final VoidCallback onStartTrip;
  final VoidCallback onOpenStops;
  final VoidCallback onOpenMap;
  final VoidCallback onEditRoute;

  @override
  Widget build(BuildContext context) {
    final totalStops = trip.totalParadas;
    final signal = presentLocationFreshness(
      trip.trackingState.locationFreshness,
      role: TripSignalRole.driver,
    );
    final departureLabel =
        nextStop == null
            ? AppStrings.motorista.partidaPronta
            : AppStrings.motorista.primeiraColetaEm(
              presentEtaLabel(
                nextStop!.etaMinutos ?? trip.etaMinutosAteProximaParada,
              ),
            );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (dayTrips.length > 1) ...[
          DriverHomeDayAgendaSelector(
            agendaKey: agendaKey,
            dayTrips: dayTrips,
            selectedTripId: selectedTripId,
            tripKeyBuilder: tripKeyBuilder,
            onSelectTrip: onSelectTrip,
            dark: true,
          ),
          const SizedBox(height: 14),
        ],
        DriverTodayReadinessPanel(
          title: 'Hoje pronto para sair',
          subtitle:
              '${driverTodayTripCountLabel(dayTrips)} • ${driverSentenceLower(departureLabel)}',
          icon: Icons.event_available_outlined,
          accent: VanRidesMapTokens.greenAction,
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            DriverPremiumChip(
              icon: Icons.route_rounded,
              label: presentDriverProgressLabel(trip),
              color: VanRidesMapTokens.blueTracking,
            ),
            DriverPremiumChip(
              icon: signal.icon,
              label: signal.label,
              color: signal.color,
            ),
          ],
        ),
        const SizedBox(height: 12),
        Text(
          AppStrings.motorista.plannedStopsSummary(totalStops, departureLabel),
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: VanRidesMapTokens.textSecondary,
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 14),
        DriverHomeNextActionPanel(
          title: AppStrings.motorista.corridaProntaParaSair,
          description: AppStrings.motorista.planejamentoProximaAcaoDescricao,
          primaryKey: startTripKey,
          primaryLabel:
              runningLifecycleAction
                  ? AppStrings.motorista.iniciandoCorrida
                  : AppStrings.motorista.iniciarCorrida,
          primaryIcon:
              runningLifecycleAction
                  ? Icons.sync_rounded
                  : Icons.play_arrow_rounded,
          onPrimary: runningLifecycleAction ? null : onStartTrip,
        ),
        const SizedBox(height: 10),
        DriverHomeSecondaryActionGrid(
          actions: [
            DriverHomeSecondaryAction(
              key: detailsKey,
              icon: Icons.route_rounded,
              label: AppStrings.motorista.revisarRota,
              onPressed: onOpenStops,
            ),
            DriverHomeSecondaryAction(
              key: mapKey,
              icon: Icons.map_outlined,
              label: AppStrings.motorista.mapa,
              onPressed: onOpenMap,
              color: MapFirstPalette.blue,
            ),
            DriverHomeSecondaryAction(
              key: editRouteKey,
              icon: Icons.edit_outlined,
              label: AppStrings.motorista.editarRota,
              onPressed: onEditRoute,
            ),
          ],
        ),
        const SizedBox(height: 14),
        if (nextStop != null)
          Text(
            AppStrings.motorista.proximaColetaNome(nextStop!.passageiroNome),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: VanRidesMapTokens.textSecondary,
              fontWeight: FontWeight.w600,
            ),
          ),
      ],
    );
  }
}

class DriverHomeActiveSheet extends StatelessWidget {
  const DriverHomeActiveSheet({
    super.key,
    required this.trip,
    required this.nextStop,
    required this.dayTrips,
    required this.selectedTripId,
    required this.agendaKey,
    required this.tripKeyBuilder,
    required this.completeStopKey,
    required this.navigationKey,
    required this.skipStopKey,
    required this.noShowKey,
    required this.endTripKey,
    required this.sharingLocation,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    this.navigationActionQueueSummary,
    this.routePlanResponse,
    required this.onSelectTrip,
    required this.onCompleteStop,
    required this.onOpenNavigation,
    required this.onSkipStop,
    required this.onNoShowStop,
    this.onRetryQueuedActions,
    required this.onEndTrip,
  });

  final DriverTripRecord trip;
  final DriverTripStopRecord? nextStop;
  final List<DriverTripRecord> dayTrips;
  final int? selectedTripId;
  final Key agendaKey;
  final DriverTripKeyBuilder tripKeyBuilder;
  final Key completeStopKey;
  final Key navigationKey;
  final Key skipStopKey;
  final Key noShowKey;
  final Key endTripKey;
  final bool sharingLocation;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final DriverNavigationActionQueueSummary? navigationActionQueueSummary;
  final DriverNavigationRoutePlanResponse? routePlanResponse;
  final ValueChanged<DriverTripRecord> onSelectTrip;
  final VoidCallback onCompleteStop;
  final VoidCallback onOpenNavigation;
  final VoidCallback onSkipStop;
  final VoidCallback onNoShowStop;
  final VoidCallback? onRetryQueuedActions;
  final VoidCallback onEndTrip;

  @override
  Widget build(BuildContext context) {
    final stop = nextStop;
    final tipoOperacaoLabel = trip.tipoOperacaoLabel;
    if (stop == null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DriverHomeNextActionPanel(
            title: AppStrings.motorista.corridaConcluida,
            description: AppStrings.motorista.semMaisParadasNestaViagem,
            primaryKey: endTripKey,
            primaryLabel: AppStrings.motorista.encerrarCorrida,
            primaryIcon: Icons.flag_outlined,
            primaryBackgroundColor: MapFirstPalette.red,
            onPrimary: runningLifecycleAction ? null : onEndTrip,
          ),
        ],
      );
    }

    final canOpenMaps =
        trip.rotaGeografica?.navegacaoPreferencial?.hasUrl ?? false;
    final primaryUsesNavigation = canOpenMaps;
    final primaryKey = primaryUsesNavigation ? navigationKey : completeStopKey;
    final primaryLabel =
        primaryUsesNavigation
            ? openingNavigation
                ? AppStrings.motorista.abrindoNavegacao
                : AppStrings.motorista.irParaProximaParada
            : runningLifecycleAction
            ? AppStrings.motorista.atualizandoParada
            : AppStrings.motorista.confirmarEmbarque;
    final primaryIcon =
        primaryUsesNavigation
            ? openingNavigation
                ? Icons.sync_rounded
                : Icons.near_me_outlined
            : runningLifecycleAction
            ? Icons.sync_rounded
            : Icons.check_circle_outline_rounded;
    final primaryAction =
        primaryUsesNavigation
            ? openingNavigation || runningLifecycleAction
                ? null
                : onOpenNavigation
            : runningLifecycleAction
            ? null
            : onCompleteStop;
    final secondaryActions = [
      if (primaryUsesNavigation)
        DriverHomeSecondaryAction(
          key: completeStopKey,
          icon: Icons.check_circle_outline_rounded,
          label: AppStrings.motorista.confirmarEmbarque,
          onPressed: runningLifecycleAction ? null : onCompleteStop,
        ),
      DriverHomeSecondaryAction(
        key: skipStopKey,
        icon: Icons.skip_next_outlined,
        label: AppStrings.motorista.pular,
        onPressed: runningLifecycleAction ? null : onSkipStop,
      ),
      DriverHomeSecondaryAction(
        key: noShowKey,
        icon: Icons.person_off_outlined,
        label: AppStrings.motorista.faltou,
        onPressed: runningLifecycleAction ? null : onNoShowStop,
      ),
      DriverHomeSecondaryAction(
        key: endTripKey,
        icon: Icons.flag_outlined,
        label: AppStrings.motorista.encerrar,
        onPressed: runningLifecycleAction ? null : onEndTrip,
        color: MapFirstPalette.red,
      ),
    ];
    final signal = presentLocationFreshness(
      trip.trackingState.locationFreshness,
      role: TripSignalRole.driver,
    );
    final offlineContinuity = presentTripOfflineContinuity(
      trackingState: trip.trackingState,
      role: TripSignalRole.driver,
    );
    final actionFocus = presentDriverTripActionFocus(trip);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (dayTrips.length > 1) ...[
          DriverHomeDayAgendaSelector(
            agendaKey: agendaKey,
            dayTrips: dayTrips,
            selectedTripId: selectedTripId,
            tripKeyBuilder: tripKeyBuilder,
            onSelectTrip: onSelectTrip,
            dark: true,
          ),
          const SizedBox(height: 14),
        ],
        DriverTodayReadinessPanel(
          title: 'Hoje em execução',
          subtitle:
              '${driverTodayTripCountLabel(dayTrips)} • próxima: ${stop.passageiroNome}',
          icon: Icons.near_me_outlined,
          accent: VanRidesMapTokens.blueTracking,
        ),
        const SizedBox(height: 12),
        DriverActiveOperationHeader(
          signalIcon: signal.icon,
          signalLabel: signal.label,
          signalColor: signal.color,
          signalHelper:
              offlineContinuity.visible
                  ? offlineContinuity.helper
                  : signal.helper,
          signalContextLabel:
              offlineContinuity.visible ? offlineContinuity.title : null,
          operationLabel:
              tipoOperacaoLabel.isEmpty
                  ? AppStrings.motorista.emRota
                  : tipoOperacaoLabel,
          stop: stop,
          routeName: trip.nomeOperacional,
          etaLabel: presentEtaLabel(
            stop.etaMinutos ?? trip.etaMinutosAteProximaParada,
          ),
        ),
        const SizedBox(height: 12),
        if (actionFocus != null) ...[
          DriverTripActionFocusPanel(display: actionFocus),
          const SizedBox(height: 12),
        ],
        DriverHomeNextActionPanel(
          title: AppStrings.motorista.proximaColetaNome(stop.passageiroNome),
          description:
              primaryUsesNavigation
                  ? AppStrings.motorista.execucaoNavegacaoProximaAcaoDescricao(
                    stop.passageiroNome,
                  )
                  : AppStrings.motorista.execucaoEmbarqueProximaAcaoDescricao(
                    stop.passageiroNome,
                  ),
          primaryKey: primaryKey,
          primaryLabel: primaryLabel,
          primaryIcon: primaryIcon,
          primaryBackgroundColor:
              primaryUsesNavigation
                  ? MapFirstPalette.blue
                  : MapFirstPalette.green,
          onPrimary: primaryAction,
        ),
        const SizedBox(height: 14),
        DriverHomeNavigationPanel(
          trip: trip,
          canOpenMaps: canOpenMaps,
          openingNavigation: openingNavigation,
          runningLifecycleAction: runningLifecycleAction,
          onCompleteStop: onCompleteStop,
          onOpenNavigation: onOpenNavigation,
          onSkipStop: onSkipStop,
          onNoShowStop: onNoShowStop,
          actionQueueSummary: navigationActionQueueSummary,
          routePlanResponse: routePlanResponse,
          onRetryQueuedActions: onRetryQueuedActions,
        ),
        const SizedBox(height: 10),
        DriverHomeReviewActionSection(actions: secondaryActions),
        if (!sharingLocation) ...[
          const SizedBox(height: 10),
          DriverLocationSignalHint(
            label: AppStrings.motorista.sinalAoVivoPausadoAcaoCurta,
          ),
        ],
      ],
    );
  }
}

class DriverHomeRouteEditingSheet extends StatelessWidget {
  const DriverHomeRouteEditingSheet({
    super.key,
    required this.trip,
    required this.editableStops,
    required this.savingRoute,
    required this.saveRouteKey,
    required this.cancelRouteKey,
    required this.onReorder,
    required this.onSaveRoute,
    required this.onCancelRoute,
  });

  final DriverTripRecord trip;
  final List<DriverTripStopRecord> editableStops;
  final bool savingRoute;
  final Key saveRouteKey;
  final Key cancelRouteKey;
  final ReorderCallback onReorder;
  final VoidCallback onSaveRoute;
  final VoidCallback onCancelRoute;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        DriverRoutePlanningBanner(trip: trip),
        const SizedBox(height: 16),
        ReorderableListView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          itemCount: editableStops.length,
          onReorder: onReorder,
          buildDefaultDragHandles: false,
          itemBuilder: (context, index) {
            final stop = editableStops[index];
            return Padding(
              key: ValueKey('driver-stop-${stop.id}'),
              padding: EdgeInsets.only(
                bottom: index == editableStops.length - 1 ? 0 : 12,
              ),
              child: DriverEditableStopCard(
                stop: stop,
                routeName: trip.nomeOperacional,
                indexLabel: index + 1,
              ),
            );
          },
        ),
        const SizedBox(height: 16),
        LayoutBuilder(
          builder: (context, constraints) {
            final compact = constraints.maxWidth < 520;
            if (compact) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _SaveRouteButton(
                    buttonKey: saveRouteKey,
                    savingRoute: savingRoute,
                    onPressed: onSaveRoute,
                  ),
                  const SizedBox(height: 12),
                  _CancelRouteButton(
                    buttonKey: cancelRouteKey,
                    savingRoute: savingRoute,
                    onPressed: onCancelRoute,
                  ),
                ],
              );
            }

            return Row(
              children: [
                Expanded(
                  child: _SaveRouteButton(
                    buttonKey: saveRouteKey,
                    savingRoute: savingRoute,
                    onPressed: onSaveRoute,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _CancelRouteButton(
                    buttonKey: cancelRouteKey,
                    savingRoute: savingRoute,
                    onPressed: onCancelRoute,
                  ),
                ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _SaveRouteButton extends StatelessWidget {
  const _SaveRouteButton({
    required this.buttonKey,
    required this.savingRoute,
    required this.onPressed,
  });

  final Key buttonKey;
  final bool savingRoute;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton.icon(
      key: buttonKey,
      onPressed: savingRoute ? null : onPressed,
      icon:
          savingRoute
              ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
              : const Icon(Icons.save_outlined),
      label: Text(
        savingRoute
            ? AppStrings.motorista.salvandoRota
            : AppStrings.motorista.salvarRotaDoDia,
      ),
    );
  }
}

class _CancelRouteButton extends StatelessWidget {
  const _CancelRouteButton({
    required this.buttonKey,
    required this.savingRoute,
    required this.onPressed,
  });

  final Key buttonKey;
  final bool savingRoute;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      key: buttonKey,
      onPressed: savingRoute ? null : onPressed,
      icon: const Icon(Icons.close_outlined),
      label: Text(AppStrings.motorista.cancelar),
    );
  }
}
