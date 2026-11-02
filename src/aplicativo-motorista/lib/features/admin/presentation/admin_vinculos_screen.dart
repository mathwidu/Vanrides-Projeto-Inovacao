import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../l10n/app_strings.dart';
import '../data/admin_vinculo_portal_service.dart';
import '../domain/admin_company_link_flow_models.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_motorista_link_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_responsavel_passageiro_link_record.dart';
import '../domain/admin_responsavel_record.dart';
import '../domain/admin_servico_recorrente_record.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_vinculos_access_request_dialog.dart';
import 'widgets/admin_vinculos_guardian_widgets.dart';
import 'widgets/admin_vinculos_invite_creation_dialog.dart';
import 'widgets/admin_vinculos_motorista_widgets.dart';
import 'widgets/admin_vinculos_onboarding_widgets.dart';
import 'widgets/admin_vinculos_service_widgets.dart';
import 'widgets/admin_vinculos_task_queue_widgets.dart';
import 'widgets/admin_vinculos_widgets.dart';

class AdminVinculosScreen extends StatefulWidget {
  const AdminVinculosScreen({super.key, required this.session, this.gateway});

  final AdminPortalSession session;
  final AdminVinculoGateway? gateway;

  @override
  State<AdminVinculosScreen> createState() => _AdminVinculosScreenState();
}

