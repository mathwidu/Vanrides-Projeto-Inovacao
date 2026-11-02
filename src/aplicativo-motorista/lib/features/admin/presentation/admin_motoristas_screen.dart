import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../l10n/app_strings.dart';
import '../data/admin_driver_link_portal_service.dart';
import '../data/admin_motorista_assignment_service.dart';
import '../data/admin_motorista_portal_service.dart';
import '../domain/admin_company_link_flow_models.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../domain/admin_viagem_record.dart';
import 'admin_list_state_mixin.dart';
import 'widgets/admin_motorista_detail_widgets.dart';
import 'widgets/admin_motorista_form_dialog.dart';
import 'widgets/admin_motoristas_widgets.dart';
import 'widgets/admin_driver_link_widgets.dart';
import 'widgets/admin_portal_layout.dart';

class AdminMotoristasScreen extends StatefulWidget {
  const AdminMotoristasScreen({
    super.key,
    this.session,
    this.gateway,
    this.driverLinkGateway,
    this.assignmentGateway,
  }) : assert(session != null || gateway != null);

  final AdminPortalSession? session;
  final AdminMotoristaGateway? gateway;
  final AdminDriverLinkGateway? driverLinkGateway;
  final AdminMotoristaAssignmentGateway? assignmentGateway;

  @override
  State<AdminMotoristasScreen> createState() => _AdminMotoristasScreenState();
}

