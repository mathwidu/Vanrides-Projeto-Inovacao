import 'package:flutter/material.dart';

import '../../support/data/support_ticket_service.dart';
import '../domain/admin_portal_session.dart';
import 'widgets/admin_portal_layout.dart';
import 'widgets/admin_support_ticket_widgets.dart';

class AdminSupportTicketsScreen extends StatefulWidget {
  const AdminSupportTicketsScreen({
    super.key,
    required this.session,
    this.gateway,
  });

  static const replyFieldKey = Key('admin.support.reply');
  static const replyButtonKey = Key('admin.support.replyButton');

  final AdminPortalSession session;
  final AdminSupportTicketGateway? gateway;

  @override
  State<AdminSupportTicketsScreen> createState() =>
      _AdminSupportTicketsScreenState();
}

class _AdminSupportTicketsScreenState extends State<AdminSupportTicketsScreen> {
  late final AdminSupportTicketGateway _gateway;
  final _replyController = TextEditingController();
  var _loading = false;
  var _loadingDetail = false;
  var _saving = false;
  String? _error;
  String? _success;
  List<SupportTicketSummary> _tickets = const [];
  SupportTicket? _selectedTicket;

  @override
  void initState() {
    super.initState();
    _gateway =
        widget.gateway ??
        AdminSupportTicketPortalService(session: widget.session);
    _loadTickets();
  }

  @override
  void dispose() {
    _replyController.dispose();
    super.dispose();
  }

  void _updateState(VoidCallback callback) {
    if (mounted) {
      final update = setState;
      update(callback);
    }
  }

