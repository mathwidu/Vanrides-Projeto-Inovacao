# VanRides UX/UI Complete Improvement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar uma experiencia premium, clara e operacionalmente confiavel para todos os papeis do VanRides, cobrindo portal admin e app mobile, com fluxos simples, estados compreensiveis, UI dark/liquid consistente, acessibilidade, validacao visual e smokes reais.

**Architecture:** O plano separa identidade, empresa, vinculo e operacao do dia. O portal admin deve ser uma ferramenta de preparacao/manutencao operacional; o app mobile deve ser uma experiencia map-first para motorista, passageiro e responsavel. Toda melhoria deve nascer de fluxo, nao de tela isolada.

**Tech Stack:** Flutter app/web, Spring Boot backend, Google Maps/Places/Directions, WebSocket/STOMP, GitHub Actions, Docker Swarm DEV, Playwright para portal web, Flutter widget/integration tests para mobile.

---

## 1. Contexto e evidencia analisada

### Repositorios
- App Flutter: `~/projetos/pessoal/vanrides/vanrides-app`
- Backend Spring Boot: `~/projetos/pessoal/vanrides/vanrides-backend`

### Fontes locais lidas
- `vanrides-app/backlog/Goal-Backlog-Triage-2026-06-18.md`
- `vanrides-app/backlog/admin-portal-ux-audit-2026-06-18.md`
- `vanrides-app/backlog/Product-Screens-Plan.md`
- `vanrides-app/backlog/Flutter-Cards.md`
- `vanrides-app/backlog/Driver-Company-Link-Hybrid-Flow.md`
- `vanrides-app/backlog/Passenger-Company-Link-Hybrid-Flow.md`
- `vanrides-backend/backlog/Driver-Company-Link-Hybrid-Flow.md`
- `vanrides-backend/backlog/Passenger-Company-Link-Hybrid-Flow.md`
- `vanrides-backend/backlog/API-Contract-Matrix.md`
- Principais telas e services em `lib/features/**`
- Principais controllers/services/dtos em `src/main/java/com/vanrides/vanrides/**`

### Referencias externas de design usadas como regua
- Apple Human Interface Guidelines: sheets, layout, alerts e toolbars.
- Material Design 3: components, lists, search, tabs, dialogs e selection.
- Nielsen Norman Group: dashboards acionaveis, heuristicas para aplicacoes complexas, indicadores/validacoes/notificacoes.

Links:
- Apple HIG Sheets: https://developer.apple.com/design/human-interface-guidelines/sheets
- Apple HIG Layout: https://developer.apple.com/design/human-interface-guidelines/layout
- Apple HIG Toolbars: https://developer.apple.com/design/human-interface-guidelines/toolbars
- Apple HIG Alerts: https://developer.apple.com/design/human-interface-guidelines/alerts
- Material 3 Components: https://m3.material.io/components
- Material 3 Lists: https://m3.material.io/components/lists/overview
- Material 3 Search: https://m3.material.io/components/search/overview
- Material 3 Tabs: https://m3.material.io/components/tabs
- NN/g dashboards: https://www.nngroup.com/articles/dashboards-preattentive/
- NN/g complex apps heuristics: https://www.nngroup.com/articles/usability-heuristics-complex-applications/
- NN/g indicators/validations/notifications: https://www.nngroup.com/articles/indicators-validations-notifications/

---

## 2. Diagnostico executivo

O VanRides ja tem bastante base funcional: onboarding hibrido, perfis, avatar, portal admin, rotas, servicos recorrentes, viagens, tracking, presenca, responsavel, motorista, suporte/tickets e varios gates de QA. O problema principal agora nao e "falta tela"; e coesao de produto.

Hoje o usuario ainda pode sentir:
- que algumas telas sao bonitas isoladamente, mas nao formam um fluxo unico;
- que o portal admin tem muitos conceitos tecnicos expostos;
- que "vinculo", "servico", "viagem", "rota", "manifesto" e "presenca" ainda podem competir entre si;
- que algumas acoes exigem conhecimento interno do sistema;
- que estados vazios, bloqueios, pendencias e erros ainda nao sao sempre orientados a acao;
- que app mobile e portal admin ainda nao parecem 100% do mesmo produto;
- que algumas validacoes ainda dependem de evidencia manual, provider externo ou device fisico.

### Regra de produto para esta fase

Toda tela deve responder uma pergunta principal.

- Portal admin: "o que a empresa precisa preparar, corrigir ou acompanhar agora?"
- Passageiro: "minha van vem? eu preciso decidir presenca? onde esta minha parada?"
- Motorista: "qual viagem devo dirigir agora? o que falta antes de sair? qual a proxima acao?"
- Responsavel: "quem acompanho? a pessoa esta segura? preciso tomar alguma acao?"

Se uma informacao nao responde uma dessas perguntas, ela deve ir para detalhe, historico, menu secundario ou suporte.

---

## 3. Principios obrigatorios de UX/UI

### 3.1 Sistema visual
- Tema base: dark premium, com mapa e superficies translucidas como ambiente principal.
- Material/liquid: usar vidro apenas quando ajuda a preservar contexto, especialmente sobre mapa. Nao usar glass como enfeite em todas as telas.
- Paleta: neutros azulados escuros, azul de tracking, verde de sucesso/presenca, amber para atencao, vermelho apenas para erro real.
- Sem telas brancas soltas dentro de fluxos dark.
- Sem texto truncado em CTA principal, chips de viagem, status ou labels de entidade.
- O estado selecionado deve ser claro sem depender apenas de cor.

### 3.2 Layout e densidade
- Portal admin pode ser denso, mas deve ter hierarchy: painel principal, lista, ficha, acoes.
- Mobile deve ser map-first quando o contexto for viagem/localizacao.
- Bottom sheets mobile devem ter detents claros: mini, medio e expandido.
- Conteudo extra deve aparecer por continuidade vertical, nao "surgir" trocando o topo da sheet.
- Evitar carrossel para informacao essencial. Se ha mais de dois itens, preferir lista compacta ou selecao segmentada.

### 3.3 Linguagem
- Trocar termos tecnicos para linguagem operacional.
- "Vinculo" vira "Acesso", "Empresa", "Responsaveis", "Servicos liberados" ou "Equipe".
- "Manifesto" vira "Lista da viagem" ou "Paradas da viagem".
- "Conflito" vira "Ja existe uma viagem para este servico e data".
- "Sem companyId" vira "Sua conta ainda nao esta ligada a uma empresa".

### 3.4 Estados
Cada fluxo deve ter:
- carregando com skeleton ou bloco contextual;
- vazio com proxima acao;
- erro com linguagem humana e recuperacao;
- sucesso com confirmacao curta;
- bloqueado com motivo e caminho;
- pendente com expectativa clara;
- offline/retry onde houver rede em tempo real;
- permissao negada com CTA para Ajustes quando for mobile.

