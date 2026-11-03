# Plano de Telas do Produto — VanRides

Objetivo: transformar a visao do produto em um mapa executavel de experiencias, telas e ordem de implementacao.

Conceito base:
- um backend
- um projeto Flutter
- duas experiencias de uso
  - `Portal Empresa` em Flutter Web
  - `App Operacional` em Flutter Mobile

Isso evita separar cedo demais em dois produtos distintos e, ao mesmo tempo, respeita o fato de que empresa administra e motorista/passageiro/responsavel operam o dia a dia.

## 0.1 Sequencia oficial de execucao

Modelo decidido para o projeto:
1. fechar um bloco coerente no backend
2. espelhar esse bloco no frontend
3. validar ponta a ponta
4. so depois voltar para o proximo bloco no backend

Bloco atual em fechamento:
- `0126` a `0129`
  - o app ja consome `rotaGeografica` provider-based
  - o mapa ja diferencia `Rota de ruas` e `Trajeto aproximado`
  - o app ja entende `rotaGeografica.snapshotOperacional/calculadaEm`, exibindo quando a rota operacional da viagem foi materializada
  - o motorista ja prefere `rotaGeografica.navegacaoRestante` no CTA Maps, evitando reabrir paradas ja atendidas durante a execucao
  - a pendencia restante e QA geografico/manual consolidado em iOS/Android

Bloco seguinte prioritario:
- `0132` a `0139`
  - experiencia `map-first` completa da corrida
  - mapa full-screen como ambiente principal
  - motorista dividido em:
    - `planejamento da corrida`
    - `corrida em andamento`
  - passageiro e responsavel com jornada full-screen, `minha parada` e progresso da fila
  - notificacoes operacionais ligadas ao estado da viagem

Conceito importante:
- a fase anterior provou que o VanRides ja consegue mostrar rota real no mapa
- a fase seguinte prova se o produto consegue **guiar a corrida** sem virar ainda um navegador completo
- `turn-by-turn` segue como avaliacao posterior, nao como prerequisito desta fase

## 0.2 Estado atual do plano

Ja concluido:
- `0200` arquitetura de experiencias
- `0201` portal empresa web: login + shell + dashboard
- `0202` gestao de motoristas
- `0203` gestao de passageiros
- `0204` gestao de responsaveis
- `0205` tela de vinculos
- `0206` guardian mobile: fundacao de sessao e navegacao
- `0207` guardian mobile: home + lista de passageiros vinculados
- `0208` guardian mobile: presencas por vinculo
- `0209` guardian mobile: localizacao e status operacional
- `0210` testes/QA do portal empresa web MVP
- `0211` testes/QA do guardian mobile
- `0108` home do passageiro em modo `map-first`
- `0110` home operacional do motorista com rota do dia

Em fechamento/manual:
- `0127` QA do motorista com roteirizacao real
- `0129` QA geografico consolidado da jornada atual

Proxima fase prioritaria:
- `0132` a `0139`
  - shell `map-first` full-screen comum entre papeis
  - motorista com tela de planejamento da corrida
  - motorista com tela de corrida em andamento
  - passageiro e responsavel com `minha parada` e fila em destaque
  - realtime operacional vinculado ao lifecycle da viagem
  - notificacoes operacionais

Conceito importante:
- o portal empresa deixou de ser mock e virou produto real
- o guardian mobile e a operacao MVP de passageiro/motorista tambem ja existem como produto validavel
- o bloco `0114` a `0119` ja saiu do campo de ideia e virou implementacao concreta
- o bloco `0126` a `0129` transformou a linha reta em rota real provider-based
- o QA manual mostrou uma nova diferenca importante:
  - o mapa ja parece rota real, mas a experiencia ainda esta mais proxima de um dashboard do que de um app de mobilidade
  - por isso, a fase seguinte precisa separar **planejamento** de **execucao**

## 1. Experiencias do sistema

## 1.0 Direcao de UX da proxima fase

