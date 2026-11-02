# Auditoria UX do Portal Admin - 2026-06-18

## Objetivo
Validar o portal administrativo em um fluxo real de empresa com dados
operacionais e registrar os próximos refinamentos para deixar a experiência
mais simples, premium e funcional.

## Referências usadas como régua
- Apple Human Interface Guidelines: continuidade visual, hierarquia clara,
  materiais translúcidos com contraste e transições que não confundem estado.
- Material Design 3: navegação lateral persistente em ferramentas
  operacionais, estados de botão claros e feedback visível.
- Nielsen Norman Group: dashboards devem priorizar decisões acionáveis,
  reduzir ruído e evitar métricas que não levam a uma ação clara.

## Fixture validada
- Backend DEV: `http://127.0.0.1:8080`.
- Empresa: `adminux0034-133035`.
- Admin: `op.adminux0034.133035@vanrides.dev`.
- Dados criados:
  - 2 motoristas.
  - 2 passageiros.
  - 1 responsável vinculado.
  - 3 serviços recorrentes.
  - 3 viagens no dia, incluindo viagem em andamento, planejadas e serviço que
    exigia escala manual.

## Evidência visual
Diretório principal:
`output/admin-portal-ux-0034/visual-fixture-pass/`

Screenshots principais:
- `admin-dashboard.png`
- `admin-trips.png`
- `admin-services.png`
- `admin-drivers.png`
- `admin-passengers.png`
- `admin-passenger-services.png`
- `admin-passenger-guardians-dialog.png`
- `admin-guardian-passengers-dialog.png`
- `admin-account.png`

## Resultado funcional
- Login/admin com fixture existente: PASS.
- Painel: PASS.
- Viagens do dia: PASS.
- Serviços recorrentes: PASS.
- Motoristas: PASS.
- Passageiros: PASS.
- Responsáveis: PASS.
- Conta: PASS.
- Diálogos contextuais de vínculo responsável-passageiro: PASS.
- `Vínculos avançados` fora da navegação comum: PASS.

## Achados de UX
1. O portal está visualmente coerente no padrão dark/liquid, mas ainda pode
   reduzir densidade em telas com muitas entidades.
2. `PERFIL COMPLETO` em passageiros aparece como 0% na fixture porque nascimento
   e foto não estão preenchidos; isso é funcionalmente verdadeiro, mas ainda
   comunica pouco sobre a ação necessária.
3. A ficha do passageiro funciona, porém mistura muitos blocos: vínculo
   operacional, contato, endereço, serviços e responsáveis. A informação está
   correta, mas a hierarquia ainda pede revelação progressiva.
4. A tela de viagens melhorou muito, mas ainda pode destacar melhor a pergunta
   central do admin: "o que preciso revisar hoje?".
5. O smoke E2E anterior gerou falsos negativos quando rodado sem fixture
   operacional. O script foi corrigido parcialmente, mas ainda merece virar um
   relatório de QA determinístico.

## Próximos cards
1. Backlog 1016 - Perfil completo como checklist acionável.
2. Backlog 1017 - Fichas de pessoas com revelação progressiva.
3. Backlog 1018 - Dia operacional com menos densidade visual.
4. Backlog 1019 - Roteiro E2E visual com fixture determinística.

## Decisão
O portal admin está funcionalmente validado para a fase atual, mas ainda não
deve ser considerado "requintado". O próximo ciclo deve focar em reduzir carga
cognitiva, transformar métricas em ações e melhorar a leitura de estado em
cadastro/perfil/viagens.
