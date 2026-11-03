import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../data/admin_passageiro_portal_service.dart';
import '../data/admin_vinculo_portal_service.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_create_input.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../../service_presence/domain/passenger_available_service_record.dart';
import 'admin_list_state_mixin.dart';
import 'admin_viagens_review_intent.dart';
import 'widgets/admin_passageiro_edit_dialog.dart';
import 'widgets/admin_passageiro_entry_flow.dart';
import 'widgets/admin_passageiro_form_dialog.dart';
import 'widgets/admin_passageiro_reassign_driver_dialog.dart';
import 'widgets/admin_passageiro_services_flow.dart';
import 'widgets/admin_passageiros_widgets.dart';
import 'widgets/admin_portal_error_state.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_responsavel_passageiro_link_flow.dart';

class AdminPassageirosScreen extends StatefulWidget {
  const AdminPassageirosScreen({
    super.key,
    required this.session,
    this.gateway,
    this.linkGateway,
    this.onboardingGateway,
    this.onOpenViagens,
    this.onOpenViagensReview,
  });

  final AdminPortalSession session;
  final AdminPassageiroGateway? gateway;
  final AdminResponsavelPassageiroLinkGateway? linkGateway;
  final AdminPassengerOnboardingGateway? onboardingGateway;
  final VoidCallback? onOpenViagens;
  final ValueChanged<AdminViagensReviewIntent>? onOpenViagensReview;

  @override
  State<AdminPassageirosScreen> createState() => _AdminPassageirosScreenState();
}