Conceito importante:
- a fase anterior provou que o app ja consegue desenhar rota real no mapa
- a proxima fase nao e sobre "mais backend"; e sobre **usar melhor o mapa como ambiente**
- nesta primeira implementacao vamos usar o `vanrides-figma-v2` como referencia visual da fase operacional, e nao como design master final do produto inteiro

Modelo de UX decidido:

1. motorista em `planejamento da corrida`
   - ve todos os pins
   - ve a rota completa
   - entende a jornada inteira
2. motorista em `corrida em andamento`
   - ve apenas o proximo passageiro em foco
   - reduz distracao
   - opera a corrida parada por parada
3. passageiro e responsavel em acompanhamento full-screen
   - veem a rota inteira
   - veem `minha parada`
   - entendem quantas paradas faltam

Decisao explicita:
- `turn-by-turn` nativo nao faz parte desta fase
- primeiro vamos construir um app que organiza muito bem a corrida
- depois decidimos se ele tambem deve virar um navegador completo

Escolhas visuais e de produto adotadas agora:
- login, motorista, passageiro e responsavel passam a seguir a linguagem visual da `V2`
- mapa vira a camada principal da experiencia
- motorista deixa de operar um "dashboard" e passa a operar uma cena de corrida
- detalhes secundarios migram para bottom sheet/paineis compactos

O que fica deliberadamente para o futuro:
- design system final completo do VanRides
- parity final de tabs/navegacao entre todos os papeis
- refinamento completo de chat, presenca e portal empresa usando a mesma linguagem
- estrategia final para labels persistentes, fotos reais e turn-by-turn

### 1.1 Portal Empresa (Web-first)

Usuario principal:
- `COMPANY_ADMIN`

Objetivo:
- cadastrar
- vincular
- planejar
- acompanhar operacao

Caracteristicas:
- layout de dashboard
- tabelas/listas
- formularios administrativos
- uso prioritario em desktop/web

### 1.2 App Operacional (Mobile-first)

Usuarios principais:
- `DRIVER`
- `PASSENGER`
- `GUARDIAN`

Objetivo:
- operar viagem
- acompanhar presenca
- acompanhar localizacao
- receber acoes e notificacoes do dia

Caracteristicas:
- uso frequente em celular
- acoes rapidas
- estado operacional em tempo real

## 2. Regra de negocio para criacao de dados

Ordem recomendada:
1. empresa existe
2. empresa cria motorista
3. empresa cria passageiro
4. empresa libera o passageiro em um ou mais servicos recorrentes
5. empresa define motorista inicial apenas quando fizer sentido operacional
6. se necessario:
   - marcar passageiro como menor de idade
   - criar responsavel
   - vincular responsavel ao passageiro
7. depois o responsavel acompanha o passageiro no app

Conceito importante:
- o responsavel nasce em relacao a um passageiro
- por isso, para o produto ficar claro, o passageiro costuma existir antes do vinculo do responsavel
- o passageiro pode existir sem motorista fixo inicial; o direito de uso fica nos servicos recorrentes e o motorista operacional fica na viagem do dia

## 3. Mapa de telas — Portal Empresa (Flutter Web)

### 3.1 Autenticacao e shell

#### Tela: `AdminLoginScreen`
Objetivo:
- autenticar `COMPANY_ADMIN`

Funcoes:
- login
- tratamento de erro
- redirecionamento ao dashboard

#### Tela: `AdminShell`
Objetivo:
- fornecer estrutura comum do portal

Componentes:
- menu lateral
- cabecalho
- area de conteudo
- logout

### 3.2 Dashboard

#### Tela: `AdminDashboardScreen`
Objetivo:
- visao geral da empresa

Blocos:
- total de motoristas
- total de passageiros
- total de responsaveis
- presencas do dia
- viagens ativas
- alertas

Acoes rapidas:
- criar motorista
- criar passageiro
- criar responsavel
- abrir tela de vinculos

### 3.3 Gestao de motoristas

#### Tela: `MotoristasListScreen`
Objetivo:
- listar todos os motoristas da empresa

Funcoes:
- busca
- filtros
- abrir detalhe
- abrir criacao

#### Tela: `MotoristaFormScreen`
Objetivo:
- criar e editar motorista

