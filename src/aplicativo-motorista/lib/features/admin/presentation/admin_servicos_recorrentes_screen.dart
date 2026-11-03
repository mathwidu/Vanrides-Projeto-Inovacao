import 'package:flutter/material.dart';

import '../data/admin_places_gateway.dart';
import '../data/admin_servicos_recorrentes_portal_service.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_servico_recorrente_input.dart';
import '../domain/admin_servico_recorrente_record.dart';
import 'admin_viagens_review_intent.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_servico_recorrente_form.dart';
import 'widgets/admin_servicos_recorrentes_detail_widgets.dart';
import 'widgets/admin_servicos_recorrentes_form_widgets.dart';
import 'widgets/admin_servicos_recorrentes_list_widgets.dart';

class AdminServicosRecorrentesScreen extends StatefulWidget {
  const AdminServicosRecorrentesScreen({
    super.key,
    required this.session,
    this.gateway,
    this.onOpenViagens,
    this.onOpenViagensReview,
  });

  final AdminPortalSession session;
  final AdminServicosRecorrentesGateway? gateway;
  final VoidCallback? onOpenViagens;
  final ValueChanged<AdminViagensReviewIntent>? onOpenViagensReview;

  @override
  State<AdminServicosRecorrentesScreen> createState() =>
      _AdminServicosRecorrentesScreenState();
}

enum _AdminServicosMode { list, detail, create, edit }

