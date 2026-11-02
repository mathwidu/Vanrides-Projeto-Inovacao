# Portal Admin S2 — Pesquisa, IA e Plano Premium

Data: 2026-06-17

Objetivo: transformar o portal da empresa em uma ferramenta operacional premium,
simples e confiável. A empresa deve entender o que preparar, o que está rodando
hoje, o que exige atenção e qual ação tomar, sem precisar pensar em conceitos
técnicos como vínculo, snapshot ou manifesto.

Este documento substitui a direção antiga de portal claro/cream para a fase
atual. A fonte visual da fase é o padrão dark/liquid do VanRides, alinhado a
`PRODUCT.md`, `DESIGN.md` e ao app passageiro/motorista.

## Referências Usadas

- Apple Liquid Glass: usar translucidez, profundidade e transformação para
  trazer foco ao conteúdo, mas sem sacrificar legibilidade.
  - https://developer.apple.com/documentation/technologyoverviews/liquid-glass
  - https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass
  - https://developer.apple.com/design/Human-Interface-Guidelines/materials
- Apple HIG: sheets e materiais devem reforçar hierarquia e continuidade entre
  conteúdo e controles, não virar decoração pesada.
  - https://developer.apple.com/design/human-interface-guidelines/sheets
- Material Design 3: navegação lateral funciona melhor quando os destinos
  principais são poucos, estáveis e agrupados por importância.
  - https://m3.material.io/components/navigation-rail/overview
  - https://m3.material.io/components/navigation-drawer/overview
- Material Design 3: feedback temporário deve explicar operações concluídas ou
  em andamento, sem virar o principal mecanismo de correção.
  - https://m3.material.io/components/snackbar/guidelines
- Nielsen Norman Group: sistemas complexos precisam falar a linguagem do
  usuário, manter status visível, reduzir memória de trabalho e usar erros com
  orientação construtiva.
  - https://www.nngroup.com/articles/ten-usability-heuristics/
  - https://www.nngroup.com/articles/usability-heuristics-complex-applications/
  - https://www.nngroup.com/articles/visibility-system-status/
  - https://www.nngroup.com/articles/errors-forms-design-guidelines/
  - https://www.nngroup.com/articles/dashboards-preattentive/

## Impact Analysis

### Contexto

O portal admin já possui capacidades reais:

- login e bootstrap de empresa;
- dashboard;
- gestão de passageiros, motoristas e responsáveis;
- serviço recorrente;
- rota-base;
- viagem do dia;
- geração automática de viagens;
- detalhe de viagem com motorista operacional, presença e mapa;
- convite/link e solicitação de vínculo de motorista.

O problema principal não é ausência de feature. O problema é que a experiência
apresenta a operação como módulos técnicos independentes. Isso força o admin a
descobrir mentalmente o caminho correto.

### Mudanças técnicas esperadas

- Reorganizar a navegação e o modelo mental do portal.
- Rebaixar `Vínculos` para relações contextuais dentro de Pessoas, Serviços e
  Motoristas.
- Transformar `Viagens` em `Viagens do dia`, com manutenção da data atual como
  caminho principal.
- Criar ações guiadas para montagem do dia, atualização de manifesto e exceções.
- Revisar telas de entidade para ficha operacional, não tabela genérica.
- Refinar o sistema visual dark/liquid com menos cards decorativos, mais
  hierarquia e feedback consistente.
- Adicionar contratos de backend apenas quando a UX precisar de uma operação
  que hoje não existe de forma segura.

### Contratos possivelmente afetados

- Novo contrato dedicado para foto de perfil:
  - `POST /account/me/avatar`;
  - multipart com tipo/tamanho validados;
  - retorno com `fotoPerfilUrl` persistida e servida de forma estavel.
- Extensao dos summaries/details administrativos de pessoas:
  - `dataNascimento`;
  - `fotoPerfilUrl`;
  - status de menoridade/responsavel quando aplicavel.
- Criacao atomica de passageiro com servicos:
  - evita passageiro criado sem relacao quando a segunda chamada falha;
  - rejeita servico de outra empresa dentro da mesma transacao.
- Novo contrato para calcular impacto ao alterar serviços de um passageiro:
  - entrada: `passageiroId`, `servicoIds`, `dataOperacao`;
  - saída: próximas viagens afetadas, viagens de hoje já montadas, se podem ser
    atualizadas e ação recomendada.
- Novo contrato para status de manifesto nas viagens:
  - `manifestoStatus`: atualizado, pendente, bloqueado, nao_aplicavel;
  - contadores de entradas/saidas;
  - motivo de bloqueio quando a viagem ja iniciou.
