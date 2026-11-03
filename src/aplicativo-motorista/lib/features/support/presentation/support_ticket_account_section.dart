import 'package:flutter/material.dart';

import '../../account/presentation/account_profile_screen.dart';
import '../../tracking/presentation/widgets/vanrides_map_tokens.dart';
import '../data/support_ticket_service.dart';

class SupportTicketAccountSection extends StatefulWidget {
  const SupportTicketAccountSection({
    super.key,
    required this.gateway,
    this.enabled = true,
  });

  static const openButtonKey = Key('support.account.openTicket');

  final SupportTicketGateway? gateway;
  final bool enabled;

  @override
  State<SupportTicketAccountSection> createState() =>
      _SupportTicketAccountSectionState();
}

class _SupportTicketAccountSectionState
    extends State<SupportTicketAccountSection> {
  var _loading = false;
  var _loaded = false;
  var _saving = false;
  String? _error;
  String? _success;
  List<SupportTicketSummary> _tickets = const [];

  @override
  void initState() {
    super.initState();
    if (widget.enabled) {
      _loadTickets();
    }
  }

  @override
  void didUpdateWidget(covariant SupportTicketAccountSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((!oldWidget.enabled && widget.enabled) ||
        oldWidget.gateway != widget.gateway) {
      _loadTickets(force: true);
    }
  }

  void _updateState(VoidCallback callback) {
    if (mounted) {
      final update = setState;
      update(callback);
    }
  }

  Future<void> _loadTickets({bool force = false}) async {
    final gateway = widget.gateway;
    if (gateway == null || _loading || (_loaded && !force)) {
      return;
    }

    _updateState(() {
      _loading = true;
      _error = null;
    });

    try {
      final tickets = await gateway.fetchMyTickets();
      if (!mounted) {
        return;
      }
      _updateState(() {
        _tickets = tickets;
        _loaded = true;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel carregar atendimentos.');
        _loaded = true;
        _loading = false;
      });
    }
  }

  Future<void> _openTicket() async {
    final gateway = widget.gateway;
    if (gateway == null || _saving) {
      return;
    }

    final input = await showModalBottomSheet<SupportTicketCreateInput>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const SupportTicketCreateSheet(),
    );
    if (input == null) {
      return;
    }

    _updateState(() {
      _saving = true;
      _error = null;
      _success = null;
    });

    try {
      final ticket = await gateway.createTicket(
        category: input.category,
        subject: input.subject,
        message: input.message,
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _tickets = [
          ticket.toSummary(),
          ..._tickets.where((item) => item.id != ticket.id),
        ];
        _loaded = true;
        _success = ticket.status.userSummary;
        _saving = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel abrir atendimento.');
        _saving = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final gatewayAvailable = widget.gateway != null;
    return AccountProfileInfoCard(
      children: [
        AccountProfileInfoRow(
          icon: Icons.support_agent_outlined,
          title: 'Atendimento',
          value:
              !gatewayAvailable
                  ? 'Indisponível nesta sessão'
                  : _loading
                  ? 'Carregando atendimentos...'
                  : _tickets.isEmpty
                  ? 'Nenhum atendimento aberto'
                  : '${_tickets.length} ${_tickets.length == 1 ? 'atendimento' : 'atendimentos'}',
          helper:
              !gatewayAvailable
                  ? 'Entre novamente para abrir solicitações com a empresa.'
                  : 'Use este canal para dúvidas operacionais sobre rota, presença ou localização.',
          actionRequired: gatewayAvailable && _tickets.any(_needsAttention),
        ),
        if (_error != null)
          AccountProfileInfoRow(
            icon: Icons.error_outline_rounded,
            title: 'Falha ao atualizar',
            value: _error!,
            helper: 'Tente novamente em alguns instantes.',
            actionRequired: true,
          ),
        if (_success != null)
          AccountProfileInfoRow(
            icon: Icons.mark_email_read_outlined,
            title: 'Solicitação enviada',
            value: _success!,
            helper: 'A empresa verá sua mensagem no portal operacional.',
          ),
        for (final ticket in _tickets.take(3))
          AccountProfileInfoRow(
            icon: Icons.forum_outlined,
            title:
                ticket.subject.isEmpty
                    ? 'Atendimento sem assunto'
                    : ticket.subject,
            value: ticket.status.label,
            helper: ticket.status.userSummary,
            actionRequired: ticket.status != SupportTicketStatus.resolved,
          ),
        const SizedBox(height: 6),
        FilledButton.icon(
          key: SupportTicketAccountSection.openButtonKey,
          onPressed: gatewayAvailable && !_saving ? _openTicket : null,
          icon:
              _saving
                  ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                  : const Icon(Icons.add_comment_outlined),
          label: Text(_saving ? 'Enviando...' : 'Abrir atendimento'),
          style: FilledButton.styleFrom(
            minimumSize: const Size.fromHeight(52),
            backgroundColor: VanRidesMapTokens.blueTracking,
            foregroundColor: VanRidesMapTokens.backgroundDeep,
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
            ),
          ),
        ),
      ],
    );
  }
}

