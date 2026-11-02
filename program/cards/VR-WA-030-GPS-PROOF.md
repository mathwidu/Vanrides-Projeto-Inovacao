# VR-WA-030-GPS-PROOF — Comprovar localização e viabilidade em iPhone e Redmi Note 8

## Resultado esperado

Prova M1 separada por plataforma, incluindo background, rede, energia, aproximação e parada automática.

## Estado

`waiting_human`. Pacote P01, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

Escopo efetivo após020: coordenação/receptor dedicado e flavor driver do app;
backend Spring permanece fora desta prova. Receptor de publicação
`9fd20d9140291c082a1f8630e94dffbe4ee38ce6`; app de runtime
`02183270813550d020bf3e223d22be798de1fd85`, builds assinados 0.1.0+2.
A instalação física010 não prova esse coletor. [Manifest e evidência020](../evidence/VR-WA-020-GPS-SPIKE.md).

Após os ensaios estacionados, a revisão mobile
`24969587151857ba4c1dadc7e4bd0616546f3e96` acrescenta diagnóstico sanitizado
e confirmação visual de parada, nos builds `0.1.0+3`. O receptor continua em
`9fd20d9`; parâmetros de coleta e critérios de GPS não mudam. Gates, artefatos
e ensaios dessa revisão são registrados separadamente no manifest030.

Em 31/10, a falha de continuidade persistiu após reinicialização do Redmi.
Os ensaios Android `0.1.0+4` (espera inicial desativada) e `0.1.0+5` (pedido único
finito) não produziram posições elegíveis nas janelas de180s. Lease, critérios
de GPS e serviços publicados permaneceram iguais. O coletor original foi
restaurado nos fontes para o build de retorno `0.1.0+6`; não é uma correção
comprovada do GPS. IA, comparação e retorno estão em
`src/aplicativo-motorista/docs/driver-mvp-development.md` e na evidência de aquisição030.

## Autorização e participação humana

Em 31/10, o proprietário pediu para pausar os testes no iPhone físico e avançar
no Android, deixando a retomada iOS preparada. Não executar novos comandos no
iPhone enquanto a pausa estiver vigente. A preparação e a auditoria de
artefatos existentes continuam no Mac. Proprietário confirmou Redmi desbloqueado, USB e internet nesta retomada.
Smoke08 concluído no recorte estacionado/USB; percurso/energia continuam dependendo de participação física. A coleta está parada; essa mudança de prioridade não aprova M1 nem
dispensa os aceites iOS. A ADR-007 permite preparar040 mantendo um único WIP.

Automação solicitada em31/10: preparar executor XCUITest separado para o iPhone,
sem alterar o runtime nem exigir cópia manual. IA, limites e resultados na
[evidência de automação](../evidence/VR-WA-030-GPS-PROOF-ios-automation.md).
GPS direto no Redmi produziu166posições elegíveis/180s; a comparação com o
coletor A06 perdeu continuidade. D07/FLP1s manteve frescor depois da aquisição
na janela de181,4s, mas o início ultrapassou30s. Foi repetido como candidato,
sem encerrar o gate, antes da atualização08. A evidência de aquisição030
separa o probe, cada binário e os resultados de primeiro plano/segundo plano.

Depois das provas D07, o build Android08 acrescenta somente o diagnóstico de
energia. Fonte `9b3c1166877f10c7a106f33d23ed3ef5b555d2d7`, gates718/20 PASS,
APK instalado sem apagar dados; smoke estacionado/USB validado em31/10. O
[manifest08](../evidence/VR-WA-030-GPS-PROOF-android-energy8.json) separa essa
revisão dos resultados D07. iPhone físico permanece pausado.

- Proprietário aprovou explicitamente em 30/10/2026 o [plano concreto](../../docs/runbooks/whatsapp-mvp/020-publicacao-laboratorio.md)
  da revisão acima, GitHub, GHCR privado, stack isolada, dados de ensaio, retorno
  e testes nos dois aparelhos. Repositório e identidade VPS reconferidos.