- Novo contrato para atualizar manifesto de uma viagem ainda não iniciada:
  - `POST /company-admins/viagens/{id}/sincronizar-manifesto`;
  - objetivo: aplicar alterações de passageiros elegíveis/presença sem apagar a
    viagem e criar outra manualmente.
- Possível endpoint de resumo operacional do dia:
  - data selecionada;
  - viagens montadas;
  - serviços sem viagem;
  - pendências de presença;
  - solicitações de motorista;
  - passageiros sem serviço.

### Riscos e mitigação

- Risco: virar redesign visual sem melhorar a operação.
  - Mitigação: cada card precisa reduzir uma decisão confusa do admin.
- Risco: esconder vínculos e perder capacidade operacional.
  - Mitigação: manter relações em fichas contextuais e preservar área avançada
    apenas para suporte/admin técnico, se necessário.
- Risco: introduzir backend demais antes de validar UX.
  - Mitigação: implementar primeiro contratos de leitura/impacto e só depois
    mutações irreversíveis.
- Risco: liquid glass prejudicar legibilidade em telas densas.
  - Mitigação: vidro translúcido só em superfícies principais; tabelas e campos
    usam base escura sólida com bordas sutis.

### Validação mínima

- App:
  - `flutter analyze`;
  - `flutter test`;
  - `./scripts/qa_gate_card.sh`;
  - Playwright/web smoke com cliques reais.
- Backend, quando houver contrato:
  - `./mvnw test`;
  - MockMvc dos novos contratos;
  - smoke HTTP em DEV após deploy.
- Visual:
  - screenshots do portal nos fluxos principais;
  - conferência de truncamento, contraste, estados vazios, erro e sucesso.

## Auditoria Read-Only Com Subagentes

Em 2026-06-17, tres subagentes read-only revisaram o codigo para reduzir
viés de implementação:

1. **IA e navegação admin**
   - O shell ja esconde `Vínculos` e `Rotas-base` em area avancada, mas o
     modelo tecnico continua vivo como destino real.
   - `Vínculos` concentra tarefas de onboarding, convites, responsaveis,
     servicos liberados e atalhos de motorista; deve ser desmontado por
     contexto antes de sumir de vez.
   - A proposta alvo fica em seis areas: Painel, Viagens do dia, Servicos,
     Pessoas, Motoristas e Empresa.
2. **Serviços, viagens e manifesto**
   - `Serviço recorrente` e planejamento; `Viagem` e snapshot operacional.
   - Alterar passageiros do servico depois de montar o dia nao atualiza a
     viagem automaticamente.
   - `Montar dia` e idempotente: cria viagens novas ou retorna existentes, mas
     nao resincroniza manifestos existentes.
   - `Criar exceção` abre com `DateTime.now()`, nao necessariamente com a data
     selecionada na lista.
3. **Perfil, conta e avatar**
   - A troca de foto atual nao e upload real; o app envia `PATCH /account/me`
     com `fotoPerfilUrl` em JSON.
   - O backend aceita URL HTTPS ou `data:image/{jpeg,png,webp};base64,...`.
   - A policy rejeita URLs relativas como `/uploads/...`, apesar de partes do
     app conseguirem renderizar esse formato.
   - Para uma experiencia premium, a fase deve criar upload real de avatar,
     expor `dataNascimento`/`fotoPerfilUrl` nos contratos admin e validar os
     quatro papeis.

Conclusao: a reforma nao pode ser apenas visual. Ela precisa transformar
estado tecnico em decisao operacional: status visivel, impacto claro e proxima
acao segura.

### Auditoria complementar — 2026-06-18

Depois da validação real do fluxo limpo em DEV, duas leituras read-only
focadas reforçaram um padrão recorrente: o portal já tem capacidades reais,
mas ainda mistura ação operacional com artefatos de implementação.

Achados incorporados no card `ADMIN-UX-0016`:

- em `Viagens do dia`, a ação global `Abrir viagem existente` era ambígua em
  dias com múltiplas viagens; a abertura passou a acontecer por linha;
- o botão `Troca via campo` na viagem manual parecia ação editável, mas não
  executava nada; foi substituído por estado informativo;
- Passageiros e Responsáveis ainda exibiam paginação ou filtros decorativos,
  aumentando a carga cognitiva sem dar controle real;
- a sidebar precisava de agrupamento básico para o admin entender o modelo:
  `Operação`, `Pessoas` e `Empresa`;
- o perfil já possuía upload real, mas o alvo visual de troca de foto precisava
  de semantics/tooltip para ser descoberto e testável.

