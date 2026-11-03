# VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001 — Endurecer integridade e concorrência da vigência de elegibilidade

## Resultado esperado

Depois que todos os writers adotarem a data operacional, o PostgreSQL e a
aplicação passam a impedir estados temporais/tenant incoerentes e atualizações
concorrentes perdidas. Dados legados são reconciliados somente por política
aprovada e preflight sanitizado; eventual requisito de histórico real recebe
modelo próprio, sem ser simulado pela linha corrente.

## Estado

`analyzed` — risco e divisão de entrega identificados. Não está pronto para
implementação nem autoriza leitura/mudança em banco persistente. A prova local
poderá ser desenvolvida sem acessar ambiente real; preflight real é gate de
promoção, não dependência para escrever e testar a solução.

## Risco ou objetivo associado

`R-022`, épico `VR-BE-008`, gates `G2 — CONTRACTED`, `G5 — OPERABLE` e
`G6 — PILOT_READY`.

## Contexto

A auditoria de `VR-BE-OPERATIONAL-DATE-ADOPTION-001` mostrou que corrigir os
writers não encerra a dívida estrutural:

- V16 permite `data_inicio` e `data_fim` nulos e sem coerência de intervalo;
- linhas legadas foram criadas com `data_inicio = NULL`;
- não há constraint que prove o mesmo tenant entre associação, serviço e
  passageiro;
- existe uma única linha por serviço/passageiro, logo não há histórico de
  múltiplos períodos;
- `PassengerServiceEligibilityManagementService` depende hoje de transação
  externa;
- não há lock nem `@Version`;
- dois creates podem disputar a unique constraint sem resposta contratada;
- replaces conflitantes podem causar lost update;
- `count(active)+1` pode produzir ordem concorrente não determinística.

Esses problemas não devem ampliar o writer-adoption card. Código, preflight e
migration fail-closed podem ser desenvolvidos e testados localmente com dados
sintéticos após a decisão humana; aplicação ou reconciliação em banco
persistente exige preflight real, autorização e plano de promoção próprios.

## Impact Analysis inicial

### Mudanças técnicas candidatas

- ADR sobre projeção corrente versus histórico imutável;
- preflight sanitizado de nulls, intervalos, estado, tenant e duplicidade;
- migration forward-only após sequência de rollout compatível;
- invariantes de início, fim e estado no PostgreSQL;
- coerência tenant por chaves/constraints estruturais;
- `@Version`, lock ou outro protocolo explícito contra lost update;
- convergência/`409` determinístico para corridas de criação e replace;
- ordem estável e transação integral do lote;
- matriz PostgreSQL, contract test do gate e smoke same-version.

### Contratos afetados

O wire de sucesso deve permanecer compatível. Erros concorrentes hoje não têm
contrato confiável; a ADR deve decidir quando a operação idêntica converge e
quando conflito não convergente retorna `409`, sem vazar SQL ou existência
cross-tenant.

Se Produto exigir histórico as-of, novas tabelas e APIs serão uma entrega
separada ou uma ampliação explicitamente aprovada; a linha corrente não será
apresentada como história.

### Riscos

- backfill fabricar uma data pessoal/operacional sem evidência;
- constraint interromper rolling deploy porque réplica antiga ainda escreve
  null;
- lock order introduzir deadlock;
- retry duplicar efeitos ou esconder conflito real;
- `@Version` quebrar writers antigos ou serialização;
- constraint cross-tenant falhar por legado real;
- logs/preflight exporem passageiro, serviço ou tenant;
- migration irreversível ser promovida sem backup e forward-fix.

### Mitigação

- writer adoption como dependência concluída;
- rollout em fases, expand-compatible, congelado em ADR;
- preflight somente com contagens/categorias sanitizadas;
- nenhuma escolha automática de vencedor, início ou tenant;
- PostgreSQL descartável percorrendo toda a cadeia real de migrations até a
  nova versão, com dados adversos;
- testes concorrentes repetidos e ratcheteados, sem skip;
- lock order documentado e timeout explícito;
- backup/restore e forward-fix antes de ambiente persistente;
- promoção humana separada e `HOLD` diante de qualquer ambiguidade.

### Validação mínima

- contract tests de preflight e migration real;
- banco rejeita null, intervalo, estado e tenant incoerentes conforme ADR;
- criação simultânea converge ou retorna conflito contratado;
- replaces conflitantes não perdem update;
- falha num item reverte o lote;
- gate canônico limpo, matriz PostgreSQL sem skip e smoke same-JAR;
- preflight autorizado em cada ambiente persistente antes de promoção;
- evidência sem IDs pessoais, e-mails, GPS ou SQL com valores brutos.

### Rollback

Depois de aplicada, a migration numerada no DOR não será editada nem removida.
Rollback de código deve continuar compatível com o schema; correções são
forward-only. A sequência de deploy precisa impedir retorno a writer que
produza dados inválidos. Qualquer reconciliação de dado exige backup, auditoria
e plano de reversão próprios.

