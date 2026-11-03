class AdminPassageiroUpdateInput {
  const AdminPassageiroUpdateInput({
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
    this.senha,
  });

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
  final String? senha;

  Map<String, dynamic> toJson() {
    return {
      'nome': nome,
      'email': email,
      'telefone': telefone,
      'endereco': endereco,
      'bairro': bairro,
      'cidade': cidade,
      'estado': estado,
      'cep': cep,
      'menorDeIdade': menorDeIdade,
      'motoristaId': motoristaId,
      if (senha != null && senha!.trim().isNotEmpty) 'senha': senha,
    };
  }
}