### 3.5 Acessibilidade
- Todos os CTAs icon-only precisam de tooltip/semantic label.
- Contrast ratio deve ser revisado para textos em glass.
- Tamanhos de toque minimos em mobile: 44x44.
- VoiceOver deve explicar: horario, sentido, destino, status e proxima acao.
- Animacoes devem respeitar reduced motion.
- Foco no portal web deve ser navegavel por teclado.

### 3.6 Movimento
- Transicoes principais: 150-250ms.
- Bottom sheets: fisica limpa, sem flick, sem pulo de conteudo.
- Expandir/recolher sheet deve manter a mesma primeira informacao fixa.
- Acao concluida deve ter feedback de estado, nao animacao decorativa.

---

## 4. Modelo mental canonico

### 4.1 Entidades
- Conta: identidade da pessoa.
- Empresa: tenant e governanca.
- Vinculo: autorizacao da pessoa dentro da empresa.
- Perfil: dados pessoais e completude.
- Servico recorrente: direito/linha operacional.
- Rota: desenho planejado.
- Viagem do dia: execucao operacional.
- Presenca: decisao do passageiro para um horario.
- Parada: ponto dentro da viagem.
- Tracking: estado vivo da viagem.
- Suporte: comunicacao estruturada quando algo deu errado.

### 4.2 Ordem de criacao ideal para empresa
1. Criar empresa/admin.
2. Criar ou convidar motoristas.
3. Criar passageiros.
4. Definir parada/endereco do passageiro com mapa.
5. Liberar passageiro em servicos recorrentes.
6. Adicionar responsavel quando passageiro for menor ou quando necessario.
7. Criar/ajustar servicos recorrentes.
8. Gerar viagens do dia automaticamente.
9. Corrigir excecoes no painel de viagens.
10. Acompanhar execucao.

### 4.3 Ordem ideal para usuario final
1. Criar conta.
2. Confirmar e-mail.
3. Entrar por convite ou buscar empresa.
4. Completar perfil minimo.
5. Entender seu status operacional.
6. Usar viagem/presenca/localizacao.

---

## 5. Mapa de melhorias por superficie

## 5.1 Portal Admin

### Problemas principais
- Ainda existe excesso de conceitos internos.
- Acoes de vinculo ainda podem parecer separadas das entidades.
- Viagem manual/automatica/manutencao ainda exige entendimento do modelo.
- Dashboard pode mostrar metricas, mas precisa priorizar decisao acionavel.
- Pessoas e servicos precisam explicar "o que falta para operar".

### Direcao
O portal deve parecer uma central operacional. A empresa nao deve "configurar banco"; ela deve preparar passageiros, equipe, servicos e dia operacional.

### Arquivos principais
- `lib/features/admin/presentation/admin_shell_screen.dart`
- `lib/features/admin/presentation/widgets/admin_portal_sidebar.dart`
- `lib/features/admin/presentation/widgets/admin_dashboard_screen.dart`
- `lib/features/admin/presentation/admin_viagens_screen.dart`
- `lib/features/admin/presentation/widgets/admin_viagens_detail_view.dart`
- `lib/features/admin/presentation/widgets/admin_viagens_day_status_panel.dart`
- `lib/features/admin/presentation/widgets/admin_viagens_maintenance_guide.dart`
- `lib/features/admin/presentation/admin_passageiros_screen.dart`
- `lib/features/admin/presentation/admin_motoristas_screen.dart`
- `lib/features/admin/presentation/admin_responsaveis_screen.dart`
- `lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart`
- `lib/features/admin/presentation/admin_rotas_screen.dart`
- `lib/features/admin/presentation/admin_vinculos_screen.dart`
- `lib/features/admin/data/*.dart`

---

## 5.2 App Passageiro

### Problemas principais
- Tela de viagem melhorou, mas precisa ser a linguagem-base para presenca e conta.
- Presenca precisa ficar ainda mais obvia: "o que decido agora?".
- Usuario precisa entender estados sem viagem, sem sinal, aguardando inicio, van chegando, parada anterior, minha parada.
- O mapa deve continuar util mesmo com sheet/footers.

### Direcao
Passageiro deve sentir um "Uber de vans": onde esta minha van, quanto falta, minha parada, o que preciso fazer hoje.

### Arquivos principais
- `lib/features/tracking/presentation/home_passageiro_screen.dart`
- `lib/features/tracking/presentation/map_first_trip_tracking_view.dart`
- `lib/features/tracking/presentation/widgets/passenger_map_footer_sheet_scaffold.dart`
- `lib/features/tracking/presentation/widgets/passenger_trip_*.dart`
- `lib/features/tracking/presentation/widgets/compact_trip_switcher.dart`
- `lib/features/tracking/presentation/widgets/vanrides_map_*.dart`
- `lib/features/service_presence/presentation/confirmar_presenca_screen.dart`
- `lib/features/service_presence/presentation/confirmar_presenca_widgets.dart`
- `lib/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart`

---

## 5.3 App Motorista

### Problemas principais
- Home do motorista ainda e risco de virar painel de botoes se muitas funcoes ficarem expostas.
- Planejamento, inicio, rota, presencas e excecoes precisam virar uma sequencia simples.
- Motorista precisa saber o que fazer antes de sair, durante a viagem e ao chegar em cada parada.
- Handoff para Maps deve ser claro, mas o app tambem deve manter o contexto operacional.

### Direcao
Motorista deve operar em modo "proxima acao". O app nao deve pedir que ele entenda toda a arquitetura de viagem.

### Arquivos principais
- `lib/features/driver_trip/presentation/home_motorista_screen.dart`
- `lib/features/driver_trip/presentation/widgets/driver_home_trip_tab.dart`
- `lib/features/driver_trip/presentation/widgets/driver_home_trip_sheet_widgets.dart`
- `lib/features/driver_trip/presentation/widgets/driver_trip_action_widgets.dart`
- `lib/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart`
- `lib/features/driver_trip/presentation/driver_stops_screen.dart`
- `lib/features/driver_trip/presentation/driver_map_fullscreen_screen.dart`
- `lib/features/driver_trip/data/driver_trip_service.dart`

---

## 5.4 App Responsavel

### Problemas principais
- Responsavel e funcional, mas precisa identidade de produto propria.
- A experiencia nao deve parecer "passageiro adaptado".
- O responsavel precisa ver pessoas acompanhadas, status, presenca, localizacao e alertas.

### Direcao
Responsavel deve ser uma camada de seguranca e acompanhamento: menos operacao, mais confianca.

### Arquivos principais
- `lib/features/guardian/presentation/guardian_shell_screen.dart`
- `lib/features/guardian/presentation/guardian_home_screen.dart`
- `lib/features/guardian/presentation/guardian_localizacao_screen.dart`
- `lib/features/guardian/presentation/guardian_presencas_screen.dart`
- `lib/features/guardian/presentation/guardian_passageiro_detail_screen.dart`
- `lib/features/guardian/presentation/widgets/guardian_*.dart`
- `lib/features/guardian/data/*.dart`

