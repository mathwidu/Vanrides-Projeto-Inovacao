# Portal Admin S1 — Plano de Entrega

Objetivo: transformar o `Portal Empresa` em um fluxo operacional completo para preparar `rota-base` e `viagem do dia`, reduzindo a dependência de seed manual e consolidando o produto como operação real.

Conceito importante:
- hoje o VanRides já tem um MVP técnico muito bom da corrida
- o maior gap de produto não está mais no mapa do motorista
- o maior gap está em a empresa ainda não conseguir preparar o dia por um fluxo oficial e confortável dentro do portal

## Impact Analysis

### Contexto do card
O portal empresa já possui:
- login administrativo
- shell web
- dashboard
- gestão de motoristas
- gestão de passageiros
- gestão de responsáveis
- gestão de vínculos

O que ainda não possui:
- gestão de `rota-base`
- gestão de `viagem`
- definição explícita de `destino final` para a rota operacional

### Mudanças técnicas esperadas

#### Frontend Web (principal)
- nova navegação administrativa para `Rotas` e `Viagens`
- novos services HTTP administrativos
- novos models/domains para `rota-base` e `viagem`
- novas telas e estados:
  - listagem
  - criação
  - detalhe
  - vazios/erro/loading

#### Backend (pré-requisito pequeno)
- avaliar e provavelmente adicionar `destino final` explícito em `rota-base`
- manter compatibilidade com os contratos administrativos já existentes
- refletir o novo destino na construção de navegação/rota da viagem

### Contratos afetados

#### Já existentes
- `GET /company-admins/rotas-base`
- `POST /company-admins/rotas-base`
- `GET /company-admins/viagens`
- `POST /company-admins/viagens`

#### Provável extensão de contrato
Payload de `rota-base` com dados de destino final:
- `destinoFinalNome`
- `destinoFinalEndereco`
- `destinoFinalLatitude`
- `destinoFinalLongitude`

### Riscos e mitigação
- risco: implementar as telas de rota sem o conceito de destino final e ter que refazer formulário/detalhe depois
  - mitigação: congelar antes o contrato mínimo de `destino final`
- risco: redesenhar o portal página por página sem sistema consistente
  - mitigação: pedir primeiro um pacote completo de design no Stitch
- risco: misturar operação administrativa do dia com monitoramento operacional ao vivo em uma mesma fatia
  - mitigação: S1 cobre `Rotas` e `Viagens`; `Operação do dia` fica para fase seguinte

### Validação mínima obrigatória
- `flutter analyze`
- `flutter test`
- `./scripts/qa_gate_card.sh`
- smoke Playwright do portal administrativo
- smoke manual ponta a ponta:
  - empresa cria `rota-base`
  - empresa cria `viagem`
  - motorista lê a viagem do dia

### Rollback simples
- esconder as novas entradas de navegação do portal se o backend do `destino final` ainda não estiver publicado
- preservar os módulos já existentes do portal sem impacto regressivo

## Escopo oficial da fase S1

### Parte A — Ajuste mínimo de contrato no backend
Objetivo:
- permitir que a empresa defina um `destino final` real da rota

Entregáveis esperados:
- payload de criação/listagem de `rota-base` com destino final
- uso desse destino na navegação da viagem quando estiver presente

### Parte B — Redesign/sistema visual do portal empresa
Objetivo:
- consolidar todas as páginas do portal no mesmo sistema visual

Páginas existentes a redesenhar:
- `AdminLoginScreen`
- `AdminShellScreen`
- `AdminDashboardScreen`
- `AdminMotoristasScreen`
- `AdminPassageirosScreen`
- `AdminResponsaveisScreen`
- `AdminVinculosScreen`

Páginas novas a desenhar:
- `RotasListScreen`
- `RotaFormScreen`
- `RotaDetalheScreen`
- `ViagensListScreen`
- `ViagemCreateScreen`

Estados obrigatórios no design:
- loading
- empty
- success feedback
- error feedback
- confirmation dialogs

### Parte C — Implementação funcional do portal

#### C1. Navegação
- adicionar itens `Rotas` e `Viagens` ao `AdminShell`

#### C2. Domínio e services
- `AdminRotaBaseRecord`
- `AdminRotaBaseCreateInput`
- `AdminViagemRecord`
- `AdminViagemCreateInput`
- services administrativos correspondentes

#### C3. Telas de rotas
- listagem
- criação
- detalhe

#### C4. Telas de viagens
- listagem
- criação manual da viagem do dia

#### C5. Smoke operacional
- empresa prepara o dia no portal
- motorista consome a viagem criada

## Sequência recomendada de implementação