Critério resultante para as próximas fatias: qualquer botão, filtro, paginação
ou chip que apareça no portal precisa ser uma ação real, um estado real ou uma
orientação operacional clara. Caso contrário, deve ser removido, rebaixado ou
transformado em texto explicativo.

### Auditoria complementar — dashboard como central de próxima ação — 2026-06-18

A validação visual do portal depois do `ADMIN-UX-0016` mostrou que o dashboard
ainda parecia mais um painel de métricas do que uma tela de decisão. A tela
resumia motoristas, serviços, viagens e presenças, mas não dizia ao admin qual
era o próximo passo mais seguro.

Achados incorporados no card `ADMIN-UX-0017`:

- CTAs como `Montar viagens`, `Montar dia` e `Novo Serviço` prometiam ação
  mutante, mas apenas navegavam para outra tela;
- linhas de status usavam `more_vert` como affordance de menu, mas não havia
  menu contextual real;
- o dashboard tinha dados suficientes para indicar prioridade operacional:
  criar serviço, cadastrar motorista, revisar serviços sem escala, abrir
  viagens, acompanhar execução ou revisar presenças;
- a primeira tela do portal deve reduzir pergunta, não aumentar: o admin deve
  entender "o que falta agora" antes de decidir em qual módulo entrar.

Critério resultante para a sequência: o dashboard é a central de orientação do
dia. Ele pode navegar para telas especialistas, mas não deve fingir que executa
operações críticas sem confirmação, preview ou feedback dentro do fluxo correto.

### Auditoria complementar — serviços com próximos passos legíveis — 2026-06-18

A revisão visual da tela de `Serviços recorrentes` mostrou que a tabela estava
funcional, mas ainda dependia de três botões apenas por ícone. Para o time de
produto, isso era o mesmo problema em outra forma: o portal obrigava o admin a
descobrir significado por hover ou tentativa.

Achados incorporados no card `ADMIN-UX-0018`:

- `AÇÕES` não comunica intenção; `PRÓXIMO PASSO` aproxima a tabela da decisão
  operacional;
- ícones de abrir, editar e inativar foram preservados, mas acompanhados por
  texto para reduzir interpretação;
- serviço sem motorista fixo precisava virar orientação antes da montagem do
  dia, sem dominar a página com uma faixa de alerta pesada;
- a tela de serviços deve continuar densa e escaneável, mas todo controle
  visível precisa ser compreensível sem tutorial.

Critério resultante para a sequência: em telas administrativas densas, ícones
podem reforçar ações, mas não devem ser a única linguagem para tarefas críticas.

### Auditoria complementar — responsáveis com ficha premium — 2026-06-18

A revisão visual da tela de `Responsáveis` mostrou que a listagem estava
funcional, mas o detalhe selecionado ainda rompia a linguagem premium do portal:
um cartão azul sólido dominava a página e a orientação acima da tabela expunha
termos de implementação (`backend`, `senha provisória`) que não ajudam o admin
a decidir.

Achados incorporados no card `ADMIN-UX-0019`:

- o detalhe do responsável deve parecer uma ficha administrativa do mesmo
  sistema dark/liquid, não um banner promocional;
- texto operacional precisa orientar conferência de dados e vínculos, sem
  explicar lacunas técnicas para o usuário final;
- status de acesso, telefone e vínculos são úteis, mas devem aparecer como
  sinais compactos dentro da ficha;
- validação visual deve olhar a página inteira, porque uma superfície destoante
  pode quebrar a percepção de qualidade mesmo quando o fluxo funciona.

Critério resultante para a sequência: fichas de pessoas devem combinar densidade
operacional com hierarquia calma. O admin precisa entender quem é a pessoa, qual
o status de acesso e qual ação real pode tomar sem atravessar detalhes de
implementação.

### Auditoria complementar — lista de passageiros sem ações ambíguas — 2026-06-18

A tela de `Passageiros` já concentrava criação, convites, responsáveis e
serviços no lugar certo, mas a lista principal ainda misturava affordances
pequenas: refresh por ícone, cadastro duplicado no cabeçalho da lista e menu de
três pontos com apenas uma opção.

Achados incorporados no card `ADMIN-UX-0020`:

- ações repetidas competem com o CTA principal e aumentam a sensação de painel
  complexo;
- menu visual com uma única ação parece esconder opções que não existem;
- ações de linha como editar e trocar motorista precisam ter texto quando são
  tarefas críticas de manutenção;
- o estado vazio é o lugar natural para repetir o CTA de cadastro, porque ali
  ele resolve o próximo passo imediato.

Critério resultante para a sequência: listas administrativas devem usar verbos
curtos e explícitos para ações principais. Ícones podem acompanhar, mas não
devem ser a única pista para uma operação que altera cadastro ou escala.

