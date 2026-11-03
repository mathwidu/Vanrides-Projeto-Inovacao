import 'package:flutter/material.dart';

import '../../../l10n/app_strings.dart';
import '../data/admin_places_gateway.dart';
import '../data/admin_rotas_portal_service.dart';
import '../domain/admin_motorista_record.dart';
import '../domain/admin_passageiro_record.dart';
import '../domain/admin_portal_exception.dart';
import '../domain/admin_portal_session.dart';
import '../domain/admin_rota_base_create_input.dart';
import '../domain/admin_rota_base_record.dart';
import 'admin_list_state_mixin.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_rota_form_view.dart';
import 'widgets/admin_rotas_detail_view.dart';
import 'widgets/admin_rotas_widgets.dart';

class AdminRotasScreen extends StatefulWidget {
  const AdminRotasScreen({super.key, required this.session, this.gateway});

  final AdminPortalSession session;
  final AdminRotasGateway? gateway;

  @override
  State<AdminRotasScreen> createState() => _AdminRotasScreenState();
}

enum _AdminRotasViewMode { list, create, edit, detail }

class _AdminRotasScreenState extends State<AdminRotasScreen>
    with AdminListStateMixin<AdminRotaBaseRecord, AdminRotasScreen> {
  late final AdminRotasGateway _gateway;
  late final AdminPlacesGateway _placesGateway;

  List<AdminMotoristaRecord> _motoristas = const [];
  List<AdminPassageiroRecord> _passageiros = const [];
  AdminRotaBaseRecord? _selectedRota;
  bool _isSubmitting = false;
  _AdminRotasViewMode _mode = _AdminRotasViewMode.list;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ?? AdminRotasPortalService(session: widget.session);
    _placesGateway = AdminPlacesPortalService(session: widget.session);
    _loadPage();
  }

  Future<void> _loadPage({int? preserveSelectionId}) async {
    startLoading();

    try {
      final results = await Future.wait<dynamic>([
        _gateway.fetchRotas(),
        _gateway.fetchMotoristas(),
        _gateway.fetchPassageiros(),
      ]);

      final rotas = results[0] as List<AdminRotaBaseRecord>;
      final motoristas = results[1] as List<AdminMotoristaRecord>;
      final passageiros = results[2] as List<AdminPassageiroRecord>;
      final selectedId = preserveSelectionId ?? _selectedRota?.id;
      final selected = rotas.cast<AdminRotaBaseRecord?>().firstWhere(
        (item) => item?.id == selectedId,
        orElse: () => rotas.isEmpty ? null : rotas.first,
      );

      setState(() {
        items = rotas;
        _motoristas = motoristas;
        _passageiros = passageiros;
        _selectedRota = selected;
        isLoading = false;
        if (_mode == _AdminRotasViewMode.detail && selected == null) {
          _mode = _AdminRotasViewMode.list;
        }
      });
    } on AdminPortalException catch (error) {
      setLoadError(error.message);
    } catch (_) {
      setLoadError(AppStrings.admin.erroCarregarRotas);
    }
  }

  Future<void> _createRota(AdminRotaBaseCreateInput input) async {
    setState(() => _isSubmitting = true);
    try {
      final created = await _gateway.createRota(input);
      await _loadPage(preserveSelectionId: created.id);
      if (!mounted) {
        return;
      }
      setState(() {
        _selectedRota = created;
        _mode = _AdminRotasViewMode.detail;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Rota-base criada com sucesso.')),
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

  Future<void> _editRota(AdminRotaBaseCreateInput input) async {
    final rota = _selectedRota;
    if (rota == null) return;
    setState(() => _isSubmitting = true);
    try {
      final updated = await _gateway.editRota(rota.id, input);
      await _loadPage(preserveSelectionId: updated.id);
      if (!mounted) return;
      setState(() {
        _selectedRota = updated;
        _mode = _AdminRotasViewMode.detail;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Rota-base atualizada com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  Future<void> _deleteRota() async {
    final rota = _selectedRota;
    if (rota == null) return;

    final confirm = await showDialog<bool>(
      context: context,
      builder:
          (ctx) => AlertDialog(
            title: const Text('Excluir rota-base?'),
            content: Text(
              'A rota "${rota.nome}" será inativada e não gerará mais viagens automáticas. '
              'Viagens já realizadas permanecem no histórico.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                style: FilledButton.styleFrom(
                  backgroundColor: AdminPortalColors.red,
                ),
                child: const Text('Excluir'),
              ),
            ],
          ),
    );

    if (confirm != true || !mounted) return;

    setState(() => _isSubmitting = true);
    try {
      await _gateway.deleteRota(rota.id);
      await _loadPage();
      if (!mounted) return;
      setState(() => _mode = _AdminRotasViewMode.list);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Rota-base inativada com sucesso.')),
      );
    } on AdminPortalException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (errorMessage != null) {
      return AdminRotasErrorState(
        message: errorMessage!,
        onRetry: () => _loadPage(),
      );
    }

    return switch (_mode) {
      _AdminRotasViewMode.list => _buildListView(),
      _AdminRotasViewMode.create => _buildCreateView(),
      _AdminRotasViewMode.edit => _buildEditView(),
      _AdminRotasViewMode.detail => _buildDetailView(),
    };
  }

  Widget _buildListView() {
    final autoGeradas =
        items.where((rota) => rota.geracaoAutomaticaAtiva).length;
    final totalPassageiros = items.fold<int>(
      0,
      (total, rota) => total + rota.quantidadeParadas,
    );

    return AdminPortalPage(
      title: 'Rotas-base legadas',
      description:
          'Área técnica de compatibilidade para itinerários antigos. Para operar novas linhas, use Serviços recorrentes; viagens do dia são montadas em Viagens.',
      trailing: AdminPortalSecondaryButton(
        label: 'Criar rota legada',
        icon: Icons.add,
        onPressed: () => setState(() => _mode = _AdminRotasViewMode.create),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AdminRotasLegacyNotice(),
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: AdminRotasStatCard(
                  eyebrow: 'FILTRAR POR\nMOTORISTA',
                  child: Container(
                    width: double.infinity,
                    decoration: BoxDecoration(
                      color: AdminPortalColors.input,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 14,
                    ),
                    child: const Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Todos os Motoristas',
                            style: TextStyle(
                              color: Color(0xFF1B1C18),
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        ),
                        Icon(Icons.keyboard_arrow_down_rounded),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                child: AdminPortalMetricCard(
                  icon: Icons.alt_route_rounded,
                  iconBackground: AdminPortalColors.surfaceSoft,
                  value: items.where((rota) => rota.ativo).length.toString(),
                  label: 'Rotas legadas ativas',
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                child: AdminPortalMetricCard(
                  icon: Icons.auto_awesome_outlined,
                  iconBackground: AdminPortalColors.surfaceSoft,
                  value: autoGeradas.toString().padLeft(2, '0'),
                  label: 'Auto-geração legada',
                ),
              ),
              const SizedBox(width: 24),
              Expanded(
                child: AdminPortalMetricCard(
                  icon: Icons.groups_2_outlined,
                  iconBackground: AdminPortalColors.surfaceSoft,
                  value: totalPassageiros.toString(),
                  label: 'Paradas legadas',
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          if (items.isEmpty)
            AdminPortalSurface(
              radius: 48,
              padding: const EdgeInsets.all(32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Nenhuma rota-base legada criada.',
                    style: TextStyle(
                      color: AdminPortalColors.text,
                      fontSize: 28,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Cadastre uma rota legada apenas para compatibilidade ou migração; novas operações devem nascer em Serviços recorrentes.',
                    style: TextStyle(
                      color: AdminPortalColors.textMuted,
                      fontSize: 16,
                    ),
                  ),
                  const SizedBox(height: 24),
                  AdminPortalSecondaryButton(
                    label: 'Criar primeira rota legada',
                    icon: Icons.route_outlined,
                    onPressed:
                        () =>
                            setState(() => _mode = _AdminRotasViewMode.create),
                  ),
                ],
              ),
            )
          else
            Column(
              children: items
                  .map(
                    (rota) => Padding(
                      padding: const EdgeInsets.only(bottom: 20),
                      child: AdminRotaListCard(
                        rota: rota,
                        onOpen: () {
                          setState(() {
                            _selectedRota = rota;
                            _mode = _AdminRotasViewMode.detail;
                          });
                        },
                      ),
                    ),
                  )
                  .toList(growable: false),
            ),
          const SizedBox(height: 16),
          AdminRotasPaginationFooter(
            summary:
                'Exibindo ${items.isEmpty ? 0 : 1}-${items.length} de ${items.length} rotas-base legadas',
          ),
        ],
      ),
    );
  }

  Widget _buildCreateView() {
    return AdminRotaFormView(
      placesGateway: _placesGateway,
      motoristas: _motoristas,
      passageiros: _passageiros,
      isSubmitting: _isSubmitting,
      onCancel: () => setState(() => _mode = _AdminRotasViewMode.list),
      onSubmit: _createRota,
    );
  }

  Widget _buildEditView() {
    final rota = _selectedRota;
    if (rota == null) return _buildListView();
    return AdminRotaFormView(
      placesGateway: _placesGateway,
      motoristas: _motoristas,
      passageiros: _passageiros,
      isSubmitting: _isSubmitting,
      initialRota: rota,
      onCancel: () => setState(() => _mode = _AdminRotasViewMode.detail),
      onSubmit: _editRota,
    );
  }

  Widget _buildDetailView() {
    final rota = _selectedRota;
    if (rota == null) {
      return _buildListView();
    }

    return AdminRotaDetailView(
      rota: rota,
      isSubmitting: _isSubmitting,
      onBack: () => setState(() => _mode = _AdminRotasViewMode.list),
      onEdit: () => setState(() => _mode = _AdminRotasViewMode.edit),
      onDelete: _deleteRota,
    );
  }
}