---

## 5.5 Perfil e conta

### Problemas principais
- Perfil precisa virar area util para todos os papeis.
- Foto, nascimento, telefone, e-mail verificado, senha, empresa e permissao precisam ter edicao clara.
- Admin precisa entender perfil de pessoas sem depender de tela tecnica.

### Direcao
Conta deve ser "minha identidade e meus acessos"; ficha admin deve ser "esta pessoa esta pronta para operar?".

### Arquivos principais
- `lib/features/account/presentation/account_profile_screen.dart`
- `lib/features/account/presentation/edit_account_profile_sheet.dart`
- `lib/features/account/presentation/account_profile_completion_card.dart`
- `lib/features/account/data/account_profile_service.dart`
- Backend: `AccountController`, `AccountMeService`, `AccountAvatarService`

---

## 5.6 Onboarding, convites e e-mail

### Problemas principais
- Convite, solicitacao, e-mail pendente e upgrade de sessao sao fluxos de alto risco cognitivo.
- Se o usuario entra por convite, ele deve entender que a empresa ja autorizou.
- Se ele solicitou empresa, deve entender que aguarda aprovacao.
- Se e-mail nao chegou, deve haver recuperacao clara.

### Direcao
Onboarding deve ser uma esteira guiada, nao uma colecao de estados tecnicos.

### Arquivos principais
- `lib/app/experience/experience_home_router.dart`
- `lib/features/auth/presentation/email_verification_pending_screen.dart`
- `lib/features/passenger_onboarding/presentation/passenger_company_link_flow.dart`
- `lib/features/passenger_onboarding/presentation/passenger_company_*_step.dart`
- `lib/features/driver_trip/presentation/driver_company_pending_screen.dart`
- `lib/features/driver_onboarding/presentation/driver_invite_screen.dart`
- Backend: `PassengerOnboardingController`, `DriverOnboardingController`, `EmailVerificationService`

---

## 5.7 Suporte, notificacoes, chat e pagamentos

### Problemas principais
- Suporte/tickets parece proximo de ser util, mas precisa smoke real e UI de inbox bem pensada.
- Push depende de provider, APNs/FCM e device.
- Chat e pagamentos sao importantes, mas podem distrair se entram antes do operacional ficar excelente.

### Direcao
Fechar suporte/tickets antes de chat. Push deve ser planejado como plataforma. Pagamentos fica como frente futura com provider definido.

---

## 6. Fases recomendadas

### Fase 0 - Fundacao de experiencia e validacao
Objetivo: criar a base que impede retrabalho.

Entregas:
- inventario visual atual;
- matriz de fluxos;
- padrao de componentes;
- tokens e motion auditados;
- fixture de DEV para smokes visuais;
- roteiro de screenshots por papel.

### Fase 1 - Portal admin como cockpit operacional simples
Objetivo: reduzir carga cognitiva da empresa.

Entregas:
- dashboard orientado a "proxima acao";
- viagens do dia como manutencao, nao recriacao;
- servicos como centro de recorrencia;
- estados de conflito, bloqueio e sincronizacao explicados.

### Fase 2 - Pessoas e vinculos dentro das entidades
Objetivo: retirar mentalidade tecnica de "vinculos".

Entregas:
- ficha unificada de passageiro;
- ficha unificada de motorista;
- ficha unificada de responsavel;
- tela `Vinculos` rebaixada para avancado ou removida da navegacao comum;
- checklist de prontidao por pessoa.

### Fase 3 - Onboarding e convites sem ambiguidade
Objetivo: deixar criacao de conta e ligacao com empresa profissional.

Entregas:
- motorista por link, sem aprovacao indevida;
- motorista por solicitacao, com pendencia clara;
- passageiro por codigo/solicitacao;
- e-mail pendente com recuperacao;
- upgrade de sessao claro.

### Fase 4 - App operacional mobile polido por papel
Objetivo: alinhar passageiro, motorista e responsavel ao mesmo sistema premium.

Entregas:
- passageiro: viagem, presenca, conta e estados vazios no mesmo padrao;
- motorista: proxima acao, planejamento e execucao;
- responsavel: acompanhamento e seguranca;
- bottom sheets com fisica e detents consistentes.

### Fase 5 - Suporte/tickets e comunicacao controlada
Objetivo: resolver problemas reais antes de chat amplo.

Entregas:
- ticket do usuario para empresa;
- inbox da empresa;
- estados e historico;
- notificacoes operacionais in-app;
- push apenas quando provider estiver pronto.

### Fase 6 - Release readiness funcional
Objetivo: provar que o app esta pronto funcionalmente.

Entregas:
- matriz de fluxos PASS/LIMITES/BLOCKED;
- screenshots reais de todos os papeis;
- smoke DEV limpo;
- QA visual web/mobile;
- lista de pendencias futuras separada do que bloqueia uso.

---

## 7. Cards detalhados

## UX-0001 - Matriz canonica de fluxos e estados

**Problema:** O produto tem muitas telas e cards fechados, mas falta uma matriz unica que diga quais estados existem por papel e qual UI deve aparecer.

**Decisao:** Criar uma matriz de produto por papel: admin, passageiro, motorista, responsavel.

**Arquivos:**
- Create: `vanrides-app/backlog/UX-Flow-State-Matrix-2026-06-19.md`
- Modify: `vanrides-app/backlog/QA-Execution-Log.md`

**Escopo:**
- Mapear estados de conta: sem e-mail, e-mail pendente, verificado.
- Mapear estados de empresa: sem empresa, solicitacao pendente, convite valido, vinculado, rejeitado, suspenso.
- Mapear estados de operacao: sem viagem, planejada, aguardando inicio, em andamento, pausada, concluida, cancelada.
- Mapear estados de presenca: pendente, vou, nao vou, expirado, travado.
- Mapear estados de suporte: sem tickets, aberto, aguardando empresa, respondido, resolvido.

**Criterios de aceite:**
- Cada estado tem copy recomendada.
- Cada estado tem CTA principal e CTA secundario.
- Cada estado aponta para a tela/widget responsavel.
- Nenhum estado termina em "fale com suporte" sem explicar o motivo.

**Validacao:**
- `rg -n "UnsupportedRoleScreen|sem contexto|Conflict|Bad Request|mocado|mock" lib`
- Revisao manual da matriz contra `ExperienceHomeRouter`.

**Registro incremental 2026-06-19:**
- Copy canonica de passageiro, presenca, detalhes, suporte, deep link e estados
  sem vinculo foi polida no app.
- Estados sem empresa/contexto do `ExperienceHomeRouter` passaram a exibir
  mensagens seguras para admin e responsavel, sem termos internos de token.