### Auditoria complementar — motoristas com próxima ação clara — 2026-06-18

A tela de `Motoristas` já tinha o novo fluxo de convites, solicitações e
motoristas ativos, mas a lista final ainda carregava um resquício de UI genérica:
`more_horiz` nos registros não selecionados e `chevron_right` no selecionado.
Na prática, isso insinuava menus e navegação sem dizer ao admin o que fazer.

Achados incorporados no card `ADMIN-UX-0021`:

- refresh por ícone funciona para usuário técnico, mas no portal operacional
  deve aparecer como `Atualizar`;
- `AÇÕES` continuava genérico demais para a linguagem da fase; `PRÓXIMO PASSO`
  reforça que a linha oferece uma decisão;
- `Ver ficha` comunica melhor a ação real de selecionar/abrir o detalhe do
  motorista;
- dados reais de contato precisam ser tratados como conteúdo de interface:
  e-mail não pode quebrar no meio da palavra e degradar a leitura da tabela.

Critério resultante para a sequência: fichas e listas de pessoas precisam
proteger legibilidade de dados reais, especialmente e-mail, telefone e nome.
Truncamento intencional com uma linha é preferível a quebra acidental que cria
ruído visual.

### Auditoria complementar — viagens do dia como manutenção segura — 2026-06-18

A tela de `Viagens do dia` já tinha suporte técnico para montar dia, criar
manual e sincronizar manifesto, mas a linguagem ainda deixava o admin pensar que
apagar/recriar viagem era o caminho natural para incluir passageiro depois de
alterar serviço. Isso ficou evidente no teste real de operação com passageiro
adicionado depois da primeira montagem.

Achados incorporados no card `ADMIN-UX-0022`:

- quando já existem viagens, a ação principal precisa ser `Atualizar dia`, não
  `Montar dia`, para comunicar manutenção e idempotência;
- `Criar exceção` era abstrato demais; `Criar viagem extra` descreve que é uma
  viagem pontual fora do plano, não uma correção do manifesto existente;
- a lista deve apontar `Revisar manifesto` quando o backend sinaliza pendência
  ou bloqueio, levando o admin para o detalhe e para `Sincronizar manifesto`;
- a coluna de ação precisa representar o próximo passo operacional, não apenas
  "abrir" uma tela.

Critério resultante para a sequência: telas operacionais não devem oferecer a
mesma ação com o mesmo peso para estados diferentes. Quando a entidade já existe,
o portal deve guiar manutenção segura antes de sugerir criação nova.

## Diagnóstico Atual

### Navegação

- Sidebar expõe muitos destinos de primeira ordem.
- `Vínculos` aparece como módulo principal, mas para a empresa isso é uma
  relação dentro de passageiro, motorista, responsável ou serviço.
- `Rotas` e `Serviços` coexistem como destinos semelhantes; para o admin, a
  unidade mental mais forte é o serviço/linha recorrente.
- Topbar mostra busca, ajuda e notificações como se fossem funcionais, mas hoje
  não guiam uma tarefa real.
- O botão lateral `Viagens do dia` duplica a navegação e cria peso visual.

### Painel

- Precisa virar central de comando do dia, não apenas resumo.
- A primeira dobra deve responder:
  - qual data estou operando;
  - o dia está montado ou não;
  - quais pendências bloqueiam a operação;
  - qual é a próxima ação mais segura.

### Passageiros

- A tela ainda parece uma tabela administrativa genérica.
- Existem métricas que soam falsas ou pouco úteis, como documentação baseada em
  e-mail preenchido.
- Há paginação mockada fixa (`1`, `2`, `3`) mesmo sem paginação real.
- Ação de cadastrar aparece com ícone incorreto em um ponto da lista.
- A ficha do passageiro deve ser o lugar para:
  - dados pessoais;
  - data de nascimento e menoridade;
  - parada/endereço validado no mapa;
  - serviços liberados;
  - responsáveis;
  - impacto nas viagens de hoje;
  - status de conta/e-mail/foto.

### Motoristas

- O fluxo de convite por link já existe, mas ainda precisa ser apresentado como
  pipeline claro:
  - convidar por link;
  - solicitações recebidas pelo app;
  - motoristas ativos;
  - motoristas com pendências.
- O cadastro manual deve ser secundário e explicado como exceção operacional.
- A tela precisa diferenciar motorista cadastrado, convidado, solicitante e
  ativo na empresa.

### Responsáveis

- Deve deixar de ser apenas cadastro.
- Precisa mostrar passageiros vinculados, permissões e status de acesso.
- Para menor de idade, a relação passageiro-responsável deve ser evidente.

