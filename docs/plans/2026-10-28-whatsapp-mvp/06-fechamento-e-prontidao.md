> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Fechamento técnico e prontidão da primeira entrega

Revisão: 30/10/2026. Documento de preparação, vinculado ao [plano completo](README.md). Não declara o MVP implementado nem autoriza publicação. A sequência foi cadastrada no programa: um card ready, demais analyzed, nenhum ativo. A recomendação abaixo responde à escolha entre Navigation Connect e coleta própria considerando complexidade total, qualidade e a exigência de Android+iPhone.

## 1. Recomendação técnica

**Usar um app Flutter pequeno com coleta própria apoiada nos serviços nativos de Android e iOS.** WhatsApp continua sendo a interface de cadastro, presença, início e comunicação. O app é instalado uma vez pelo motorista, recebe o link de início, mantém a coleta em segundo plano e oferece status/parada/recuperação. Passageiro não instala app. Navegação no Maps/Waze fica fora do MVP desde a ADR-009 de 02/11/2026; a comparação abaixo permanece como histórico das alternativas avaliadas.

Esta é a base técnica do plano executável solicitado, sujeita à prova P01 nos dois sistemas. A indicação inicial de Navigation Connect priorizava reduzir código de captura. A revisão independente e a comparação com o percurso real favoreceram independência da navegação: o motorista conhece uma sequência de embarques, precisa de GPS contínuo e não necessariamente de orientação até um único destino.

| Critério | Navigation Connect | Coletor no VanRides |
|---|---|---|
| Construção inicial | Menos código de sensor; integração de sessão, tokens, OAuth, Pub/Sub/consulta e navegador | Mais cuidado com serviço nativo, permissões, energia, envio e recuperação |
| Android e iPhone | Maps Android; Waze no iPhone no suporte atual | Mesmo produto Flutter com adapters de plataforma |
| Durante o percurso | Requer navegação ativa; comportamento depende também do navegador | Acompanhamento independe de abrir/fechar Maps ou Waze |
| Várias paradas habituais | O lançamento aceita um destino; uso como fonte de posição precisa de ensaio | Sessão pertence à saída VanRides e acompanha a sequência cadastrada |
| Entrada em operação | Acesso/verificação Google e Preview, além da distribuição do nosso app | Assinatura/distribuição do app e validação nativa; sem aprovação Navigation Connect |
| Qualidade a comprovar | Navegação ativa, segundo plano, interrupções e adequação das paradas | Segundo plano, processo/energia/rede, limite de coleta e recuperação |
| Trabalho comum | API de presença/progresso, avisos, segurança, n8n/Evolution e testes reais | O mesmo trabalho de negócio continua necessário |

Inferência de engenharia: o coletor próprio exige mais trabalho mobile inicial, mas reduz dependências da experiência diária e oferece maior controle do ciclo da saída. Isso não o torna automaticamente mais confiável; qualidade depende da prova física. Não prometer coleta contínua irrestrita após encerramento forçado ou revogação de permissões.

A base atual contém Geolocator e configuração iOS de background, mas o stream está ligado à jornada/tela legada e ao envio com `companyId`. É ponto de partida, não rastreador pronto. P01 decide se o plugin fixado atende ou se é necessária atualização/adaptação nativa; qualquer biblioteca paga exige avaliação explícita antes de escolha/contratação.