- DNS criado pelo proprietário e A `<IP_DE_LABORATORIO>` confirmado por consulta.
- Acesso GHCR configurado privadamente na VPS; exceção de escopos adicionais
  aceita pelo proprietário e registrada na [evidência030](../evidence/VR-WA-030-GPS-PROOF.md).
  Publicação, convergência, HTTPS e jornada sintética passaram. Não solicitar
  segredo pelo chat. Percursos reais requerem o
  proprietário com os aparelhos e pontos privados escolhidos para o ensaio.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`, `.github/workflows/gps-spike-publish.yml`.
- `app`: `lib/`, `test/`, `integration_test/`, `android/`, `ios/`, `pubspec.yaml`, `pubspec.lock`, `backlog/`, `docs/`, `README.md`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-020-GPS-SPIKE](VR-WA-020-GPS-SPIKE.md)

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [x] Dependência020 concluída; versões/dirty paths conferidos e recursos de GitHub/CI disponíveis para iniciar a publicação autorizada. Credencial GHCR e campo são passos humanos explícitos posteriores.
- [x] Gates, smoke, evidência e sequência confirmados para a revisão que será executada; aprovação externa registrada no plano exato.

## Plano sequencial

1. Obter autorização concreta do receptor de teste e publicar apenas o artefato preparado; usuário cria DNS se necessário. Alternativa local só vale se alcançável no percurso aprovado.
2. Instalar builds do manifest nos dois aparelhos; preparar pontos/observador e testar primeira posição/parada estacionados.
3. Executar roteiro 09: três percursos e vinte oportunidades por plataforma, bloqueio/background, interrupção de rede, ambiguidades e chegada.
4. Medir bateria por pelo menos uma hora por aparelho, cadência, frescor, perda e encerramento/stop offline; ajustar somente em calibração separada.
5. Corrigir falhas do spike e repetir a avaliação afetada; se a experiência exigir ação por parada ou garantia nativa não for alcançada, reportar impedimento e manter a entrega sem liberação. A ADR-007 permite desenvolvimento local independente, preservando030 obrigatório antes160/180.

## Critérios de aceite

- [ ] M1 e métricas aprovados separadamente em Android/iOS, sem compensação entre plataformas.
- [ ] Nenhuma continuidade fictícia após force-stop/permissão revogada; retomada e expiração comprovadas.
- [ ] Limites, instruções MIUI e versão de política registrados sem GPS bruto.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Roteiro físico 09 §3; T09/T12–T16/T19/T21/T22/T26/T29/T32/T33. Evidência sintética de sink não é entrega WhatsApp real.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica é `program/evidence/VR-WA-030-GPS-PROOF.md`; seu registro parcial não permite status done antes dos aceites físicos e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Parar coleta, revogar dispositivos de prova, desligar receptor aprovado e preservar somente evidências sanitizadas.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.

## Retomada do smoke Android08 em31/10

IA: APK instalado08 e fontes imutáveis; sem alteração de runtime. Paths de
escrita limitados a esta evidência/card, program.json, dashboard e QA/guia do app.
Dirty inicial: somente dashboard gerado. Verificar versão/hash instalado, UI,
renovar somente credencial de laboratório já autorizada se necessário, iniciar
sessão estacionada curta via USB, conferir energySession e parada nativa/receptor.
Encerrar sem coleta ativa. Não mede bateria sem alimentação e não aprova M1.
Rollback operacional: parada pela UI e revogação da sessão de ensaio se falhar;
nenhuma mudança de serviço publicado ou operação no iPhone.

Resultado da retomada: versão/hash08 reconferidos no aparelho, GPS inicial2,5s,
138 posições recebidas, diagnóstico OBSERVING→STOP_CONFIRMED em142,788s.
USB identificado e janela sem alimentação NOT_STARTED; parada nativa51ms,
feedback visível, fila0, serviço ausente e receptor STOPPED. Automação exigiu
reabrir/rolar o painel; tentativas incompletas preservadas no manifest08.
M1, percurso e bateria sem USB continuam pendentes. Nenhuma operação iPhone.
