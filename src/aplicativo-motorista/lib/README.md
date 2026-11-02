# VanRides App Module Boundaries

Data: 2026-06-18

Este documento define quando um arquivo pertence a `lib/services/` e quando
deve viver em `lib/features/<area>/...`. A regra existe para evitar que novos
gateways e services crescam em lugares diferentes sem criterio.

## Regra principal

Use `lib/services/` somente para infraestrutura transversal, isto e, codigo que
nao pertence a um papel ou fluxo de produto especifico e que pode ser usado por
duas ou mais features sem carregar semantica de negocio.

Use `lib/features/<area>/data/` para qualquer gateway, client HTTP, mapper ou
service que conheca endpoints e payloads de uma feature especifica.

Use `lib/features/<area>/domain/` para records, inputs, enums e modelos de
negocio consumidos pela feature.

Use `lib/features/<area>/presentation/` para telas, widgets, presenters,
view-model helpers e componentes visuais.

## O que fica em `lib/services/`

Permitido:

- constantes globais de API e ambiente;
- helpers de headers/autenticacao reutilizados por muitos gateways;
- parsing generico de resposta HTTP;
- leitura generica de JWT;
- WebSocket/STOMP compartilhado;
- adaptadores de plataforma sem semantica de produto, como Geolocator;
- configuracao global de provider externo, como chave do Google Places;
- utilitarios de data operacional usados por fluxos diferentes.

Exemplos atuais:

| Arquivo | Classificacao | Decisao |
| --- | --- | --- |
| `api_constants.dart` | ambiente/API transversal | manter em `services` |
| `auth_headers.dart` | auth transversal | manter em `services` |
| `api_response_decoding.dart` | HTTP transversal | manter em `services` |
| `jwt_claims.dart` | sessao/auth transversal | manter em `services` |
| `websocket_service.dart` | transporte STOMP compartilhado | manter em `services` |
| `operation_date.dart` | data operacional compartilhada | manter em `services` |
| `google_api_config.dart` | configuracao de provider | manter em `services` |
| `location_gateway.dart` | adaptador de plataforma | manter em `services` |
| `location_sharing_use_case.dart` | permissao de localizacao transversal enquanto as telas legadas existem | manter por enquanto |
| `public_auth_support.dart` | auth publica compartilhada entre login, cadastro e verificacao | manter em `services` ate a migracao de auth/router |

## O que vai para `features/<area>/data`

Vai para a feature quando o arquivo:

- chama endpoint de dominio especifico;
- monta payload de cadastro, viagem, presenca, perfil, vinculo, admin ou
  tracking;
- conhece mensagens/codigos de erro de um fluxo especifico;
- so tem sentido dentro de uma area de produto.

Exemplos atuais corretos:

| Feature | Exemplo |
| --- | --- |
| `features/admin/data/` | gateways do portal da empresa |
| `features/passenger_onboarding/data/` | onboarding e cadastro de passageiro |
| `features/driver_onboarding/data/` | onboarding e cadastro de motorista |
| `features/tracking/data/` | tracking, rota e localizacao do passageiro |
| `features/driver_trip/data/` | operacao da viagem do motorista |
| `features/account/data/` | perfil e foto da conta |
| `features/service_presence/data/` | presenca por servico |
| `features/guardian/data/` | responsavel |

## Regra para novos arquivos

1. Se o arquivo conhece um endpoint de produto, comece em
   `lib/features/<area>/data/`.
2. Se o arquivo e apenas transporte, ambiente, auth, parsing ou plugin
   reutilizavel, use `lib/services/`.
3. Se duas features precisarem do mesmo contrato de produto, prefira uma feature
   compartilhada explicita ou um modulo `shared`, nao `services`, a menos que o
   codigo seja infraestrutura pura.
4. Se uma tela antiga em `lib/*.dart` ainda usa um service transversal, nao mova
   o service so para esconder a dependencia. Primeiro migre a tela pelo card
   D-11.
5. Ao criar um gateway novo, adicione teste unitario ou widget test que injete
   `http.Client` ou um fake equivalente.

## Auditoria de 2026-06-18

Nenhum gateway de feature especifica permanece em `lib/services/`.

Os arquivos que ainda parecem mais proximos de produto
(`public_auth_support.dart` e `location_sharing_use_case.dart`) foram mantidos
porque servem mais de uma superficie legada ou papel. Eles devem ser revisitados
depois da migracao D-11 das telas raiz e da futura migracao D-05 para rotas
nomeadas.
