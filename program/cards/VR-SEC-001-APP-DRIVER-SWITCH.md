# VR-SEC-001-APP-DRIVER-SWITCH — Isolar troca e remover fallback não governado

## Resultado esperado

Quando o painel ao vivo recebe outro `motoristaId`, nenhum ponto, resposta REST
ou evento realtime do motorista anterior continua visível ou consegue atualizar
o estado do novo acompanhamento. Sem viagem operacional selecionada, o app não
usa `session.motoristaId` como autorização implícita nem tenta exibir GPS.

## Estado

`analyzed`

## Risco ou objetivo associado

`R-001`, `R-007`, épico `VR-SEC-001` e gate `G1 — TRUSTED`.

## Contexto

A revisão do consumidor de `VR-SEC-001-BE-READ` identificou que
`TripLiveMapPanel.didUpdateWidget` não reinicializa integralmente o estado quando
`motoristaId` muda. Uma resposta assíncrona ou snapshot do motorista anterior
pode permanecer na tela durante a troca de viagem, expondo localização incorreta
e confundindo a jornada do passageiro.

A revisão de segurança do backend também provou que `Passageiro.motorista` não
possui proveniência confiável e não pode autorizar localização. Como o backend
passará a exigir `ViagemParada`, o app deve remover o fallback de sessão e
representar explicitamente o estado sem viagem operacional.

O app possui 49 alterações preexistentes fora deste fluxo. A implementação deve
ocorrer em worktree limpa no SHA registrado pelo programa e só depois ser
integrada de forma controlada.

## Impact Analysis inicial

### Mudanças previstas

- detectar mudança de `motoristaId` no ciclo de vida do painel;
- remover o fallback de `widget.motoristaId` quando não existe viagem atual;
- exibir estado sem viagem/sem rastreamento sem disparar leitura de GPS;
- cancelar ou invalidar requisições REST anteriores por geração/identidade;
- usar `viagemId + locationScopeVersion` como geração canônica quando a troca é
  reassign da mesma viagem;
- descartar eventos realtime cujo motorista/viagem não corresponda ao estado
  ativo;
- limpar marcador, freshness, erro e loading antes de carregar o novo alvo;
- cobrir troca rápida A → B → A e dispose durante resposta pendente.

### Contrato

Nenhuma rota ou payload backend muda. O contrato interno do widget passa a
garantir que estado visual e callbacks assíncronos pertencem ao `motoristaId`
mais recente; somente o motorista retornado pela viagem operacional pode iniciar
o acompanhamento do passageiro.

### Riscos e mitigação

- flicker ou mapa vazio durante a troca: definir estado explícito de loading;
- resposta atrasada repovoar o alvo antigo: usar token monotônico de geração;
- subscription anterior continuar ativa: encerrar antes de assinar a nova;
- misturar alterações preexistentes: usar worktree limpa e staging explícito.

### Rollback

Reverter a lógica de ciclo de vida e os testes do painel. Não há migration,
alteração de API ou mutação de dados.

## Dependências

- `VR-SEC-001-BE-READ` concluído;
- `VR-APP-001-LOCATION-ADOPTION-001` concluído, com `viagemId`, destinos por
  usuário e estados terminais já canônicos;
- worktree limpa do app criada a partir do SHA canônico;
- Impact Analysis final dos adapters REST/realtime antes da implementação.

## Critérios de aceite iniciais

- [ ] trocar `motoristaId` remove imediatamente a localização anterior;
- [ ] incrementar `locationScopeVersion` na mesma viagem invalida respostas,
  eventos e marker da versão anterior e exibe vazio até nova amostra;
- [ ] ausência de viagem não usa `session.motoristaId` nem consulta GPS;
- [ ] estado sem viagem é compreensível, acessível e não aparece como falha de
  autorização;
- [ ] resposta REST atrasada do motorista anterior é ignorada;
- [ ] evento realtime atrasado do motorista anterior é ignorado;
- [ ] somente a nova subscription permanece ativa;
- [ ] troca rápida e dispose não geram setState tardio ou vazamento;
- [ ] testes de widget/serviço, analyze, gate e smoke visual passam;
- [ ] alterações preexistentes do app permanecem intactas;
- [ ] backlog, QA Log, evidência e programa ficam sincronizados.

## Arquivos previstos

- `src/aplicativo-motorista/lib/features/tracking/presentation/trip_live_map_panel.dart`
- `src/aplicativo-motorista/lib/features/tracking/presentation/home_passageiro_screen.dart`
- `src/aplicativo-motorista/lib/app/experience/experience_home_router.dart`
- testes correspondentes em `src/aplicativo-motorista/test/features/tracking/`
- `src/aplicativo-motorista/backlog/Flutter-Cards.md`
- `src/aplicativo-motorista/backlog/QA-Execution-Log.md`

## Próximo passo de prontidão

Ler o painel, os adapters REST/realtime e seus testes em worktree limpa; então
fechar a matriz de estados, os caminhos exatos e o smoke antes de mover para
`ready`.