1. fechar o contrato do backend para `destino final`
2. gerar pacote completo de design no Stitch
3. levar os frames para o Figma
4. implementar no app a fundação visual/shell revisado
5. implementar `Rotas`
6. implementar `Viagens`
7. executar smoke Playwright + smoke operacional real

## O que deliberadamente não entra em S1
- monitoramento ao vivo da empresa
- mapa operacional da empresa
- analytics de operação do dia
- notificações operacionais
- chat
- billing

## Definition of Done da fase S1
- empresa consegue criar `rota-base`
- empresa consegue definir `destino final`
- empresa consegue criar `viagem` a partir da `rota-base`
- motorista consegue consumir a viagem do dia resultante
- portal mantém consistência visual entre páginas antigas e novas
- backlog e QA log atualizados com evidências

---

# Fase S2 — Portal Admin Premium e Operação Sem Atrito

Data de criação: 2026-06-17

Plano detalhado e referências da fase:
`backlog/Portal-Admin-S2-Premium-Research-Plan.md`.

Objetivo: transformar o portal da empresa em uma experiência operacional
simples, bonita e previsível. A empresa não deve precisar entender detalhes
técnicos como "vínculo", "manifesto" ou "snapshot" para operar o dia. Ela deve
entender Pessoas, Serviços e Viagens do Dia.

## Diagnóstico de Produto

### O que está claramente ruim hoje
- A navegação expõe `Vínculos` como uma seção própria, mas para a empresa isso é
  uma relação dentro de Passageiros, Motoristas, Responsáveis e Serviços.
- A criação de viagem manual parece ser o caminho para corrigir elenco de
  passageiros, quando deveria ser apenas uma exceção operacional do dia.
- O erro `Conflict` aparecia cru, sem explicar que a viagem já existia ou qual
  caminho tomar.
- A tela de criação manual mistura cards escuros com cards claros antigos,
  quebrando a estética premium/liquid glass.
- O campo de data estava com baixo contraste e formato americano, prejudicando
  confiança no preenchimento.
- A empresa ainda não tem um fluxo óbvio para "adicionei um passageiro, como
  isso entra na viagem de hoje?".

### Princípios da reforma
- O portal deve ser orientado a tarefas, não a tabelas técnicas.
- A sidebar deve priorizar no máximo: Painel, Serviços, Viagens do dia,
  Passageiros, Motoristas, Responsáveis e Conta.
- Vínculos devem virar seções contextuais dentro das entidades:
  - Passageiro: serviços, responsáveis, motorista/atalhos, presença do dia.
  - Motorista: empresa, convites, solicitações, serviços/viagens atribuídas.
  - Responsável: passageiros vinculados e permissões.
  - Serviço: passageiros elegíveis, motorista padrão, geração automática.
- Toda operação com risco deve responder com ação clara:
  - "Abrir viagem existente"
  - "Editar serviço"
  - "Montar dia novamente"
  - "Criar exceção manual"
- A estética deve ser dark/liquid operacional: superfícies translúcidas, azul
  como ação primária, verde para saudável, amarelo só para atenção real e sem
  blocos claros isolados.

## Modelo Mental Alvo

1. `Serviços` é onde a empresa define a operação recorrente:
   nome, dias, horário, destino, motorista padrão e passageiros elegíveis.
2. `Viagens do dia` é onde a empresa monta, monitora e ajusta exceções da data:
   gerar dia, abrir viagem existente, trocar motorista, presença e cancelamento.
3. `Passageiros`, `Motoristas` e `Responsáveis` são cadastros vivos, cada um com
   suas relações e pendências.
4. `Vínculos` deixa de ser destino principal e vira infraestrutura por trás das
   telas de entidade.

## Fluxo Alvo — Adicionar Passageiro Depois do Dia Montado

1. Admin cria ou abre Passageiro.
2. Admin vincula o passageiro a um ou mais Serviços.
3. O portal mostra impacto:
   - "Entrará nas próximas viagens geradas automaticamente."
   - "Existe viagem hoje para este serviço. Deseja atualizar presença/manter
     viagem atual?"
4. Se a viagem do dia ainda não começou:
   - o admin pode atualizar o manifesto da viagem existente, sem apagar e criar
     outra.
5. Se a viagem já começou:
   - o portal explica que a alteração vale para próximas viagens e orienta
     exceção operacional manual se necessário.

## Cards Planejados

### ADMIN-UX-0001 — Hotfix de mensagens, data e identidade visual da viagem manual
Status: DONE (commit: c486a03, docs: d19871a)