class _AdminVinculosScreenState extends State<AdminVinculosScreen> {
  late final AdminVinculoGateway _gateway;
  List<AdminAccessRequestRecord> _accessRequests = const [];
  List<AdminInviteCodeRecord> _inviteCodes = const [];
  List<AdminPassageiroMotoristaLinkRecord> _passageiroMotoristaLinks = const [];
  List<AdminMotoristaRecord> _motoristas = const [];
  List<AdminServicoRecorrenteRecord> _servicos = const [];
  List<AdminResponsavelPassageiroLinkRecord> _responsavelPassageiroLinks =
      const [];
  List<AdminPassageiroRecord> _passageiros = const [];
  List<AdminResponsavelRecord> _responsaveis = const [];
  bool _isLoading = true;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminVinculoPortalService(session: widget.session);
    _loadPage();
  }

  Future<void> _loadPage() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchAccessRequests(),
        _gateway.fetchInviteCodes(),
        _gateway.fetchPassageiroMotoristaLinks(),
        _gateway.fetchMotoristas(),
        _gateway.fetchResponsavelPassageiroLinks(),
        _gateway.fetchPassageiros(),
        _gateway.fetchResponsaveis(),
        _gateway.fetchServicos(),
      ]);

      setState(() {
        _accessRequests = results[0] as List<AdminAccessRequestRecord>;
        _inviteCodes = results[1] as List<AdminInviteCodeRecord>;
        _passageiroMotoristaLinks =
            results[2] as List<AdminPassageiroMotoristaLinkRecord>;
        _motoristas = results[3] as List<AdminMotoristaRecord>;
        _responsavelPassageiroLinks =
            results[4] as List<AdminResponsavelPassageiroLinkRecord>;
        _passageiros = results[5] as List<AdminPassageiroRecord>;
        _responsaveis = results[6] as List<AdminResponsavelRecord>;
        _servicos = results[7] as List<AdminServicoRecorrenteRecord>;
        _isLoading = false;
      });
    } on AdminPortalException catch (error) {
      setState(() {
        _errorMessage = error.message;
        _isLoading = false;
      });
    } catch (_) {
      setState(() {
        _errorMessage = AppStrings.admin.erroCarregarVinculos;
        _isLoading = false;
      });
    }
  }

  Future<void> _openAccessRequest(AdminAccessRequestRecord request) async {
    try {
      final detail = await _gateway.fetchAccessRequestDetail(request.id);
      if (!mounted) {
        return;
      }
      final action = await showDialog<AdminAccessRequestDecision>(
        context: context,
        builder:
            (context) => AdminAccessRequestDecisionDialog(
              detail: detail,
              motoristas: _motoristas,
              servicos: _servicos,
            ),
      );
      if (action == null) {
        return;
      }

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

      await _loadPage();
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            action.type == AdminAccessRequestDecisionType.approve
                ? 'Solicitação aprovada e vínculo atualizado.'
                : 'Solicitação rejeitada no portal.',
          ),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
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

    try {
      final invite = await _gateway.createInviteCode(
        expiresAt: config.expiresAt,
        maxUses: config.maxUses,
      );
      await _loadPage();
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Convite ${invite.code} criado com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _openInviteDetail(AdminInviteCodeRecord invite) async {
    try {
      final detail = await _gateway.fetchInviteCodeDetail(invite.id);
      if (!mounted) {
        return;
      }
      final action = await showDialog<AdminVinculosInviteDetailAction>(
        context: context,
        builder:
            (context) => AdminVinculosInviteCodeDetailDialog(detail: detail),
      );
      if (action == null) {
        return;
      }

      if (action == AdminVinculosInviteDetailAction.copyCode) {
        await Clipboard.setData(ClipboardData(text: detail.code));
        if (!mounted) {
          return;
        }
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Código copiado para a área de transferência.'),
          ),
        );
        return;
      }

      if (action == AdminVinculosInviteDetailAction.cancelCode) {
        await _gateway.cancelInviteCode(invite.id);
      } else if (action == AdminVinculosInviteDetailAction.regenerateCode) {
        await _gateway.regenerateInviteCode(invite.id);
      }

      await _loadPage();
      if (!mounted) {
        return;
      }
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
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _unlinkPassageiroMotorista(
    AdminPassageiroMotoristaLinkRecord link,
  ) async {
    final confirmed =
        await showDialog<bool>(
          context: context,
          builder:
              (context) => AlertDialog(
                title: const Text('Remover atalho operacional'),
                content: Text(
                  'Deseja remover o atalho entre ${link.passageiroNome} e ${link.motorista?.nome ?? 'o motorista atual'}? Os serviços liberados e viagens do dia continuam sendo controlados separadamente.',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('Cancelar'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.of(context).pop(true),
                    child: const Text('Desvincular'),
                  ),
                ],
              ),
        ) ??
        false;

    if (!confirmed) return;

    try {
      await _gateway.unlinkPassageiroMotorista(link.passageiroId);
      await _loadPage();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Atalho passageiro-motorista removido.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _openCreateResponsavelPassageiroLinkDialog() async {
    if (_responsaveis.isEmpty || _passageiros.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Cadastre responsáveis e passageiros antes de criar vínculos.',
          ),
        ),
      );
      return;
    }

    final selection =
        await showDialog<AdminVinculosResponsavelPassageiroSelection>(
          context: context,
          builder:
              (context) => AdminVinculosResponsavelPassageiroDialog(
                responsaveis: _responsaveis,
                passageiros: _passageiros,
              ),
        );

    if (selection == null) return;

    try {
      await _gateway.linkResponsavelPassageiro(
        responsavelId: selection.responsavelId,
        passageiroId: selection.passageiroId,
      );
      await _loadPage();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vínculo responsável-passageiro criado com sucesso.'),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _unlinkResponsavelPassageiro(
    AdminResponsavelPassageiroLinkRecord link,
  ) async {
    final confirmed =
        await showDialog<bool>(
          context: context,
          builder:
              (context) => AlertDialog(
                title: const Text('Remover vínculo responsável-passageiro'),
                content: Text(
                  'Deseja remover o vínculo de ${link.responsavel.nome} com ${link.passageiro.nome}?',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(false),
                    child: const Text('Cancelar'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.of(context).pop(true),
                    child: const Text('Remover vínculo'),
                  ),
                ],
              ),
        ) ??
        false;

    if (!confirmed) return;

    try {
      await _gateway.unlinkResponsavelPassageiro(
        responsavelId: link.responsavel.id,
        passageiroId: link.passageiro.id,
      );
      await _loadPage();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vínculo responsável-passageiro removido.'),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _openPassageiroServicosDialog(
    AdminPassageiroRecord passageiro,
  ) async {
    if (_servicos.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Crie um serviço recorrente antes de liberar acessos.'),
        ),
      );
      return;
    }

    final initialServicoIds =
        _servicos
            .where((servico) => servico.passageiroIds.contains(passageiro.id))
            .map((servico) => servico.id)
            .toSet();

    final selectedIds = await showDialog<List<int>>(
      context: context,
      builder:
          (context) => AdminVinculosPassageiroServicosDialog(
            passageiro: passageiro,
            servicos: _servicos,
            initialServicoIds: initialServicoIds,
          ),
    );

    if (selectedIds == null) {
      return;
    }

    try {
      await _gateway.updatePassageiroServicos(
        passageiroId: passageiro.id,
        servicoIds: selectedIds,
      );
      await _loadPage();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Serviços liberados atualizados para o passageiro.'),
        ),
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
    if (_isLoading) {
      return const AdminVinculosTaskQueueLoadingState();
    }

    if (_errorMessage != null) {
      return AdminVinculosErrorState(
        message: _errorMessage!,
        onRetry: _loadPage,
      );
    }

    return AdminPortalPage(
      breadcrumb: 'Operação / Onboarding',
      title: 'Onboarding de acessos e vínculos',
      description:
          'Acompanhe solicitações, convites e vínculos familiares como tarefas da operação.',
      trailing: const AdminPortalTag(
        label: 'Fila de tarefas',
        background: AdminPortalColors.blueSoft,
        foreground: AdminPortalColors.blue,
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final stacked = constraints.maxWidth < 1180;
          final accessRequestsPanel = AdminVinculosAccessRequestsPanel(
            requests: _accessRequests,
            onRefresh: _loadPage,
            onOpenRequest: _openAccessRequest,
          );
          final inviteCodesPanel = AdminVinculosInviteCodesPanel(
            inviteCodes: _inviteCodes,
            onCreateInvite: _openCreateInviteDialog,
            onOpenInvite: _openInviteDetail,
          );
          final creationPanel = AdminVinculosCreationPanel(
            passageiros: _passageiros,
            responsaveis: _responsaveis,
            onCreateLink: _openCreateResponsavelPassageiroLinkDialog,
          );
          final serviceRightsPanel = AdminVinculosPassageiroServicosPanel(
            passageiros: _passageiros,
            servicos: _servicos,
            onEdit: _openPassageiroServicosDialog,
          );
          final operationalPanel = AdminVinculosPassageiroMotoristaPanel(
            links: _passageiroMotoristaLinks,
            onRefresh: _loadPage,
            onUnlink: _unlinkPassageiroMotorista,
          );
          final guardianPanel = AdminVinculosResponsavelPassageiroPanel(
            links: _responsavelPassageiroLinks,
            onCreateLink: _openCreateResponsavelPassageiroLinkDialog,
            onUnlink: _unlinkResponsavelPassageiro,
          );
          final taskQueuePanel = AdminVinculosTaskQueuePanel(
            accessRequests: _accessRequests,
            inviteCodes: _inviteCodes,
            responsavelPassageiroLinks: _responsavelPassageiroLinks,
            passageiros: _passageiros,
            responsaveis: _responsaveis,
            onOpenFirstRequest: () async {
              if (_accessRequests.isEmpty) {
                return;
              }
              await _openAccessRequest(_accessRequests.first);
            },
            onCreateInvite: _openCreateInviteDialog,
            onCreateGuardianLink: _openCreateResponsavelPassageiroLinkDialog,
          );

          if (stacked) {
            return Column(
              children: [
                taskQueuePanel,
                const SizedBox(height: 24),
                accessRequestsPanel,
                const SizedBox(height: 24),
                inviteCodesPanel,
                const SizedBox(height: 24),
                creationPanel,
                const SizedBox(height: 24),
                serviceRightsPanel,
                const SizedBox(height: 24),
                operationalPanel,
                const SizedBox(height: 24),
                guardianPanel,
              ],
            );
          }

          return Column(
            children: [
              taskQueuePanel,
              const SizedBox(height: 24),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 6, child: accessRequestsPanel),
                  const SizedBox(width: 24),
                  Expanded(flex: 6, child: inviteCodesPanel),
                ],
              ),
              const SizedBox(height: 24),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    flex: 4,
                    child: Column(
                      children: [
                        creationPanel,
                        const SizedBox(height: 24),
                        serviceRightsPanel,
                      ],
                    ),
                  ),
                  const SizedBox(width: 32),
                  Expanded(
                    flex: 8,
                    child: Column(
                      children: [
                        operationalPanel,
                        const SizedBox(height: 24),
                        guardianPanel,
                      ],
                    ),
                  ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }
}
