> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Primeira entrega do MVP — plano executável

Versão de planejamento: 31/10/2026. Entrada principal para executar o pedido atual. O [programa](../../../program/program.json) controla os estados; os cards abaixo são seus contratos. Este documento planeja a implementação completa, mas não declara código, instalação, integração ou percurso aprovados.

## 1. Entrega que o proprietário poderá testar

Um número de WhatsApp do VanRides recebe cadastros, organiza rotinas e presenças, envia o link de início e avisa cada passageiro quando a van se aproxima. O passageiro usa somente WhatsApp. O motorista instala uma aplicação pequena uma vez e inicia a coleta pelo link; não envia posição nem confirma paradas durante o trajeto. Android e iPhone devem passar separadamente pelo mesmo ciclo de negócio.

O coletor próprio é a base técnica adotada neste plano. Usa Flutter para a interface e serviços nativos para localização em segundo plano. A integração com Google Maps/Waze está fora do MVP desde a ADR-009 de 02/11/2026; esses apps não fornecem a telemetria à nossa API. Um link de compartilhamento do Google Maps não é o contrato de localização do MVP. Navigation Connect permanece alternativa estudada, sem uma segunda implementação concorrente.

**Primeira entrega completa** significa ambiente de testes integrado, builds instaláveis e instalados nos aparelhos do proprietário, todas as funcionalidades abaixo demonstradas e evidências reproduzíveis. Não significa publicação em lojas, operação comercial ou promoção de produção. Os HOLDs de piloto/produção do programa continuam vigentes; testes controlados não os removem.

| Função | Resultado exigido |
|---|---|
| Motorista | Cadastro, retomada, habilitação restrita, rotinas de ida/volta, convites e aprovação de pontos pelo WhatsApp |
| Passageiro | Cadastro próprio, pin estático, referência, vínculo aprovado e correções pelo WhatsApp |
| Presença | Pergunta por data/sentido, confirmado/ausente/pendente; silêncio mantém o habitual previsto |
| Início | Link → app → coleta comprovada → confirmação do bot; uma execução ativa por motorista |
| Localização | Posições automáticas atuais, tela bloqueada e recuperação; consulta de pin recente pelo passageiro no WhatsApp |
| Avisos | Passageiro correto, ponto e sentido corretos, sem duplicação de intenção ou minutos inventados |
| Fim | Encerramento automático validado, parada do coletor e contingência explícita |
| Recuperação | Permissões, link vencido, troca de aparelho, indisponibilidade do canal e ajuda tratadas |
| Operação | Logs sanitizados, credenciais próprias, backup/restore, rollback e guia de suporte |

## 2. Recursos e decisões fechados

