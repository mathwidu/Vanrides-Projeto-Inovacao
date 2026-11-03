import 'package:flutter/material.dart';

import '../../domain/admin_passageiro_record.dart';
import 'admin_profile_checklist.dart';

class AdminPassageiroProfileChecklist extends StatelessWidget {
  const AdminPassageiroProfileChecklist({
    super.key,
    required this.passageiro,
    this.enabledServicosCount = 0,
    this.hasServicosCatalog = false,
    this.onEditProfile,
    this.onEditServicos,
    this.onManageResponsaveis,
  });

  final AdminPassageiroRecord passageiro;
  final int enabledServicosCount;
  final bool hasServicosCatalog;
  final Future<void> Function()? onEditProfile;
  final Future<void> Function()? onEditServicos;
  final Future<void> Function()? onManageResponsaveis;

  @override
  Widget build(BuildContext context) {
    final readiness = _passageiroChecklistReadiness(
      passageiro,
      enabledServicosCount: enabledServicosCount,
    );
    final action = _passageiroChecklistAction(
      readiness: readiness,
      hasServicosCatalog: hasServicosCatalog,
      onEditProfile: onEditProfile,
      onEditServicos: onEditServicos,
      onManageResponsaveis: onManageResponsaveis,
    );

    return AdminProfileChecklist(
      readyItems: readiness.readyItems,
      requiredItems: readiness.requiredItems,
      recommendedItems: const [],
      actionHint: action.hint,
      editLabel: action.label,
      onEdit: action.onPressed,
    );
  }
}

bool hasAdminPassageiroProfileChecklistItems(
  AdminPassageiroRecord passageiro, {
  int enabledServicosCount = 0,
}) {
  final readiness = _passageiroChecklistReadiness(
    passageiro,
    enabledServicosCount: enabledServicosCount,
  );
  return readiness.requiredItems.isNotEmpty;
}

_PassageiroChecklistReadiness _passageiroChecklistReadiness(
  AdminPassageiroRecord passageiro, {
  required int enabledServicosCount,
}) {
  final readyItems = <String>[];
  final requiredItems = <String>[];
  final hasPersonalData =
      _hasText(passageiro.nome) && _hasText(passageiro.telefone);
  final hasEmail = _hasText(passageiro.email);
  final hasBirth =
      DateTime.tryParse(passageiro.dataNascimento?.trim() ?? '') != null;
  final hasStopAddress =
      _hasText(passageiro.endereco) &&
      _hasText(passageiro.cidade) &&
      _hasText(passageiro.estado);
  final hasGuardian =
      passageiro.responsavelCount > 0 || passageiro.responsaveis.isNotEmpty;

  if (hasPersonalData) {
    readyItems.add('Dados pessoais informados');
  } else {
    requiredItems.add('Informar nome e telefone');
  }
  if (hasEmail) {
    readyItems.add('E-mail para convite informado');
  } else {
    requiredItems.add('Informar e-mail para convite');
  }
  if (hasBirth) {
    readyItems.add('Nascimento informado');
  } else {
    requiredItems.add('Informar data de nascimento');
  }
  if (hasStopAddress) {
    readyItems.add('Parada/endereço informado');
  } else {
    requiredItems.add('Informar parada/endereço');
  }
  if (enabledServicosCount > 0) {
    readyItems.add(
      enabledServicosCount == 1
          ? '1 serviço liberado'
          : '$enabledServicosCount serviços liberados',
    );
  } else {
    requiredItems.add('Liberar pelo menos um serviço');
  }
  if (passageiro.menorDeIdade) {
    if (hasGuardian) {
      readyItems.add('Responsável vinculado');
    } else {
      requiredItems.add('Vincular responsável do menor');
    }
  } else {
    readyItems.add('Responsável não obrigatório');
  }

  return _PassageiroChecklistReadiness(
    readyItems: readyItems,
    requiredItems: requiredItems,
    missingProfileData:
        !hasPersonalData || !hasEmail || !hasBirth || !hasStopAddress,
    missingGuardian: passageiro.menorDeIdade && !hasGuardian,
    missingServices: enabledServicosCount == 0,
  );
}

_PassageiroChecklistAction _passageiroChecklistAction({
  required _PassageiroChecklistReadiness readiness,
  required bool hasServicosCatalog,
  required Future<void> Function()? onEditProfile,
  required Future<void> Function()? onEditServicos,
  required Future<void> Function()? onManageResponsaveis,
}) {
  if (readiness.missingProfileData) {
    return _PassageiroChecklistAction(
      label: 'Editar cadastro',
      hint: 'Ação: complete os dados cadastrais nesta ficha.',
      onPressed: _wrapAsyncAction(onEditProfile),
    );
  }
  if (readiness.missingGuardian) {
    return _PassageiroChecklistAction(
      label: 'Vincular responsável',
      hint: 'Ação: vincule um responsável sem sair da ficha.',
      onPressed: _wrapAsyncAction(onManageResponsaveis),
    );
  }
  if (readiness.missingServices) {
    return _PassageiroChecklistAction(
      label: hasServicosCatalog ? 'Liberar serviços' : 'Criar serviços',
      hint: 'Ação: libere um serviço recorrente nesta ficha.',
      onPressed: _wrapAsyncAction(onEditServicos),
    );
  }
  return const _PassageiroChecklistAction(
    label: 'Revisar ficha',
    hint: 'Ação: acompanhe presença de hoje quando houver serviço no dia.',
  );
}

bool _hasText(String? value) => (value ?? '').trim().isNotEmpty;

VoidCallback? _wrapAsyncAction(Future<void> Function()? action) {
  if (action == null) {
    return null;
  }
  return () {
    action();
  };
}

class _PassageiroChecklistReadiness {
  const _PassageiroChecklistReadiness({
    required this.readyItems,
    required this.requiredItems,
    required this.missingProfileData,
    required this.missingGuardian,
    required this.missingServices,
  });

  final List<String> readyItems;
  final List<String> requiredItems;
  final bool missingProfileData;
  final bool missingGuardian;
  final bool missingServices;
}

class _PassageiroChecklistAction {
  const _PassageiroChecklistAction({
    required this.label,
    required this.hint,
    this.onPressed,
  });

  final String label;
  final String hint;
  final VoidCallback? onPressed;
}
