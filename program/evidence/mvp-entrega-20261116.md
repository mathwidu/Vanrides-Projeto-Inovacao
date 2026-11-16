# Entrega técnica do MVP — 16/11/2026

## Integração da presença

Os PRs #113 e #121 foram integrados com a autoria e os ancestrais preservados.
O aviso ao motorista usa V42; V41 continua sendo o cadastro por convite.
A integração inclui seleção por data/sentido, lista do motorista, corte da
alteração e avisos de saída/cancelamento com revalidação antes do envio.

A frente registrou 250 casos Java em 27 classes em rodadas direcionadas,
com zero falhas, erros ou pulos no conjunto final dos relatórios. A primeira
rodada revelou uma expectativa antiga de migração e uma restrição do Docker
local; os 23 casos afetados foram repetidos após as correções. Não é uma
execução única limpa da suíte completa. Também passaram 714 testes Node,
303 Python, 46 verificações de wiring e 11 do agrupamento PostgreSQL.
Esses resultados cobrem o laboratório, sem provar entrega real dos novos
avisos no WhatsApp. O recibo detalhado permanece privado no Mac.

## Recuperação de conversa pela equipe — #80

A consulta informa se a conversa está pausada e identifica o caso atual.
A retomada exige sessão GitHub ativa, CSRF, contato confirmado pela Evolution,
coorte correta e perfil ativo ou em cadastro. A transação encerra somente o
caso selecionado, invalida confirmações antigas, preserva os dados de cadastro,
o aceite e as permissões, e registra o identificador GitHub do operador.
Repetir uma retomada antiga não encerra uma nova pausa.

Verificações executadas no candidato local:

- `TeamConversationRecoveryIntegrationTest`: quatro cenários PostgreSQL,
  incluindo preservação de permissões, rollback após perda da sessão,
  repetição contra uma nova pausa e recusa de perfil/coorte incompatível.
- `WaTeamWebBoundaryTest` e `WaTeamSessionsTest`: oito casos aprovados.
- `node --test scripts/homologacao/team_ui_test.mjs scripts/homologacao/team_simulator_test.mjs`:
  15 casos aprovados, incluindo vínculo entre telefone/caso e proteção CSRF.
- `python3 src/backend/scripts/qa_test_groups_test.py`: 11 casos aprovados;
  a matriz inclui a nova classe PostgreSQL.
- `python3 scripts/check_repository.py`: links, backlog e documentos válidos.

A primeira execução identificou somente o tipo de erro esperado para coorte
incompatível; a expectativa foi corrigida para `ADMISSION_CONFLICT` e os quatro
casos PostgreSQL passaram na repetição. O armazenamento temporário em memória
foi usado apenas no ensaio local devido ao disco do Docker e removido dos
fontes. A publicação e a conferência pelo WhatsApp ainda estão pendentes.

## Limites desta etapa

A revisão integrada, a implantação das novas migrações e a jornada do candidato
publicado ainda precisam ser executadas. Percurso físico, medição de bateria,
utilidade do aviso e aceite final ficam para a rodada do proprietário. As
issues correspondentes permanecem abertas até existir essa evidência.