class _AdminServicosRecorrentesScreenState
    extends State<AdminServicosRecorrentesScreen> {
  late final AdminServicosRecorrentesGateway _gateway;
  late final AdminPlacesGateway _placesGateway;

  List<AdminServicoRecorrenteRecord> _servicos = const [];
  List<AdminMotoristaRecord> _motoristas = const [];
  List<AdminPassageiroRecord> _passageiros = const [];
  bool _isLoading = true;
  bool _isSubmitting = false;
  String? _loadError;
  _AdminServicosMode _mode = _AdminServicosMode.list;
  AdminServicoRecorrenteRecord? _editingServico;
  AdminServicoRecorrenteRecord? _selectedServico;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ??
        AdminServicosRecorrentesPortalService(session: widget.session);
    _placesGateway = AdminPlacesPortalService(session: widget.session);
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _loadError = null;
    });

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchServicos(),
        _gateway.fetchMotoristas(),
        _gateway.fetchPassageiros(),
      ]);

      setState(() {
        _servicos = results[0] as List<AdminServicoRecorrenteRecord>;
        _motoristas = results[1] as List<AdminMotoristaRecord>;
        _passageiros = results[2] as List<AdminPassageiroRecord>;
        _isLoading = false;
      });
    } on AdminPortalException catch (error) {
      setState(() {
        _loadError = error.message;
        _isLoading = false;
      });
    } catch (_) {
      setState(() {
        _loadError = 'Nao foi possivel carregar os servicos recorrentes.';
        _isLoading = false;
      });
    }
  }

  Future<void> _saveServico(AdminServicoRecorrenteInput input) async {
    setState(() => _isSubmitting = true);
    try {
      final editing = _editingServico;
      if (editing == null) {
        await _gateway.createServico(input);
      } else {
        await _gateway.editServico(editing.id, input);
      }
      await _loadData();
      if (!mounted) return;
      setState(() {
        _mode = _AdminServicosMode.list;
        _editingServico = null;
        _selectedServico = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            editing == null
                ? 'Servico recorrente criado com sucesso.'
                : 'Servico recorrente atualizado com sucesso.',
          ),
          action: _buildReviewTripsAction(
            servicoNome: input.nome,
            created: editing == null,
          ),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Nao foi possivel salvar o servico recorrente.'),
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  SnackBarAction? _buildReviewTripsAction({
    required String servicoNome,
    required bool created,
  }) {
    final review = widget.onOpenViagensReview;
    if (review != null) {
      return SnackBarAction(
        label: 'Revisar viagens',
        onPressed:
            () => review(
              AdminViagensReviewIntent.servicoRecorrente(
                servicoNome: servicoNome,
                created: created,
              ),
            ),
      );
    }

    final openViagens = widget.onOpenViagens;
    if (openViagens == null) {
      return null;
    }
    return SnackBarAction(label: 'Revisar viagens', onPressed: openViagens);
  }

  Future<void> _confirmInativar(AdminServicoRecorrenteRecord servico) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('Inativar serviço?'),
            content: Text(
              'O serviço "${servico.nome}" deixará de gerar novas viagens automaticamente.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: const Text('Inativar'),
              ),
            ],
          ),
    );

    if (confirmed != true) return;

    setState(() => _isSubmitting = true);
    try {
      await _gateway.deleteServico(servico.id);
      await _loadData();
      if (!mounted) return;
      setState(() {
        _mode = _AdminServicosMode.list;
        _selectedServico = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Serviço "${servico.nome}" inativado.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  void _openCreate() {
    setState(() {
      _mode = _AdminServicosMode.create;
      _editingServico = null;
      _selectedServico = null;
    });
  }

  void _openEdit(AdminServicoRecorrenteRecord servico) {
    setState(() {
      _mode = _AdminServicosMode.edit;
      _editingServico = servico;
      _selectedServico = servico;
    });
  }

  void _openDetail(AdminServicoRecorrenteRecord servico) {
    setState(() {
      _mode = _AdminServicosMode.detail;
      _editingServico = null;
      _selectedServico = servico;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_loadError != null) {
      return AdminPortalPage(
        breadcrumb: 'Operação • Serviços',
        title: 'Serviços recorrentes',
        description:
            'Organize as linhas operacionais antes das viagens do dia.',
        trailing: AdminPortalSecondaryButton(
          label: 'Tentar novamente',
          icon: Icons.refresh,
          onPressed: _loadData,
        ),
        child: AdminPortalSurface(
          child: Text(
            _loadError!,
            style: const TextStyle(color: AdminPortalColors.red),
          ),
        ),
      );
    }

    return switch (_mode) {
      _AdminServicosMode.list => _buildListView(),
      _AdminServicosMode.detail => _buildDetailView(),
      _AdminServicosMode.create => _buildFormView(),
      _AdminServicosMode.edit => _buildFormView(),
    };
  }

  Widget _buildListView() {
    final ativos = _servicos.where((servico) => servico.ativo).length;
    final automaticos =
        _servicos
            .where((servico) => servico.ativo && servico.geracaoAutomaticaAtiva)
            .length;
    final passageiros = _servicos.fold<int>(
      0,
      (sum, servico) => sum + servico.quantidadePassageiros,
    );
    final semMotoristaServicos = _servicos
        .where((servico) => servico.ativo && !servico.hasMotoristaPadrao)
        .toList(growable: false);
    final semMotorista = semMotoristaServicos.length;

    return AdminPortalPage(
      breadcrumb: 'Operação • Serviços',
      title: 'Serviços recorrentes',
      description:
          'Cadastre as linhas que a empresa oferece e controle quem pode usar cada uma.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Atualizar',
            icon: Icons.refresh,
            onPressed: _loadData,
          ),
          const SizedBox(width: 12),
          AdminPortalPrimaryButton(
            label: 'Novo serviço',
            icon: Icons.add_circle_outline,
            onPressed: _openCreate,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: 16,
            runSpacing: 16,
            children: [
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'Serviços ativos',
                  value: ativos.toString(),
                  icon: Icons.route_outlined,
                  subtitle: 'Linhas operacionais disponíveis.',
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'Geração automática',
                  value: automaticos.toString(),
                  icon: Icons.auto_mode_outlined,
                  subtitle: 'Serviços que geram viagem do dia.',
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'Elegibilidades',
                  value: passageiros.toString(),
                  icon: Icons.groups_outlined,
                  subtitle: 'Vínculos passageiro-serviço.',
                ),
              ),
              SizedBox(
                width: 250,
                child: AdminPortalMetricCard(
                  label: 'Sem motorista fixo',
                  value: semMotorista.toString(),
                  icon: Icons.person_off_outlined,
                  subtitle: 'Exigem escala na viagem do dia.',
                ),
              ),
            ],
          ),
          if (semMotorista > 0) ...[
            const SizedBox(height: 18),
            AdminServicosMotoristaAlert(
              semMotorista: semMotorista,
              firstServicoName: semMotoristaServicos.first.nome,
              onReviewFirst: () => _openDetail(semMotoristaServicos.first),
            ),
          ],
          const SizedBox(height: 24),
          AdminPortalSurface(
            padding: const EdgeInsets.all(0),
            child:
                _servicos.isEmpty
                    ? AdminServicosRecorrentesEmptyState(onCreate: _openCreate)
                    : Column(
                      children: [
                        const AdminServicosRecorrentesHeader(),
                        for (final servico in _servicos)
                          AdminServicoRecorrenteRow(
                            servico: servico,
                            politicaPresencaLabel:
                                adminServicoPoliticaOptions[servico
                                    .politicaPresenca] ??
                                servico.politicaPresenca,
                            horarioPartidaLabel: adminServicoTextOrDash(
                              servico.horarioPartida,
                            ),
                            onOpen: () => _openDetail(servico),
                            onEdit: () => _openEdit(servico),
                            onDelete:
                                _isSubmitting
                                    ? null
                                    : () => _confirmInativar(servico),
                          ),
                      ],
                    ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailView() {
    final servico = _currentSelectedServico();
    if (servico == null) {
      return AdminPortalPage(
        breadcrumb: 'Operação • Serviços',
        title: 'Serviço não encontrado',
        description: 'Atualize a lista para conferir os serviços disponíveis.',
        trailing: AdminPortalSecondaryButton(
          label: 'Voltar',
          icon: Icons.arrow_back,
          onPressed: () => setState(() => _mode = _AdminServicosMode.list),
        ),
        child: AdminPortalSurface(
          child: Text(
            _loadError ?? 'Nenhum serviço recorrente selecionado.',
            style: const TextStyle(color: AdminPortalColors.textMuted),
          ),
        ),
      );
    }

    final passageirosElegiveis = _passageirosElegiveis(servico);

    return AdminPortalPage(
      breadcrumb: 'Operação • Serviços',
      title: servico.nome,
      description:
          'Prontidão operacional, elegibilidade e regras usadas para criar viagens do dia.',
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          AdminPortalSecondaryButton(
            label: 'Voltar',
            icon: Icons.arrow_back,
            onPressed:
                () => setState(() {
                  _mode = _AdminServicosMode.list;
                  _selectedServico = null;
                }),
          ),
          const SizedBox(width: 12),
          AdminPortalSecondaryButton(
            label: 'Editar',
            icon: Icons.edit_outlined,
            onPressed: () => _openEdit(servico),
          ),
          if (servico.ativo) ...[
            const SizedBox(width: 12),
            AdminPortalSecondaryButton(
              label: 'Inativar',
              icon: Icons.block_outlined,
              onPressed: _isSubmitting ? null : () => _confirmInativar(servico),
            ),
          ],
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AdminServicoDetailHero(
            servico: servico,
            onReviewTrips: _buildOpenServicoReview(servico),
          ),
          const SizedBox(height: 20),
          const AdminServicoChangeImpactNotice(),
          const SizedBox(height: 20),
          LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 1050;
              final readiness = AdminServicoReadinessSurface(servico: servico);
              final rules = AdminServicoRulesSurface(servico: servico);

              if (compact) {
                return Column(
                  children: [readiness, const SizedBox(height: 20), rules],
                );
              }

              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 5, child: readiness),
                  const SizedBox(width: 20),
                  Expanded(flex: 7, child: rules),
                ],
              );
            },
          ),
          const SizedBox(height: 20),
          AdminServicoEligiblePassengersSurface(
            passageiros: passageirosElegiveis,
            totalIds: servico.passageiroIds.length,
          ),
        ],
      ),
    );
  }

  VoidCallback? _buildOpenServicoReview(AdminServicoRecorrenteRecord servico) {
    final review = widget.onOpenViagensReview;
    if (review != null) {
      return () {
        review(
          AdminViagensReviewIntent.servicoRecorrente(
            servicoNome: servico.nome,
            created: false,
          ),
        );
      };
    }

    return widget.onOpenViagens;
  }

  AdminServicoRecorrenteRecord? _currentSelectedServico() {
    final selected = _selectedServico;
    if (selected == null) return null;

    return _servicos.cast<AdminServicoRecorrenteRecord?>().firstWhere(
      (servico) => servico?.id == selected.id,
      orElse: () => selected,
    );
  }

  List<AdminPassageiroRecord> _passageirosElegiveis(
    AdminServicoRecorrenteRecord servico,
  ) {
    final ids = servico.passageiroIds.toSet();
    return _passageiros
        .where((passageiro) => ids.contains(passageiro.id))
        .toList(growable: false);
  }

  Widget _buildFormView() {
    final editing = _editingServico;
    return AdminServicoRecorrenteForm(
      key: ValueKey('servico-form-${editing?.id ?? 'novo'}'),
      placesGateway: _placesGateway,
      initial: editing,
      motoristas: _motoristas,
      passageiros: _passageiros,
      isSubmitting: _isSubmitting,
      onCancel:
          () => setState(() {
            _mode = _AdminServicosMode.list;
            _editingServico = null;
            _selectedServico = null;
          }),
      onSubmit: _saveServico,
    );
  }
}