Campos iniciais:
- nome
- email
- telefone
- senha

#### Tela: `MotoristaDetalheScreen`
Objetivo:
- centralizar informacoes do motorista

Blocos:
- dados cadastrais
- passageiros vinculados
- rotas vinculadas
- situacao operacional

### 3.4 Gestao de passageiros

#### Tela: `PassageirosListScreen`
Objetivo:
- listar passageiros da empresa

Funcoes:
- busca
- filtros
- abrir detalhe
- abrir criacao

#### Tela: `PassageiroFormScreen`
Objetivo:
- criar e editar passageiro

Campos iniciais:
- dados pessoais
- endereco
- motorista inicial opcional, apenas quando fizer sentido operacional
- servicos recorrentes liberados ficam na tela de vinculos/servicos, nao como pre-requisito do cadastro
- `menorDeIdade`

#### Tela: `PassageiroDetalheScreen`
Objetivo:
- mostrar tudo sobre um passageiro

Blocos:
- dados cadastrais
- motorista vinculado quando houver legado/atalho operacional
- servicos recorrentes liberados e status de direito de uso
- responsaveis vinculados
- presencas recentes
- status operacional

### 3.5 Gestao de responsaveis

#### Tela: `ResponsaveisListScreen`
Objetivo:
- listar responsaveis da empresa

#### Tela: `ResponsavelFormScreen`
Objetivo:
- criar e editar responsavel

Campos iniciais:
- nome
- email
- telefone

#### Tela: `ResponsavelDetalheScreen`
Objetivo:
- ver vinculos e dados do responsavel

Blocos:
- dados cadastrais
- passageiros vinculados
- acoes de vinculo

### 3.6 Gestao de vinculos

#### Tela: `VinculosScreen`
Objetivo:
- administrar os relacionamentos principais do dominio

Funcoes:
- vincular passageiro -> motorista
- desvincular passageiro -> motorista
- vincular responsavel -> passageiro
- desvincular responsavel -> passageiro
- revisar solicitacoes de acesso do passageiro
- aprovar passageiro liberando um ou mais servicos recorrentes
- revisar e substituir servicos liberados por passageiro

Conceito importante:
- esta tela e um dos coracoes do produto
- ela alimenta o resto da operacao
- vinculo com empresa e direito de uso do servico recorrente sao decisoes relacionadas, mas nao sao a mesma coisa
- a empresa consegue corrigir direito de uso apos a aprovacao inicial sem recriar passageiro ou servico
- passageiro vinculado sem motorista fixo deve entrar no app e marcar presenca por servico; motorista e contexto de mapa vêm da viagem operacional do dia

### 3.7 Rotas e operacao

#### Tela: `RotasListScreen`
Objetivo:
- listar rotas salvas da empresa

Funcoes:
- busca por nome
- filtro por motorista
- leitura de dias de operacao
- leitura de quantidade de paradas
- leitura do status de geracao automatica
- atalho para criar nova rota
- atalho para abrir detalhe/editar

#### Tela: `RotaFormScreen`
Objetivo:
- criar e editar rota

Campos obrigatorios esperados:
- nome da rota
- motorista responsavel
- passageiros da rota em ordem
- dias de operacao
- geracao automatica ativa/inativa

Campos que passam a ser recomendados para o produto:
- destino final da rota
  - nome de exibicao
  - endereco
  - latitude
  - longitude

Conceito importante:
- o backend ja persiste `destinoFinalNome`, `destinoFinalEndereco`, `destinoFinalLatitude` e `destinoFinalLongitude` na rota-base
- quando uma viagem e provisionada, esse ponto entra no snapshot operacional da viagem
- se o destino final tiver coordenadas, a rota calculada usa esse ponto como destino; sem coordenadas, a roteirizacao continua usando a ultima parada como fallback

#### Tela: `RotaDetalheScreen`
Objetivo:
- ver paradas, passageiros e motorista

Blocos:
- resumo da rota
- motorista vinculado
- lista ordenada de paradas
- destino final configurado
- dias de operacao
- status de geracao automatica
- acoes de editar e criar viagem

