import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../../tracking/presentation/trip_live_map_panel.dart';
import '../data/admin_viagens_portal_service.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_rota_base_record.dart';
import '../domain/admin_servico_recorrente_record.dart';
import '../domain/admin_viagem_bulk_create_result.dart';
import '../domain/admin_viagem_create_input.dart';
import '../domain/admin_viagem_detail_record.dart';
import '../domain/admin_viagem_manifest_preview_record.dart';
import '../domain/admin_viagem_record.dart';
import 'admin_list_state_mixin.dart';
import 'admin_viagens_presenter.dart';
import 'admin_viagens_review_intent.dart';
import 'widgets/admin_viagens_create_widgets.dart';
import 'widgets/admin_viagens_detail_view.dart';
import 'widgets/admin_viagens_error_state.dart';
import 'widgets/admin_viagens_manifest_preview_dialog.dart';
import 'widgets/admin_viagens_operational_notice_panel.dart';
import 'widgets/admin_viagens_widgets.dart';
import 'widgets/admin_portal_layout.dart';

class AdminViagensScreen extends StatefulWidget {
  const AdminViagensScreen({
    super.key,
    required this.session,
    this.gateway,
    this.reviewIntent,
    this.mapRenderMode = TripLiveMapRenderMode.interactive,
  });

  final AdminPortalSession session;
  final AdminViagensGateway? gateway;
  final AdminViagensReviewIntent? reviewIntent;
  final TripLiveMapRenderMode mapRenderMode;

  @override
  State<AdminViagensScreen> createState() => _AdminViagensScreenState();
}

