# Centralização para contribuição do grupo

Estado: **publicada e validada**. O desenvolvimento passa a usar
[mathwidu/Vanrides-Projeto-Inovacao](https://github.com/mathwidu/Vanrides-Projeto-Inovacao).
A mudança foi autorizada em 02/11/2026 e integrada pelo [PR #48](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/48).
A implantação na VPS não foi alterada.

## Código e origem

Backend, aplicativo do motorista, bot, automações, contratos e testes estão em
um clone, nas pastas `src/backend`, `src/aplicativo-motorista` e `src/whatsapp`.
Guias atuais ficam em `docs/desenvolvimento`; o planejamento está em `program`.
Novos PRs entram aqui. Os repositórios anteriores ficam como histórico; não
manter cópias paralelas nem exportações que sobrescrevam contribuições da equipe.

A [origem e autoria](origem-e-autoria.md) identifica os três commits de partida.
O [inventário inicial](inventario-migracao.json) registra inclusões e exclusões
na importação; as adaptações posteriores estão nos commits do PR. O inventário
é um recibo inicial, não um manifesto do estado mutável de todos os arquivos.
A entrega acadêmica anterior está preservada em `docs/historico/entrega-anterior`.

Dependências instaladas, builds, ambientes privados, credenciais, assinaturas,
bancos e o histórico Git privado completo ficaram fora da cópia. Evidências
anteriores são recibos sanitizados, ligados às versões de origem.

## Verificação da nova estrutura

Commit integrado e avaliado na branch `main`: `b6ff35904ea832bbf6f4bdc208372bdf52ce92cc`.
Candidato: `d27d577365d6b3e4ca334a63217db7f253d63580`; a CI do PR usou o merge
sintético `c3808398606903695abf745afaedd031f7b0a7c4`, cuja árvore foi conferida.
A tabela abaixo registra as novas execuções do commit integrado, não a CI antiga.

| Frente | Resultado | Evidência |
|---|---|---|
| Aplicativo motorista | PASS | [Execução 41556631032](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631032) |
| Backend e integração sintética | PASS | [Execução 41556631198](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631198) |
| Documentação e backlog | PASS | [Execução 41556631237](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631237) |
| Programa | PASS | [Execução 41556630964](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556630964) |
| WhatsApp, workflows e receptor sintético | PASS | [Execução 41556631186](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/41556631186) |

Os checks incluem a suíte PostgreSQL obrigatória, quatro jornadas HTTP com
serviços reais locais e canal simulado, testes do bot, smoke do n8n, testes
Flutter/nativos, builds Android e iOS sem assinatura, isolamento dos artefatos,
controle do programa e conferência documental no Windows/Linux.

As correções de migração adaptaram caminhos e Git compartilhado; a CI também
exigiu ajustes de rpath do XCTest, alvo ARM64 dos testes Android, uma fixture
HTTPS e compatibilidade da inspeção Docker. O ensaio de integração passou a
respeitar a cota real de mensagens antes de enviar, sem elevar limites do
produto nem aceitar um envio incompleto como sucesso.

Os workflows usam runners padrão públicos, sem publicação de imagens, deploy,
credenciais de produção ou upload de builds. Nenhum serviço pago foi contratado.
[Condições do GitHub Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

## Backlog e trabalho da equipe

As 65 tarefas atuais possuem issues e três critérios cada. Foram atualizadas
23 issues existentes e criadas 54; 24 encerradas permaneceram intactas e 12
abertas foram identificadas como históricas. Responsáveis e labels anteriores
foram preservados. A label `mvp` seleciona o recorte atual.

Planilha, JSONs e documentos usam os mesmos números reais. O formato acadêmico
continua com duas abas e oito colunas. Implementação e validação permanecem
separadas; nenhuma tarefa foi encerrada automaticamente. O guia de contribuição
explica a preparação por frente, primeiro teste, PR e revisão.

## Limites e recuperação

CI não comprova a preparação dos computadores dos colegas, revisão humana de
todo o código, WhatsApp real, percurso, bateria ou utilidade dos avisos. O piloto
e a produção permanecem HOLD; o iPhone físico continua pausado.

As origens e o histórico acadêmico foram preservados. Uma reversão deve ocorrer
por commits, sem force push. Após contribuições no destino, reconciliar esses
commits antes de qualquer retorno; não substituir a árvore por uma cópia antiga.
