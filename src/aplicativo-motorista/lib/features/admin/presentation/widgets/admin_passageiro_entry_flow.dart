import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../data/admin_vinculo_portal_service.dart';
import '../../domain/admin_company_link_flow_models.dart';
import '../../domain/admin_motorista_record.dart';
import '../../domain/admin_portal_exception.dart';
import '../../domain/admin_portal_session.dart';
import '../../domain/admin_servico_recorrente_record.dart';
import 'admin_passageiro_entry_section.dart';
import 'admin_vinculos_access_request_dialog.dart';
import 'admin_vinculos_invite_creation_dialog.dart';
import 'admin_vinculos_onboarding_widgets.dart';

class AdminPassageiroEntryFlow extends StatefulWidget {
  const AdminPassageiroEntryFlow({
    super.key,
    required this.session,
    required this.motoristas,
    required this.servicos,
    required this.onPassengerChanged,
    this.gateway,
  });

  final AdminPortalSession session;
  final AdminPassengerOnboardingGateway? gateway;
  final List<AdminMotoristaRecord> motoristas;
  final List<AdminServicoRecorrenteRecord> servicos;
  final Future<void> Function(int passageiroId) onPassengerChanged;

  @override
  State<AdminPassageiroEntryFlow> createState() =>
      _AdminPassageiroEntryFlowState();
}

class _AdminPassageiroEntryFlowState extends State<AdminPassageiroEntryFlow> {
  late final AdminPassengerOnboardingGateway _gateway;
  late final ValueNotifier<_PassengerEntrySnapshot> _snapshot;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminVinculoPortalService(session: widget.session);
    _snapshot = ValueNotifier(const _PassengerEntrySnapshot.loading());
    _load();
  }

  @override
  void dispose() {
    _snapshot.dispose();
    super.dispose();
  }

  Future<void> _load({bool showLoading = true}) async {
    if (showLoading) {
      _snapshot.value = _snapshot.value.withLoading();
    }

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchAccessRequests(),
        _gateway.fetchInviteCodes(),
      ]);
      if (!mounted) return;
      _snapshot.value = _snapshot.value.withData(
        requests: results[0] as List<AdminAccessRequestRecord>,
        inviteCodes: results[1] as List<AdminInviteCodeRecord>,
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      _snapshot.value = _snapshot.value.withError(error.message);
    } catch (_) {
      if (!mounted) return;
      _snapshot.value = _snapshot.value.withError(
        'Nao foi possivel carregar entradas de passageiros agora.',
      );
    }
  }

  Future<void> _openAccessRequest(AdminAccessRequestRecord request) async {
    try {
      final detail = await _gateway.fetchAccessRequestDetail(request.id);
      if (!mounted) return;

      final action = await showDialog<AdminAccessRequestDecision>(
        context: context,
        builder:
            (context) => AdminAccessRequestDecisionDialog(
              detail: detail,
              motoristas: widget.motoristas,
              servicos: widget.servicos,
            ),
      );
      if (action == null) {
        return;
      }

      _snapshot.value = _snapshot.value.withMutating(true);

      if (action.type == AdminAccessRequestDecisionType.approve) {
        await _gateway.approveAccessRequest(
          requestId: request.id,
          motoristaId: action.motoristaId,
          reason: action.reason,
          servicoIds: action.servicoIds,
        );
      } else {
        await _gateway.rejectAccessRequest(
          requestId: request.id,
          reason: action.reason,
        );
      }

      await widget.onPassengerChanged(detail.passageiroId);
      await _load(showLoading: false);
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            action.type == AdminAccessRequestDecisionType.approve
                ? 'Solicitação aprovada e passageiro atualizado.'
                : 'Solicitação rejeitada no portal.',
          ),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        _snapshot.value = _snapshot.value.withMutating(false);
      }
    }
  }

  Future<void> _openCreateInviteDialog() async {
    final config = await showDialog<AdminVinculosInviteCreationConfig>(
      context: context,
      builder: (context) => const AdminVinculosCreateInviteCodeDialog(),
    );
    if (config == null) {
      return;
    }

    _snapshot.value = _snapshot.value.withMutating(true);

    try {
      final invite = await _gateway.createInviteCode(
        expiresAt: config.expiresAt,
        maxUses: config.maxUses,
      );
      await _load(showLoading: false);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Convite ${invite.code} criado com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        _snapshot.value = _snapshot.value.withMutating(false);
      }
    }
  }

  Future<void> _openInviteDetail(AdminInviteCodeRecord invite) async {
    try {
      final detail = await _gateway.fetchInviteCodeDetail(invite.id);
      if (!mounted) return;

      final action = await showDialog<AdminVinculosInviteDetailAction>(
        context: context,
        builder:
            (context) => AdminVinculosInviteCodeDetailDialog(detail: detail),
      );
      if (action == null) {
        return;
      }

      if (action == AdminVinculosInviteDetailAction.copyCode) {
        try {
          await Clipboard.setData(ClipboardData(text: detail.code));
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Código copiado para a área de transferência.'),
            ),
          );
        } catch (_) {
          if (!mounted) return;
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                'Não foi possível copiar automaticamente. Código: ${detail.code}',
              ),
            ),
          );
        }
        return;
      }

      _snapshot.value = _snapshot.value.withMutating(true);

      if (action == AdminVinculosInviteDetailAction.cancelCode) {
        await _gateway.cancelInviteCode(invite.id);
      } else if (action == AdminVinculosInviteDetailAction.regenerateCode) {
        await _gateway.regenerateInviteCode(invite.id);
      }

      await _load(showLoading: false);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            action == AdminVinculosInviteDetailAction.cancelCode
                ? 'Convite cancelado com sucesso.'
                : 'Novo convite gerado com sucesso.',
          ),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        _snapshot.value = _snapshot.value.withMutating(false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<_PassengerEntrySnapshot>(
      valueListenable: _snapshot,
      builder:
          (context, snapshot, _) => AdminPassageiroEntrySection(
            loading: snapshot.loading,
            mutating: snapshot.mutating,
            errorMessage: snapshot.errorMessage,
            requests: snapshot.requests,
            inviteCodes: snapshot.inviteCodes,
            onRefresh: _load,
            onCreateInvite: _openCreateInviteDialog,
            onOpenRequest: _openAccessRequest,
            onOpenInvite: _openInviteDetail,
          ),
    );
  }
}

