# VR-PGM-002-ACADEMIC-MIGRATION-001 — Centralizar código e contribuição no repositório acadêmico

## Resultado esperado

O grupo encontra código, documentação, backlog com critérios e comandos de teste em mathwidu/Vanrides-Projeto-Inovacao. A origem de novas contribuições fica inequívoca e a publicação não implanta nada na VPS.

## Estado

`in_progress`. Autorizado pelo proprietário em 02/11/2026: organizar e subir tudo no repositório acadêmico, atualizar issues e preparar contribuição. Há uma única frente vertical; frentes delegadas são partes desta migração.

## Impact Analysis

Coordenação base c1e0546; backend e25ed4e; app b9fcedb. Árvores originais limpas no início. Destino clonado de main com histórico acadêmico preservado; nenhuma edição de colega será sobrescrita sem reconciliação. Portar fontes rastreados e entradas de testes por manifesto, excluindo arquivos privados, artefatos, caches e histórico privado.

Contratos HTTP, auth, schema e comportamento de produto permanecem. Caminhos, gates, configuração local, topologia Git, CI e documentação mudam na árvore de destino. Riscos: caminhos relativos, gate dependente de Git por módulo, fonte faltante, informação privada publicada, estado de tarefa inflado e duas origens concorrentes.

Mitigação: inventário por arquivo, revisão de conteúdo, teste da árvore nova, CI sem deploy, ambiente simulado sem credenciais, recibos por SHA e conferência remota. Responsabilidade: principal integra governança, backlog, issues e publicação; executor backend/bot e executor app possuem diretórios separados. iPhone físico pausado; apps pessoais abertos; nenhuma compra.

## Contratos

Preservar contratos HTTP, auth, schema, GPS, estados e payloads. Adaptar somente topologia, caminhos, receitas de teste e documentação na cópia.

## Riscos

Perder entradas de teste, publicar dados privados, enfraquecer gates por caminho Git ou manter duas origens. Inventário, revisão e CI da árvore nova são obrigatórios; implantação permanece separada.

## Escopo

- Coordenação: program/program.json, este card e evidência, docs/faculdade/retorno-2026-11-03/, docs/workspace/, README.md, AGENTS.md, scripts de migração, dashboard/harness.
- Destino: README/CONTRIBUTING/AGENTS, documentos acadêmicos, src/backend, src/aplicativo-motorista, src/whatsapp, programa adaptado, scripts, exemplos locais, CI e templates. GitHub: branch/PR/merge e issues do repositório explicitamente indicado.
- Não alterar código nas origens. Registrar mudança de origem somente após CI e reconciliação final.

Na cópia do monorepo, `repositoryRoot=.` identifica o Git compartilhado e
`moduleRoot` mantém `src/backend` e `src/aplicativo-motorista` separados; WhatsApp
fica sob coordenação em `src/whatsapp`. O escopo executável deste card está em
`program.json`, inclui a transferência completa dos dois módulos e não altera o
estado dos demais cards. A configuração portátil fica em `workspaces/harness.json`.

## Fora de escopo

Deploy/VPS, DNS, aquisição de linha, convites ou mensagens ao grupo, mapas adiado, testes físicos iOS, piloto real e atribuição de tarefas sem combinação humana.

## Dependências

Nenhuma dependência de produto impede esta migração. Acesso pessoal de escrita ao destino confirmado; histórico e base serão registrados antes da cópia. Número do bot não é necessário para testes sintéticos.

## Definition of Ready

- [x] Resultado, Impact Analysis, contratos, riscos e rollback definidos.
- [x] Destino autorizado e acesso confirmado, sem card concorrente ativo.
- [x] Fontes e dirty paths identificados, propriedade separada e validação definida.

## Plano sequencial

1. Congelar inventário de origem e HEAD do destino; criar branch de migração.
2. Transferir fontes/testes/exemplos necessários e preservar atribuições; revisar conteúdo para publicação pública.
3. Adaptar módulos, controle e CI; criar guia Windows/macOS com comandos reproduzíveis.
4. Revisar backlog por objetivo, critérios, prova e próximo passo; preparar e aplicar issues sem sobrescrever contribuições concorrentes.
5. Testar localmente o que for leve e publicar candidato com CI padrão pública; corrigir até comprovar a árvore nova.
6. Integrar PR autorizado, conferir remoto e registrar corte das origens; atualizar documentação e evidência.

## Critérios de aceite

- [ ] Fontes necessárias presentes e manifest identifica origem e adaptações, sem segredos ou submódulos privados.
- [ ] CI do mesmo SHA aprovada para backend/PostgreSQL, bot/contratos, app e builds de preparação.
- [ ] Guia permite começar sem chave privada, com limites Windows/macOS e físicos explícitos.
- [ ] Issues atualizadas com escopo, critérios e prova; histórico, responsáveis e labels preservados.
- [ ] Docs/backlog coerentes, um destino de desenvolvimento explícito e publicação conferida.

## Validação

Gates proporcionais por módulo, testes do programa, links e manifest; CI Linux/macOS/Windows quando pertinente, sem deploy. Backend requer PostgreSQL sem skips. Android e iOS recebem builds, não aceites físicos. Evidência canônica: program/evidence/VR-PGM-002-ACADEMIC-MIGRATION-001.md.

## Rollback

Antes de publicar, abandonar branch e manter origens. Após publicação, reverter o PR preservando histórico e issues por snapshot. Depois de contribuição alheia, reconciliar commits antes de qualquer retorno, sem force push. Serviços VPS permanecem na versão atual.

## Encerramento

Somente após resultados e URLs reais. Limitações de acesso ou máquinas dos colegas serão explícitas e não convertidas em aprovação humana.
