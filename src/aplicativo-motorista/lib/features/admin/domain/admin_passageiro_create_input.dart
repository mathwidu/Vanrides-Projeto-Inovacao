class AdminPassageiroCreateInput {
  const AdminPassageiroCreateInput({
    required this.nome,
    required this.email,
    required this.telefone,
    required this.dataNascimento,
    required this.senha,
    required this.endereco,
    required this.bairro,
    required this.cidade,
    required this.estado,
    required this.cep,
    required this.menorDeIdade,
    this.motoristaId,
    this.servicoIds = const [],
  });

  final String nome;
  final String email;
  final String telefone;
  final String dataNascimento;
  final String senha;
  final String endereco;
  final String bairro;
  final String cidade;
  final String estado;
  final String cep;
  final int? motoristaId;
  final bool menorDeIdade;
  final List<int> servicoIds;

  Map<String, dynamic> toJson() {
    return {
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'dataNascimento': dataNascimento,
      'senha': senha,
      'endereco': endereco,
      'bairro': bairro,
      'cidade': cidade,
      'estado': estado,
      'cep': cep,
      'menorDeIdade': menorDeIdade,
      if (motoristaId != null) 'motoristaId': motoristaId,
      if (servicoIds.isNotEmpty) 'servicoIds': servicoIds,
    };
  }
}
