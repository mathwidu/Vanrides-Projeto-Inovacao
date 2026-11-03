class AccountPasswordChange {
  const AccountPasswordChange({
    required this.senhaAtual,
    required this.novaSenha,
  });

  final String senhaAtual;
  final String novaSenha;

  Map<String, dynamic> toJson() {
    return {'senhaAtual': senhaAtual, 'novaSenha': novaSenha};
  }
}