### Serviços

- Serviços recorrentes devem ser o centro da operação planejada.
- A tela deve explicar que o serviço define:
  - destino;
  - dias;
  - horário;
  - motorista padrão;
  - passageiros elegíveis;
  - geração automática.
- A manutenção de passageiros do serviço precisa avisar impacto nas viagens já
  montadas.

### Viagens do Dia

- A tela deve ser orientada a uma data.
- `Montar dia` deve ser o caminho primário quando não há viagens.
- `Criar manual` deve virar `Criar exceção` e ficar rebaixado.
- O admin não deve precisar deletar viagem para incluir passageiro novo.
- A manutenção normal deve ser:
  - abrir viagem existente;
  - ajustar motorista;
  - atualizar presença/manifesto;
  - cancelar viagem, se necessário.

### Visual e feedback

- Ainda há superfícies claras isoladas em alguns botões/linhas.
- O glass atual é mais "card escuro com borda" do que liquid glass.
- Há excesso de cards e métricas, competindo por atenção.
- Snackbars são úteis, mas operações críticas precisam de confirmação visual na
  própria área afetada.
- Erros devem dizer o que aconteceu e qual ação seguir, nunca `Conflict`,
  `Bad Request` ou texto técnico.

## Modelo Mental Alvo

O admin não pensa em banco de dados. Ele pensa em operação.

Top-level ideal:

1. `Painel`
   - central de comando e pendências do dia.
2. `Viagens do dia`
   - montar, abrir, ajustar e monitorar a operação da data.
3. `Serviços`
   - configurar linhas recorrentes.
4. `Passageiros`
   - cadastrar pessoas, parada, responsáveis e serviços.
5. `Motoristas`
   - convidar, aprovar, cadastrar exceção e ver disponibilidade.
6. `Responsáveis`
   - operar vínculo familiar/permissões.
7. `Conta`
   - empresa, admins, perfil e segurança.

`Rotas` vira subárea de `Serviços`.

`Vínculos` vira subárea contextual:

- dentro de Passageiro: serviços e responsáveis;
- dentro de Motorista: empresa, solicitações e serviços atribuídos;
- dentro de Responsável: passageiros vinculados;
- dentro de Serviço: passageiros elegíveis e motorista padrão.

## Arquitetura de Experiência Alvo

### 1. Painel como comando do dia

Primeira dobra:

- seletor de data;
- status do dia;
- CTA principal:
  - `Montar viagens de hoje`;
  - `Abrir viagens de hoje`;
  - `Resolver pendências`;
- cards pequenos apenas para decisões:
  - viagens montadas;
  - serviços sem motorista;
  - presenças pendentes;
  - solicitações de motorista.

Não mostrar gráficos decorativos nesta fase.

### 2. Viagens do dia

Fluxo principal:

1. escolher data;
2. ver serviços que deveriam operar;
3. montar automaticamente;
4. abrir viagem existente;
5. ajustar motorista/presença/manifesto;
6. criar exceção apenas quando fugir do serviço recorrente.

Estados:

- vazio, mas montável;
- vazio porque nenhum serviço opera na data;
- parcialmente montado;
- montado com pendências;
- em andamento;
- encerrado;
- cancelado.

### 3. Passageiro

Layout alvo:

- lista enxuta à esquerda;
- ficha operacional à direita;
- ações primárias:
  - editar cadastro;
  - editar parada;
  - editar serviços;
  - vincular responsável;
- painel de impacto:
  - "entra nas próximas viagens";
  - "existe viagem hoje ainda não iniciada";
  - "atualizar manifesto agora";
  - "viagem já iniciou, vale para próximas".

### 4. Motorista

Layout alvo:

- pipeline no topo:
  - convites ativos;
  - solicitações pendentes;
  - ativos;
  - pendências de e-mail/perfil;
- lista de motoristas;
- ficha com:
  - perfil;
  - empresa;
  - serviços/viagens atribuídas;
  - status de e-mail;
  - link de convite quando aplicável.

### 5. Serviço

Layout alvo:

- visão geral com status de geração automática;
- passageiros elegíveis;
- motorista padrão;
- calendário/dias;
- destino/rota;
- "gerar viagem da data" quando aplicável.

### 6. Responsável

Layout alvo:

- dados do responsável;
- passageiros ligados;
- menores sob responsabilidade;
- status de conta;
- permissões futuras.

### 7. Conta e empresa

Layout alvo:

- perfil do admin;
- dados da empresa;
- admins da empresa;
- segurança:
  - e-mail verificado;
  - troca de senha;
  - convites administrativos futuros.

## Sistema Visual Admin

Direção:

