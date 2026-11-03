import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'admin_login_form_fields.dart';
import 'admin_portal_layout.dart';

class AdminPendingVerificationForm extends StatelessWidget {
  const AdminPendingVerificationForm({
    super.key,
    required this.email,
    required this.isSubmitting,
    required this.resendCooldownListenable,
    required this.onResendVerification,
    required this.onChangeEmail,
    required this.onBackToLogin,
    this.errorMessage,
    this.feedbackMessage,
  });

  final String email;
  final bool isSubmitting;
  final String? errorMessage;
  final String? feedbackMessage;
  final ValueListenable<Duration> resendCooldownListenable;
  final Future<void> Function() onResendVerification;
  final Future<void> Function() onChangeEmail;
  final VoidCallback onBackToLogin;

  String _formatCooldown(Duration cooldown) {
    final minutes = cooldown.inMinutes;
    final seconds = cooldown.inSeconds.remainder(60);
    return '$minutes:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return AdminPortalSurface(
      radius: 48,
      padding: const EdgeInsets.fromLTRB(36, 34, 36, 34),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              color: AdminPortalColors.orangeSoft,
              borderRadius: BorderRadius.circular(32),
              border: Border.all(
                color: AdminPortalColors.orange.withValues(alpha: 0.32),
              ),
            ),
            child: const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundColor: AdminPortalColors.orange,
                  child: Icon(
                    Icons.mark_email_unread_outlined,
                    color: AdminPortalColors.canvas,
                    size: 18,
                  ),
                ),
                SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Acesso bloqueado',
                        style: TextStyle(
                          color: AdminPortalColors.warningText,
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      SizedBox(height: 6),
                      Text(
                        'Detectamos que seu e-mail ainda não foi confirmado. Por segurança, a conta administrativa fica temporariamente restrita até a verificação.',
                        style: TextStyle(
                          color: AdminPortalColors.textMuted,
                          fontSize: 14,
                          height: 1.55,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          const Text(
            'Entrar no portal',
            style: TextStyle(
              color: AdminPortalColors.text,
              fontSize: 34,
              fontWeight: FontWeight.w800,
              height: 1.15,
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            'Confirme o e-mail corporativo para liberar a dashboard operacional. Enquanto isso, você pode reenviar a mensagem ou trocar o endereço pendente.',
            style: TextStyle(
              color: AdminPortalColors.textMuted,
              fontSize: 16,
              height: 1.6,
            ),
          ),
          const SizedBox(height: 24),
          const AdminLoginFieldLabel(label: 'E-mail corporativo pendente'),
          const SizedBox(height: 8),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            decoration: BoxDecoration(
              color: AdminPortalColors.input.withValues(alpha: 0.92),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AdminPortalColors.outline),
            ),
            child: Text(
              email,
              style: const TextStyle(
                color: AdminPortalColors.text,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          if (errorMessage != null) ...[
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AdminPortalColors.orangeSoft,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: AdminPortalColors.orange.withValues(alpha: 0.22),
                ),
              ),
              child: Text(
                errorMessage!,
                style: const TextStyle(
                  color: AdminPortalColors.warningText,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
          if (feedbackMessage != null) ...[
            const SizedBox(height: 16),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AdminPortalColors.blueSoft,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: AdminPortalColors.blueBright.withValues(alpha: 0.22),
                ),
              ),
              child: Text(
                feedbackMessage!,
                style: const TextStyle(
                  color: AdminPortalColors.blueBright,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
          const SizedBox(height: 24),
          ValueListenableBuilder<Duration>(
            valueListenable: resendCooldownListenable,
            builder: (context, cooldown, _) {
              final blocked = isSubmitting || cooldown > Duration.zero;
              final cooldownLabel = _formatCooldown(cooldown);
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: blocked ? null : onResendVerification,
                      icon:
                          isSubmitting
                              ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                              : const Icon(Icons.mark_email_read_outlined),
                      label: Text(
                        isSubmitting
                            ? 'Reenviando...'
                            : cooldown > Duration.zero
                            ? 'Reenviar em $cooldownLabel'
                            : 'Reenviar verificação',
                      ),
                      style: FilledButton.styleFrom(
                        backgroundColor: AdminPortalColors.blueBright,
                        foregroundColor: AdminPortalColors.canvas,
                        padding: const EdgeInsets.symmetric(vertical: 18),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(999),
                        ),
                        textStyle: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ),
                  if (cooldown > Duration.zero) ...[
                    const SizedBox(height: 10),
                    Text(
                      'Você poderá solicitar um novo envio em $cooldownLabel.',
                      style: const TextStyle(
                        color: AdminPortalColors.textMuted,
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ],
              );
            },
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              TextButton.icon(
                onPressed: isSubmitting ? null : onChangeEmail,
                icon: const Icon(Icons.alternate_email_rounded),
                label: const Text('Alterar e-mail'),
              ),
              TextButton.icon(
                onPressed: isSubmitting ? null : onBackToLogin,
                icon: const Icon(Icons.arrow_back_rounded),
                label: const Text('Voltar ao login'),
              ),
            ],
          ),
          const Spacer(),
          const Divider(height: 32),
          const Wrap(
            spacing: 20,
            runSpacing: 12,
            children: [
              _InlineFooterAction(
                icon: Icons.support_agent_rounded,
                label: 'Suporte',
              ),
              _InlineFooterAction(
                icon: Icons.markunread_mailbox_outlined,
                label: 'Verificar pasta de spam',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _InlineFooterAction extends StatelessWidget {
  const _InlineFooterAction({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: AdminPortalColors.textMuted),
        const SizedBox(width: 6),
        Text(
          label,
          style: const TextStyle(
            color: AdminPortalColors.textMuted,
            fontSize: 13,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class AdminChangePendingEmailDialog extends StatefulWidget {
  const AdminChangePendingEmailDialog({super.key, required this.currentEmail});

  final String currentEmail;

  @override
  State<AdminChangePendingEmailDialog> createState() =>
      _AdminChangePendingEmailDialogState();
}

class _AdminChangePendingEmailDialogState
    extends State<AdminChangePendingEmailDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _emailController;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: widget.currentEmail);
  }

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Alterar e-mail pendente'),
      content: Form(
        key: _formKey,
        child: TextFormField(
          controller: _emailController,
          keyboardType: TextInputType.emailAddress,
          decoration: adminLoginInputDecoration(
            hintText: 'novo.email@empresa.com',
          ),
          validator: (value) {
            return value != null && value.contains('@')
                ? null
                : 'Informe um e-mail válido';
          },
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        FilledButton(
          onPressed: () {
            if (_formKey.currentState!.validate()) {
              Navigator.of(context).pop(_emailController.text.trim());
            }
          },
          child: const Text('Salvar novo e-mail'),
        ),
      ],
    );
  }
}
