# ADR: Limites Entre Services Globais E Feature Data

Data: 2026-06-18

## Status

Aceita.

## Contexto

O app cresceu com dois estilos convivendo:

- `lib/services/` para constantes, auth, JWT, WebSocket, location e helpers
  historicos;
- `lib/features/<area>/data/` para gateways de admin, tracking, perfil,
  onboarding, presenca e guardian.

Sem uma regra escrita, cada novo card precisava decidir do zero onde colocar
um client, mapper ou helper. Isso aumentava o risco de gateways especificos de
feature voltarem para `lib/services/`.

## Decisao

`lib/services/` fica reservado para infraestrutura transversal:

- ambiente e base URL;
- auth/header/JWT;
- parsing generico de resposta;
- transporte WebSocket/STOMP;
- adaptadores de plataforma;
- configuracao de provider externo;
- utilitarios globais.

Gateways, clients HTTP, DTO parsing e regras de erro que conhecem um dominio de
produto devem ficar em `lib/features/<area>/data/`.

Records, inputs e enums de produto devem ficar em
`lib/features/<area>/domain/`.

Widgets, telas, presenters e componentes visuais devem ficar em
`lib/features/<area>/presentation/`.

## Auditoria Aplicada

Auditoria feita nos arquivos atuais de `lib/services/`:

| Arquivo | Resultado |
| --- | --- |
| `api_constants.dart` | infra transversal |
| `auth_headers.dart` | infra transversal |
| `api_response_decoding.dart` | infra transversal |
| `jwt_claims.dart` | infra transversal |
| `websocket_service.dart` | transporte transversal |
| `operation_date.dart` | utilitario transversal |
| `google_api_config.dart` | configuracao transversal |
| `location_gateway.dart` | adaptador de plataforma |
| `location_sharing_use_case.dart` | permissao/localizacao compartilhada enquanto telas legadas existem |
| `public_auth_support.dart` | auth publica compartilhada por login, cadastro e verificacao |

Nenhum gateway de feature especifica foi encontrado em `lib/services/`. Os
gateways de cadastro criados no card D-06 ficaram em
`features/driver_onboarding/data/` e `features/passenger_onboarding/data/`, que
e o destino correto.

## Alternativas Consideradas

### Mover tudo de `services` para features agora

Rejeitado. Misturaria este card com a migracao D-11 das telas legadas e com a
futura migracao de roteamento D-05.

### Manter criterio implicito

Rejeitado. O objetivo do card e justamente reduzir variacao entre agentes e
desenvolvedores.

### Criar uma camada `core/`

Adiado. A estrutura atual ja funciona com `services` como infra transversal.
Renomear para `core` agora geraria churn de imports sem ganho proporcional.

## Consequencias

- Novos endpoints de produto devem nascer em `features/<area>/data`.
- `lib/services` nao deve receber gateway de feature.
- D-11 pode mover telas legadas sem precisar decidir novamente a fronteira de
  dados.
- D-14 pode endurecer analyzer depois que esta regra estiver documentada.

## Validacao

- `lib/README.md` publicado com a regra operacional.
- Auditoria de `lib/services/*.dart` concluida.
- Sem mudanca de runtime ou contrato API.