#### Tela: `ViagensListScreen`
Objetivo:
- listar viagens criadas para a empresa

Funcoes:
- filtro por data
- leitura de rota-base vinculada
- leitura de motorista
- leitura de status da viagem
- leitura de quantidade de paradas
- atalho para criar viagem manual

#### Tela: `ViagemCreateScreen`
Objetivo:
- criar a viagem do dia a partir de uma rota-base

Campos iniciais:
- rota-base
- data de operacao

Evolucao futura possivel:
- leitura do destino final herdado da rota-base
- resumo das paradas da viagem antes da confirmacao

#### Tela: `OperacaoDoDiaScreen`
Objetivo:
- acompanhar o que esta acontecendo agora

Blocos futuros:
- viagens do dia
- vans em andamento
- passageiros presentes
- atrasos/alertas

Leitura atual do produto:
- esta tela ainda e um placeholder conceitual
- o gap real mais urgente vem antes dela: criar e operar `rota-base` e `viagem`
- por isso, a fase seguinte do portal deve priorizar `RotasListScreen`, `RotaFormScreen`, `RotaDetalheScreen`, `ViagensListScreen` e `ViagemCreateScreen`

## 4. Mapa de telas — App Operacional Mobile

### 4.1 Motorista

#### Tela atual: `HomeMotoristaScreen`
- painel operacional do dia
- mostra rota do dia, resumo de confirmados/pendentes e lista de paradas
- hoje ainda funciona como ponte tecnica: prova ETA/reorder, mas ainda nao entrega navegacao pronta nem rota geografica real

#### Telas relacionadas ja existentes
- `CompartilharLocalizacaoScreen`
- `PresencasHojeScreen`

Evolucoes futuras:
- viagem do dia gerada automaticamente a partir da operacao recorrente
- rota geografica pronta para seguir
- CTA "abrir no Maps" / "seguir navegacao"
- progresso de parada e contexto de qual trecho esta sendo seguido

### 4.2 Passageiro

#### Tela atual: `HomePassageiroScreen`
- shell operacional `map-first`
- abas `Viagem`, `Presenca` e `Conta`
- passageiro com empresa vinculada entra mesmo sem motorista fixo inicial
- quando ha viagem operacional do dia, a tela usa o motorista publicado na viagem; quando nao ha, ainda permite acessar presenca por servico

#### Telas relacionadas ja existentes
- `ConfirmarPresencaScreen`
- `VerLocalizacaoScreen`

Evolucoes futuras:
- minha parada destacada e fila de paradas antes/depois de mim
- posicao do motorista sobre a rota real
- notificacoes de eventos
- bateria E2E completa apos estabilizacao da operacao guiada por rota real

### 4.3 Responsavel (`GUARDIAN`)

#### Tela: `GuardianHomeScreen`
Objetivo:
- entrada principal do responsavel

Blocos:
- passageiros vinculados
- status rapido
- acessos de localizacao
- acessos de presenca
- notificacoes recentes

#### Tela: `GuardianPassageirosScreen`
Objetivo:
- listar os passageiros vinculados ao responsavel

#### Tela: `GuardianPassageiroDetalheScreen`
Objetivo:
- centralizar informacoes de um passageiro vinculado

Blocos:
- dados basicos
- motorista vinculado quando houver legado/atalho operacional
- servicos e viagens operacionais do passageiro vinculado
- status operacional
- atalhos para presenca e localizacao

#### Tela: `GuardianPresencasScreen`
Objetivo:
- confirmar/remover presencas do passageiro vinculado

#### Tela: `GuardianLocalizacaoScreen`
Objetivo:
- acompanhar em tempo real a localizacao da viagem operacional do passageiro vinculado
- reutilizar a mesma narrativa operacional do tracking `map-first` do passageiro

#### Tela futura: `GuardianNotificationsScreen`
Objetivo:
- reunir alertas e eventos importantes

Eventos desejados:
- foi pego
- chegou na escola
- saiu da escola
- esta proximo

## 5. O que o backend ja suporta para isso

