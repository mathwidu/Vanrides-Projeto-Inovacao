class GuardianLinkedPassageiroRecord {
  const GuardianLinkedPassageiroRecord({
    required this.id,
    required this.nome,
    required this.email,
    required this.telefone,
    required this.endereco,
    required this.bairro,
    required this.cidade,
    required this.estado,
    required this.cep,
    required this.menorDeIdade,
    this.motoristaId,
    this.motoristaNome,
  });

  final int id;
  final String nome;
  final String email;
  final String telefone;
  final String endereco;
  final String bairro;
  final String cidade;
  final String estado;
  final String cep;
  final bool menorDeIdade;
  final int? motoristaId;
  final String? motoristaNome;

  factory GuardianLinkedPassageiroRecord.fromJson(Map<String, dynamic> json) {
    final motorista = json['motorista'];
    return GuardianLinkedPassageiroRecord(
      id: (json['id'] as num).toInt(),
      nome: (json['nome'] as String?)?.trim() ?? 'Passageiro sem nome',
      email: (json['email'] as String?)?.trim() ?? '',
      telefone: (json['telefone'] as String?)?.trim() ?? '',
      endereco: (json['endereco'] as String?)?.trim() ?? '',
      bairro: (json['bairro'] as String?)?.trim() ?? '',
      cidade: (json['cidade'] as String?)?.trim() ?? '',
      estado: (json['estado'] as String?)?.trim() ?? '',
      cep: (json['cep'] as String?)?.trim() ?? '',
      menorDeIdade: (json['menorDeIdade'] as bool?) ?? false,
      motoristaId:
          motorista is Map<String, dynamic>
              ? (motorista['id'] as num?)?.toInt()
              : null,
      motoristaNome:
          motorista is Map<String, dynamic>
              ? (motorista['nome'] as String?)?.trim()
              : null,
    );
  }

  String get enderecoResumo => [
    endereco,
    bairro,
    cidade,
    estado,
  ].where((part) => part.trim().isNotEmpty).join(', ');
}
