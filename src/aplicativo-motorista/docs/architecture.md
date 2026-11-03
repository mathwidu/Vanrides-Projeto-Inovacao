# Mapa do código do aplicativo

Origem: `b9fcedbbf949ad9f1684efcc1a2b0f2176cf7877`, migrada para o repositório acadêmico em 2026-11-02.
Este mapa descreve a organização observada; não comprova execução no M5.

## Estrutura

| Caminho | Responsabilidade |
|---|---|
| `lib/main.dart` | Inicialização do Flutter, Riverpod, sessão e deep links |
| `lib/app/` | Composição do app, tema, experiência inicial, sessão e widgets comuns |
| `lib/features/` | Funcionalidades separadas por domínio e suas camadas |
| `lib/services/` | Infraestrutura transversal: API, auth, parsing, data e transporte |
| `lib/l10n/` | Recursos de localização de texto |
| `lib/*.dart` | Entradas/telas legadas remanescentes; verificar imports antes de mover |
| `test/` | Testes unitários e de widgets |
| `integration_test/` | Fluxos integrados por papel e fixtures operacionais |
| `test_driver/` | Suporte a execução/captura de testes |
| `scripts/`, `tool/`, `quality/` | Runners, ferramentas e limites de qualidade |
| `ios/`, `android/`, `web/`, demais plataformas | Integração de cada plataforma; presença não equivale a suporte validado |

## Features encontradas

| Área | Diretório em `lib/features/` |
|---|---|
| Login e autenticação | `auth/` |
| Administração | `admin/` |
| Conta e perfil | `account/` |
| Cadastro/vínculo do passageiro | `passenger_onboarding/` |
| Cadastro/vínculo do motorista | `driver_onboarding/` |
| Viagem do motorista | `driver_trip/` |
| Localização do motorista | `driver_location/` |
| Acompanhamento da viagem | `tracking/` |
| Responsável | `guardian/` |
| Presença em serviço | `service_presence/` |
| Navegação | `navigation/` |
| Deep links | `deep_linking/` |
| Notificações | `notifications/` |
| Suporte | `support/` |
| Componentes compartilhados | `shared/` |

Quando presentes, `data/` contém acesso a dados/gateways, `domain/` contém
contratos de domínio e `presentation/` contém apresentação. Nem toda feature
possui as três camadas. A regra detalhada já está em [lib/README.md](../lib/README.md).

## Integração e componentes auxiliares

A URL da API é definida em `lib/services/api_constants.dart`. Mudanças nos
contratos devem atualizar [a matriz de consumo](api-contract-consumption.md)
e os testes correspondentes no backend e no app.

Os protótipos de design e marketing da origem não integram esta árvore.

Não mover telas, assets ou contratos apenas para uniformizar nomes.