Fontes verificadas: [Android foreground service de localização](https://developer.android.com/develop/background-work/services/fgs/service-types#location), [Core Location em segundo plano](https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates), [plataformas Navigation Connect](https://developers.google.com/maps/documentation/navigation/connect/overview), [destino único e lançamento](https://developers.google.com/maps/documentation/navigation/connect/launch-navigation-app), [verificação Google](https://developers.google.com/maps/documentation/navigation/connect/setup-api). Documentação de uma capacidade não comprova nosso comportamento no aparelho.

## 2. O que está fechado e o que é padrão proposto

| Tema | Situação para esta revisão |
|---|---|
| Plataformas | Confirmado pelo proprietário: Android e iPhone desde a primeira entrega |
| Presença sem resposta | Confirmado: continua previsto, marcado pendente; permanece elegível para aviso |
| Pessoas do piloto | Confirmado: próprio passageiro; motorista com pessoas/pontos/horários recorrentes |
| Experiência | Confirmado: gestão pelo WhatsApp; instalação inicial aceitável; sem ação por parada |
| Fonte de GPS | Base técnica do plano: coletor próprio; sem aceite de funcionamento antes de P01 |
| D02 | Padrão proposto: um motorista ativo por passageiro e um aparelho ativo por motorista |
| D04 | Padrão proposto: ida e volta configuradas separadamente, sequência aprovada pelo motorista |
| D05 | Padrão proposto: aviso sem minutos; ETA individual somente se validado |
| D06 | Padrão proposto: término automático validado; finalizar manualmente é contingência. Torná-lo obrigatório seria nova decisão |
| D07 | Recomendação técnica: contexto próprio dentro da API existente, sem empresa/credenciais fictícias |
| D09 | Redmi 8 e Apple ID gratuito confirmados: APK direto + Xcode/Personal Team; signing/renovação e links serão testados |
| D10 | Parâmetros de ensaio definidos abaixo; orçamento, dados reais e operação exigem fechamento antes de piloto |

O pedido atual é um plano completo executável; adotar o coletor próprio como decisão técnica resolve a comparação sem reabrir as respostas do proprietário. Funcionalidade em aparelhos reais continua pendente de prova, não de mais uma pergunta de preferência.

## 3. Parâmetros concretos para construir e testar

Valores abaixo são **propostas configuráveis**, não SLA, resultado medido ou aceite de piloto. Alterações feitas após observar resultados precisam aparecer no relatório; não mudar critérios silenciosamente para aprovar uma execução.

| Parâmetro | Base proposta |
|---|---|
| Calendário | Timezone `America/Sao_Paulo`; rotina informa dias, horário e sentido; exceção/cancelamento por data |
| Pergunta de presença | Uma pergunta por ocorrência às 20h do dia anterior; sem lembretes repetidos no primeiro piloto; editar por rotina antes de ativar |
| Corte da lista | Transação de início `STARTING`; cancelamento tardio bloqueia aviso individual sem prometer mudança da rota |
| Coleta em movimento | Alvo de envio 5–10 s; atualidade máxima inicial 20 s; precisão/ruas próximas entram no critério |
| Buffer offline | Até dois minutos e 120 amostras, o que ocorrer primeiro; descarte contado; backlog não produz aviso antigo |
| Autorização local | Lease inicial de cinco minutos, renovável somente com run ativo; expiração e parada precisam ser comprovadas no sistema nativo |
| Aviso útil | Antecedência inicial de 2–5 min; mensagem textual sem previsão numérica; cobertura e falhas medidas conforme M1 |
| Mensagens e agente | Texto como caminho garantido; pin vem do payload; VOU/NÃO VOU/AJUDA têm fallback determinístico; botões dependem do canal real |
| Primeiro lote de teste | Motorista e destinatários de teste autorizados; repetir cenário nos dois sistemas antes de ampliar pessoas |

Proposta de retenção para revisão antes do piloto: última posição somente durante a operação e remoção ao encerrar; buffer local conforme limite acima; recibos técnicos sem coordenadas por sete dias; contexto mínimo de conversa por sete dias de inatividade; presença e auditoria operacional por 90 dias; perfil/ponto enquanto vínculo ativo e limpeza até 30 dias após encerramento, ressalvadas obrigações aplicáveis; backups por sete dias com reaplicação de exclusões após restore. Não manter transcrição integral ou trilha completa de GPS por padrão. Esses valores precisam ser compatíveis com a finalidade, política publicada e contratos dos fornecedores efetivos; não representam parecer jurídico.

Limite financeiro não foi inventado: antes de qualquer contratação ou piloto, apresentar estimativa por saída, custos fixos de distribuição, eventual licença mobile e teto escolhido pelo proprietário. Billing/Preview do Navigation Connect não entra no orçamento do caminho principal.

## 4. Estado observado nesta preparação

| Recurso | Evidência (local reconferido em 30/10; VPS de 29/10) | Limite |
|---|---|---|
| Flutter | SDK 3.38.5 no tag `f6ff1529fd6d8af5f706051d9251ac9231c83407`; Dart 3.10.4; comando de versão executado | Nenhum build do MVP executado |
| Java | OpenJDK 17.0.21.0.3 instalado; Java 21 continua padrão do Mac | Gate do backend não executado nesta revisão |
| CocoaPods | 1.16.2 instalado em diretório dedicado; comando `pod --version` passou com Ruby 4.0.9 explícito | `pod install`/build iOS e compatibilidade completa ainda não testados |
| Xcode | 27.1, build 27A9275; um iPhone físico reportado acessível pelo inventário | Não comprova assinatura/instalação; proprietário informou Apple ID gratuito em 30/10 |
| Android | SDK/ferramentas não encontrados no PATH nem no diretório convencional verificado | Redmi 8 confirmado em 30/10; versão Android/MIUI/ABI será observada na conexão |
| Docker/PostgreSQL | Docker local acessível; sem Postgres observado na porta local 5432 | Não comprova banco preparado nem migrations testadas |
| n8n na VPS | Serviços editor/webhook/worker/MCP observados em `2.45.4`, 1/1 réplicas | Não comprova workflow do MVP, credenciais, payload ou integração funcional |
| Evolution na VPS | Serviço observado em `v2.3.7`, 1/1 réplicas | Tipo/conexão da instância, autenticação e contrato real ainda não verificados |
| Backend remoto | Tags observadas de dev `dev-b438e2f` e produção `prod-7231574` | Divergem do checkout local; reconciliar proveniência antes de preparar release; não publicar HEAD antigo por suposição |
| Programa | Nenhum card ativo; HOLD de piloto/produção; 14 dirty paths preexistentes fora de card | Controle global não está verde; a documentação não remove os bloqueios |

O inventário da VPS foi somente leitura. Não houve mudança de serviço, workflow, DNS, banco, credencial ou envio de WhatsApp. SDKs e dependências locais foram preparados; código do app/backend e seus arquivos preexistentes não foram alterados.

## 5. Preparação local e restauração de configuração

Flutter está em `<caminho-local-historico>`; gems em `<caminho-local-historico>`. Usar caminhos explícitos e variáveis apenas por comando. Java 17 está em `/opt/homebrew/opt/openjdk@17`; Ruby de execução está em `/opt/homebrew/opt/ruby`.

Nenhum perfil de shell foi editado. Ruby Homebrew foi desvinculado; `java` continua Temurin 21 e `ruby` continua `/usr/bin/ruby`. O CLI OpenSSL 3.6.4 foi religado após a instalação ter selecionado OpenSSL 4. Os três defaults foram conferidos. `brew install openjdk@17 ruby` também instalou/atualizou dependências compartilhadas; não foi uma alteração limitada a dois executáveis. A limpeza automática foi desabilitada para preservar kegs anteriores.

Para deixar de usar esse ambiente, parar de passar os caminhos explícitos; isso já retorna aos defaults observados. Remoção de SDKs/gems dedicados ou rollback de fórmulas Homebrew deve ser uma ação separada após verificar quem depende delas; não rodar `brew autoremove` ou apagar kegs antigos indiscriminadamente. O estado anterior do dashboard desta rodada foi copiado e restaurado byte a byte; alterações preexistentes dos três repositórios permanecem fora do commit documental.

## 6. Entradas resolvidas e impedimentos de execução

| ID | Estado / pendência | Etapa e encerramento |
|---|---|---|
| R01 | Fechado no plano: coletor próprio como baseline | P01 comprova capacidade; falha abre impedimento técnico, não nova votação genérica |
| R02 | Respostas recebidas: Redmi 8, iPhone, Apple ID gratuito, número/contatos de teste | 010/050 observam SO/capabilities, assinatura e conexão; não pedir senha/número em docs |
| R03 | Ferramentas/build/identidade/links ainda precisam ser executados | 010/060: builds instalados sem debugger, APK/Personal Team, renovação iOS e fallback seguros |
| R04 | 14 dirty paths preexistentes e proveniência divergente observados | 000: preservar, atribuir e reconciliar; nenhum gate enfraquecido |
| R05 | Canal/modelo/ambiente precisam de credenciais e prova real | 050/070/170: payload/status, número dedicado, API key/crédito e HTTPS testados |
| R06 | GPS/fim físico ainda não testados | 030 e repetição 180: segundo plano, rede, energia, aproximação e stop online/offline nos dois aparelhos |
| R07 | Funcionalidades ainda por implementar | 040–140/180: T01–T43 pertinentes passam no manifest entregue |
| R08 | Operação/custos/retenção precisam de ensaio/configuração final | 150/160: restore, purge, limites, suporte e orçamento concreto antes de cobranças/expansão |
| R09 | Publicação de teste requer autorização específica | 160 prepara; 170 publica somente após autorização; 190 entrega pacote testável |

Recursos já confirmados não são novas perguntas. R03–R08 são tarefas técnicas ou entradas de execução com responsável; pedir ajuda somente quando necessária e com evidência/preparação. Não depender de Play Console, TestFlight ou Navigation Connect para a primeira instalação do proprietário.

Sequência: reconciliar base → preparar ferramentas/builds → provar GPS → integrar negócio/agente → preparar operação/artefatos → publicar teste autorizado → validar ambos os aparelhos → entregar. Ver [cards e critérios](07-plano-executavel.md).

## 7. O significado de primeira entrega completa

O passageiro consegue se cadastrar e responder presença inteiramente pelo WhatsApp. O motorista instala/configura uma vez, inicia uma saída pelo link, recebe GPS funcional com tela bloqueada e dispensa ações por parada. A API mantém pendentes previstos e avisa o passageiro correto no ponto correto com antecedência medida. No final, o run encerra, a coleta para ou sua pendência fica explicitamente tratada, e mensagens antigas não voltam a ser submetidas.

Isso precisa acontecer nos builds Android/iPhone e na API/workflows efetivamente distribuídos, com instalação limpa, regressões, ensaio físico, monitoramento e recuperação. Documentação, comandos de versão ou testes unitários isolados não satisfazem esse aceite.