class _AdminMotoristasScreenState extends State<AdminMotoristasScreen>
    with AdminListStateMixin<AdminMotoristaRecord, AdminMotoristasScreen> {
  late final AdminMotoristaGateway _gateway;
  late final AdminDriverLinkGateway? _driverLinkGateway;
  late final AdminMotoristaAssignmentGateway? _assignmentGateway;
  AdminMotoristaRecord? _selectedMotorista;
  List<AdminDriverInviteRecord> _driverInvites = const [];
  List<AdminDriverLinkRequestRecord> _driverRequests = const [];
  List<AdminServicoRecorrenteRecord> _assignmentServicos = const [];
  List<AdminViagemRecord> _assignmentViagens = const [];
  bool _loadingDriverLinks = false;
  bool _loadingAssignments = false;
  bool _mutatingDriverLink = false;
  String? _driverLinkError;
  String? _assignmentError;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminMotoristaPortalService(session: widget.session!);
    _driverLinkGateway =
        widget.driverLinkGateway ??
        (widget.session == null || widget.gateway != null
            ? null
            : AdminDriverLinkPortalService(session: widget.session!));
    _assignmentGateway =
        widget.assignmentGateway ??
        (widget.session == null || widget.gateway != null
            ? null
            : AdminMotoristaAssignmentPortalService(session: widget.session!));
    _loadMotoristas();
    _loadDriverLinkState();
    _loadAssignments();
  }

  void _updateState(VoidCallback update) {
    if (!mounted) {
      return;
    }
    setState(update);
  }

  Future<void> _loadMotoristas() => loadItems(
    () => _gateway.fetchMotoristas(),
    errorMessageBuilder: (error) {
      if (error is AdminMotoristaPortalException) {
        return error.message;
      }
      return AppStrings.admin.erroCarregarMotoristas;
    },
    onLoaded:
        (motoristas) =>
            _selectedMotorista = motoristas.isEmpty ? null : motoristas.first,
  );

  Future<void> _loadDriverLinkState() async {
    final gateway = _driverLinkGateway;
    if (gateway == null) {
      return;
    }

    _updateState(() {
      _loadingDriverLinks = true;
      _driverLinkError = null;
    });

    try {
      final invites = await gateway.fetchDriverInvites();
      final requests = await gateway.fetchDriverLinkRequests();
      if (!mounted) {
        return;
      }
      _updateState(() {
        _driverInvites = invites;
        _driverRequests = requests;
        _loadingDriverLinks = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _loadingDriverLinks = false;
        _driverLinkError = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _loadAssignments() async {
    final gateway = _assignmentGateway;
    if (gateway == null) {
      return;
    }

    _updateState(() {
      _loadingAssignments = true;
      _assignmentError = null;
    });

    try {
      final results = await Future.wait<dynamic>([
        gateway.fetchServicos(),
        gateway.fetchViagens(data: DateTime.now()),
      ]);
      if (!mounted) {
        return;
      }
      _updateState(() {
        _assignmentServicos = results[0] as List<AdminServicoRecorrenteRecord>;
        _assignmentViagens = results[1] as List<AdminViagemRecord>;
        _loadingAssignments = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _loadingAssignments = false;
        _assignmentError = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _createDriverInvite() async {
    final gateway = _driverLinkGateway;
    if (gateway == null) {
      return;
    }
    _updateState(() => _mutatingDriverLink = true);
    try {
      final invite = await gateway.createDriverInvite();
      if (invite.shareUrl != null && invite.shareUrl!.isNotEmpty) {
        unawaited(
          Clipboard.setData(
            ClipboardData(text: invite.shareUrl!),
          ).catchError((_) {}),
        );
      }
      if (!mounted) {
        return;
      }
      _updateState(() {
        _driverInvites = [invite, ..._driverInvites];
        _mutatingDriverLink = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Convite criado. O link esta no painel.')),
      );
    } catch (error) {
      _showDriverLinkError(error);
    }
  }

  Future<void> _copyDriverInvite(AdminDriverInviteRecord invite) async {
    final shareUrl = invite.shareUrl;
    if (shareUrl == null || shareUrl.isEmpty) {
      return;
    }
    await Clipboard.setData(ClipboardData(text: shareUrl));
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Link copiado.')));
  }

  Future<void> _cancelDriverInvite(AdminDriverInviteRecord invite) async {
    final gateway = _driverLinkGateway;
    if (gateway == null) {
      return;
    }
    _updateState(() => _mutatingDriverLink = true);
    try {
      final updated = await gateway.cancelDriverInvite(invite.id);
      if (!mounted) {
        return;
      }
      _updateState(() {
        _driverInvites = _driverInvites
            .map((current) => current.id == updated.id ? updated : current)
            .toList(growable: false);
        _mutatingDriverLink = false;
      });
    } catch (error) {
      _showDriverLinkError(error);
    }
  }

  Future<void> _approveDriverRequest(
    AdminDriverLinkRequestRecord request,
  ) async {
    final gateway = _driverLinkGateway;
    if (gateway == null) {
      return;
    }
    _updateState(() => _mutatingDriverLink = true);
    try {
      await gateway.approveDriverLinkRequest(requestId: request.id);
      await _loadDriverLinkState();
      await _loadMotoristas();
      if (!mounted) {
        return;
      }
      _updateState(() => _mutatingDriverLink = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${request.motoristaNome} aprovado.')),
      );
    } catch (error) {
      _showDriverLinkError(error);
    }
  }

  Future<void> _rejectDriverRequest(
    AdminDriverLinkRequestRecord request,
  ) async {
    final gateway = _driverLinkGateway;
    if (gateway == null) {
      return;
    }
    _updateState(() => _mutatingDriverLink = true);
    try {
      final updated = await gateway.rejectDriverLinkRequest(
        requestId: request.id,
        reason: 'Rejeitado pelo portal',
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _driverRequests = _driverRequests
            .map((current) => current.id == updated.id ? updated : current)
            .toList(growable: false);
        _mutatingDriverLink = false;
      });
    } catch (error) {
      _showDriverLinkError(error);
    }
  }

  void _showDriverLinkError(Object error) {
    if (!mounted) {
      return;
    }
    _updateState(() => _mutatingDriverLink = false);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
    );
  }

  Future<void> _openCreateDialog() async {
    final created = await showDialog<AdminMotoristaRecord>(
      context: context,
      builder: (context) => AdminMotoristaFormDialog(gateway: _gateway),
    );

    if (created == null) {
      return;
    }

    onItemCreated(created);
    _updateState(() => _selectedMotorista = created);

    if (!mounted) {
      return;
    }

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Motorista criado com sucesso no portal.')),
    );
  }

  void _selectMotorista(AdminMotoristaRecord motorista) {
    _updateState(() => _selectedMotorista = motorista);
  }

  Future<void> _openEditDialog(AdminMotoristaRecord motorista) async {
    final updated = await showDialog<AdminMotoristaRecord>(
      context: context,
      builder:
          (context) => AdminMotoristaFormDialog(
            gateway: _gateway,
            initialMotorista: motorista,
          ),
    );

    if (updated == null) return;

    onItemUpdated(updated, where: (motorista) => motorista.id == updated.id);
    if (_selectedMotorista?.id == updated.id) {
      _updateState(() => _selectedMotorista = updated);
    }

    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(AppStrings.admin.motoristaEditado)));
  }

  Future<void> _deleteMotorista(AdminMotoristaRecord motorista) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder:
          (ctx) => AlertDialog(
            title: const Text('Excluir motorista?'),
            content: Text(
              'O motorista "${motorista.nome}" será removido. '
              'Esta ação não pode ser desfeita se não houver viagem em andamento.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                style: FilledButton.styleFrom(backgroundColor: Colors.red),
                child: const Text('Excluir'),
              ),
            ],
          ),
    );

    if (confirm != true || !mounted) return;

    try {
      await _gateway.deleteMotorista(motorista.id);
      onItemDeleted(where: (current) => current.id == motorista.id);
      if (_selectedMotorista?.id == motorista.id) {
        _updateState(
          () => _selectedMotorista = items.isEmpty ? null : items.first,
        );
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.admin.motoristaExcluido)),
      );
    } on AdminMotoristaPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nao foi possivel excluir o motorista agora.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final hasDriverLinkFlow = _driverLinkGateway != null;

    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (errorMessage != null) {
      return AdminMotoristaErrorState(
        message: errorMessage!,
        onRetry: _loadMotoristas,
      );
    }

    return AdminPortalPage(
      title: 'Motoristas',
      description:
          'Crie convites, revise solicitações do app e acompanhe quem já está pronto para escala.',
      trailing: AdminPortalPrimaryButton(
        label:
            hasDriverLinkFlow ? 'Convidar motorista' : 'Cadastrar manualmente',
        icon: hasDriverLinkFlow ? Icons.link_rounded : Icons.add_circle_outline,
        onPressed: hasDriverLinkFlow ? _createDriverInvite : _openCreateDialog,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_driverLinkGateway != null) ...[
            AdminDriverLinkPanel(
              loading: _loadingDriverLinks,
              mutating: _mutatingDriverLink,
              errorMessage: _driverLinkError,
              invites: _driverInvites,
              requests: _driverRequests,
              onRefresh: _loadDriverLinkState,
              onCopyInvite: _copyDriverInvite,
              onCancelInvite: _cancelDriverInvite,
              onApproveRequest: _approveDriverRequest,
              onRejectRequest: _rejectDriverRequest,
            ),
            const SizedBox(height: 20),
          ],
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 7,
                child: AdminMotoristasListPanel(
                  motoristas: items,
                  selectedMotorista: _selectedMotorista,
                  onSelect: _selectMotorista,
                  onCreate: _openCreateDialog,
                  onRefresh: _loadMotoristas,
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                flex: 4,
                child: AdminMotoristaDetailPanel(
                  motorista: _selectedMotorista,
                  assignedServicos: _assignedServicosFor(_selectedMotorista),
                  assignedViagens: _assignedViagensFor(_selectedMotorista),
                  loadingAssignments: _loadingAssignments,
                  assignmentError: _assignmentError,
                  onRefreshAssignments:
                      _assignmentGateway == null ? null : _loadAssignments,
                  onEdit:
                      _selectedMotorista == null
                          ? null
                          : () => _openEditDialog(_selectedMotorista!),
                  onDelete:
                      _selectedMotorista == null
                          ? null
                          : () => _deleteMotorista(_selectedMotorista!),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  List<AdminServicoRecorrenteRecord> _assignedServicosFor(
    AdminMotoristaRecord? motorista,
  ) {
    if (motorista == null) {
      return const [];
    }
    return _assignmentServicos
        .where((servico) => servico.motoristaPadrao?.id == motorista.id)
        .toList(growable: false);
  }

  List<AdminViagemRecord> _assignedViagensFor(AdminMotoristaRecord? motorista) {
    if (motorista == null) {
      return const [];
    }
    return _assignmentViagens
        .where((viagem) => viagem.motorista.id == motorista.id)
        .toList(growable: false);
  }
}