- manter dark premium;
- aplicar liquid glass com moderação;
- reduzir radius exagerado em áreas densas;
- usar 1 ação primária por contexto;
- remover blocos claros legados;
- manter azul para foco/ação;
- verde só para saudável/concluído;
- amarelo só para atenção real;
- vermelho só para erro destrutivo.

Regras:

- Sem cards dentro de cards.
- Tabelas densas usam superfícies sólidas.
- Glass fica em shell, painéis de resumo e overlays.
- Botões destrutivos nunca usam fundo branco.
- Busca só aparece se for funcional; caso contrário vira escopo do card de
  busca global.
- Ícones precisam corresponder à ação.
- Textos não podem truncar ações principais.

## Cards de Implementação

### ADMIN-S2-0001 — Shell e navegação orientados à operação

Objetivo: remover destinos técnicos da navegação principal e criar a hierarquia
oficial do portal.

Status em 2026-06-17: em execução. O primeiro incremento rebaixou `Vínculos`
e `Rotas-base` para a área `Avançado`, removeu affordances falsas do topbar e
tirou o CTA duplicado `Viagens do dia` da sidebar. A fase só fecha depois da
validação visual com clique real e depois que os fluxos comuns de vínculo
forem absorvidos pelas fichas de entidade nos cards seguintes.

Entregáveis:

- Sidebar com Painel, Viagens do dia, Serviços, Passageiros, Motoristas,
  Responsáveis e Conta.
- `Rotas` movida para dentro de Serviços ou área avançada.
- `Vínculos` removida da navegação principal.
- Topbar sem affordances falsas, ou busca global real com escopo claro.
- Botão duplicado `Viagens do dia` removido da sidebar.

Validação:

- widget test do shell;
- screenshot desktop;
- fluxo de navegação com cliques reais.

### ADMIN-S2-0002 — Painel como central do dia

Objetivo: trocar dashboard genérico por comando operacional da data.

Entregáveis:

- seletor de data;
- status do dia;
- CTA principal contextual;
- pendências acionáveis;
- cards reduzidos e úteis.

Validação:

- empty tenant;
- tenant com serviço sem viagem;
- tenant com viagem montada;
- tenant com solicitações pendentes.

### ADMIN-S2-0003 — Serviços como centro da operação recorrente

Objetivo: fazer o admin configurar a linha sem precisar entender rota/vínculo.

Entregáveis:

- tela de serviço com abas/seções:
  - visão geral;
  - passageiros;
  - motorista;
  - agenda;
  - rota/destino;
- CTA para montar viagem da data quando fizer sentido;
- copy clara sobre geração automática.

Validação:

- criar serviço;
- editar passageiros;
- trocar motorista padrão;
- gerar viagem a partir do serviço.

### ADMIN-S2-0004 — Passageiro como ficha operacional completa

Objetivo: passageiro deixa de ser tabela e vira ficha de operação.

Entregáveis:

- lista enxuta;
- ficha lateral/detalhe;
- serviços liberados dentro do passageiro;
- responsáveis dentro do passageiro;
- data de nascimento/menoridade visível;
- parada/endereço validado como endereço humano, não latitude/longitude;
- impacto nas viagens da data.

Contrato provável:

- `GET/POST` de impacto ao alterar serviços/parada do passageiro.

Validação:

- criar passageiro;
- editar dados;
- vincular serviço;
- vincular responsável;
- verificar impacto na viagem do dia.

### ADMIN-S2-0005 — Viagens do dia sem recriação manual

Status em 2026-06-17: `IN_PROGRESS`. A primeira fatia funcional foi
implementada localmente: o backend agora expõe
`POST /company-admins/viagens/{id}/sincronizar-manifesto` e o portal chama a
ação no detalhe da viagem planejada por serviço recorrente. A validação local
cobriu contrato, tela, snackbar, snapshot administrativo, `flutter analyze`,
488 testes e ratchet de qualidade. Na sequência, o portal também trocou
`Criar manual` por `Criar exceção` e passou a orientar duplicidade com ação
`Abrir viagem` quando a viagem existente está carregada. A fatia seguinte
adicionou preview de impacto antes da sincronização: o portal mostra quantas
paradas ficam no manifesto, quem entra e quem sai antes de aplicar a ação.
Ainda falta validar o cenário ponta a ponta no DEV.

Objetivo: resolver o fluxo reportado pelo usuário: adicionar passageiro novo
não deve exigir apagar viagem e criar outra.

Entregáveis:

- renomear `Viagens` para `Viagens do dia`;
- `Criar manual` vira `Criar exceção`;
- tela mostra serviços da data e viagens já montadas;
- ação `Atualizar manifesto` para viagem não iniciada;
- explicação quando viagem já iniciou;
- mensagens de conflito com ação de abrir viagem existente.