  Future<void> _loadTickets() async {
    _updateState(() {
      _loading = true;
      _error = null;
      _success = null;
    });

    try {
      final tickets = await _gateway.fetchTickets();
      if (!mounted) {
        return;
      }
      _updateState(() {
        _tickets = tickets;
        _loading = false;
      });
      if (tickets.isNotEmpty) {
        await _selectTicket(tickets.first);
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel carregar atendimentos.');
        _loading = false;
      });
    }
  }

  Future<void> _selectTicket(SupportTicketSummary ticket) async {
    _updateState(() {
      _loadingDetail = true;
      _error = null;
      _success = null;
      _selectedTicket = null;
      _replyController.clear();
    });

    try {
      final detail = await _gateway.fetchTicket(ticket.id);
      if (!mounted) {
        return;
      }
      _updateState(() {
        _selectedTicket = detail;
        _loadingDetail = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel abrir atendimento.');
        _loadingDetail = false;
      });
    }
  }

  Future<void> _reply() async {
    final ticket = _selectedTicket;
    final message = _replyController.text.trim();
    if (ticket == null || message.isEmpty || _saving) {
      return;
    }

    _updateState(() {
      _saving = true;
      _error = null;
      _success = null;
    });

    try {
      final updated = await _gateway.replyTicket(
        ticketId: ticket.id,
        message: message,
        status: SupportTicketStatus.inProgress.backendValue,
      );
      if (!mounted) {
        return;
      }
      _replyController.clear();
      _updateState(() {
        _selectedTicket = updated;
        _tickets = _replaceSummary(updated.toSummary());
        _success = 'Resposta registrada para o usuario.';
        _saving = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel responder atendimento.');
        _saving = false;
      });
    }
  }

  Future<void> _markResolved() async {
    final ticket = _selectedTicket;
    if (ticket == null || _saving) {
      return;
    }

    _updateState(() {
      _saving = true;
      _error = null;
      _success = null;
    });

    try {
      final updated = await _gateway.updateTicketStatus(
        ticketId: ticket.id,
        status: SupportTicketStatus.resolved.backendValue,
      );
      if (!mounted) {
        return;
      }
      _updateState(() {
        _selectedTicket = updated;
        _tickets = _replaceSummary(updated.toSummary());
        _success = 'Atendimento marcado como resolvido.';
        _saving = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _updateState(() {
        _error = _messageFor(error, 'Nao foi possivel resolver atendimento.');
        _saving = false;
      });
    }
  }

  List<SupportTicketSummary> _replaceSummary(SupportTicketSummary summary) {
    return [summary, ..._tickets.where((ticket) => ticket.id != summary.id)];
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalPage(
      title: 'Atendimento',
      description:
          'Acompanhe mensagens de passageiros e responsáveis sem misturar suporte com cadastro operacional.',
      trailing: OutlinedButton.icon(
        onPressed: _loading ? null : _loadTickets,
        icon: const Icon(Icons.refresh_rounded),
        label: const Text('Atualizar'),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final compact = constraints.maxWidth < 980;
          final content =
              compact
                  ? Column(
                    children: [
                      _buildQueue(),
                      const SizedBox(height: 18),
                      _buildDetail(),
                    ],
                  )
                  : Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(flex: 4, child: _buildQueue()),
                      const SizedBox(width: 18),
                      Expanded(flex: 6, child: _buildDetail()),
                    ],
                  );
          return content;
        },
      ),
    );
  }

  Widget _buildQueue() {
    return AdminPortalSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AdminSupportSectionTitle(
            title: 'Fila da empresa',
            subtitle: 'Quem precisa de retorno agora',
          ),
          const SizedBox(height: 16),
          if (_loading)
            const AdminSupportStateMessage(
              icon: Icons.sync_rounded,
              title: 'Carregando atendimentos...',
              description: 'Buscando chamados reais da empresa.',
            )
          else if (_tickets.isEmpty)
            const AdminSupportStateMessage(
              icon: Icons.check_circle_outline_rounded,
              title: 'Sem atendimentos pendentes',
              description: 'Novas solicitações aparecem aqui automaticamente.',
            )
          else
            for (final ticket in _tickets)
              AdminSupportTicketQueueTile(
                ticket: ticket,
                selected: ticket.id == _selectedTicket?.id,
                onTap: () => _selectTicket(ticket),
              ),
        ],
      ),
    );
  }

  Widget _buildDetail() {
    final ticket = _selectedTicket;
    return AdminPortalSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AdminSupportSectionTitle(
            title: 'Conversa',
            subtitle: 'Contexto e resposta da empresa',
          ),
          const SizedBox(height: 16),
          if (_error != null)
            AdminSupportFeedbackBanner(message: _error!, error: true),
          if (_success != null) AdminSupportFeedbackBanner(message: _success!),
          if (_loadingDetail)
            const AdminSupportStateMessage(
              icon: Icons.forum_outlined,
              title: 'Abrindo atendimento...',
              description: 'Carregando histórico e estado atual.',
            )
          else if (ticket == null)
            const AdminSupportStateMessage(
              icon: Icons.inbox_outlined,
              title: 'Selecione um atendimento',
              description: 'Escolha uma solicitação na fila para responder.',
            )
          else ...[
            AdminSupportTicketDetailHeader(ticket: ticket),
            const SizedBox(height: 18),
            for (final message in ticket.messages)
              AdminSupportTicketMessageBubble(message: message),
            const SizedBox(height: 18),
            TextField(
              key: AdminSupportTicketsScreen.replyFieldKey,
              controller: _replyController,
              minLines: 3,
              maxLines: 5,
              decoration: const InputDecoration(
                labelText: 'Resposta para o usuário',
                hintText: 'Ex.: Vamos confirmar com o motorista e avisar aqui.',
              ),
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: [
                FilledButton.icon(
                  key: AdminSupportTicketsScreen.replyButtonKey,
                  onPressed: _saving ? null : _reply,
                  icon: const Icon(Icons.send_outlined),
                  label: Text(_saving ? 'Enviando...' : 'Responder'),
                ),
                OutlinedButton.icon(
                  onPressed:
                      _saving || ticket.status == SupportTicketStatus.resolved
                          ? null
                          : _markResolved,
                  icon: const Icon(Icons.check_circle_outline_rounded),
                  label: const Text('Marcar resolvido'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

String _messageFor(Object error, String fallback) {
  final message = error.toString().trim();
  return message.isEmpty ? fallback : message;
}