class SupportTicketCreateInput {
  const SupportTicketCreateInput({
    required this.category,
    required this.subject,
    required this.message,
  });

  final String category;
  final String subject;
  final String message;
}

class SupportTicketCreateSheet extends StatefulWidget {
  const SupportTicketCreateSheet({super.key});

  static const subjectKey = Key('support.create.subject');
  static const messageKey = Key('support.create.message');
  static const submitButtonKey = Key('support.create.submit');

  @override
  State<SupportTicketCreateSheet> createState() =>
      _SupportTicketCreateSheetState();
}

class _SupportTicketCreateSheetState extends State<SupportTicketCreateSheet> {
  final _formKey = GlobalKey<FormState>();
  final _subjectController = TextEditingController();
  final _messageController = TextEditingController();
  String _category = 'ATRASO';

  void _updateState(VoidCallback callback) {
    if (mounted) {
      final update = setState;
      update(callback);
    }
  }

  @override
  void dispose() {
    _subjectController.dispose();
    _messageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    return Padding(
      padding: EdgeInsets.only(bottom: bottomInset),
      child: Container(
        padding: const EdgeInsets.fromLTRB(22, 18, 22, 22),
        decoration: const BoxDecoration(
          color: VanRidesMapTokens.surface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
        child: SafeArea(
          top: false,
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 54,
                    height: 5,
                    decoration: BoxDecoration(
                      color: VanRidesMapTokens.textMuted.withValues(alpha: 0.5),
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const Text(
                  'Abrir atendimento',
                  style: TextStyle(
                    color: VanRidesMapTokens.textPrimary,
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Conte o que aconteceu. A empresa recebe isso como uma fila de atendimento no portal.',
                  style: TextStyle(
                    color: VanRidesMapTokens.textSecondary,
                    height: 1.45,
                  ),
                ),
                const SizedBox(height: 18),
                DropdownButtonFormField<String>(
                  initialValue: _category,
                  dropdownColor: VanRidesMapTokens.surfaceRaised,
                  decoration: _inputDecoration('Categoria'),
                  items: const [
                    DropdownMenuItem(value: 'ATRASO', child: Text('Atraso')),
                    DropdownMenuItem(value: 'ROTA', child: Text('Rota')),
                    DropdownMenuItem(
                      value: 'PRESENCA',
                      child: Text('Presença'),
                    ),
                    DropdownMenuItem(value: 'OUTRO', child: Text('Outro')),
                  ],
                  onChanged: (value) {
                    if (value != null) {
                      _updateState(() => _category = value);
                    }
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  key: SupportTicketCreateSheet.subjectKey,
                  controller: _subjectController,
                  decoration: _inputDecoration('Assunto'),
                  validator:
                      (value) =>
                          value != null && value.trim().length >= 4
                              ? null
                              : 'Informe um assunto claro.',
                ),
                const SizedBox(height: 12),
                TextFormField(
                  key: SupportTicketCreateSheet.messageKey,
                  controller: _messageController,
                  minLines: 4,
                  maxLines: 6,
                  decoration: _inputDecoration('Mensagem'),
                  validator:
                      (value) =>
                          value != null && value.trim().length >= 8
                              ? null
                              : 'Explique o que aconteceu.',
                ),
                const SizedBox(height: 18),
                FilledButton.icon(
                  key: SupportTicketCreateSheet.submitButtonKey,
                  onPressed: _submit,
                  icon: const Icon(Icons.send_outlined),
                  label: const Text('Enviar atendimento'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(54),
                    backgroundColor: VanRidesMapTokens.blueTracking,
                    foregroundColor: VanRidesMapTokens.backgroundDeep,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(18),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  InputDecoration _inputDecoration(String label) {
    return InputDecoration(
      labelText: label,
      filled: true,
      fillColor: VanRidesMapTokens.surfaceRaised,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(18),
        borderSide: const BorderSide(color: VanRidesMapTokens.blueTracking),
      ),
    );
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    Navigator.of(context).pop(
      SupportTicketCreateInput(
        category: _category,
        subject: _subjectController.text.trim(),
        message: _messageController.text.trim(),
      ),
    );
  }
}

bool _needsAttention(SupportTicketSummary ticket) {
  return ticket.status == SupportTicketStatus.open ||
      ticket.status == SupportTicketStatus.inProgress;
}

String _messageFor(Object error, String fallback) {
  if (error is SupportTicketException) {
    return error.message;
  }
  final message = error.toString().trim();
  return message.isEmpty ? fallback : message;
}