- Busca por copies antigas ficou restrita a fixture de contrato e normalizacao
  compatível com payload legado.
- Evidencia: `backlog/QA-Execution-Log.md`, entrada
  `UX/UI follow-up - copy canonica e estados seguros de passageiro`.

---

## UX-0002 - Design system operacional dark/liquid

**Problema:** O app tem tokens e componentes, mas ainda pode haver divergencia entre portal, mobile, sheets, tabs, cards e formularios.

**Decisao:** Consolidar um design system operacional com tokens, estados e regras de uso.

**Arquivos:**
- Modify: `lib/app/theme/vanrides_design_tokens.dart`
- Modify: `lib/app/theme/vanrides_motion.dart`
- Modify: `lib/app/widgets/vanrides_liquid_surface.dart`
- Modify: `lib/app/widgets/vanrides_liquid_tab_scaffold.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_portal_theme.dart`
- Create: `vanrides-app/backlog/VanRides-Design-System-Operational-Guide.md`

**Escopo:**
- Definir tokens por uso: background, surface, glass, border, text, tracking, success, warning, danger.
- Definir quando usar glass: mapa, sheet, overlay contextual.
- Definir quando nao usar glass: tabelas densas, formularios longos, dialogs criticos.
- Definir radii, spacing, icon size, button height, chip height.
- Definir motion: detent sheet, button press, status update, route refresh, loading.

**Criterios de aceite:**
- Todos os componentes principais tem default/hover/focus/disabled/loading/error.
- Portal e mobile usam a mesma semantica de cor.
- Textos em glass passam contraste minimo.
- Icon-only tem tooltip/semantic label.

**Validacao:**
- `flutter test test/l10n/app_strings_test.dart`
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- Smoke visual de 8 telas: login, admin dashboard, viagens, passageiro viagem, presenca, motorista, responsavel, conta.

---

## UX-0101 - Portal admin dashboard de proxima acao

**Problema:** Dashboard pode mostrar numeros, mas a empresa precisa saber o que fazer agora.

**Decisao:** Transformar o painel em lista priorizada de acoes operacionais.

**Arquivos:**
- Modify: `lib/features/admin/presentation/widgets/admin_dashboard_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_dashboard_next_action_widgets.dart`
- Modify: `lib/features/admin/data/admin_dashboard_service.dart`
- Backend se necessario: `CompanyAdminDashboardController`, `CompanyAdminDashboardService`

**Escopo:**
- Topo: "Hoje" com status do dia.
- Bloco principal: "Precisa de revisao" com no maximo 3 acoes.
- Acoes possiveis:
  - gerar viagens de hoje;
  - revisar servico sem motorista;
  - revisar passageiro sem servico;
  - aprovar solicitacao;
  - responder ticket aberto;
  - viagem com manifesto pendente;
  - viagem em andamento.
- Metricas ficam secundarias e clicaveis.

**Criterios de aceite:**
- Admin entende em 5 segundos qual primeira acao tomar.
- Nenhuma metrica aparece sem acao associada.
- Dashboard vazio ensina a criar primeiro motorista/passageiro/servico.

**Validacao:**
- Widget test do dashboard com fixture: vazio, parcial, completo, com alertas.
- Playwright smoke: login -> painel -> clicar primeira acao -> destino correto.

---

## UX-0102 - Viagens do dia como manutencao, nao recriacao

**Problema:** Admin pode ainda acreditar que precisa apagar/recriar viagem para refletir mudanca em passageiro/servico.

**Decisao:** A tela de viagens deve explicar sincronizacao e manutencao como caminho principal.

**Arquivos:**
- Modify: `lib/features/admin/presentation/admin_viagens_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_viagens_day_status_panel.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_viagens_maintenance_guide.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_viagens_detail_view.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_viagens_manifest_status_widgets.dart`
- Modify: `lib/features/admin/data/admin_viagens_portal_service.dart`
- Backend: `CompanyAdminViagemController`, `CompanyAdminViagemService`, `ViagemManifestSyncService`

**Escopo:**
- Renomear fluxo manual para "Excecao do dia".
- Na viagem planejada por servico, CTA primario: "Sincronizar lista".
- Mostrar antes/depois do manifesto em linguagem humana.
- Quando passageiro novo entra no servico, sugerir sincronizar viagem existente.
- Quando viagem esta em andamento, explicar por que nao sincroniza automaticamente.
- Conflito deve abrir a viagem existente, nao exibir erro cru.

**Criterios de aceite:**
- Admin consegue adicionar passageiro a servico e atualizar viagem sem apagar nada.
- Admin entende quando uma viagem esta bloqueada para manutencao.
- Data usa formato local e bom contraste.
- Nenhum erro "Conflict", "Bad Request" ou status tecnico chega cru.

**Validacao:**
- `flutter test test/features/admin/admin_viagens_screen_test.dart`
- `flutter test test/features/admin/admin_viagens_portal_service_test.dart`
- Backend: testes de `sincronizar-manifesto`.
- Playwright: criar servico -> gerar viagem -> adicionar passageiro -> sincronizar -> validar lista atualizada.

---

## UX-0103 - Servico recorrente como centro da operacao

**Problema:** Servico, rota, passageiro e viagem ainda podem parecer conceitos separados demais.

**Decisao:** O servico recorrente vira o lugar para preparar a linha operacional.

**Arquivos:**
- Modify: `lib/features/admin/presentation/admin_servicos_recorrentes_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_servicos_recorrentes_detail_widgets.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_servicos_recorrentes_form.dart`
- Modify: `lib/features/admin/data/admin_servicos_recorrentes_portal_service.dart`
- Backend: `CompanyAdminServicoRecorrenteController`, `CompanyAdminServicoRecorrenteService`

**Escopo:**
- Ficha do servico mostra:
  - nome, sentido, horario, dias;
  - motorista padrao;
  - passageiros elegiveis;
  - capacidade;
  - rota/destino;
  - status para gerar viagem hoje.
- Se falta algo, mostrar checklist:
  - sem motorista;
  - sem passageiros;
  - capacidade excedida;
  - sem destino/rota;
  - fora do dia de operacao.

**Criterios de aceite:**
- Admin sabe por que um servico nao gera viagem.
- Admin edita passageiros elegiveis sem entrar em tela tecnica de vinculos.
- Admin entende se alteracao afeta hoje ou futuras viagens.

**Validacao:**
- Widget tests de servico vazio, pronto, bloqueado e excedido.
- Smoke portal: criar servico -> liberar passageiro -> montar viagem.

---

## UX-0104 - Entidades com fichas de prontidao

**Problema:** Pessoas estao cadastradas, mas o admin precisa saber se elas estao prontas para operar.

**Decisao:** Passageiro, motorista e responsavel devem ter fichas com checklist acionavel.