Contrato provável:

- `GET /company-admins/viagens/{id}/manifesto-preview`;
- `POST /company-admins/viagens/{id}/sincronizar-manifesto`.

Validação:

- criar serviço com passageiro A;
- montar viagem;
- criar passageiro B;
- vincular B ao serviço;
- atualizar manifesto sem deletar viagem;
- confirmar B no detalhe da viagem.

### ADMIN-S2-0006 — Motoristas com pipeline profissional

Objetivo: simplificar o fluxo de empresa convidando motorista ou aprovando
solicitação do app.

Entregáveis:

- pipeline de convites, solicitações e ativos;
- copiar link com feedback visual;
- cancelar link;
- aprovar/rejeitar solicitação;
- cadastro manual rebaixado para exceção.

Validação:

- criar link;
- copiar link;
- cancelar link;
- simular solicitação;
- aprovar solicitação;
- confirmar motorista na lista.

### ADMIN-S2-0007 — Responsáveis e relações contextuais

Objetivo: tirar vínculo familiar da tela técnica e colocar no contexto certo.

Entregáveis:

- ficha do responsável com passageiros vinculados;
- ficha do passageiro com responsáveis vinculados;
- criar/remover relação por dentro das fichas;
- estado de menoridade destacado quando aplicável.

Validação:

- criar responsável;
- vincular passageiro;
- remover vínculo;
- garantir que o passageiro menor aparece com responsável esperado.

### ADMIN-S2-0008 — Visual liquid/dark, feedback e acessibilidade

Objetivo: polir o portal inteiro para não parecer junção de telas antigas.

Entregáveis:

- tokens admin alinhados ao dark system;
- glass com legibilidade;
- botões, dialogs, snackbars e estados vazios padronizados;
- remover superfícies claras legadas;
- corrigir ícones incorretos, paginação mockada e textos truncados;
- foco de teclado e contraste mínimos.

Validação:

- screenshots por tela;
- testes de overflow;
- reduced motion quando houver animação;
- auditoria de estados vazios/erro/sucesso.

### ADMIN-S2-0009 — Validação real end-to-end do portal

Objetivo: só considerar a fase entregue quando os fluxos principais forem
executados com cliques reais e backend real.

Roteiro:

1. resetar DEV/staging com backup;
2. criar empresa/admin;
3. confirmar e-mail quando aplicável;
4. criar motorista por link;
5. criar passageiro com parada e data de nascimento;
6. criar responsável e vincular passageiro;
7. criar serviço recorrente;
8. montar viagens do dia;
9. adicionar novo passageiro ao serviço depois da viagem montada;
10. atualizar manifesto sem recriar viagem;
11. abrir app motorista/passageiro quando aplicável;
12. registrar prints e logs.

Validação:

- Playwright web real;
- smoke HTTP backend;
- `flutter analyze`;
- `flutter test`;
- `./scripts/qa_gate_card.sh`;
- QA log com evidências.

### ADMIN-S2-0010 — Avatar real e perfil premium por papel

Objetivo: corrigir o Bad Request de foto de perfil e substituir o contrato
fragil de `fotoPerfilUrl` manual por upload real e validado.

Entregáveis:

- `POST /account/me/avatar` multipart com validação de tipo/tamanho;
- armazenamento/serving com URL estavel;
- app usando endpoint dedicado para trocar foto;
- perfil comum funcionando para passageiro, motorista, responsavel e admin;
- mensagens claras para arquivo invalido, grande demais ou falha de rede;
- smoke por papel confirmando persistencia apos recarregar.

Contratos:

- backend: novo endpoint de avatar e resposta com `fotoPerfilUrl`;
- app: `AccountProfileService` deixa de transformar imagem em data URI como
  caminho principal;
- manter compatibilidade temporaria com `PATCH /account/me` para dados textuais.

Validação:

- `AccountMeServiceTest`;
- `AccountProfileControllerWebMvcTest`;
- `account_profile_service_test`;
- `account_profile_screen_test`;
- smoke HTTP com JPG/PNG/WebP pequenos;
- smoke manual nos quatro papeis.

### ADMIN-S2-0011 — Dados de pessoa completos no portal

Objetivo: fazer listas e fichas administrativas usarem dados reais de perfil,
incluindo avatar, nascimento e menoridade.

Entregáveis:

- summaries/details de passageiro, motorista e responsavel expõem
  `dataNascimento` e `fotoPerfilUrl`;
