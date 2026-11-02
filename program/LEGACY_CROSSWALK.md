# Relação com Fontes Anteriores

> Histórico preservado da coordenação anterior à migração de 2026-11-02. Prioridade e estado atuais: `program/program.json`; evolução fora do MVP não está concluída por esta importação. Topologia e entrada atuais: [programa no monorepo](../docs/desenvolvimento/programa.md).

Nada é apagado durante a implantação do programa. As fontes anteriores são
classificadas para preservar contexto sem permitir conflito de autoridade.

| Fonte | Classificação | Uso futuro |
|---|---|---|
| `src/backend/backlog/` | histórico e detalhamento local | critérios técnicos, QA Log e cards concluídos |
| `src/aplicativo-motorista/backlog/` | histórico e detalhamento local | critérios técnicos, QA Log e cards concluídos |
| `vanrides-maps-platform/backlog/` | histórico e detalhamento local | critérios técnicos, QA Log e cards concluídos |
| `docs/` e planos extensos existentes | referência/snapshot | pesquisa, decisões e evidência histórica |
| `.dev-harness/dashboard.md` | projeção gerada | visão rápida, nunca autoridade de release |
| vault VanRides | memória e navegação | contexto durável e decisões sincronizadas |
| GitHub Issues/Projects futuros | projeção colaborativa | execução em equipe e integração externa |
| `program/` | canônico | prioridade, estado, risco, gate e promoção |

## Regra de migração

Ao reencontrar um item legado:

1. verificar se continua relevante no código atual;
2. associá-lo a um épico/card canônico;
3. classificá-lo como ativo, concluído, cancelado ou substituído;
4. manter link para a origem;
5. não copiar estados contraditórios para mais de um lugar.

O épico `VR-PGM-002` fará a reconciliação sistemática das fontes legadas.