- O usuário terá **Redmi 8**, iPhone e **Apple ID sem assinatura Apple Developer Program**. Versões reais de Android/MIUI/iOS serão registradas ao conectar; o modelo não comprova a versão instalada.
- O usuário disponibilizará número exclusivo do bot de teste e contatos autorizados. Dados reais serão provisionados fora do Git.
- Instalar Android Studio e componentes locais necessários já está autorizado. Inspecionar antes, preservar configurações e documentar a restauração; não pedir novamente essa autorização.
- Android: APK de teste assinado e instalação direta. iPhone: build de desenvolvimento pelo Xcode/Personal Team, sem exigir TestFlight para este aceite. A Apple informa validade de sete dias do provisionamento gratuito; a entrega inclui procedimento de renovação. [Apple: conta de desenvolvimento](https://developer.apple.com/help/account/basics/about-your-developer-account).
- Interface mínima: parear, verificar permissões/conectividade, mostrar estado de acompanhamento, parar e recuperar. Sem empresa, senha legada, cadastro duplicado ou mapa obrigatório.
- Um motorista ativo por passageiro, um aparelho ativo por motorista e rotinas independentes por sentido são os padrões de engenharia desta primeira versão. O próprio passageiro responde. Sem resposta permanece pendente e elegível; nunca confirmar por silêncio.
- Presença às 20h do dia anterior; timezone `America/Sao_Paulo`; uma pergunta por ocorrência. Horários/dias reais são configuração do ensaio, não nova decisão de arquitetura.
- Aviso textual sem ETA numérico; o ensaio mede utilidade real. Fim manual é contingência. A necessidade de intervenção habitual por parada ou de finalizar sempre manualmente reprova a experiência planejada.

## 3. O que reaproveitar e o que construir

| Componente | Reaproveitar | Implementar / ajustar |
|---|---|---|
| Spring Boot | Infraestrutura HTTP, PostgreSQL/Flyway, testes, autenticação e padrões de operação úteis | Contexto WhatsApp independente do cadastro por empresa, identidade do canal, rotinas, execução, telemetria, avisos, inbox/outbox e autorização própria |
| Flutter | Repositório, ferramentas, base de links/localização e gates | Entrypoint/flavor do motorista, pareamento seguro, serviço nativo durável, fila limitada, estados de falha e distribuição de teste |
| n8n existente | Execução e credenciais, após inventário | W01–W08 versionados, escopos separados, importação, modelo estruturado e recuperação |
| Evolution existente | Instância de teste e operações documentadas da versão efetiva | Adaptador, fixtures reais sanitizadas, status, reconciliação e monitoramento da sessão |
| Coordenador | Programa, documentação e gates | Contratos entre módulos, workflows, manifests, roteiros, evidências e runbooks |
| Mapas | Referências do catálogo e código útil de cálculo, se verificável | Nenhum clone/execução de `vanrides-maps-platform`; nenhuma dependência obrigatória de Maps/Places para coletar GPS |

O backend de teste terá banco/credenciais e exposição isolados do legado. Antes de uma implantação, preparar ingress com lista explícita de endpoints do MVP; não expor automaticamente todas as rotas antigas. Reaproveitar código não exige substituir o serviço remoto atual. As tags remotas observadas em 29/10 diferem dos HEADs locais.

## 4. Sequência canônica e dependências

Cada linha aponta para um card registrado, com tarefas, paths permitidos, aceite, validação e rollback. **Cada sucessor fica `analyzed` até suas dependências e DoR estarem satisfeitas.** Um único card vertical ativo. Código continua dividido em commits/gates por repositório.

| Ordem / pacote | Card e resultado | Depende de |
|---|---|---|
| 00 / P00 | [VR-WA-000-PREFLIGHT](../../../program/cards/VR-WA-000-PREFLIGHT.md): preservar/reconciliar base e registrar gates disponíveis | Nenhum |
| 01 / P00 | [VR-WA-010-TOOLCHAINS](../../../program/cards/VR-WA-010-TOOLCHAINS.md): SDKs, identidades e builds mínimos instalados | 000 |
| 02 / P01 | [VR-WA-020-GPS-SPIKE](../../../program/cards/VR-WA-020-GPS-SPIKE.md): coletor e receptor de prova isolados | 010 |
| 03 / P01 | [VR-WA-030-GPS-PROOF](../../../program/cards/VR-WA-030-GPS-PROOF.md): prova física Android+iPhone | 020 |
| 04 / P02 | [VR-WA-040-DOMAIN-AUTH](../../../program/cards/VR-WA-040-DOMAIN-AUTH.md): modelo, contratos e fronteira de autorização | 020 |
| 05 / P03 | [VR-WA-050-WHATSAPP-TRANSPORT](../../../program/cards/VR-WA-050-WHATSAPP-TRANSPORT.md): entrada/saída do canal e fixtures | 040 |
| 06 / P03 | [VR-WA-070-AGENT-WORKFLOWS](../../../program/cards/VR-WA-070-AGENT-WORKFLOWS.md): agente estruturado e W01–W08 | 050 |
| 07 / P04 | [VR-WA-080-DRIVER-ROUTINES](../../../program/cards/VR-WA-080-DRIVER-ROUTINES.md): motorista, rotina e convites | 070 |
| 08 / P05 | [VR-WA-060-DRIVER-APP](../../../program/cards/VR-WA-060-DRIVER-APP.md): app de uso real e pareamento | 020, 080 |
| 09 / P06 | [VR-WA-090-PASSENGERS](../../../program/cards/VR-WA-090-PASSENGERS.md): passageiro, pin e aprovação | 080 |
| 10 / P07 | [VR-WA-100-ATTENDANCE](../../../program/cards/VR-WA-100-ATTENDANCE.md): calendário e presença | 090 |
| 11 / P08 | [VR-WA-110-RUN-TRACKING](../../../program/cards/VR-WA-110-RUN-TRACKING.md): snapshot, sessão e telemetria integrados | 060, 100 |
| 12 / P09 | [VR-WA-120-DELIVERY-RECOVERY](../../../program/cards/VR-WA-120-DELIVERY-RECOVERY.md): retries, status, UNKNOWN e cancelamento | 050, 110 |
| 13 / P10 | [VR-WA-130-APPROACH](../../../program/cards/VR-WA-130-APPROACH.md): aproximação por pessoa/ponto | 050, 110; base técnica 120 pinada (RD44) |
| 14 / P11 | [VR-WA-140-FINISH-SUPPORT](../../../program/cards/VR-WA-140-FINISH-SUPPORT.md): fim automático e contingências | 130 |
| 15 / P12 | [VR-WA-150-OPERATIONS](../../../program/cards/VR-WA-150-OPERATIONS.md): operação, segredos, retenção e restauração | 040, 140 |
| 16 / P13 | [VR-WA-160-RELEASE-PREP](../../../program/cards/VR-WA-160-RELEASE-PREP.md): artefatos imutáveis e plano externo | 150, 030, 120 |
| 17 / P13 | [VR-WA-170-TEST-ENVIRONMENT](../../../program/cards/VR-WA-170-TEST-ENVIRONMENT.md): implantação de teste autorizada | 160 |
| 18 / P13 | [VR-WA-180-E2E-DEVICES](../../../program/cards/VR-WA-180-E2E-DEVICES.md): regressão integrada e percurso nos dois aparelhos | 170, 030 |
| 19 / P13 | [VR-WA-190-HANDOFF](../../../program/cards/VR-WA-190-HANDOFF.md): entrega utilizável pelo proprietário | 180 |

Em31/10, o proprietário pausou os testes no iPhone físico e pediu avanço no
Android. A [ADR-007](../../../program/decisions/ADR-007-whatsapp-android-first-development.md)
permite desenvolver040 a partir dos contratos020 depois de concluir os ensaios
Android autônomos de030. Quando restar participação física,030 fica em espera
explícita; apenas um card permanece ativo. M1 continua obrigatório em ambas as
plataformas antes160/180. A pausa não dispensa testes, não conclui030 e não
amplia a autorização de publicação do laboratório.

P01 não depende do cadastro completo: usa identidade sintética, receptor autenticado isolado, pontos de ensaio e link de teste. Exercita a mesma interface do coletor que será integrada depois. A prova precisa de HTTPS alcançável pela rede móvel; um servidor apenas em `localhost`/LAN não atende ao percurso. Preparar o artefato e solicitar autorização concreta de ambiente antes de publicar esse receptor. Remover/desabilitar as entradas de prova na entrega integrada.

## 5. Preparação de desenvolvimento

Estado inicial do plano: Flutter 3.38.5/Dart 3.10.4, Java 17 e CocoaPods 1.16.2 existiam em caminhos dedicados; Xcode 27.1 e Docker estavam disponíveis, Android Studio/SDK e banco local ainda não preparados. A execução de 30/10 instalou/preparou esses componentes e validou os gates. O [runbook de builds e instalação](../../runbooks/whatsapp-mvp/010-builds-e-instalacao.md) e a [evidência 010](../../../program/evidence/VR-WA-010-TOOLCHAINS.md) registram o resultado atual, inclusive a confirmação física pendente no Android.

1. Congelar os três SHAs, salvar diffs preexistentes e atribuir a mudança de Graphify antes de normalizar o baseline. Não apagar diffs nem ampliar exclusões para fabricar gate verde. Resolver o wrapper que ignora `GRAPHIFY_FORCE`, se confirmado no card.
2. Instalar Android Studio para Apple Silicon pelo canal oficial, command-line tools, platform-tools, SDK 36 e componentes exigidos pelo build. Registrar versões e licenças; não alterar Java padrão do Mac. Documentar os diretórios adicionados.
3. Alinhar o app à matriz oficial do Flutter 3.38: Java 17, KGP 2.2.20, AGP 8.11.1, Gradle 8.14, compile/target SDK 36, minSdk 24. O ponto de partida usava AGP 8.9.1/Gradle 8.11.1/KGP 2.1.0. A execução de 30/10 confirmou que o plugin MapLibre 0.24.1 exige Java 21: o build Android passa a usar OpenJDK 21 dedicado, preservando Java 17 no backend. Xcode 27 também exige iOS mínimo 15 e inicialização UIScene no driver. Esses ajustes são validados por compilação e abertura, sem atualizar o Flutter como efeito incidental. [Flutter 3.38](https://flutter.dev/blog/whats-new-in-flutter-3-38), [AGP 8.11](https://developer.android.com/build/releases/agp-8-11-0-release-notes).
4. Criar configuração/entrypoint do motorista sem exigir chave Maps/Places para abrir. Fixar dependências/lockfiles; não atualizar toda a stack sem necessidade. Se Flutter/Xcode/plugins forem incompatíveis, reproduzir, corrigir a menor matriz suportada e atualizar a evidência.
5. Conectar o Redmi informado pelo proprietário, autorizar USB no próprio aparelho e observar Android, MIUI e ABI. Em 30/10/2026, o aparelho disponibilizado foi identificado como **Redmi Note 8**, Android 10/API 29, MIUI 12 e ABI arm64-v8a/armeabi-v7a; este passa a ser o aparelho Android físico de referência da execução, corrigindo a denominação inicial “Redmi 8”. Criar emulador ARM64 para automação; a prova física usa o Redmi. Ensaiar configurações de bateria padrão antes de pedir exceções MIUI; documentar somente os ajustes realmente necessários.
6. No iPhone, o proprietário entra na conta Apple no Xcode, confirma confiança/Developer Mode quando solicitado. Verificar signing/provisionamento reais, instalar build executável sem debugger anexado e testar relançamento. Senhas e códigos ficam no fluxo nativo.
7. Definir identificadores estáveis do flavor e assinatura de teste; não herdar `com.example…` ou chave debug como identidade final. iOS usa assinatura de desenvolvimento explicitamente documentada. Preparar seguro o material de assinatura Android e guardar fora do Git.

## 6. Experiência do link e limites reais

No Android, usar App Link verificado com o certificado do APK distribuído. No iPhone, testar Associated Domains com o Personal Team antes de depender dele. Se indisponível, usar landing HTTPS enxuta com botão **Abrir VanRides**, seguido de custom scheme. Isso pode exigir dois toques e uma confirmação do sistema, além das permissões na primeira instalação. Não prometer abertura invisível ou coleta pelo navegador.

No primeiro pareamento, o motorista abre o app pelo ícone, que cria pedido/chave antes de mostrar o código a confirmar no WhatsApp. Um scheme só pode retornar ao pedido já vinculado; não cria primeiro pareamento. A credencial final fica vinculada à chave original do aparelho. GET, preview do WhatsApp e navegador nunca consomem o convite nem iniciam GPS. O link só leva uma intenção opaca e curta. Remover logs de URI inteira existentes. Detalhes em [contratos e workflows](08-contratos-configuracao-e-workflows.md).

O bot só informa “acompanhamento iniciado” depois de receber posição válida e estado ativo. Abrir o link é insuficiente. Localização negada, aproximada, serviço encerrado ou coleta vencida geram estado explicável. Forçar o encerramento do VanRides não tem garantia de continuidade; a experiência deve detectar falta de posição e orientar retomada, sem avisos baseados em GPS antigo.

## 7. Validação e condição de conclusão

Seguir o [roteiro executável](09-roteiro-de-validacao.md), a matriz T01–T43 e os checkpoints M0–M4. A primeira entrega ao proprietário exige M0–M3 e os testes controlados do card 180; M4 continua sendo aceite de piloto ampliado, quando autorizado.

Cada card deixa: código/contrato/workflow, testes proporcionais ao risco, gate do módulo, smoke da mesma versão, QA Log, commit local e evidência canônica. Não usar testes skipped, build simulado, posição repetida ou retorno HTTP da Evolution como prova de entrega real. O relatório final separa automação, simulator/emulator, instalação física, percurso e aceite humano.

Reprovação de GPS, destinatário incorreto, fim prematuro, duplicação crítica, segredo exposto, perda irrecuperável ou fluxo essencial incompleto impede a entrega como pronta. Corrigir e repetir apenas os testes afetados e sua regressão. O sucesso em um sistema não compensa falha no outro.

## 8. Participação humana e impedimentos

| Momento | Ajuda necessária | Preparação que o executor entrega antes |
|---|---|---|
| Instalar nos aparelhos | Conectar/desbloquear, autorizar USB/confiança, Apple login e permissões | Builds e telas prontas, passos curtos e motivo da solicitação |
| Primeira conversa real | Ativar o número de teste/QR e identificar os destinatários autorizados em canal seguro | Adaptador, workflows inativos, teste sintético e configurações sem segredos |
| Modelo real | Conta/API key e saldo/crédito da API confirmados; não inferir disponibilidade só pela assinatura ChatGPT | Modelo/configuração, estimativa e teste por stub; picker/cofre para credencial |
| Expor receptor/ambiente | Aprovar a alteração externa concreta; criar DNS se necessário | SHA/digest, serviços, rotas, impacto, validação e rollback, conforme skill de VPS |
| Percurso | Disponibilizar os dois aparelhos e observador; fornecer sequência/pontos de teste | Instalação validada, checklist e coleta de métricas sem GPS bruto nas evidências |
| Aceite | Executar o roteiro curto no ambiente entregue | Links, builds, versões, instruções de renovação e relatório de resultados |

Informar impedimento com: tarefa, falha observada, tentativa/evidência, impacto, ação exata solicitada e trabalho que pode continuar. A ausência de uma entrada para etapa futura não impede implementar os módulos independentes. Não pedir números, senhas ou coordenadas em documentação pública.

Atualização de 01/11/2026: o número do bot precisa ser substituído. O 120 fica
waiting_human, preservando sua prova de entrega/callback real. O 130 pode ter
DoR preparado sobre 050/110 e a base técnica pinada do 120; ainda permanece
analyzed. O 160 passa a exigir também o fechamento do 120, preservando a barreira
antes de candidato/170/180. Os aceites de utilidade física e GPS por plataforma
continuam pendentes quando não exercitados. Ver RD44 no registro de decisões.

Mudanças externas continuam sujeitas ao procedimento de [publicação na VPS](<<caminho-local-historico>>): “Prepare arquivos, diffs, validação local e o plano concreto antes de pedir.” A autorização local para ferramentas já foi dada. Não há solicitação de publicação antecipada nesta entrega documental.

## 9. Conteúdo do pacote final

APK assinado + checksum; build iOS efetivamente instalado + identificação/provisionamento/renovação; código e commits dos três repositórios; contrato OpenAPI; migrations; exports W01–W08 sem segredos; exemplos de configuração; manifest completo; guia de instalação/onboarding; roteiro de teste do proprietário; evidências sanitizadas por plataforma; suporte, backup/restore, interrupção e rollback. A instalação deve ser reproduzível sem depender de arquivos esquecidos ou alterações não commitadas do executor.