- passageiro menor de idade aparece como tal;
- responsavel vinculado aparece no contexto do passageiro;
- formularios administrativos mantem prefill correto;
- removidas métricas falsas como documentação baseada apenas em e-mail.

Validação:

- testes backend de DTO/service por entidade;
- testes Flutter dos services admin;
- screenshots de lista e ficha com/sem avatar e nascimento.

### ADMIN-S2-0012 — Criar passageiro com serviços em uma operação segura

Objetivo: evitar estado parcial em que o passageiro é criado, mas os serviços
falham em uma segunda chamada.

Entregáveis:

- contrato transacional para criar passageiro com `servicoIds`;
- validação de empresa/serviços antes de persistir;
- app mostra o serviço já ligado na ficha ao finalizar;
- erro explica exatamente qual serviço impediu a criação.

Validação:

- rejeitar serviço de outra empresa sem criar passageiro;
- criar passageiro com serviço e conferir elegibilidade no serviço;
- smoke portal: criar passageiro + serviço + abrir impacto da viagem do dia.

### ADMIN-S2-0013 — Data coerente e criação de exceção sem surpresa

Objetivo: resolver a confusão do formulário de viagem manual abrindo em data
errada.

Entregáveis:

- `Criar exceção` herda a data selecionada em `Viagens do dia`;
- copy reforça que exceção nao e o caminho normal de manutenção;
- conflito de viagem existente mostra ação `Abrir viagem`.

Contratos:

- nenhum obrigatório;
- aproveitar `viagemId` no erro de conflito se o backend já expuser ou criar
  card backend separado caso não exponha.

Validação:

- widget test selecionando data futura e abrindo formulario;
- smoke Playwright com data diferente de hoje.

### ADMIN-S2-0014 — Manifesto pendente visível antes do detalhe

Objetivo: o admin deve perceber que uma viagem planejada está desatualizada
sem precisar abrir cada detalhe e procurar o botão.

Entregáveis:

- DTO de viagem com `manifestoStatus`, contadores e motivo de bloqueio;
- lista mostra chip discreto: atualizado, pendente, bloqueado ou nao aplicavel;
- detalhe abre preview de entradas/saidas antes de sincronizar;
- após sync, estado volta para atualizado.

Validação:

- backend: serviço recorrente alterado depois da viagem planejada;
- app: lista e detalhe renderizam estados;
- smoke real: adicionar passageiro ao serviço, ver pendência, sincronizar.

### ADMIN-S2-0015 — Montar/Sincronizar dia como ação guiada

Objetivo: transformar `Montar dia` em uma ação que explica o resultado e
sincroniza o que for seguro, sem apagar viagens.

Entregáveis:

- resultado separando criadas, existentes atualizadas, existentes bloqueadas e
  ignoradas;
- opção segura para sincronizar viagens planejadas existentes da data;
- explicação quando uma viagem em andamento não pode mudar;
- feedback persistente na tela, não apenas snackbar.

Validação:

- dia vazio;
- dia parcialmente montado;
- dia montado com mudança de serviço;
- viagem em andamento bloqueada;
- smoke DEV com fixture limpa.

## Sequência de Execução com Subagentes

### Subagente A — Shell, sistema visual e referências

- Implementa shell/navegação.
- Remove affordances falsas.
- Padroniza tokens, botões, surfaces e feedback.

### Subagente B — Backend de impacto e manifesto

- Cria contratos mínimos para impacto e sincronização de manifesto.
- Garante testes MockMvc e services.
- Preview deve ser não mutante e reutilizar a mesma regra de seleção da
  sincronização real.

### Subagente C — Pessoas

- Passageiros, responsáveis e relações contextuais.
- Remove dependência da tela `Vínculos` para fluxos comuns.

### Subagente D — Operação

- Serviços e viagens do dia.
- Montagem, manutenção e exceções.

### Subagente E — QA e validação visual

- Mantém Playwright, screenshots e roteiro real.
- Garante que os fluxos passem em DEV/staging.

## Critérios de Pronto da Fase

- A empresa consegue entender a operação pela navegação sem abrir uma tela
  chamada `Vínculos`.
- O admin consegue criar passageiro, motorista, responsável, serviço e viagem
  sem precisar conhecer conceitos técnicos.
- Adicionar passageiro após montar o dia não obriga deletar e recriar viagem.
- Toda operação crítica responde com status, explicação e próxima ação.
- O portal segue dark/liquid sem blocos claros antigos.
- Busca, ajuda e notificações não aparecem como fake UI.
- Não existem erros crus como `Conflict` ou `Bad Request`.
- Não existem paginações, métricas ou ícones mockados em fluxos principais.
- Fluxos principais passam com testes automatizados, cliques reais e screenshots.