**Arquivos:**
- Modify: `lib/features/admin/presentation/admin_passageiros_screen.dart`
- Modify: `lib/features/admin/presentation/admin_motoristas_screen.dart`
- Modify: `lib/features/admin/presentation/admin_responsaveis_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_person_detail_components.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_profile_checklist.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_passageiro_profile_checklist.dart`

**Escopo:**
- Passageiro pronto quando:
  - nome, telefone, e-mail;
  - e-mail verificado ou convite pendente explicado;
  - data de nascimento;
  - parada/endereco confirmado por mapa;
  - pelo menos um servico liberado;
  - responsavel se menor de idade;
  - presenca de hoje visivel quando houver servico.
- Motorista pronto quando:
  - nome, telefone, e-mail;
  - e-mail verificado;
  - empresa vinculada;
  - foto opcional recomendada;
  - documentos futuros como placeholder de roadmap, nao campo morto.
- Responsavel pronto quando:
  - dados pessoais;
  - passageiro(s) vinculados;
  - permissao de acompanhar status.

**Criterios de aceite:**
- Checklist sempre aponta uma acao.
- O percentual de perfil nao aparece sozinho sem explicar o que falta.
- Admin nao precisa ir para `Vinculos` para completar ficha comum.

**Validacao:**
- Widget tests por papel com perfil completo/incompleto.
- Smoke visual de fichas.

---

## UX-0105 - Rebaixar tela tecnica de Vinculos

**Problema:** `Vinculos` e conceito tecnico demais para navegacao primaria.

**Decisao:** Manter somente como avancado/diagnostico, ou remover da sidebar comum.

**Arquivos:**
- Modify: `lib/features/admin/presentation/admin_shell_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_portal_sidebar.dart`
- Modify: `lib/features/admin/presentation/admin_vinculos_screen.dart`
- Modify: `lib/features/admin/presentation/widgets/admin_vinculos_*.dart`

**Escopo:**
- Sidebar principal: Painel, Viagens, Servicos, Passageiros, Motoristas, Responsaveis, Conta.
- `Vinculos avancados` fica em menu secundario ou dentro de Conta/Configuracoes.
- Acoes comuns migram para fichas:
  - passageiro -> servicos/responsaveis/motorista;
  - motorista -> convites/solicitacoes/viagens;
  - responsavel -> passageiros vinculados.

**Criterios de aceite:**
- Admin consegue concluir criacao operacional sem abrir `Vinculos`.
- A tela avancada nao e necessaria no happy path.
- Testes de navegacao cobrem a ausencia/rebaixamento.

**Validacao:**
- Playwright: fluxo admin completo sem clicar em `Vinculos`.
- Widget tests do shell/sidebar.

---

## UX-0201 - Passageiro: presenca como decisao simples

**Problema:** Presenca pode virar lista densa ou repetir agenda.

**Decisao:** Mostrar somente horarios que exigem decisao e estado de cada um.

**Arquivos:**
- Modify: `lib/features/service_presence/presentation/confirmar_presenca_screen.dart`
- Modify: `lib/features/service_presence/presentation/confirmar_presenca_widgets.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_home_presence_widgets.dart`
- Modify: `lib/features/service_presence/data/service_presence_service.dart`

**Escopo:**
- Header curto: "Presenca de hoje".
- Resumo: pendentes, confirmados, ausencias.
- Lista vertical compacta:
  - horario;
  - sentido/destino;
  - empresa/servico;
  - decisao atual;
  - CTA Vou / Nao vou / Alterar.
- Sem carrossel para horarios essenciais.
- Se tudo decidido, mostrar estado calmo com edicao possivel se regra permitir.

**Criterios de aceite:**
- Usuario entende em ate 3 segundos o que falta decidir.
- Nenhum texto corta em iPhone pequeno.
- VoiceOver informa horario, destino e decisao atual.

**Validacao:**
- Widget tests com 0, 1, 2, 5 horarios.
- Screenshot iPhone 16e e viewport pequena.

---

## UX-0202 - Passageiro: tracking com estados narrativos

**Problema:** Estados como "sinal pausado", "aguardando inicio" e "sem sinal" precisam ser mais humanos e consistentes.

**Decisao:** Criar narrativa de tracking baseada em etapas da viagem.

**Arquivos:**
- Modify: `lib/features/tracking/presentation/map_first_trip_tracking_view.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_trip_footer_sheet_*.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_trip_collapsed_hud.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_map_stage_top_layer.dart`
- Modify: `lib/features/tracking/presentation/trip_tracking_presenter.dart`

**Estados e copy:**
- Sem viagem: "Nenhuma viagem prevista agora".
- Viagem planejada: "Van ainda nao iniciou".
- Sem sinal: "Ultima posicao" + apoio "Aguardando atualizacao".
- Motorista vindo: "Van a caminho".
- Proxima parada antes de mim: "Faltam X paradas".
- Minha parada: "Sua parada e a proxima".
- Viagem concluida: "Viagem encerrada".

**Criterios de aceite:**
- Sheet mini, media e expandida mantem o mesmo topo.
- Botao `Ver van` e `Minha parada` minimizam sheet para mostrar mapa.
- Chips de viagem nao truncam texto essencial.
- Google logo/controles nao ficam cobertos de forma incoerente; se inevitavel, usar padding do mapa.

**Validacao:**
- Widget tests dos estados de sheet.
- Visual acceptance com `TripLiveMapRenderMode.placeholder`.
- Smoke no simulator com mapa real quando chave local existir.

**Registro incremental 2026-06-19:**
- O smoke real `output/visual-smoke/ux-goal-real-mobile-20260619-copy-polish/passenger-trip.png`
  revelou truncamento da copy longa no sheet do passageiro.
- A copy de estado sem sinal foi encurtada para `Ultima posicao`, mantendo
  `Aguardando atualizacao` como explicacao secundaria.
- Implementado em `538d9ea`; evidencias registradas no QA log em
  `UX/UI follow-up - ajuste visual do sheet de tracking do passageiro`.

---

## UX-0203 - Passageiro: detalhes da viagem em camada secundaria

**Problema:** Detalhes podem competir com tracking principal.

**Decisao:** Detalhes viram tela/camada de consulta, nao bloco permanente no mapa.

**Arquivos:**
- Modify: `lib/features/tracking/presentation/passenger_trip_details_screen.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_trip_glass_actions.dart`
- Modify: `lib/features/tracking/presentation/widgets/passenger_trip_footer_sheet_sections.dart`

**Escopo:**
- Detalhes mostram:
  - horario;
  - servico;
  - motorista/van;
  - minha parada;
  - paradas antes/depois;
  - status de presenca;
  - suporte da viagem.
- No mapa, manter somente resumo e CTAs.

**Criterios de aceite:**
- Mapa nao fica poluido por informacao secundaria.
- Detalhes sao acessiveis por CTA claro.
- Voltar preserva estado da sheet.

