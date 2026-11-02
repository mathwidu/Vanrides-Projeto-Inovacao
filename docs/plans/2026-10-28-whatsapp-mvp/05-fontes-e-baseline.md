> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Fontes, estado observado e verificação do plano

Consolidação original: 28/10/2026; revisão: 29/10/2026. Este documento separa evidência de código/documentação, decisões propostas e provas ainda necessárias. Faz parte do [plano do MVP](README.md). A recomendação atual é coleta própria em Android/iOS; fontes Navigation Connect documentam a alternativa pesquisada. O inventário atualizado está em [fechamento e prontidão](06-fechamento-e-prontidao.md).

## 1. Base do workspace

| Item | Estado observado na preparação |
|---|---|
| Coordenador | Branch `codex/organizacao-m5`; HEAD anterior ao plano `a1e3b02c5cda2d6330973d840a374036e4bd2fc6` |
| Backend | HEAD `8dace9109a8c9879a3a5e355b6100a24a969ddaa` |
| App | HEAD `eab050928ced0c6a7bb9027496e7fcd9a6749801` |
| Perfil | `core`: coordenador, backend e app; mapas adiado, sem clone/execução |
| Programa | Nenhum card ready/ativo indicado pelo startup; piloto/produção permanecem HOLD |
| Graphify | Consultas com verificação de atualidade, estado CURRENT; pendências semânticas exigiram leitura dos fontes |
| Infraestrutura | Usuário informou n8n/Evolution configurados na VPS; não houve configuração externa nesta entrega documental |
| Integração real | Nenhum percurso, sessão Navigation Connect, pareamento, workflow do MVP ou envio real foi validado nesta entrega |

Autoridade: [programa](../../../program/README.md), [estado canônico](../../../program/program.json), [modelo operacional](../../../program/OPERATING_MODEL.md), [perfil core](../../../workspaces/core.json), [organização documental](../../workspace/organization.md). Esta proposta não substitui esses documentos.

## 2. Código que sustenta as decisões

Links apontam para os fontes locais da revisão, não para prova de runtime. As linhas podem mudar em implementações futuras; os SHAs acima delimitam a inspeção.

| Achado | Fonte | Consequência para o plano |
|---|---|---|
| Cadastro legado requer email/senha; passageiro exige endereço estruturado | [Motorista.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/model/Motorista.java), [Passageiro.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/model/Passageiro.java), [LoginIdentityService.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/LoginIdentityService.java) | Novo contexto de identidade, sem credenciais fictícias |
| Serviço recorrente/presença/operação associados a empresa | [ServicoRecorrente.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/model/ServicoRecorrente.java), [PresencaServico.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/model/PresencaServico.java) | Modelo WhatsApp próprio, sem tornar colunas nulas e presumir compatibilidade |
| Snapshot parcial ainda resolve pin pelo cadastro atual | [ViagemSnapshotApplier.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/ViagemSnapshotApplier.java), [ViagemRotaStopPointResolver.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/ViagemRotaStopPointResolver.java) | Congelar pin, referência, ordem e presença de cada parada |
| ETA contém constantes por estado/parada | [ViagemEtaCalculator.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/ViagemEtaCalculator.java) | Não apresentar como previsão baseada no movimento |
| Posição é gravada sem a nova política temporal/sessão | [LocalizacaoMotoristaService.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/LocalizacaoMotoristaService.java) | Nova ingestão com atualidade, ordenação e geração |
| Despacho existente é push síncrono por dispositivo | [NotificationDispatchService.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/service/NotificationDispatchService.java) | Outbox de WhatsApp com lease, validade e reconciliação |
| Filtro de auth enumera prefixos protegidos | [FilterAuthBasic.java](../../../vanrides-backend/src/main/java/com/vanrides/vanrides/filtros/FilterAuthBasic.java) | Proteger explicitamente os novos endpoints |
| Parser aceita rotas customizadas do produto antigo | [deep_link_parser.dart](../../../vanrides-app/lib/features/deep_linking/data/deep_link_parser.dart), [deep_link_resolver.dart](../../../vanrides-app/lib/features/deep_linking/data/deep_link_resolver.dart) | Adicionar ativação/pareamento sem exigir jornada empresarial |
| Sessão usa SharedPreferences e inclui vínculo empresarial | [app_session_store.dart](../../../vanrides-app/lib/app/session/app_session_store.dart), [app_session_user.dart](../../../vanrides-app/lib/app/session/app_session_user.dart) | Armazenamento seguro e sessão própria do ativador |
| Launcher atual é genérico; atividade nativa não contém bridge Navigation Connect | [trip_navigation_launcher.dart](../../../vanrides-app/lib/features/tracking/data/trip_navigation_launcher.dart), [MainActivity.kt](../../../vanrides-app/android/app/src/main/kotlin/com/example/vanrides_frontend_dev/MainActivity.kt) | A alternativa Google exigiria trabalho adicional; navegação genérica pode continuar opcional no caminho próprio |
| App Links HTTPS não estão preparados; package/assinatura são de desenvolvimento | [AndroidManifest.xml](../../../vanrides-app/android/app/src/main/AndroidManifest.xml), [build.gradle.kts](../../../vanrides-app/android/app/build.gradle.kts) | Preparar identidade, assinatura, distribuição e associação de domínio |
| Geolocator presente; stream usa AppleSettings e envio legado depende de companyId | [location_gateway.dart](../../../vanrides-app/lib/services/location_gateway.dart), [motorista_location_presenter.dart](../../../vanrides-app/lib/features/driver_trip/presentation/motorista_location_presenter.dart) | Separar coletor da tela, configuração por plataforma e contrato de envio; não declarar background confiável só por possuir plugin |
| Tela cancela stream no dispose; iOS já declara background location | [compartilhar_localizacao_screen.dart](../../../vanrides-app/lib/features/driver_location/presentation/compartilhar_localizacao_screen.dart), [Info.plist](../../../vanrides-app/ios/Runner/Info.plist) | Reutilizar elementos com novo ciclo de vida e prova física; declaração de permissão não é evidência de execução |