class _PassengerEntrySnapshot {
  const _PassengerEntrySnapshot({
    required this.loading,
    required this.mutating,
    required this.requests,
    required this.inviteCodes,
    this.errorMessage,
  });

  const _PassengerEntrySnapshot.loading()
    : loading = true,
      mutating = false,
      requests = const [],
      inviteCodes = const [],
      errorMessage = null;

  final bool loading;
  final bool mutating;
  final List<AdminAccessRequestRecord> requests;
  final List<AdminInviteCodeRecord> inviteCodes;
  final String? errorMessage;

  _PassengerEntrySnapshot withLoading() {
    return _PassengerEntrySnapshot(
      loading: true,
      mutating: mutating,
      requests: requests,
      inviteCodes: inviteCodes,
    );
  }

  _PassengerEntrySnapshot withData({
    required List<AdminAccessRequestRecord> requests,
    required List<AdminInviteCodeRecord> inviteCodes,
  }) {
    return _PassengerEntrySnapshot(
      loading: false,
      mutating: mutating,
      requests: requests,
      inviteCodes: inviteCodes,
    );
  }

  _PassengerEntrySnapshot withError(String message) {
    return _PassengerEntrySnapshot(
      loading: false,
      mutating: mutating,
      requests: requests,
      inviteCodes: inviteCodes,
      errorMessage: message,
    );
  }

  _PassengerEntrySnapshot withMutating(bool value) {
    return _PassengerEntrySnapshot(
      loading: loading,
      mutating: value,
      requests: requests,
      inviteCodes: inviteCodes,
      errorMessage: errorMessage,
    );
  }
}