**Validacao:**
- Widget test de navegacao mapa -> detalhes -> voltar.

---

## UX-0301 - Motorista: home por proxima acao

**Problema:** Motorista pode receber muitas acoes de uma vez.

**Decisao:** A home deve sempre promover uma acao principal.

**Arquivos:**
- Modify: `lib/features/driver_trip/presentation/home_motorista_screen.dart`
- Modify: `lib/features/driver_trip/presentation/widgets/driver_home_trip_tab.dart`
- Modify: `lib/features/driver_trip/presentation/widgets/driver_trip_action_widgets.dart`
- Modify: `lib/features/driver_trip/presentation/motorista_session_presenter.dart`

**Acoes por estado:**
- Sem viagem: "Aguardando viagem da empresa".
- Viagem planejada: "Revisar rota" / "Iniciar viagem".
- Em andamento: "Ir para proxima parada".
- Na parada: "Confirmar embarque", "Nao apareceu", "Pular".
- Final: "Encerrar viagem".

**Criterios de aceite:**
- Apenas um CTA primario por estado.
- Acoes perigosas exigem confirmacao.
- Handoff para Maps nao substitui estado operacional no app.

**Validacao:**
- Widget tests dos estados do motorista.
- Smoke com fixture de viagem planejada e em andamento.

---

## UX-0302 - Motorista: fila de paradas clara

**Problema:** Lista de paradas pode ser longa e operacionalmente pesada.

**Decisao:** Mostrar fila em tres grupos: proxima, pendentes, resolvidas.

**Arquivos:**
- Modify: `lib/features/driver_trip/presentation/driver_stops_screen.dart`
- Modify: `lib/features/driver_trip/presentation/widgets/driver_trip_stop_widgets.dart`
- Modify: `lib/features/driver_trip/presentation/widgets/driver_home_trip_sheet_widgets.dart`

**Escopo:**
- Proxima parada sempre destacada.
- Passageiro com presenca `nao vou` nao deve competir com pendentes.
- Mostrar endereco/parada com acao de mapa.
- Acoes de no-show/pular devem ter copy segura.

**Criterios de aceite:**
- Motorista identifica a proxima parada sem ler a lista inteira.
- Resolvidas nao escondem pendentes.
- Acoes de excecao deixam rastro visual.

**Validacao:**
- Tests para 0, 1, 8 paradas; presenca mista; no-show.

---

## UX-0401 - Responsavel: home de acompanhamento

**Problema:** Responsavel precisa experiencia propria, nao copia do passageiro.

**Decisao:** Home mostra pessoas acompanhadas e status prioritario.

**Arquivos:**
- Modify: `lib/features/guardian/presentation/guardian_home_screen.dart`
- Modify: `lib/features/guardian/presentation/widgets/guardian_localizacao_widgets.dart`
- Modify: `lib/features/guardian/presentation/widgets/guardian_presencas_summary_widgets.dart`

**Escopo:**
- Lista de passageiros acompanhados.
- Para cada pessoa:
  - status de viagem;
  - presenca;
  - proxima acao;
  - localizacao quando disponivel.
- Se nao ha passageiro vinculado, explicar que empresa precisa vincular.

**Criterios de aceite:**
- Responsavel entende quem acompanha e estado de cada pessoa.
- Nao ha CTA administrativo que o responsavel nao pode executar.

**Validacao:**
- Widget tests para 0, 1 e multiplos passageiros.
- Smoke de guardian no simulator.

---

## UX-0402 - Responsavel: localizacao com seguranca e contexto

**Problema:** Responsavel precisa localizar sem parecer que esta operando a viagem.

**Decisao:** Localizacao prioriza seguranca, ultima atualizacao e status.

**Arquivos:**
- Modify: `lib/features/guardian/presentation/guardian_localizacao_screen.dart`
- Modify: `lib/features/guardian/presentation/widgets/guardian_map_footer_sheet_widgets.dart`
- Add: `lib/features/guardian/presentation/widgets/guardian_map_safety_widgets.dart`

**Escopo:**
- Mostrar:
  - pessoa acompanhada;
  - van;
  - parada;
  - ultima atualizacao;
  - status da viagem.
- Sheet segue a mesma fisica do passageiro, mas com copy de acompanhamento.

**Criterios de aceite:**
- Responsavel entende se dado e ao vivo ou ultima posicao.
- Nao ha texto cortado em sheet.

**Validacao:**
- Visual acceptance da tela de localizacao.

**Follow-up 2026-06-19:**
- Ajustada a copy do sheet para o responsavel nao receber frase em primeira
  pessoa do passageiro (`Você é o próximo da fila.`).
- Evidencia real em
  `output/visual-smoke/ux-goal-real-mobile-20260619-guardian-fit/guardian-location.png`.

---

## UX-0501 - Perfil editavel por papel

**Problema:** Perfil deve ser area viva, com edicao e completude clara.

**Decisao:** Perfil base igual para todos, secoes por papel.

**Arquivos:**
- Modify: `lib/features/account/presentation/account_profile_screen.dart`
- Modify: `lib/features/account/presentation/edit_account_profile_sheet.dart`
- Modify: `lib/features/account/presentation/account_profile_completion_card.dart`
- Modify: `lib/features/account/data/account_profile_service.dart`
- Backend: `AccountController`, `AccountMeService`, `AccountAvatarService`

**Escopo:**
- Foto/avatar funcional.
- Nome, telefone, nascimento editaveis quando permitido.
- E-mail verificado com fluxo de troca seguro.
- Alterar senha.
- Empresa e papel como informacao de acesso, nao campo editavel comum.
- Secoes por papel:
  - passageiro: parada, servicos, responsaveis;
  - motorista: empresa, status, documentos futuros como "em breve" apenas se houver roadmap claro;
  - responsavel: passageiros acompanhados;
  - admin: empresa, papel, permissoes.

**Criterios de aceite:**
- Nenhum dado mocado.
- Upload de foto funciona ou mostra erro especifico.
- Perfil incompleto aponta a acao exata.

**Validacao:**
- Tests de profile service.
- Widget tests por papel.
- Smoke HTTP `GET/PATCH /account/me` e upload.

---

## UX-0601 - Onboarding passageiro sem ambiguidade

**Problema:** Passageiro pode nao entender diferenca entre conta criada, e-mail confirmado e empresa vinculada.

**Decisao:** Onboarding deve ser um fluxo de passos visiveis.

**Arquivos:**
- Modify: `lib/features/passenger_onboarding/presentation/passenger_company_link_flow.dart`
- Modify: `lib/features/passenger_onboarding/presentation/passenger_company_search_step.dart`
- Modify: `lib/features/passenger_onboarding/presentation/passenger_company_request_step.dart`
- Modify: `lib/features/passenger_onboarding/presentation/passenger_company_invite_step.dart`
- Modify: `lib/app/experience/experience_home_router.dart`
- Backend: `PassengerOnboardingController`, `PassengerOnboardingService`