## Escopo

- decisão do modelo temporal e contrato de concorrência;
- preflight e política de reconciliação de legado;
- integridade temporal e tenant no banco;
- fronteira transacional e proteção contra lost update;
- corrida de criação e ordenação;
- testes PostgreSQL/gate/smoke, runbooks e evidência.

## Fora de escopo

- inferir datas históricas de `created_at` sem aprovação;
- alterar V16;
- consultar ou modificar banco persistente sem autorização;
- retenção/lifecycle geral de passageiro, rastreado por `VR-PRIV-003`;
- deploy, cutover, piloto ou produção dentro do gate local;
- APIs históricas, salvo decisão explícita que crie card próprio.

## Dependências

- `VR-BE-OPERATIONAL-DATE-ADOPTION-001` concluído;
- `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` concluído antes de `ready`,
  para que qualquer escolha por tabela/API histórica nova entre como D32 com
  manifest, owner, decisão e lifecycle; manter a projeção corrente registra
  explicitamente que nenhuma nova coleta/store foi criada;
- aceite da ADR-004 ainda não escrita;
- decisão de Produto/Dados sobre projeção atual versus histórico;
- contrato fail-closed do preflight e política de reconciliação;
- nenhum card `analyzed` reserva número Flyway; ao entrar em `ready`, revalidar
  o maior número presente/aplicado, reservar o próximo contínuo no DOR e provar
  ausência de collision/out-of-order;
- plano de rollout/rollback compatível com writers antigos.

## Definition of Ready

- [x] falhas estruturais e consumidores conhecidos;
- [x] separação do writer adoption definida;
- [x] riscos de migration, dados e concorrência identificados;
- [ ] requisito de histórico decidido;
- [ ] escolha por histórico novo possui manifest D32 e lifecycle; escolha por
  projeção corrente prova que não adiciona categoria, store ou provider;
- [ ] semântica de null/início/fim/estado aceita;
- [ ] contrato de convergência e conflito concorrente aceito;
- [ ] contrato do preflight sanitizado e de aborto aceito;
- [ ] próximo número Flyway revalidado e reservado somente no freeze de
  `ready`, sem colisão, lacuna artificial ou execução out-of-order;
- [ ] estratégia de rollout, backup, restore e forward-fix congelada;
- [ ] matriz de arquivos/testes atualizada após a decisão.

## Critérios de aceite iniciais

- [ ] nenhuma linha nova pode nascer com estado temporal incoerente;
- [ ] legado ambíguo interrompe migration sem alteração parcial;
- [ ] preflight e evidência não expõem dados pessoais;
- [ ] constraints representam exatamente a ADR aceita;
- [ ] coerência de tenant é estrutural ou possui compensação explícita;
- [ ] duas criações simultâneas não produzem 500/SQL não tratado;
- [ ] replaces concorrentes não causam lost update silencioso;
- [ ] ordem e lote são determinísticos e transacionais;
- [ ] versão antiga incompatível não pode voltar após enforcement;
- [ ] migration real, testes concorrentes, gate e smoke passam na mesma versão;
- [ ] runbooks de preflight, backup, forward-fix e rollback são executáveis;
- [ ] cada ambiente persistente continua em `HOLD` até executar seu próprio
  preflight autorizado e produzir contagens sanitizadas aceitáveis;
- [ ] histórico não é prometido se o modelo continuar sendo projeção corrente;
- [ ] piloto e produção permanecem `HOLD` até promoção separada.

## Plano sequencial

1. concluir e provar writer adoption;
2. obter decisão Produto/Dados sobre história e política de estados legados;
3. escrever ADR-004 e DOR com o contrato sanitizado de preflight;
4. reconciliar o registro de migrations, atribuir o próximo número seguro e
   congelar rollout em fases/compatibilidade com writers antigos;
5. escrever testes RED de migration, constraints e concorrência;
6. implementar preflight/migration e controle de concorrência em incrementos;
7. executar matriz PostgreSQL repetida e gate canônico limpo;
8. executar smoke same-JAR com casos adversos;
9. sincronizar API Matrix, runbooks, QA Log, risco e evidência;
10. executar preflight real e promover ambiente por ambiente somente após
    autorização humana e backup.

## Evidências

| Evidência | Local | Estado |
|---|---|---|
| descoberta | `program/evidence/VR-BE-OPERATIONAL-DATE-ADOPTION-001-DOR.md` | observado |
| decisão | `program/decisions/ADR-004-eligibility-period-integrity.md` | não criada |
| DOR | `program/evidence/VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001-DOR.md` | não criada |
| prova final | `program/evidence/VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001.md` | não criada |

## Encerramento

- commits: nenhum;
- QA Log: pendente;
- backlog local: pendente;
- documentação: card de descoberta criado;
- blocker: decisão de modelo e preflight autorizado;
- produção: `HOLD`.