Hoje o backend ja suporta:
- multi-tenant
- RBAC por papel
- contexto de empresa no auth
- `GUARDIAN` por vinculo explicito e login real
- presencas por vinculo
- localizacao/historico por vinculo
- viagens MVP:
  - `RotaBase`
  - `Viagem` do dia
  - leitura operacional para motorista
  - status/viagem atual para passageiro e responsavel
  - reorder da ordem operacional
  - `rotaVersao` e ETA `v1` heuristico

Hoje o backend/app ja suportam:
- CRUD principal da empresa via portal web consumivel no app
- geracao automatica da viagem do dia a partir de servicos recorrentes e presencas
- rota geografica real com polyline, legs e payload de navegacao externa
- realtime por viagem com fila de paradas e progresso contextual

Ainda falta fechar:
- QA manual consolidado iOS/Android dos fluxos ponta a ponta
- refinamento da experiencia `map-first` de execucao
- notificacoes e comunicacao operacional

## 6. Ordem recomendada de implementacao

### Fase FE-1 — Fundacao de navegacao por experiencia
Objetivo:
- preparar o app Flutter para suportar web admin e mobile operacional no mesmo projeto

Entregas:
- shell web/admin
- roteamento por papel/experiencia
- sessao centralizada

### Fase FE-2 — Portal Empresa MVP
Objetivo:
- permitir alimentar e gerir o dominio

Entregas:
- login admin
- dashboard
- motoristas
- passageiros
- responsaveis
- vinculos

### Fase FE-3 — Guardian Mobile MVP
Objetivo:
- tornar visivel no app tudo o que o backend ja suporta para responsavel

Entregas:
- home do guardian
- lista de passageiros vinculados
- detalhe do passageiro
- presencas
- localizacao

Status atual:
- concluida e validada

### Fase FE-4 — Rotas e operacao
Objetivo:
- conectar o plano administrativo com a operacao do dia

Entregas:
- rotas
- viagem do dia
- status do passageiro
- eventos e notificacoes

Status atual:
- MVP da operacao do dia entregue para passageiro, responsavel e motorista
- bloco em fechamento manual de QA

### Fase FE-5 — Rotas dinamicas + ETA
Objetivo:
- permitir ajuste operacional da rota no dia e refletir a mudanca para os demais papeis

Entregas:
- reorder de paradas pelo motorista
- persistencia da versao da rota da viagem
- ETA inicial e reflexo para passageiro/responsavel
- esta fase passou a ser entendida como fundacao tecnica, nao como destino final de UX

### Fase FE-6 — Operacao guiada por rota real
Objetivo:
- aproximar a experiencia do produto do modelo de apps de mobilidade, em que a rota do dia ja vem pronta para seguir e o passageiro enxerga claramente o seu lugar na jornada

Entregas:
- viagem do dia automatica
- rota geografica real desenhada no mapa
- CTA de navegacao externa para o motorista
- fila de paradas e "minha parada" para passageiro/responsavel
- realtime operacional por viagem
- bateria E2E completa apos estabilizacao deste bloco

Estado atual:
- implementacao do app concluida para viagem automatica, `rotaGeografica`, navegacao externa e realtime por viagem
- smoke manual consolidado e bateria E2E completa ainda pendentes para fechamento oficial do bloco

### Fase FE-7 — Roteirizacao real validavel
Objetivo:
- fazer o mapa do app se parecer com a navegacao que o motorista realmente vai seguir e com a leitura geografica que passageiro/responsavel esperam

Entregas:
- rota de ruas provider-based no backend
- desenho de rota coerente no mapa do motorista
- destaque geografico de `minha parada` e paradas anteriores para passageiro/responsavel
- matriz de fixtures geograficas controladas para QA humano
- comparacao entre mapa do app e handoff de navegacao

Estado atual:
- implementacao do app concluida para consumo de `rotaGeografica` provider-based, badges `Rota de ruas` / `Trajeto aproximado`, fila de paradas, `minha parada` e resumo de navegacao
- QA automatizado local concluido
- fechamento oficial ainda depende de validacao geografica manual iOS/Android com fixture controlada e estabilizacao da bateria completa de integracao
- a rota-base ja permite destino final explicito; a leitura operacional atual separa destino planejado da rota-base e rota calculada/snapshot da viagem