class _AdminPassageirosScreenState extends State<AdminPassageirosScreen>
    with AdminListStateMixin<AdminPassageiroRecord, AdminPassageirosScreen> {
  late final AdminPassageiroGateway _gateway;
  List<AdminMotoristaRecord> _motoristas = const [];
  List<AdminServicoRecorrenteRecord> _servicos = const [];
  AdminPassageiroRecord? _selectedPassageiroSummary;
  AdminPassageiroRecord? _selectedPassageiroDetail;
  List<PassengerAvailableServiceRecord> _selectedPresenceServices = const [];
  bool _isLoadingSelectedPresence = false;
  String? _selectedPresenceError;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminPassageiroPortalService(session: widget.session);
    _loadPage();
  }

  Future<void> _loadPage({int? preserveSelectionId}) async {
    startLoading();

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchMotoristas(),
        _gateway.fetchServicos(),
        _gateway.fetchPassageiros(),
      ]);

      final motoristas = results[0] as List<AdminMotoristaRecord>;
      final servicos = results[1] as List<AdminServicoRecorrenteRecord>;
      final passageiros = results[2] as List<AdminPassageiroRecord>;
      final selectedId = preserveSelectionId ?? _selectedPassageiroSummary?.id;
      final selected = passageiros.cast<AdminPassageiroRecord?>().firstWhere(
        (item) => item?.id == selectedId,
        orElse: () => passageiros.isEmpty ? null : passageiros.first,
      );

      setState(() {
        _motoristas = motoristas;
        _servicos = servicos;
        items = passageiros;
        _selectedPassageiroSummary = selected;
        _selectedPassageiroDetail = null;
        _selectedPresenceServices = const [];
        _selectedPresenceError = null;
        _isLoadingSelectedPresence = selected != null;
        isLoading = false;
      });

      if (selected != null) {
        await _loadDetail(selected.id);
      }
    } on AdminPortalException catch (error) {
      setLoadError(error.message);
    } catch (_) {
      setLoadError(AppStrings.admin.erroCarregarPassageiros);
    }
  }

  Future<void> _loadDetail(int passageiroId) async {
    try {
      final detail = await _gateway.fetchPassageiroDetail(passageiroId);
      if (!mounted) return;
      setState(() {
        _selectedPassageiroDetail = detail;
        _selectedPassageiroSummary = items
            .cast<AdminPassageiroRecord?>()
            .firstWhere(
              (item) => item?.id == passageiroId,
              orElse: () => detail,
            );
        _selectedPresenceServices = const [];
        _selectedPresenceError = null;
        _isLoadingSelectedPresence = true;
      });
      await _loadPresenceToday(passageiroId);
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nao foi possivel carregar o detalhe do passageiro.'),
        ),
      );
    }
  }

  Future<void> _loadPresenceToday(int passageiroId) async {
    try {
      final services = await _gateway.fetchAvailableServices(
        passageiroId: passageiroId,
        data: DateTime.now(),
      );
      _finishPresenceLoad(passageiroId, services: services);
    } on AdminPortalException catch (error) {
      _finishPresenceLoad(passageiroId, error: error.message);
    } catch (_) {
      _finishPresenceLoad(
        passageiroId,
        error: 'Nao foi possivel carregar a presenca de hoje.',
      );
    }
  }

  void _finishPresenceLoad(
    int passageiroId, {
    List<PassengerAvailableServiceRecord> services = const [],
    String? error,
  }) {
    if (!mounted || _selectedPassageiroSummary?.id != passageiroId) return;
    setState(() {
      _selectedPresenceServices = services;
      _selectedPresenceError = error;
      _isLoadingSelectedPresence = false;
    });
  }

  Future<void> _openCreateDialog() async {
    final created = await showDialog<AdminPassageiroRecord>(
      context: context,
      builder:
          (context) => AdminPassageiroFormDialog(
            title: 'Novo passageiro',
            subtitle:
                'Cadastre a pessoa e libere as linhas recorrentes que ela pode usar. Motorista é apenas atalho operacional opcional.',
            motoristas: _motoristas,
            servicos: _servicos,
            onSubmit: _createPassageiroWithServicos,
          ),
    );

    if (created == null) {
      return;
    }

    await _loadPage(preserveSelectionId: created.id);

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Passageiro criado com sucesso no portal.')),
    );
  }

  Future<AdminPassageiroRecord> _createPassageiroWithServicos(
    AdminPassageiroCreateInput input,
    List<int> _,
  ) async {
    return _gateway.createPassageiro(input);
  }

  Future<void> _openEditDialog() async {
    final passageiro = _selectedPassageiroDetail;
    if (passageiro == null) {
      return;
    }

    final updated = await showDialog<AdminPassageiroRecord>(
      context: context,
      builder:
          (context) => AdminPassageiroEditDialog(
            passageiro: passageiro,
            motoristas: _motoristas,
            onSubmit:
                (input) => _gateway.updatePassageiro(passageiro.id, input),
          ),
    );

    if (updated == null) {
      return;
    }

    await _loadPage(preserveSelectionId: updated.id);

    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Passageiro atualizado com sucesso.')),
    );
  }

  Future<void> _openReassignDialog() async {
    final passageiro = _selectedPassageiroDetail;
    if (passageiro == null || _motoristas.isEmpty) {
      return;
    }

    final selectedMotoristaId = await showDialog<int>(
      context: context,
      builder:
          (context) => AdminPassageiroReassignDriverDialog(
            passageiro: passageiro,
            motoristas: _motoristas,
          ),
    );

    if (selectedMotoristaId == null) {
      return;
    }

    try {
      await _gateway.reassignMotorista(passageiro.id, selectedMotoristaId);
      await _loadPage(preserveSelectionId: passageiro.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.admin.motoristaReatribuido)),
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
    final updated = await openAdminPassageiroServicosFlow(
      context: context,
      passageiro: passageiro,
      servicos: _servicos,
      onSave:
          (servicoIds) => _gateway.updatePassageiroServicos(
            passageiroId: passageiro.id,
            servicoIds: servicoIds,
          ),
      onOpenViagens:
          widget.onOpenViagensReview == null
              ? widget.onOpenViagens
              : () => widget.onOpenViagensReview!(
                AdminViagensReviewIntent.passageiroServicos(passageiro.nome),
              ),
    );

    if (updated) {
      await _loadPage(preserveSelectionId: passageiro.id);
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

    final selected = _selectedPassageiroDetail ?? _selectedPassageiroSummary;
    final total = items.length;
    final comServico =
        items
            .where((item) => _hasEnabledRecurringService(item.id, _servicos))
            .length;
    final semServico = total - comServico;
    final fichasProntas =
        items
            .where((item) => _hasCompletePassengerProfile(item, _servicos))
            .length;
    final fichasPendentes = total - fichasProntas;
    final perfilCompleto =
        total == 0 ? 0 : ((fichasProntas / total) * 100).round();

    return AdminPortalPage(
      breadcrumb: 'Portal > Passageiros',
      title: 'Passageiros',
      description:
          'Cadastre passageiros, revise responsáveis e libere os serviços que entram nas viagens do dia.',
      trailing: Wrap(
        spacing: 12,
        runSpacing: 12,
        alignment: WrapAlignment.end,
        children: [
          AdminPortalPrimaryButton(
            label: 'Cadastrar Passageiro',
            icon: Icons.person_add_alt_1,
            onPressed: _openCreateDialog,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 24,
            runSpacing: 24,
            children: [
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'TOTAL ATIVOS',
                  value: '$total',
                  icon: Icons.groups_2_outlined,
                  iconBackground: AdminPortalColors.blueSoft,
                  subtitle: 'Passageiros cadastrados',
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'COM SERVIÇO',
                  value: '$comServico',
                  icon: Icons.route_outlined,
                  iconBackground: AdminPortalColors.blueSoft,
                  subtitle: 'Liberados para viagens',
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'SEM SERVIÇO',
                  value: '$semServico',
                  icon: Icons.playlist_remove_rounded,
                  iconBackground: AdminPortalColors.orangeSoft,
                  subtitle:
                      semServico == 0
                          ? 'Todos entram em algum serviço'
                          : 'Precisa liberar horários',
                  trailingTag:
                      semServico == 0
                          ? null
                          : const AdminPortalTag(
                            label: 'AÇÃO',
                            background: AdminPortalColors.orangeSoft,
                            foreground: AdminPortalColors.warningText,
                          ),
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'PERFIL COMPLETO',
                  value: '$perfilCompleto%',
                  icon: Icons.fact_check_outlined,
                  iconBackground: AdminPortalColors.greenSoft,
                  subtitle:
                      fichasPendentes == 0
                          ? 'Nenhuma ficha comum pendente'
                          : '$fichasPendentes ficha(s) com cadastro, serviço ou responsável pendente',
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),
          AdminPassageiroEntryFlow(
            session: widget.session,
            gateway: widget.onboardingGateway,
            motoristas: _motoristas,
            servicos: _servicos,
            onPassengerChanged:
                (passageiroId) => _loadPage(preserveSelectionId: passageiroId),
          ),
          const SizedBox(height: 32),
          AdminPassageirosListPanel(
            passageiros: items,
            selectedPassageiro: selected,
            onRefresh: () => _loadPage(),
            onCreate: _openCreateDialog,
            onEdit: _openEditDialog,
            onReassign: _openReassignDialog,
            onSelect: (passageiro) async {
              setState(() {
                _selectedPassageiroSummary = passageiro;
                _selectedPassageiroDetail = null;
                _selectedPresenceServices = const [];
                _selectedPresenceError = null;
                _isLoadingSelectedPresence = true;
              });
              await _loadDetail(passageiro.id);
            },
          ),
          const SizedBox(height: 32),
          AdminPassageirosActivitySection(
            selectedPassageiro: selected,
            servicos: _servicos,
            presenceServices: _selectedPresenceServices,
            isLoadingPresenceToday: _isLoadingSelectedPresence,
            presenceTodayError: _selectedPresenceError,
            onEditProfile:
                _selectedPassageiroDetail == null ? null : _openEditDialog,
            onEditServicos:
                selected == null
                    ? null
                    : () => _openPassageiroServicosDialog(selected),
            onManageResponsaveis:
                selected == null
                    ? null
                    : () async {
                      final changed = await openAdminPassageiroResponsaveisFlow(
                        context: context,
                        session: widget.session,
                        passageiro: selected,
                        gateway: widget.linkGateway,
                      );
                      if (changed) {
                        await _loadPage(preserveSelectionId: selected.id);
                      }
                    },
            onReviewTrips:
                selected == null
                    ? null
                    : () {
                      final review = widget.onOpenViagensReview;
                      if (review != null) {
                        review(
                          AdminViagensReviewIntent.passageiroServicos(
                            selected.nome,
                          ),
                        );
                        return;
                      }
                      widget.onOpenViagens?.call();
                    },
          ),
        ],
      ),
    );
  }
}

bool _hasEnabledRecurringService(
  int passageiroId,
  List<AdminServicoRecorrenteRecord> servicos,
) => servicos.any(
  (servico) => servico.ativo && servico.passageiroIds.contains(passageiroId),
);

bool _hasCompletePassengerProfile(
  AdminPassageiroRecord passageiro,
  List<AdminServicoRecorrenteRecord> servicos,
) {
  final hasGuardianWhenMinor =
      !passageiro.menorDeIdade ||
      passageiro.responsavelCount > 0 ||
      passageiro.responsaveis.isNotEmpty;

  return _hasText(passageiro.nome) &&
      _hasText(passageiro.email) &&
      _hasText(passageiro.telefone) &&
      _hasText(passageiro.dataNascimento) &&
      _hasText(passageiro.endereco) &&
      _hasText(passageiro.cidade) &&
      _hasText(passageiro.estado) &&
      hasGuardianWhenMinor &&
      _hasEnabledRecurringService(passageiro.id, servicos);
}

bool _hasText(String? value) => (value ?? '').trim().isNotEmpty;
