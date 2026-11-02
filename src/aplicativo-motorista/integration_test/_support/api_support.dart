import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:vanrides_frontend_dev/services/api_constants.dart';

Future<Map<String, dynamic>> loginViaApi({
  required String email,
  required String senha,
}) async {
  final url = Uri.parse('$baseUrl/login');
  final resp = await http.post(
    url,
    headers: {'Content-Type': 'application/json'},
    body: jsonEncode({'email': email, 'senha': senha}),
  );

  if (resp.statusCode != 200) {
    throw Exception('POST /login falhou: ${resp.statusCode} ${resp.body}');
  }

  final data = jsonDecode(resp.body);
  if (data is! Map<String, dynamic>) {
    throw Exception('POST /login retornou formato inesperado');
  }

  return data;
}

Future<int> createMotoristaViaApi({
  required String nome,
  required String email,
  required String telefone,
  required String senha,
  String dataNascimento = '1990-01-15',
}) async {
  final url = Uri.parse('$baseUrl/motoristas');
  final body = jsonEncode({
    'nome': nome,
    'email': email,
    'telefone': telefone,
    'dataNascimento': dataNascimento,
    'senha': senha,
  });

  final resp = await http.post(
    url,
    headers: {'Content-Type': 'application/json'},
    body: body,
  );
  if (resp.statusCode != 200) {
    throw Exception('POST /motoristas falhou: ${resp.statusCode} ${resp.body}');
  }

  final data = jsonDecode(resp.body);
  if (data is Map<String, dynamic>) {
    final id = data['id'];
    if (id is int) return id;
    if (id is num) return id.toInt();
  } else if (data is Map) {
    final id = data['id'];
    if (id is int) return id;
    if (id is num) return id.toInt();
  }

  throw Exception('POST /motoristas retornou id ausente');
}

Future<void> createPassageiroViaApi({
  required String nome,
  required String email,
  required String telefone,
  required String senha,
  required int motoristaId,
}) async {
  final url = Uri.parse('$baseUrl/passageiros');
  final body = jsonEncode({
    'nome': nome,
    'email': email,
    'telefone': telefone,
    'senha': senha,
    'endereco': 'Rua de Teste, 123',
    'bairro': 'Centro',
    'cidade': 'São Paulo',
    'estado': 'SP',
    'cep': '01000-000',
    'latitude': -23.55052,
    'longitude': -46.633308,
    'motorista': {'id': motoristaId},
  });

  final resp = await http.post(
    url,
    headers: {'Content-Type': 'application/json'},
    body: body,
  );
  if (resp.statusCode != 200) {
    throw Exception(
      'POST /passageiros falhou: ${resp.statusCode} ${resp.body}',
    );
  }
}
