# ADR-006 — Workspace local com recuperação gradual

Data: 2026-09-02. Estado: **aceita para organização local**, conforme solicitação
do proprietário de preparar coordenação, app e backend e adiar mapas.

## Contexto

O controlador preservado verificava a existência dos quatro clones antes de
permitir a consulta de trabalho. A recuperação gradual solicitada precisa
expressar disponibilidade sem remover mapas do catálogo e do planejamento.

## Decisão

- `workspaces/core.json` declara três repositórios incluídos e a razão de mapas adiado.
- `--workspace core` aplica esse recorte somente à disponibilidade e ao Git local.
- Estado, dependências, contratos, evidências, WIP, harness e ADRs continuam validados para todo o programa.
- Cards `ready`, `in_progress` ou `validating` que exijam módulo adiado são bloqueados no perfil.
- O modo padrão permanece completo; um dashboard core não atende ao check full.
- O dashboard inicializa o arquivo local ignorado do harness apenas se ausente; nunca sobrescreve uma projeção existente.

## Consequências

A organização dos três repositórios pode ser verificada agora. Gates de Flutter,
Java, banco, integração, mapas, piloto e produção permanecem verificações próprias.
Um PASS core não satisfaz promoção nem constitui waiver desses gates.

As baselines do M2 e os 70 cards previamente preservados continuam com o mesmo
conteúdo e status. A organização do M5 recebe um novo card de manutenção.

## Reversão

Omitir a opção para exigir todos os módulos. Restaurar mapas somente após nova
seleção do proprietário; reverter os commits locais se o recurso for abandonado.
