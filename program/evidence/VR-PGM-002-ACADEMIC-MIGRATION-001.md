# VR-PGM-002-ACADEMIC-MIGRATION-001 — Evidência de centralização

## Resultado e revisão

Data: 2026-11-02. PASS no escopo de importação, organização, CI e publicação
acadêmica. Commit integrado: `b6ff35904ea832bbf6f4bdc208372bdf52ce92cc`. Candidato publicado:
`d27d577365d6b3e4ca334a63217db7f253d63580`. [PR #48](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/48).
Os commits de fechamento documental posteriores não alteram os runtimes testados.

## Execuções atuais

| Workflow | Resultado | Recibo remoto |
|---|---|---|
| Aplicativo motorista | PASS | [Execução 41556631032](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631032) |
| Backend e integração sintética | PASS | [Execução 41556631198](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631198) |
| Documentação e backlog | PASS | [Execução 41556631237](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631237) |
| Programa | PASS | [Execução 41556630964](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556630964) |
| WhatsApp, workflows e receptor sintético | PASS | [Execução 41556631186](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631186) |

Todos os registros acima pertencem ao commit integrado. O PR também foi aprovado
na CI sobre seu merge sintético `c3808398606903695abf745afaedd031f7b0a7c4`,
árvore `ac37458f7a5fd6448663f6325f02167242dd3c7d`, idêntica à do candidato.
Não se usou resultado de outro repositório como aprovação do código importado.

Comandos executados pelos workflows: `python3 scripts/check_repository.py`,
`python3 scripts/backlog_test.py`, `python3 scripts/program_control_test.py`,
`python3 scripts/workspace_profile_test.py`, `python3 scripts/check_whatsapp.py`,
`bash scripts/check_backend.sh --integration-only`,
`cd src/backend && ./scripts/ci_quality_gate.sh`,
`bash scripts/check_app.sh`; builds e inspeções conforme workflows versionados.

## Publicação e colaboração

Inventário inicial, origem/autoria e histórico acadêmico preservados. Fontes,
testes e guias portáveis publicados. Issues: 23 atualizadas, 54 criadas, 24
encerradas preservadas integralmente, 12 abertas históricas. Todas as 65 tarefas
atuais têm issue e três critérios de conclusão. Estado, responsáveis e labels
anteriores preservados; nenhuma conclusão de produto inferida da importação.
A conferência remota das 77 issues criadas ou atualizadas resultou em PASS e 101 issues totais,
sem contar PRs. Planilha e JSONs foram sincronizados; verificador compara as
520 células de dados e preserva as duas abas acadêmicas.

O fechamento de planilha, guias e relatório está no [PR #103](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/103).
Seu head documental `393f9f606c057f5e67467ef51f83910d537fc9aa` teve PASS no
[check Windows/Linux](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41558455969)
e no [controle do programa](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41558455999).
Essa prova documental é separada dos testes dos runtimes no commit integrado acima.
O restante do PR registra o encerramento do controle e recebe seus próprios checks.

## Escopo, limites e recuperação

Sem deploy, compra, envio real, alteração de VPS ou testes físicos do iPhone.
Nenhum app pessoal foi fechado. Builds sem assinatura e simulações não provam
GPS em campo, bateria, canal WhatsApp ou aceite de usuários. Piloto/produção
continuam HOLD. Preparação das máquinas e revisão humana da equipe pendentes.

Origens preservadas e mudança de fonte principal registrada. Rollback por commit,
sem force push e sem sobrescrever contribuições do grupo. Evidências anteriores
continuam históricas. O recibo detalhado está em docs/desenvolvimento/migracao.md.