Inspeção de backend/app foi dividida em análises somente leitura e integrada ao plano. A revisão independente de fluxos/contratos identificou e corrigiu corte do snapshot, unicidade de aviso por destinatário, deduplicação de callbacks e ativação por saída. Nenhum achado foi tratado como funcionalidade já entregue.

## 3. Documentação oficial consultada

Fontes são temporais: confirmar versões, cobertura e condições novamente ao iniciar P00/P01. Não há cópia de credenciais, link pessoal de localização ou trajeto de terceiros neste plano.

| Tema / conclusão documentada | Fonte primária | Limite da conclusão |
|---|---|---|
| Navigation Connect fornece telemetria de navegação ao backend; URLs comuns não têm a mesma função | [Visão geral](https://developers.google.com/maps/documentation/navigation/connect/overview) | Documentação não comprova configuração da nossa conta |
| Preview anunciado em 09/10/2026 | [Release notes](https://developers.google.com/maps/documentation/navigation/connect/release-notes) | Não é maturidade/estabilidade comprovada para nosso piloto |
| Habilitar API é insuficiente: exige verificação do app, dados de privacidade e acesso | [Setup](https://developers.google.com/maps/documentation/navigation/connect/setup-api) | Prazo típico publicado não é aprovação garantida; detalhes enviados exigem revisão antes da submissão |
| Maps Android a partir de 26.14; Waze Android/iOS a partir de 5.15.5; consentimento e localização precisa | [Requisitos](https://developers.google.com/maps/documentation/navigation/connect/setup-overview-requirements) | Features avançadas têm versões mínimas adicionais |
| Maps iOS, browser, Android Auto/CarPlay e Incognito não são suportados pelo caminho documentado | [Visão geral](https://developers.google.com/maps/documentation/navigation/connect/overview) | Se a alternativa Google for escolhida, iPhone exige Waze; Android-only foi rejeitado pelo proprietário |
| Lançamento exige app atribuído e um destino; múltiplos waypoints na URL não são suportados | [Lançamento](https://developers.google.com/maps/documentation/navigation/connect/launch-navigation-app) | Link enviado diretamente pelo bot não substitui automaticamente o ativador |
| UUID de sessão, token temporário, Pub/Sub opcional, cadências 60 s/5 s | [Criar sessão](https://developers.google.com/maps/documentation/navigation/connect/create-trip) | Cadência documentada não é garantia de entrega ponta a ponta |
| Telemetria pode ser recebida por REST/Pub/Sub; posição possui tempos próprios | [Receber dados](https://developers.google.com/maps/documentation/navigation/connect/retrieve-trip-data), [tratar dados](https://developers.google.com/maps/documentation/navigation/connect/handle-trip-data) | Validar envelope/campos na conta/versão real |
| ETA e dados de rota se referem ao destino final; paradas adicionadas alteram métricas | [Tratar dados](https://developers.google.com/maps/documentation/navigation/connect/handle-trip-data) | Não é ETA individual de cada passageiro |
| Sessão ativa pode ser retomada sem criar outra | [Alternância de app](https://developers.google.com/maps/documentation/navigation/connect/mid-trip-app-switching) | Tela bloqueada, rede e comportamento do aparelho continuam dependentes de ensaio |
| `authToken` só é retornado por create; lista de métodos inspecionada expõe create/get | [REST Trip](https://developers.google.com/maps/documentation/navigation/connect/reference/rest/v1/projects.trips) | Não supor recuperação do token por GET nem método remoto de finalizar |
| Preview sem cobrança da API; billing e OAuth necessários; Pub/Sub separado; alta frequência aciona modalidade Enterprise | [Uso e cobrança](https://developers.google.com/maps/documentation/navigation/connect/usage-and-billing) | Preços futuros e limites precisam ser revistos antes de comprometer orçamento |
| Pub/Sub push autenticado usa identidade verificável | [Autenticar push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions) | Validar JWT completo e serviço esperado, não apenas presença do header |
| Evolution oferece eventos de mensagem/conexão; presença online não é presença de transporte | [Webhooks Evolution](https://docs.evolutionfoundation.com.br/evolution-api/configuration/webhooks) | Caminhos, campos, headers e recursos exatos dependem da versão instalada |
| n8n oferece entrada HTTP e opções de retenção/armazenamento de execução | [Webhook](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.webhook/), [dados de execução](https://docs.n8n.io/deploy/host-n8n/configure-n8n/scaling/manage-execution-data.md) | Configurações globais não devem afetar outros workflows sem avaliação |
| JID/LID não permite presumir telefone; normalização deve ser validada | [Baileys v7](https://github.com/WhiskeySockets/docs/blob/main/migration/v7.mdx), [libphonenumber](https://github.com/google/libphonenumber) | Usar apenas se aplicável à conexão real |
| App Links verificados abrem app instalado ou web; Play oferece canais de teste | [Android App Links](https://developer.android.com/training/app-links/about), [testes Play](https://support.google.com/googleplay/android-developer/answer/9845334?hl=pt-BR) | Não presumir deferred deep link ou publicação instantânea |
| Geolocalização web depende de condições do documento, incluindo visibilidade | [Especificação Geolocation](https://www.w3.org/TR/geolocation/#request-a-position) | Página aberta em segundo plano não foi escolhida como rastreador confiável |
| Android possui foreground service de localização com requisitos de tipo/permissão/início | [Android Developers](https://developer.android.com/develop/background-work/services/fgs/service-types#location) | API nativa disponível não garante o comportamento do nosso plugin/build/aparelho |
| Core Location permite atualizações em segundo plano com configuração adequada | [Apple Core Location](https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates) | Não garante coleta depois de force quit nem dispensa ensaio físico |
| Universal Links precisam de associação do domínio; TestFlight possui distribuição/revisão próprias | [Associated Domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains), [TestFlight](https://developer.apple.com/testflight/) | Xcode e um iPhone visível não comprovam assinatura ou conta de distribuição |

## 4. O que permanece por provar

- Coletor próprio em Android/iPhone, independente da tela Flutter; acesso Navigation Connect só se a alternativa voltar a ser escolhida.
- Conexão/versionamento efetivos da Evolution, payload de pin, autenticação de webhook e semântica de status.
- Instalação APK/Xcode Personal Team, renovação iOS, capabilities/fallback dos links, assinatura e pareamento em aparelhos reais.
- Telemetria com tela bloqueada/segundo plano, perda de rede, retomada e comportamento de bateria.
- Experiência com coleta independente da navegação, buffer/ACK, parada online/offline e expiração da autorização, sem ação por parada.
- Antecedência individual, ruas próximas, progresso e encerramento automático.
- Entrega ponta a ponta, capacidade/custos, privacidade/retentiva e aceitação das propostas D01–D10.

Essas lacunas são tarefas e checkpoints explícitos do plano. Não foram resolvidas por leitura da documentação.

## 5. Verificação da entrega documental original de 28/10

Escopo da entrega: seis arquivos deste diretório e entrada em `docs/README.md`. App, backend, migrations, workflows e VPS não fazem parte do diff.

Startup de leitura cumprido: README, programa, perfil, dashboard/summary/next, instruções dos módulos e consultas Graphify com leitura dos fontes relevantes. O principal preservou/restaurou o dashboard no seu startup; uma execução delegada posterior regenerou esse arquivo, sem cópia dos bytes anteriores àquela execução. Não foi possível confirmar mudança de conteúdo nesse momento. O dashboard, que já estava dirty, ficou fora do commit do plano e não foi restaurado ao HEAD por suposição.

Baseline anterior ao plano: `program_control.py check --workspace core` retornou **FAIL: 14 erros e 10 avisos**. Os erros são alterações preexistentes fora de card em `.gitignore`, `AGENTS.md`, configuração/ignore Graphify e scripts Graphify dos três repositórios. Avisos incluem drift de branch/HEAD, falta de evidência remota e mapas adiado. Não representam testes do MVP.

Verificações executadas nesta entrega:

| Verificação | Resultado |
|---|---|
| `python3 scripts/program_control_test.py` | PASS: 59 testes |
| `python3 scripts/workspace_profile_test.py` | PASS: 11 testes |
| Links locais e estrutura do plano | PASS: 42 links locais, fences fechadas, seis documentos |
| Cobertura documental | 14 pacotes P00–P13, dez fluxos F01–F10 e 28 cenários T01–T28 presentes |
| Revisão independente | Achados de consistência corrigidos e reconferidos; sem bloqueadores documentais remanescentes nos pontos revisados |
| Código/aparelho/integrações | NOT RUN nesta entrega; previstos como trabalho futuro |

O PASS dos testes de governança não transforma o controle global em PASS nem comprova o MVP. O commit documental contém somente o plano e seu índice; alterações preexistentes permanecem separadas. Antes de implementar, reconciliar os erros de controle e preparar o card conforme o programa.

## 6. Escopo da revisão de 29/10

Atualiza os seis documentos originais, acrescenta o registro de fechamento e ajusta o índice. Inclui Android+iPhone e D03 confirmados; revisa a recomendação para GPS próprio após comparar complexidade total; remove a dependência obrigatória de Navigation Connect dos contratos/pacotes/gates; amplia a matriz para T01–T33. A escolha técnica recomendada não foi registrada como aceite do proprietário.

O startup desta rodada preservou/restaurou o dashboard com cópia dos bytes e metadados antes da geração. Leitura da VPS e comandos de versão são evidências de inventário, não smoke do MVP. A preparação de Flutter/Java/CocoaPods e seus efeitos locais estão registrados no documento de prontidão. App, backend, migrations, workflows, programa e serviços não foram alterados nesta revisão documental.

Verificação desta revisão: PASS em `git diff --check`; sete documentos, 53 links locais existentes, dez fluxos, 14 pacotes e 33 cenários em uma tabela contínua. Dashboard conferido idêntico ao backup do início desta rodada. Revisão independente encontrou ambiguidade entre nova fixação e replay, dependência prematura de autorização em P13/fechamento de R07 e separação indevida de T33 na tabela; os três pontos foram corrigidos e reconferidos pelo principal. Os testes de governança de 28/10 acima não foram repetidos nem apresentados como prova nova. Build, percurso e integração do MVP permanecem NOT RUN.


## 7. Revisão executável de 30/10/2026

Entradas recebidas do proprietário: Redmi 8; Apple ID sem Apple Developer Program pago; número dedicado do bot e contatos de teste autorizados disponíveis; instalação de ferramentas locais autorizada. Essas respostas fecham perguntas de preferência. SO/MIUI/ABI, capabilities, assinatura e canal continuam sendo verificações de execução.

Foram acrescentados plano executável, contratos/configuração/workflows e roteiro de validação. O programa recebe `VR-WA-MVP` com 20 cards: `VR-WA-000-PREFLIGHT` ready e 19 analyzed; nenhum novo card ativo/concluído, nenhum HOLD removido. Matriz ampliada para T01–T43. Esta revisão não implementou app/API/workflows nem modificou VPS/credenciais.

Reconfirmação local nesta rodada: Flutter 3.38.5/Dart 3.10.4 no caminho dedicado, Java 17.0.21.0.3 e CocoaPods 1.16.2 responderam aos comandos de versão. Ausência no PATH não significa ausência da ferramenta. Android Studio/SDK continuam tarefa de execução; nenhum build do MVP foi feito. O inventário remoto de 29/10 é histórico, não health check de 30/10.

### Fontes oficiais adicionais consultadas

| Fonte | Decisão sustentada / limite |
|---|---|
| [Apple: conta e Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account) | Desenvolvimento no próprio aparelho com conta gratuita; provisionamento expira em sete dias. Não comprova assinatura deste app |
| [Capabilities iOS](https://developer.apple.com/help/account/reference/supported-capabilities-ios), [Associated Domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains) | Capacidade depende da conta/configuração; verificar entitlement real antes de exigir Universal Links |
| [Flutter 3.38](https://flutter.dev/blog/whats-new-in-flutter-3-38), [AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes) | Matriz Java 17/KGP 2.2.20/AGP 8.11.1/Gradle 8.14/API 36; projeto atual exige alinhamento, ainda sem prova de build |
| [Android setup Flutter](https://docs.flutter.dev/platform-integration/android/setup), [iOS setup Flutter](https://docs.flutter.dev/platform-integration/ios/setup) | Instalação, toolchains e testes físicos; confirmação de dispositivo não equivale a build instalado |
| [n8n export/import](https://docs.n8n.io/build/manage-workflows/export-and-import.md), [queue mode](https://docs.n8n.io/deploy/host-n8n/configure-n8n/scaling/enable-queue-mode.md) | Export pode conter nomes/IDs e headers; workers precisam da configuração correta. Não comprova workflow criado/importado |
| [Evolution webhooks](https://docs.evolutionfoundation.com.br/evolution-api/configuration/webhooks) | Adapter depende de evento/versão/conexão reais; documentação não comprova status/pin na nossa instância |
| [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) | Modelo/contrato estruturado escolhido para intenção limitada; disponibilidade, credencial e crédito precisam de smoke na conta real |

### Verificações documentais executadas

- `python3 scripts/program_control_test.py`: PASS, 59 testes.
- `python3 scripts/workspace_profile_test.py`: PASS, 11 testes.
- `validate_static` de `scripts/program_control.py`, perfil core com mapas adiado: zero issues.
- `python3 scripts/program_control.py next --workspace core`: retorna `VR-WA-000-PREFLIGHT [ready]` e o card correto.
- Matriz: 43 cenários únicos e contínuos, T01–T43. Programa: 20 cards novos, dependências válidas, um ready e nenhum ativo.
- Verificação final de links locais: PASS, 232 links em 32 documentos/cards/índices; nenhum destino inexistente. `git diff --check` e validação da ordem das dependências também passaram.

Limite dessas evidências: validam organização, contrato documental e programa. Não atestam execução dos cenários do produto, instalação física, GPS, bateria, entrega WhatsApp ou restore do MVP. O controle runtime global deve registrar separadamente os dirty paths anteriores; não atribuir sua correção à escrita deste plano.


### Revisão independente e correções

A revisão encontrou cinco lacunas: primeiro pareamento por scheme permitia substituição da chave; chave única geral de outbox conflitaria em consultas sucessivas; TTL do pin podia superar a idade útil; app dependia de consumidor de conversa ainda posterior; monitoramento por W08 seria lento para detectar falta de GPS.

As cinco correções foram aplicadas aos contratos, fluxos, cards, dependências e cenários: primeiro pareamento pelo app aberto diretamente; unicidade por finalidade/comando; revalidação do pin antes de submeter; ordem 050 → 070 → 080 → 060; watchdog da API independente do n8n. A rechecagem independente não encontrou achados restantes ou regressões relevantes nesses pontos. Isso é revisão documental, não teste dos mecanismos implementados.

A ordem dos 20 cards na tabela foi conferida contra as dependências do JSON: nenhum consumidor antecede dependência. Cards legados, decisões de produto e milestones foram comparados ao HEAD anterior e permanecem inalterados. Dashboard e status locais conferidos byte a byte contra o backup da rodada; diffs anteriores de app/backend preservados. O grafo foi regenerado estruturalmente; documentos foram lidos diretamente porque enriquecimento semântico permanece pendente.


Verificação runtime após salvar o plano: `python3 scripts/program_control.py check --workspace core` retorna FAIL com os mesmos 14 `UNSCOPED_DIRTY_PATH` preexistentes e dez avisos (branch/HEAD distintos do baseline, ausência de CI remoto e mapas adiado). Os erros estão nas configurações Graphify/AGENTS/gitignore/wrappers dos três repositórios, preservadas fora deste commit. Não são erro da estrutura dos 20 cards, mas impedem chamar o workspace inteiro de limpo. A reconciliação é o escopo explícito de `VR-WA-000-PREFLIGHT`; não foi executada nesta entrega documental. Nenhum push ou alteração externa foi realizado.
