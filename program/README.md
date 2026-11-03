# Programa VanRides

Esta pasta mantém o estado operacional, os contratos dos cards, as decisões e
as regras de promoção. No monorepo acadêmico, a entrada para contribuir é
[CONTRIBUTING.md](../CONTRIBUTING.md); o controle é descrito em
[docs/desenvolvimento/programa.md](../docs/desenvolvimento/programa.md).

## Autoridade e estado

1. `program/program.json`: estado atual, prioridade, dependências e único WIP.
2. `program/cards/*.md`: contrato executável de cada entrega.
3. `program/repositories.json`: módulos, raiz Git, baselines e gates.
4. `program/RISK_REGISTER.md` e `program/decisions/`: riscos e decisões.
5. Backlogs locais, issues e planilha: detalhamento e projeções colaborativas.

A migração não conclui o roadmap legado, não aceita cards parcialmente
implementados e não reclassifica evidências históricas como teste do monorepo.
Piloto e produção permanecem **HOLD**. Testes físicos de iOS estão pausados pelo
proprietário. A centralização foi verificada e concluída no seu escopo; o recibo específico
identifica a publicação e os testes da nova árvore.

## Operação diária

Na raiz do checkout, com Git e Python 3.9 ou superior:

```sh
python3 scripts/program_control.py dashboard --workspace core
python3 scripts/program_control.py summary --workspace core
python3 scripts/program_control.py check --workspace core
python3 scripts/program_control.py next --workspace core
```

O perfil `core` inclui coordenação/WhatsApp, backend e aplicativo do motorista.
Mapas segue no catálogo, adiado; não clonar, instalar ou executar. Sem
`--workspace`, o modo completo continua exigindo mapas. O perfil aplica-se à
disponibilidade local e ao Git; todos os cards, dependências, estados, evidências
e contratos continuam verificados. Cards executáveis que exijam mapas são
rejeitados no perfil core.

`workspaces/harness.json` é a configuração portátil versionada. O comando
`dashboard` cria `.dev-harness/status.json` e o dashboard local quando ausentes.
Uma projeção já existente não é sobrescrita para esconder divergência: depois
de transitar o card, atualizar `active_card` no status local antes de gerar o
dashboard. Esses arquivos são ignorados e não provam execução externa.

## Ciclo e evidência

1. Selecionar um card `ready`, sem blockers e com Definition of Ready completa.
2. Conferir Impact Analysis, caminhos permitidos, dependências e rollback.
3. Mover o único card vertical para `in_progress` e implementar incrementos.
4. Executar gates e smoke da mesma versão; registrar evidência e QA Log.
5. Concluir no escopo comprovado ou registrar a espera/bloqueio real.
6. Promover por etapa própria, com autorização e evidência correspondentes.

`done` exige critérios concluídos, commits completos e evidência específica em
`program/evidence/<CARD_ID>.md`. Baseline ou evidência de dependência não
substitui a do card. Uma execução antiga, outro SHA, stub, build ou gate de
controle não aprova o código atual, iOS físico, canal WhatsApp, piloto ou produção.

Sem card executável, `activeCardId` e `nextCardId` podem ser `null`; itens
`analyzed` não são promovidos automaticamente. `next` falha antes de anunciar
um card com contrato, dependências, estado ou blockers inválidos.

## Histórico e documentos

- `OPERATING_MODEL.md`: estados, WIP, DoR/DoD e exceções.
- `DELIVERY_PIPELINE.md` e `QUALITY_GATES.md`: lanes e critérios G0 a G7.
- `ROADMAP.md`, `BASELINE.md`, `LEGACY_CROSSWALK.md`: histórico contextualizado.
- `cards/`, `decisions/`, `privacy/`: contratos e decisões preservados.
- `evidence/`: recibos históricos sanitizados e futuras evidências atuais.
- `archive/MIGRATION_HISTORY.md`: limites da importação e acervo.
- `templates/`, `releases/`: modelos e manifests de promoção.

O resultado correto para prova ausente continua sendo `BLOCKED`, `HOLD`,
`SKIPPED` ou `WAIVED`, sem um PASS implícito.