**Escopo:**
- Stepper compacto:
  - Conta;
  - E-mail;
  - Empresa;
  - Servicos;
  - Pronto.
- Busca por empresa com resultados claros.
- Codigo de convite com erro recuperavel.
- Pendente de aprovacao com expectativa.
- Vinculado sem viagem com empty state operacional.

**Criterios de aceite:**
- Usuario nunca cai em tela generica de "role unsupported" no fluxo esperado.
- Aprovacao pendente tem motivo e proximo passo.
- Upgrade de sessao apos aprovacao/resgate e explicito.

**Validacao:**
- Widget tests de todos os estados.
- Smoke real: cadastro -> e-mail QA -> solicitar -> aprovar -> upgrade.

---

## UX-0602 - Onboarding motorista por link e solicitacao

**Problema:** Motorista por convite e por solicitacao tem semanticas diferentes e nao podem se misturar.

**Decisao:** Link da empresa autoriza vinculo apos e-mail verificado; busca gera solicitacao pendente.

**Arquivos:**
- Modify: `lib/features/driver_onboarding/presentation/driver_invite_screen.dart`
- Modify: `lib/features/driver_trip/presentation/driver_company_pending_screen.dart`
- Modify: `lib/features/driver_trip/presentation/driver_company_pending_widgets.dart`
- Modify: `lib/features/driver_onboarding/data/driver_onboarding_service.dart`
- Backend: `DriverOnboardingController`, `DriverOnboardingService`, `DriverInviteRedemptionService`

**Escopo:**
- Link:
  - preview da empresa;
  - criar/login;
  - confirmar e-mail;
  - resgatar;
  - home dentro da empresa sem viagem.
- Busca:
  - pesquisar empresa;
  - solicitar;
  - status aguardando aprovacao;
  - aprovar no portal.

**Criterios de aceite:**
- Convite valido nunca mostra "aguardando aprovacao da empresa".
- Solicitacao manual sempre mostra pendencia.
- Empty state sem viagem nao parece erro.

**Validacao:**
- `xcrun simctl openurl booted "vanrides://driver-invite/<token>"`
- Widget tests de convite valido, expirado, usado, e-mail pendente.
- Smoke portal: criar convite, copiar link, aprovar solicitacao.

---

## UX-0603 - E-mail, troca de e-mail e recuperacao

**Problema:** Confirmacao de e-mail, reenvio e troca de e-mail sao fluxos sensiveis e precisam prova real.

**Decisao:** Tratar e-mail como etapa central de identidade, com smoke provider quando possivel.

**Arquivos:**
- Modify: `lib/features/auth/presentation/email_verification_pending_screen.dart`
- Modify: `lib/features/admin/presentation/admin_login_screen.dart`
- Backend: `AuthController`, `EmailVerificationService`, `PendingEmailChangeService`
- Possible script: `vanrides-backend/scripts/qa_email_verification_dev_smoke.sh`

**Escopo:**
- Reenviar verificacao com cooldown visivel.
- Alterar e-mail com confirmacao.
- Mensagem clara quando provider nao enviar.
- Smoke que diferencia API aceitou vs e-mail saiu.

**Criterios de aceite:**
- Usuario sabe onde verificar e como reenviar.
- Logs nao vazam token.
- Admin com e-mail pendente nao entra no portal sem verificacao.

**Validacao:**
- Backend tests de e-mail.
- Smoke Resend/mailbox quando acesso existir.
- Widget tests do estado pendente.

---

## UX-0701 - Suporte/tickets funcional e claro

**Problema:** Suporte precisa estar funcional antes de chat amplo.

**Decisao:** Tickets devem cobrir problema real com estado e resposta da empresa.

**Arquivos:**
- App user side: localizar telas/widgets de suporte atuais em `lib/features/**`
- Admin: `lib/features/admin/data/admin_*support*` se existir ou criar modulo dedicado.
- Backend: `SupportTicketController`, `CompanyAdminSupportTicketController`, `SupportTicketService`

**Escopo:**
- Usuario cria ticket com categoria e mensagem.
- Empresa ve inbox.
- Empresa responde.
- Usuario ve resposta e status.
- Status: aberto, em andamento, resolvido.
- Entrada contextual a partir de viagem/perfil.

**Criterios de aceite:**
- Ticket criado aparece no portal sem seed manual.
- Usuario recebe confirmacao.
- Empresa entende prioridade/status.

**Validacao:**
- Backend smoke DEV de tickets.
- Playwright portal inbox.
- Widget test app user.

---

## UX-0801 - Push notifications readiness

**Problema:** Push esta bloqueado por provider, mas precisa plano tecnico claro para nao virar improviso.

**Decisao:** Preparar contrato e UX de preferencia, sem prometer funcionamento ate FCM/APNs/TestFlight.

**Arquivos:**
- `lib/features/notifications/**`
- Backend futuro: push token, preferencias, dispatcher.
- Docs: `vanrides-app/backlog/Push-Notifications-Readiness.md`

**Escopo:**
- Preferencias por categoria:
  - viagem;
  - presenca;
  - mensagem/ticket;
  - operacao.
- Estados:
  - permissao nao pedida;
  - permitida;
  - negada;
  - indisponivel.

**Criterios de aceite:**
- Fluxo bloqueado externo documentado.
- App nao pede permissao cedo demais.
- QA de device fisico definido.

**Validacao:**
- Widget/unit tests com gateway fake.
- Roteiro iPhone fisico.

---

## UX-0901 - QA visual deterministico completo

**Problema:** So saber que os testes passam nao prova qualidade visual ou clareza de fluxo.

**Decisao:** Criar uma suite de screenshots deterministica por papel e por estado.

**Arquivos:**
- Modify/Create scripts em `vanrides-app/scripts/`
- Modify: `vanrides-app/backlog/QA-Execution-Log.md`
- Use fixtures em `output/visual-smoke/<phase>/`

**Escopo minimo de prints:**
- Admin:
  - login/bootstrap;
  - dashboard vazio/completo;
  - viagens do dia;
  - servico pronto/bloqueado;
  - passageiro completo/incompleto;
  - motorista convite/solicitacao;
  - responsavel vinculado;
  - conta.
- Passageiro:
  - viagem sem viagem;
  - planejada;
  - aguardando inicio;
  - sinal pausado;
  - van chegando;
  - presenca pendente;
  - perfil completo/incompleto.
- Motorista:
  - sem empresa;
  - sem viagem;
  - viagem planejada;
  - em andamento;
  - parada atual;
  - conta.
- Responsavel:
  - sem passageiro;
  - home com passageiro;
  - localizacao;
  - presencas;
  - conta.

**Criterios de aceite:**
- Cada screenshot tem nome estavel.
- Cada papel tem pelo menos um happy path e um estado vazio.
- Falhas visuais viram cards ou limites documentados.

