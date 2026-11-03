import 'package:flutter/material.dart';
import 'package:vanrides_frontend_dev/features/driver_trip/domain/driver_trip_record.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_route_plan_contract.dart';
import 'package:vanrides_frontend_dev/features/navigation/data/driver_navigation_tts_gateway.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_action_queue.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_models.dart';
import 'package:vanrides_frontend_dev/features/navigation/domain/driver_navigation_voice.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/driver_navigation_presenter.dart';
import 'package:vanrides_frontend_dev/features/navigation/presentation/widgets/driver_navigation_panel.dart';

typedef DriverNavigationVoiceGatewayFactory =
    DriverNavigationVoiceGateway Function();

DriverNavigationVoiceGateway createDriverNavigationVoiceGateway() {
  return DriverNavigationTtsGateway.native();
}

class DriverHomeNavigationPanel extends StatefulWidget {
  const DriverHomeNavigationPanel({
    required this.trip,
    required this.canOpenMaps,
    required this.openingNavigation,
    required this.runningLifecycleAction,
    required this.onCompleteStop,
    required this.onOpenNavigation,
    this.onSkipStop,
    this.onNoShowStop,
    this.onRetryQueuedActions,
    this.voiceGateway,
    this.voiceGatewayFactory = createDriverNavigationVoiceGateway,
    this.initialVoiceState = const DriverNavigationVoiceState(),
    this.actionQueueSummary,
    this.routePlanResponse,
    super.key,
  });

  final DriverTripRecord trip;
  final bool canOpenMaps;
  final bool openingNavigation;
  final bool runningLifecycleAction;
  final VoidCallback onCompleteStop;
  final VoidCallback onOpenNavigation;
  final VoidCallback? onSkipStop;
  final VoidCallback? onNoShowStop;
  final VoidCallback? onRetryQueuedActions;
  final DriverNavigationVoiceGateway? voiceGateway;
  final DriverNavigationVoiceGatewayFactory voiceGatewayFactory;
  final DriverNavigationVoiceState initialVoiceState;
  final DriverNavigationActionQueueSummary? actionQueueSummary;
  final DriverNavigationRoutePlanResponse? routePlanResponse;

  @override
  State<DriverHomeNavigationPanel> createState() =>
      _DriverHomeNavigationPanelState();
}

class _DriverHomeNavigationPanelState extends State<DriverHomeNavigationPanel> {
  late DriverNavigationVoiceController _voiceController;
  late final ValueNotifier<DriverNavigationVoiceState> _voiceState;

  @override
  void initState() {
    super.initState();
    _voiceController = _buildVoiceController();
    _voiceState = ValueNotifier(widget.initialVoiceState);
  }

  @override
  void dispose() {
    _voiceState.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant DriverHomeNavigationPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.voiceGateway != widget.voiceGateway ||
        oldWidget.voiceGatewayFactory != widget.voiceGatewayFactory) {
      _voiceController = _buildVoiceController();
    }
  }

  DriverNavigationVoiceController _buildVoiceController() {
    return DriverNavigationVoiceController(
      gateway: widget.voiceGateway ?? widget.voiceGatewayFactory(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final viewModel = const DriverNavigationPresenter().present(
      _navigationSession(),
    );

    return ValueListenableBuilder<DriverNavigationVoiceState>(
      valueListenable: _voiceState,
      builder: (context, voiceState, _) {
        return DriverNavigationPanel(
          viewModel: viewModel,
          showDistanceLabel: false,
          showNextStopDetails: false,
          voiceMuted: voiceState.muted,
          onPrimaryAction:
              widget.runningLifecycleAction ? null : widget.onCompleteStop,
          onFallback:
              widget.canOpenMaps &&
                      !widget.openingNavigation &&
                      !widget.runningLifecycleAction
                  ? widget.onOpenNavigation
                  : null,
          onSkipStop: widget.runningLifecycleAction ? null : widget.onSkipStop,
          onNoShowStop:
              widget.runningLifecycleAction ? null : widget.onNoShowStop,
          onRetryQueuedActions:
              widget.runningLifecycleAction
                  ? null
                  : widget.onRetryQueuedActions,
          onRepeatInstruction: () => _repeatInstruction(viewModel),
          onToggleVoiceMute: () {
            _voiceState.value = _voiceController.toggleMute(_voiceState.value);
          },
        );
      },
    );
  }

  DriverNavigationSession _navigationSession() {
    final routePlanResponse = widget.routePlanResponse;
    if (routePlanResponse != null) {
      return routePlanResponse.toNavigationSession(
        trip: widget.trip,
        actionQueueSummary: widget.actionQueueSummary,
      );
    }
    return DriverNavigationSession(
      trip: widget.trip,
      offlineState: DriverNavigationOfflineState.missing,
      coverageStatus:
          widget.canOpenMaps
              ? DriverNavigationCoverageStatus.fallback
              : DriverNavigationCoverageStatus.blocked,
      actionQueueSummary: widget.actionQueueSummary,
    );
  }

  void _repeatInstruction(DriverNavigationViewModel viewModel) {
    _repeatInstructionAsync(viewModel);
  }

  Future<void> _repeatInstructionAsync(
    DriverNavigationViewModel viewModel,
  ) async {
    final currentState = _voiceState.value;
    final wasMuted = currentState.muted;
    final nextState = await _voiceController.repeat(
      viewModel: viewModel,
      instructionId: _instructionIdFor(viewModel),
      state: currentState,
    );
    if (!mounted) {
      return;
    }
    _voiceState.value = nextState;
    if (!wasMuted && nextState.lastSpokenText != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(nextState.lastSpokenText!),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  String _instructionIdFor(DriverNavigationViewModel viewModel) {
    final stopId = widget.trip.proximaParada?.id ?? 'none';
    return '${widget.trip.id}:$stopId:${viewModel.mode.name}:'
        '${viewModel.instructionText}';
  }
}