### Fase FE-8 — Experiencia `map-first` completa da corrida
Objetivo:
- fazer o app se comportar como um produto de mobilidade operacional maduro, com mapa full-screen, modo de planejamento e modo de corrida

Entregas:
- shell `map-first` full-screen para motorista, passageiro e responsavel
- tela `Planejamento da corrida` do motorista
- tela `Corrida em andamento` do motorista
- apenas o proximo passageiro destacado na fase de execucao
- `minha parada` e quantidade de paradas anteriores para passageiro/responsavel
- realtime operacional vinculado ao estado da viagem
- notificacoes operacionais como `voce e o proximo` e `motorista chegando`

Conceito importante:
- esta fase nao e `turn-by-turn`
- ela entrega um app que **explica e conduz a corrida**
- o navegador completo continua como decisao futura

Estado desejado ao fim da fase:
- o motorista entende a rota inteira antes de sair
- depois, durante a corrida, ve apenas o que importa agora
- passageiro e responsavel entendem com clareza onde estao dentro da jornada

Estado atual:
- implementacao local iniciada para `0132` a `0136`, com shell full-screen compartilhado entre motorista, passageiro e responsavel
- motorista agora alterna entre leitura de `planejamento` e `corrida em andamento` no proprio app, com acoes explicitas para iniciar corrida, concluir parada, pular parada e encerrar corrida
- passageiro e responsavel passaram a ler a viagem em uma cena `map-first` full-screen, com `minha parada`, ETA e fila no bottom sheet operacional
- login e shell visual principal passaram a seguir a primeira implementacao do `vanrides-figma-v2`, reduzindo densidade visual e aproximando o app da direcao de produto desejada
- a segunda rodada de port do `vanrides-figma-v2` ja colocou no Flutter as telas `DriverPlanning`, `DriverActive`, `DriverMapFullscreen`, `DriverStops`, `PassengerHome`, `PassengerTripDetails` e `GuardianTracking`
- os componentes compartilhados do shell novo agora existem no app (`map shell`, `header`, `bottom sheet`, CTA principal e estados vazios/erro`), servindo como base real para as proximas telas
- os frames reais do Figma do motorista passaram a ser usados via MCP como fonte de correcao visual, o que levou a uma nova rodada de limpeza de overlays e ao retorno do footer persistente com aba de conta/logout no shell do motorista
- `flutter analyze` e `flutter test` seguem passando; o gate longo do app continua dependente de uma estabilizacao futura da suite de integracao legada

Escolhas assumidas nesta fase:
- usar o `vanrides-figma-v2` como referencia visual oficial apenas para o nucleo operacional mobile
- portar primeiro as telas de corrida e acompanhamento, nao o produto inteiro
- manter chat, tabs finais por papel, portal empresa e qualquer decisao de `turn-by-turn` para fases futuras
- preservar o produto real quando ele divergir do draft visual: por isso o shell do motorista manteve footer persistente e aba de conta, mesmo quando o frame principal do Figma parecia mais "limpo" sem essa navegacao

## 7. Decisao de produto recomendada

Decisao recomendada para seguirmos agora:
- `Portal Empresa` em Flutter Web
- `Guardian` no app mobile
- mesmo backend
- mesmo projeto Flutter

Isso nos da:
- organizacao de produto
- menos duplicacao tecnica
- caminho claro para validacao antes de producao

## 8. Proximo passo de implementacao

A proxima implementacao recomendada e:
1. fechar o QA manual consolidado do bloco `0126` a `0129`
2. abrir o bloco `0132` a `0139` de experiencia `map-first` completa da corrida
3. so depois reavaliar:
   - notificacoes push gerais
   - chat/suporte
   - navegacao turn-by-turn

Conceito importante:
- o app ja provou a fundacao tecnica da rota real
- o problema agora nao e mais "desenhar uma linha melhor"; e "organizar a corrida de um jeito muito mais facil de usar"