**Validacao:**
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Playwright web para portal.
- Flutter tests para mobile placeholders.

---

## UX-1001 - Arquitetura de estado e reducao de arquivos grandes

**Problema:** Algumas telas ainda concentram muita logica de UI, estado e gateway.

**Decisao:** Separar presenter/state model quando o fluxo tiver mais de uma tela/estado.

**Arquivos candidatos:**
- `lib/features/admin/presentation/admin_viagens_screen.dart`
- `lib/features/admin/presentation/admin_passageiros_screen.dart`
- `lib/features/driver_trip/presentation/home_motorista_screen.dart`
- `lib/features/tracking/presentation/map_first_trip_tracking_view.dart`
- `lib/features/guardian/presentation/guardian_localizacao_screen.dart`

**Escopo:**
- Nao migrar state management global agora sem decisao.
- Extrair somente presenters locais quando reduz risco.
- Manter contratos de widgets testaveis.

**Criterios de aceite:**
- Arquivo central fica menor e mais legivel.
- Teste cobre presenter sem pump pesado quando possivel.
- Nenhuma refatoracao muda fluxo sem teste.

**Validacao:**
- `flutter analyze`
- tests focados por presenter.
- `./scripts/quality_gate.sh`

---

## 8. Ordem de execucao recomendada

### Bloco A - Plano e base
1. UX-0001 Matriz canonica de estados. DONE local em `af9a268`.
2. UX-0002 Design system operacional. DONE local em `9de8255`.
3. UX-0901 QA visual deterministico. DONE local em `19ea8d7`
   (evidencia em `d54246b`), com gaps planejados de motorista sem empresa e
   responsavel sem passageiro fechados posteriormente por smoke deterministico
   dedicado.

### Bloco B - Portal admin principal
4. UX-0101 Dashboard de proxima acao. DONE local em `5990a0e`.
5. UX-0102 Viagens do dia como manutencao. DONE local em `5990a0e`.
6. UX-0103 Servico recorrente como centro. DONE local em `5990a0e`.

### Bloco C - Pessoas e acessos
7. UX-0104 Fichas de prontidao. DONE local em `d06ccf2`.
8. UX-0105 Rebaixar Vinculos. DONE local em `d06ccf2`.
9. UX-0501 Perfil editavel por papel. DONE local em `8d5d0a1` (ratchet em `549ac5b`).

### Bloco D - Onboarding
10. UX-0601 Passageiro sem ambiguidade. DONE local em `97b429b`.
11. UX-0602 Motorista por link/solicitacao. DONE local em `824b739`.
12. UX-0603 E-mail e troca de e-mail. DONE local em `af8da4f`.

### Bloco E - Mobile operacional
13. UX-0201 Presenca simples. DONE local em `064a9bc`.
14. UX-0202 Tracking narrativo. DONE local em `9da9f99`.
15. UX-0203 Detalhes secundarios. DONE local em `e13e20a`.
16. UX-0301 Motorista proxima acao. DONE local em `e24de0e`.
17. UX-0302 Fila de paradas. DONE local em `77592e3`.
18. UX-0401 Responsavel home. DONE local em `5f5b9d0`.
19. UX-0402 Responsavel localizacao. DONE local em `3b518a0`.

### Bloco F - Suporte e readiness
20. UX-0701 Suporte/tickets. DONE local em `48874ec`.
21. UX-0801 Push readiness. DONE local em `a5de74b`.
22. UX-1001 Refatoracoes de estado. DONE local em `d3f3669`.

---

## 9. Validacao obrigatoria por tipo de mudanca

### App Flutter
- `flutter analyze --no-fatal-infos --no-fatal-warnings`
- `flutter test`
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`
- Widget tests focados no card.
- Screenshot antes/depois quando houver mudanca visual.

### Portal Admin Web
- `./scripts/qa_admin_portal_web_e2e.sh`
- Playwright smoke do fluxo alterado.
- Screenshot desktop e largura media.
- Foco por teclado em formularios/dialogs.

### Backend
- `./mvnw test`
- `./scripts/ci_quality_gate.sh`
- Smoke HTTP DEV para contratos alterados.
- Atualizar `vanrides-backend/backlog/API-Contract-Matrix.md` quando contrato mudar.

### Contrato app + backend
- Backend test + Flutter service test.
- Smoke DEV ponta a ponta.
- QA Log com payload/endpoint mascarado.

### Mobile real
- iOS Simulator para rotina geral.
- iPhone fisico/TestFlight para:
  - deep link real;
  - push;
  - permissao de localizacao;
  - Google/Apple Maps handoff;
  - camera/foto/avatar se aplicavel.

---

## 10. Definition of Done para esta fase

Uma melhoria so esta pronta quando:
- o fluxo principal passa sem seed manual fragil;
- erro principal tem copy humana;
- estado vazio orienta uma acao;
- teste automatizado cobre pelo menos o estado principal;
- smoke real ou visual cobre o fluxo critico;
- nenhum texto essencial corta em viewport pequena;
- acessibilidade basica foi revisada;
- QA Log registra evidencias;
- backlog registra status, commit e limites.

---

## 11. Pendencias intencionais que nao devem bloquear esta fase

Estas frentes nao devem bloquear a entrega de experiencia operacional premium:
- Chat amplo por viagem/empresa.
- Pagamentos/billing.
- Push real antes de provider/APNs/FCM.
- Turn-by-turn nativo dentro do app.
- App Store/TestFlight publico.
- Documentos formais de motorista, CNH e compliance completo.

Elas devem permanecer como futuras fases com decisao propria.

---

## 12. Checklist de completude do plano

- [x] Portal admin incluido.
- [x] App passageiro incluido.
- [x] App motorista incluido.
- [x] App responsavel incluido.
- [x] Perfil/conta incluido.
- [x] Onboarding passageiro incluido.
- [x] Onboarding motorista incluido.
- [x] E-mail/convites incluido.
- [x] Presenca incluida.
- [x] Viagens/servicos/rotas incluidos.
- [x] Suporte/tickets incluido.
- [x] Push/chat/pagamentos classificados.
- [x] Design system incluido.
- [x] Acessibilidade incluida.
- [x] QA visual incluido.
- [x] Backend/contratos incluidos quando necessario.

---

## 13. Execucao sugerida

Plan complete and saved to `vanrides-app/backlog/UX-UI-Complete-Improvement-Plan-2026-06-19.md`.

Two execution options:

1. Subagent-Driven (recommended) - abrir subagentes por bloco: Portal Admin, App Mobile, Backend/Contratos, QA Visual.
2. Inline Execution - executar card por card nesta thread com checkpoints apos cada bloco.

Recomendacao: usar Subagent-Driven para os Blocos A, B e C, porque eles podem ser pesquisados e planejados em paralelo, mas aplicar commits por repo com revisao central.