Entregáveis:
- backend responde erros `ResponseStatusException` com `{ code, message }`;
- app ignora `Conflict`/`Bad Request` genéricos e usa fallback contextual;
- campo de data usa contraste correto, formato `dd/mm/aaaa` e date picker dark;
- tela de criação manual explica que é exceção do dia;
- cards claros antigos removidos da criação manual.

Validação:
- `CompanyAdminViagemControllerWebMvcTest`;
- `account_profile_service_test`;
- `admin_viagens_portal_service_test`;
- `admin_viagens_create_widgets_test`;
- smoke visual/manual da tela de criação manual.

### ADMIN-UX-0002 — Arquitetura de informação sem tela técnica de vínculos
Status: PLANNED

Entregáveis:
- remover `Vínculos` da sidebar principal ou rebaixar para área avançada;
- criar seções contextuais em Passageiro, Motorista e Responsável;
- manter rotas/serviços existentes sem quebrar contratos;
- testes de navegação garantindo que ações principais seguem acessíveis.

Validação:
- widget tests do shell admin;
- smoke visual da sidebar;
- QA com tarefa: "encontrar onde vincular passageiro a serviço".

### ADMIN-UX-0003 — Passageiro como ficha operacional completa
Status: PLANNED

Entregáveis:
- ficha do passageiro com dados, endereço/parada, serviços, responsáveis,
  presença do dia e impacto em viagens;
- ação "Adicionar a serviço";
- feedback quando uma viagem do dia já existe;
- remoção de linguagem técnica de vínculo.

Validação:
- tests de `AdminPassageirosScreen`;
- smoke real criando passageiro, vinculando serviço e verificando impacto.

### ADMIN-UX-0004 — Serviço como centro da operação recorrente
Status: PLANNED

Entregáveis:
- detalhe de Serviço com passageiros elegíveis editáveis;
- ação "Montar viagem de hoje" e "Abrir viagem existente";
- aviso claro quando passageiro novo só entra em viagens futuras;
- estado de prontidão operacional: dias, motorista, passageiros, destino.

Validação:
- tests de `AdminServicosRecorrentesScreen`;
- smoke criando serviço, adicionando passageiro e gerando viagem.

### ADMIN-UX-0005 — Viagens do dia orientadas a manutenção, não recriação
Status: PLANNED

Entregáveis:
- lista de viagens com estados claros: planejada, em andamento, concluída,
  cancelada;
- botão primário "Montar dia";
- fluxo de conflito com ação para abrir viagem existente;
- detalhe da viagem com manutenção de manifesto e motorista;
- criação manual rebaixada para "Criar exceção".

Validação:
- tests de `AdminViagensScreen`;
- smoke real: gerar dia, tentar gerar de novo, abrir existente, trocar
  motorista, atualizar presença.

### ADMIN-UX-0006 — Motorista e convite com visão de empresa
Status: PLANNED

Entregáveis:
- ficha do motorista com empresa, status de vínculo, convites/solicitações e
  viagens/serviços atribuídos;
- fluxo de convite por link dentro da ficha;
- linguagem clara para motorista já cadastrado vs novo motorista.

Validação:
- tests de `AdminMotoristasScreen`;
- smoke real dos dois caminhos: link direto e solicitação manual.

### ADMIN-UX-0007 — Sistema visual admin dark/liquid consistente
Status: PLANNED

Entregáveis:
- inventário de superfícies claras/remanescentes;
- tokens e componentes de portal com estados hover/focus/disabled coerentes;
- snackbars, dialogs e date picker no estilo dark;
- redução de cards decorativos e textos explicativos excessivos.

Validação:
- `flutter analyze`;
- widget snapshots/screenshots das principais páginas;
- checklist de contraste e texto sem corte.

### ADMIN-UX-0008 — Validação visual e fluxo real do portal inteiro
Status: PLANNED

Entregáveis:
- roteiro Playwright/manual para login, bootstrap, criação de entidades,
  serviços, viagens e conta;
- screenshots por tela principal;
- QA Log com PASS/LIMITES/PENDÊNCIAS;
- lista final de melhorias pós-S2, sem bloquear o fluxo funcional.

Validação:
- app web/local ou Flutter desktop rodando;
- backend DEV/staging com banco limpo;
- clique real nos fluxos críticos.

## Definition of Done da Fase S2
- Upload de foto tem mensagem útil e contrato validado.
- Portal não mostra erros HTTP crus como `Conflict`.
- Admin entende onde criar passageiro, motorista, responsável e serviço.
- Admin entende como adicionar passageiro a serviço sem apagar viagem.
- Viagens do dia diferenciam geração automática, manutenção e exceção manual.
- Não há tela principal obrigatória chamada `Vínculos`.
- Todas as telas principais seguem a identidade dark/liquid.
- QA visual e funcional executado com evidências.