class _AdminViagensScreenState extends State<AdminViagensScreen>
    with AdminListStateMixin<AdminViagemRecord, AdminViagensScreen> {
  final AdminViagensPresenter _presenter = const AdminViagensPresenter();
  late final AdminViagensGateway _gateway;

  List<AdminRotaBaseRecord> _rotas = const [];
  List<AdminServicoRecorrenteRecord> _servicos = const [];
  List<AdminMotoristaRecord> _motoristas = const [];
  bool _isSubmitting = false;
  bool _isGeneratingAutomatic = false;
  bool _isDetailLoading = false;
  bool _isUpdatingMotorista = false;
  bool _isSyncingManifest = false;
  bool _isCancellingTrip = false;
  int? _updatingPresenceParadaId;
  DateTime? _selectedDate;
  AdminViagemBulkCreateResult? _lastGenerationResult;
  AdminViagemDetailRecord? _selectedDetail;
  String? _detailErrorMessage;
  AdminViagensMode _mode = AdminViagensMode.list;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminViagensPortalService(session: widget.session);
    _loadPage();
  }

  Future<void> _loadPage() async {
    startLoading();

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchViagens(data: _selectedDate),
        _gateway.fetchRotas(),
        _gateway.fetchServicos(),
        _gateway.fetchMotoristas(),
      ]);

      setState(() {
        items = results[0] as List<AdminViagemRecord>;
        _rotas = results[1] as List<AdminRotaBaseRecord>;
        _servicos = results[2] as List<AdminServicoRecorrenteRecord>;
        _motoristas = results[3] as List<AdminMotoristaRecord>;
        isLoading = false;
      });
    } on AdminPortalException catch (error) {
      setLoadError(error.message);
    } catch (_) {
      setLoadError(AppStrings.admin.erroCarregarViagens);
    }
  }

  Future<void> _createViagem(AdminViagemCreateInput input) async {
    setState(() => _isSubmitting = true);
    try {
      final created = await _gateway.createViagem(input);
      await _loadPage();
      if (!mounted) {
        return;
      }
      setState(() => _mode = AdminViagensMode.list);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Exceção do dia ${created.nomeOperacional} criada.'),
        ),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      var existingTrip = _presenter.findExistingTripForInput(items, input);
      if (existingTrip == null && error.code == 'CONFLICT') {
        await _reloadTripsForCreateConflict(input.dataOperacao);
        if (!mounted) return;
        existingTrip = _presenter.findExistingTripForInput(items, input);
      }
      if (existingTrip != null) {
        await _openDetail(existingTrip.id);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_presenter.createConflictMaintenanceMessage(error)),
          ),
        );
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  Future<void> _reloadTripsForCreateConflict(DateTime dataOperacao) async {
    setState(() {
      _selectedDate = _presenter.normalizeDate(dataOperacao);
      _lastGenerationResult = null;
    });
    await _loadPage();
  }

  DateTime get _operationDate {
    return _presenter.operationDate(_selectedDate, DateTime.now());
  }

  Future<void> _generateAutomaticViagens() async {
    final dataOperacao = _operationDate;
    setState(() {
      _isGeneratingAutomatic = true;
      _selectedDate = dataOperacao;
    });

    try {
      final result = await _gateway.generateAutomaticViagens(dataOperacao);
      await _loadPage();
      if (!mounted) {
        return;
      }
      setState(() => _lastGenerationResult = result);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(adminViagensBulkGenerationMessage(result))),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _isGeneratingAutomatic = false);
      }
    }
  }

  Future<void> _openDetail(int viagemId) async {
    setState(() {
      _mode = AdminViagensMode.detail;
      _isDetailLoading = true;
      _detailErrorMessage = null;
      _selectedDetail = null;
    });

    try {
      final detail = await _gateway.fetchViagemDetail(viagemId);
      if (!mounted) return;
      setState(() {
        _selectedDetail = detail;
        _isDetailLoading = false;
      });
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      setState(() {
        _detailErrorMessage = error.message;
        _isDetailLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _detailErrorMessage =
            'Não foi possível carregar o detalhe desta viagem.';
        _isDetailLoading = false;
      });
    }
  }

  Future<void> _changeTripDriver(int motoristaId) async {
    final detail = _selectedDetail;
    if (detail == null) return;

    setState(() => _isUpdatingMotorista = true);
    try {
      final updated = await _gateway.updateViagemMotorista(
        detail.id,
        motoristaId,
      );
      await _loadPage();
      if (!mounted) return;
      setState(() {
        _mode = AdminViagensMode.detail;
        _selectedDetail = updated;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Motorista operacional alterado para ${updated.motorista.nome}.',
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
        setState(() => _isUpdatingMotorista = false);
      }
    }
  }

  Future<void> _syncSelectedManifest() async {
    final detail = _selectedDetail;
    if (detail == null) return;

    setState(() => _isSyncingManifest = true);
    try {
      final preview = await _gateway.fetchManifestPreview(detail.id);
      if (!mounted) return;
      setState(() => _isSyncingManifest = false);

      if (preview.semMudancas) {
        await _showManifestPreview(preview, allowSync: false);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Lista já está atualizada.')),
        );
        return;
      }

      final confirmed = await _showManifestPreview(preview);
      if (confirmed != true || !mounted) return;

      setState(() => _isSyncingManifest = true);
      final updated = await _gateway.syncManifesto(detail.id);
      await _loadPage();
      if (!mounted) return;
      setState(() {
        _mode = AdminViagensMode.detail;
        _selectedDetail = updated;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Lista atualizada com ${updated.quantidadeParadas} paradas.',
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
        setState(() => _isSyncingManifest = false);
      }
    }
  }

  Future<bool?> _showManifestPreview(
    AdminViagemManifestPreviewRecord preview, {
    bool allowSync = true,
  }) {
    return showDialog<bool>(
      context: context,
      builder:
          (context) => AdminViagensManifestPreviewDialog(
            preview: preview,
            allowSync: allowSync,
          ),
    );
  }

  Future<void> _cancelSelectedTrip() async {
    final detail = _selectedDetail;
    if (detail == null) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('Cancelar viagem planejada?'),
            content: Text(
              'A viagem ${detail.nomeOperacional} ficará cancelada para este dia. O serviço recorrente continua ativo para próximas datas.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('Manter viagem'),
              ),
              FilledButton.icon(
                onPressed: () => Navigator.of(context).pop(true),
                icon: const Icon(Icons.block_rounded),
                label: const Text('Cancelar viagem'),
              ),
            ],
          ),
    );
    if (confirmed != true) return;

    setState(() => _isCancellingTrip = true);
    try {
      final updated = await _gateway.cancelViagem(detail.id);
      await _loadPage();
      if (!mounted) return;
      setState(() {
        _mode = AdminViagensMode.detail;
        _selectedDetail = updated;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Viagem cancelada com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _isCancellingTrip = false);
      }
    }
  }

  Future<void> _updateManifestPresence(
    AdminViagemParadaRecord parada,
    String status,
  ) async {
    final detail = _selectedDetail;
    final servicoId = detail?.servicoId;
    if (detail == null || servicoId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Presença por serviço só está disponível em viagens de serviço recorrente.',
          ),
        ),
      );
      return;
    }

    setState(() => _updatingPresenceParadaId = parada.id);
    try {
      final updated = await _gateway.updateManifestPresence(
        viagemId: detail.id,
        servicoId: servicoId,
        passageiroId: parada.passageiro.id,
        dataOperacao: detail.dataOperacao,
        presencaId: parada.presencaId,
        status: status,
      );
      if (!mounted) return;
      setState(() => _selectedDetail = updated);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_presenter.presenceUpdateMessage(status))),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _updatingPresenceParadaId = null);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (errorMessage != null) {
      return AdminViagensErrorState(message: errorMessage!, onRetry: _loadPage);
    }

    return switch (_mode) {
      AdminViagensMode.list => _buildListView(),
      AdminViagensMode.create => _buildCreateView(),
      AdminViagensMode.detail => _buildDetailView(),
    };
  }

  Widget _buildListView() {
    final summary = AdminViagensListSummary.fromTrips(items);

    return AdminPortalPage(
      breadcrumb: 'Operação • Historico de Viagens',
      title: 'Viagens do dia',
      description: 'Gere viagens do dia a partir dos serviços recorrentes.',
      trailing: AdminViagensDateRangeChip(
        date: _selectedDate,
        onPressed: () async {
          final now = DateTime.now();
          final selected = await showDatePicker(
            context: context,
            initialDate: _selectedDate ?? now,
            firstDate: DateTime(now.year - 1),
            lastDate: DateTime(now.year + 1),
            builder: adminViagensDatePickerBuilder,
          );
          if (selected == null) return;
          setState(() {
            _selectedDate = selected;
            _lastGenerationResult = null;
          });
          await _loadPage();
        },
      ),
      child: AdminViagensOperationalListSection(
        viagens: items,
        inProgressCount: summary.inProgressCount,
        completedCount: summary.completedCount,
        isGeneratingAutomatic: _isGeneratingAutomatic,
        generationResultPanel: adminViagensOperationalNoticePanel(
          reviewIntent: widget.reviewIntent,
          generationResult: _lastGenerationResult,
          isGeneratingAutomatic: _isGeneratingAutomatic,
          onGenerateAutomatic: _generateAutomaticViagens,
          onOpenTrip: _openDetail,
        ),
        onGenerateAutomatic: _generateAutomaticViagens,
        onCreateManual: () => setState(() => _mode = AdminViagensMode.create),
        onOpenDetail: _openDetail,
      ),
    );
  }

  Widget _buildCreateView() {
    return AdminViagemCreateView(
      rotas: _rotas,
      servicos: _servicos,
      motoristas: _motoristas,
      isSubmitting: _isSubmitting,
      initialDate: _operationDate,
      onCancel: () => setState(() => _mode = AdminViagensMode.list),
      onSubmit: _createViagem,
    );
  }

  Widget _buildDetailView() {
    if (_isDetailLoading) {
      return const AdminPortalPage(
        breadcrumb: 'Viagens • Detalhe',
        title: 'Detalhe da Viagem',
        description: 'Carregando lista da viagem.',
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_detailErrorMessage != null) {
      return AdminViagensErrorState(
        message: _detailErrorMessage!,
        onRetry: () async {
          final id = _selectedDetail?.id;
          if (id == null) {
            setState(() => _mode = AdminViagensMode.list);
            return;
          }
          await _openDetail(id);
        },
      );
    }

    final detail = _selectedDetail;
    if (detail == null) {
      return AdminViagensErrorState(
        message: 'Nenhuma viagem selecionada.',
        onRetry: () async => setState(() => _mode = AdminViagensMode.list),
      );
    }

    return AdminViagemDetailView(
      detail: detail,
      session: widget.session,
      motoristas: _motoristas,
      isUpdatingMotorista: _isUpdatingMotorista,
      isSyncingManifest: _isSyncingManifest,
      isCancellingTrip: _isCancellingTrip,
      updatingPresenceParadaId: _updatingPresenceParadaId,
      mapRenderMode: widget.mapRenderMode,
      onBack: () => setState(() => _mode = AdminViagensMode.list),
      onRefresh: () => _openDetail(detail.id),
      onChangeMotorista: _changeTripDriver,
      onSyncManifest: _syncSelectedManifest,
      onCancelTrip: _cancelSelectedTrip,
      onUpdatePresence: _updateManifestPresence,
    );
  }
}
