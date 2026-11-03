# VR-PGM-002-M5-WORKSPACE-001 — Restaurar e organizar o workspace no M5

## Resultado esperado

Coordenação, app e backend recuperados em três repositórios independentes,
com entradas claras para pessoas e agentes e controle local capaz de representar
a decisão do usuário de adiar a plataforma de mapas.

## Estado

`done`

## Contexto

Solicitação do proprietário em 2026-09-02: configurar e organizar os três
repositórios, sem puxar `vanrides-maps-platform` por enquanto. As referências
de migração foram verificadas no GitHub e recuperadas em `codex/organizacao-m5`.
O inventário e as decisões anteriores de produto continuam preservados.

## Impact Analysis

### Contratos afetados

CLI de coordenação: acrescentar `--workspace core` sem mudar o modo completo
padrão. Documentação e adapters dos três repositórios. Nenhuma alteração em
API, payloads, autenticação, UI, WebSocket ou schema do produto.

### Riscos e mitigação

Um recorte local não pode esconder dependência de mapas nem ser prova de release.
Validar todo o estado canônico, rejeitar cards executáveis que dependam de módulo
adiado e manter o check completo exigindo todos os repositórios. Preservar SHAs,
baselines históricas, backlogs e relatórios; testar o comportamento negativo.

### Validação mínima

`git fsck --full` por repositório; autoria, remotos e exclusão dos clones filhos;
suíte do controlador e novos testes de workspace; CLI real com e sem perfil;
integridade dos links novos; diff explícito por repositório. App/backend recebem
somente documentação: análise de links e diff são a validação desta entrega.
Gates Flutter/Java e smoke integrado ficam pendentes da preparação dos runtimes,
e não são apresentados como executados nem como prova desta organização.

## Escopo

- Perfil local explícito com coordenação, app e backend; mapas adiado.
- README de entrada, índices técnicos, mapa do código e política documental.
- Adapters portáveis e referências históricas identificadas.
- Registro da recuperação, validação e requisitos para executar o produto.

## Fora de escopo

Instalação dos SDKs e banco, execução do produto, refatoração funcional, migração
de banco, mapas, VPS, push, deploy, promoção e aceite de decisões de privacidade.

## Dependências

Nenhuma dependência de entrega funcional. Autorização do usuário recebida;
acesso SSH e três referências preservadas verificados.

## Definition of Ready

- [x] resultado, impacto, contratos, riscos e rollback definidos;
- [x] fontes de recuperação verificadas e nenhum card ativo preexistente;
- [x] escopo, arquivos, testes e limites de evidência identificados.

## Critérios de aceite

- [x] três clones íntegros, independentes e com autoria pessoal;
- [x] perfil core funciona e modo completo continua exigindo mapas;
- [x] perfil core rejeita trabalho executável que necessite mapas;
- [x] entradas humanas e de IA documentam fontes, módulos e próximos requisitos;
- [x] evidência e commits locais separados registrados.

## Rollout

Aplicação somente local em branches `codex/organizacao-m5`. Verificar primeiro
o controlador, depois as entradas e índices. Nenhuma instalação ou publicação.

## Rollback

Reverter os commits desta entrega em cada repositório. As referências remotas
preservadas e os SHAs de recuperação permanecem disponíveis. Não apagar os
clones nem descartar alterações locais de outros trabalhos.

## Plano sequencial

1. Recuperar e conferir os três clones.
2. Implementar e testar o perfil de disponibilidade local.
3. Organizar navegação, documentação e orientações por módulo.
4. Validar, registrar evidências e criar commits locais separados.

## Evidências

Registro final: `program/evidence/VR-PGM-002-M5-WORKSPACE-001.md`.

## Encerramento

Organização concluída. Commits e resultados em
`program/evidence/VR-PGM-002-M5-WORKSPACE-001.md`. Gates de execução do
produto permanecem NOT_RUN; preparação dos runtimes é a próxima etapa.
