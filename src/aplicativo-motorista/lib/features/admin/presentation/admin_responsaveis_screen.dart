import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../data/admin_responsavel_portal_service.dart';
import '../data/admin_vinculo_portal_service.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_responsavel_record.dart';
import 'admin_list_state_mixin.dart';
import 'widgets/admin_portal_dialog.dart';
import 'widgets/admin_portal_error_state.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_responsavel_passageiro_link_flow.dart';
import 'widgets/admin_responsavel_create_dialog.dart';
import 'widgets/admin_responsavel_edit_dialog.dart';
import 'widgets/admin_responsaveis_widgets.dart';

class AdminResponsaveisScreen extends StatefulWidget {
  const AdminResponsaveisScreen({
    super.key,
    required this.session,
    this.gateway,
    this.linkGateway,
  });

  final AdminPortalSession session;
  final AdminResponsavelGateway? gateway;
  final AdminResponsavelPassageiroLinkGateway? linkGateway;

  @override
  State<AdminResponsaveisScreen> createState() =>
      _AdminResponsaveisScreenState();
}

class _AdminResponsaveisScreenState extends State<AdminResponsaveisScreen>
    with AdminListStateMixin<AdminResponsavelRecord, AdminResponsaveisScreen> {
  late final AdminResponsavelGateway _gateway;
  late final AdminResponsavelPassageiroLinkGateway _linkGateway;
  AdminResponsavelRecord? _selectedSummary;
  AdminResponsavelRecord? _selectedDetail;
  bool _isDetailLoading = false;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ??
        AdminResponsavelPortalService(session: widget.session);
    _linkGateway =
        widget.linkGateway ??
        AdminVinculoPortalService(session: widget.session);
    _loadPage();
  }

  Future<void> _loadPage({int? preserveSelectionId}) async {
    startLoading();

    try {
      final responsaveis = await _gateway.fetchResponsaveis();
      final selectedId = preserveSelectionId ?? _selectedSummary?.id;
      final selected = responsaveis.cast<AdminResponsavelRecord?>().firstWhere(
        (item) => item?.id == selectedId,
        orElse: () => responsaveis.isEmpty ? null : responsaveis.first,
      );

      setState(() {
        items = responsaveis;
        _selectedSummary = selected;
        _selectedDetail = null;
        isLoading = false;
      });

      if (selected != null) {
        await _loadDetail(selected.id);
      }
    } on AdminPortalException catch (error) {
      setLoadError(error.message);
    } catch (_) {
      setLoadError(AppStrings.admin.erroCarregarResponsaveis);
    }
  }

  Future<void> _loadDetail(int responsavelId) async {
    setState(() => _isDetailLoading = true);
    try {
      final detail = await _gateway.fetchResponsavelDetail(responsavelId);
      if (!mounted) return;
      setState(() {
        _selectedDetail = detail;
        _selectedSummary = items.cast<AdminResponsavelRecord?>().firstWhere(
          (item) => item?.id == responsavelId,
          orElse: () => detail,
        );
        _isDetailLoading = false;
      });
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      setState(() => _isDetailLoading = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      setState(() => _isDetailLoading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nao foi possivel carregar o detalhe do responsavel.'),
        ),
      );
    }
  }

  Future<void> _openCreateDialog() async {
    final created = await showDialog<AdminResponsavelRecord>(
      context: context,
      builder:
          (context) => AdminResponsavelCreateDialog(
            title: 'Novo responsavel',
            subtitle:
                'Este cadastro já usa o contrato administrativo real do portal da empresa.',
            onSubmit: (input) => _gateway.createResponsavel(input),
          ),
    );

    if (created == null) return;

    await _loadPage(preserveSelectionId: created.id);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Responsavel criado com sucesso.')),
    );
  }

  Future<void> _openEditDialog() async {
    final responsavel = _selectedDetail;
    if (responsavel == null) return;

    final updated = await showDialog<AdminResponsavelRecord>(
      context: context,
      builder:
          (context) => AdminResponsavelEditDialog(
            responsavel: responsavel,
            onSubmit:
                (input) => _gateway.updateResponsavel(responsavel.id, input),
          ),
    );

    if (updated == null) return;

    await _loadPage(preserveSelectionId: updated.id);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Responsavel atualizado com sucesso.')),
    );
  }

  Future<void> _openPassageirosFlow() async {
    final responsavel = _selectedDetail ?? _selectedSummary;
    if (responsavel == null) return;

    final changed = await openAdminResponsavelPassageirosFlow(
      context: context,
      session: widget.session,
      responsavel: responsavel,
      gateway: _linkGateway,
    );

    if (changed) {
      await _loadPage(preserveSelectionId: responsavel.id);
    }
  }

  Future<void> _deleteSelected() async {
    final responsavel = _selectedDetail;
    if (responsavel == null) return;

    final confirmed =
        await showDialog<bool>(
          context: context,
          builder:
              (context) => AdminPortalDialog(
                title: 'Remover responsavel',
                maxWidth: 460,
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('Cancelar'),
                  ),
                  FilledButton.icon(
                    onPressed: () => Navigator.of(context).pop(true),
                    icon: const Icon(Icons.delete_outline),
                    label: const Text('Remover'),
                  ),
                ],
                child: Text(
                  'Deseja remover ${responsavel.nome}? Se ele ainda tiver passageiros vinculados, a operacao vai bloquear a exclusao.',
                  style: const TextStyle(
                    color: AdminPortalColors.textMuted,
                    height: 1.45,
                  ),
                ),
              ),
        ) ??
        false;

    if (!confirmed) return;

    try {
      await _gateway.deleteResponsavel(responsavel.id);
      await _loadPage();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Responsavel removido com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (errorMessage != null) {
      return AdminPortalErrorState(
        message: errorMessage!,
        onRetry: () => _loadPage(),
      );
    }

    final selected = _selectedDetail ?? _selectedSummary;

    return AdminPortalPage(
      title: 'Responsáveis',
      description:
          'Gerencie os contatos de segurança e responsáveis legais pelos passageiros. Aqui você controla o acesso e as permissões de vínculo.',
      trailing: AdminPortalPrimaryButton(
        label: 'Novo Responsável',
        icon: Icons.add_circle_outline,
        onPressed: _openCreateDialog,
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final stacked = constraints.maxWidth < 1180;
          final listPanel = AdminResponsaveisListPanel(
            responsaveis: items,
            selectedResponsavel: selected,
            onRefresh: () => _loadPage(),
            onCreate: _openCreateDialog,
            onEdit: _openEditDialog,
            onDelete: _deleteSelected,
            onSelect: (responsavel) async {
              setState(() {
                _selectedSummary = responsavel;
                _selectedDetail = null;
              });
              await _loadDetail(responsavel.id);
            },
          );
          final detailPanel = AdminResponsavelDetailPanel(
            isLoading: _isDetailLoading,
            responsavel: selected,
            onManagePassageiros: _openPassageirosFlow,
            onEdit: _openEditDialog,
            onDelete: _deleteSelected,
          );

          if (stacked) {
            return Column(
              children: [listPanel, const SizedBox(height: 24), detailPanel],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 8, child: listPanel),
              const SizedBox(width: 32),
              Expanded(flex: 4, child: detailPanel),
            ],
          );
        },
      ),
    );
  }
}
