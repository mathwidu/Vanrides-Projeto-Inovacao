# VR-WA-130-APPROACH — Entregar aproximação útil por passageiro

## Resultado esperado

API avisa corretamente cada passageiro elegível usando posição atual e progressão da rotina.

## Estado

`analyzed`. Pacote P10, épico `VR-WA-MVP`. Responsável de integração: executor principal; módulos mantêm commits e gates separados. Estado canônico em [program.json](../program.json).

## Impact Analysis

Mudança delimitada pelos paths abaixo; confirmar os fontes e o estado Git antes de começar. Reutilizar código compatível sem reintroduzir empresa/jornada legada na UX. O [plano executável](../../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md), os [contratos](../../docs/plans/2026-10-28-whatsapp-mvp/08-contratos-configuracao-e-workflows.md) e o [roteiro](../../docs/plans/2026-10-28-whatsapp-mvp/09-roteiro-de-validacao.md) definem os detalhes transversais. Mapas continua adiado; não clonar/executar esse repositório.

## Contratos

Aplicar schemas/atores/versionamento do documento 08 apenas no escopo desta tarefa; preservar API, auth, payloads, schema e comportamento mobile legados não afetados. Alteração de contrato exige teste de produtor e consumidor; no preflight, só contratos de controle e configuração são alteráveis.

## Escopo

- `coordination`: `program/program.json`, `program/cards/`, `program/evidence/`, `program/RISK_REGISTER.md`, `.dev-harness/dashboard.md`, `.dev-harness/status.json`, `docs/plans/2026-10-28-whatsapp-mvp/`, `src/whatsapp/`, `docs/runbooks/whatsapp-mvp/`.
- `backend`: `src/main/`, `src/test/`, `contracts/whatsapp-mvp/`, `backlog/`, `docs/`, `pom.xml`.

Paths são previsão de responsabilidade, não autorização para substituir arquivos inteiros ou adotar mudanças alheias. Antes do primeiro write, restringir o diff aos componentes efetivamente necessários e preservar os preexistentes.

## Fora de escopo

Produção ampla, publicação em lojas, empresas, responsáveis, navegação própria, mapas adiado e qualquer mudança externa não aprovada para o manifest/ambiente concreto.

## Dependências

[VR-WA-050-WHATSAPP-TRANSPORT](VR-WA-050-WHATSAPP-TRANSPORT.md), [VR-WA-110-RUN-TRACKING](VR-WA-110-RUN-TRACKING.md)

Precondição técnica de entrada: contratos de entrega/reconciliação do backend
`e25ed4e0131bc253b7ccea2fa4ab793e69a89af9` e coordenação
`9912a40a4e06a583baef9d598c1005bb5437bdf4`, com gates e ensaio integrado
registrados na [evidência 120](../evidence/VR-WA-120-DELIVERY-RECOVERY.md).
Revalidar contratos produtores/consumidores, fontes e dirty paths no DoR; os
pins descrevem a base comprovada, não dispensam gate/smoke do novo código.

O fechamento real do 120 aguarda número/pareamento e continua obrigatório no
160, antes do candidato e dos cards 170/180. Esta separação permite preparar
e implementar módulos independentes; não marca o 120 como done. O 130 permanece
analyzed até completar seu DoR e conserva todos os critérios de utilidade
por plataforma. Replay sintético não substitui percurso/aceite físico. Ver RD44.

Recursos humanos/externos são solicitados no momento indicado pelo plano, após preparação concreta. Ausência de recurso necessário à execução deve virar blocker explícito; não marcar dependência futura como concluída.

## Definition of Ready

- [x] Resultado, Impact Analysis, escopo, contratos, riscos e rollback definidos neste card e documentos vinculados.
- [ ] Dependências concluídas; versões/dirty paths conferidos e recursos necessários disponíveis para esta etapa.
- [ ] Gates, smoke, evidência e sequência confirmados para a revisão que será executada.

## Plano sequencial

1. Implementar política versionada do documento 08, histerese, agrupamento de ponto e progressão monotônica.
2. Testar desvio/rua paralela/viaduto/sentido/parada posterior; ambiguidade suprime aviso e conta perda.
3. Implementar GET_DRIVER_LOCATION com pin efêmero autorizado por comando, revalidar aquisição antes da submissão e testar consultas sucessivas/fila atrasada em T43. Gravar decisão/outbox na mesma transação; sem ETA numérico e sem proof of boarding.
4. Calibrar em conjunto separado e medir utilidade; se geografia simples falhar, preparar correção de corredor/rota com evidência/custo e manter aceite pendente.

## Critérios de aceite

- [ ] Zero destinatário/ponto incorreto nos testes e sem aviso duplicado por policy version.
- [ ] Critério de utilidade aprovado por plataforma ou bloqueio técnico exposto.
- [ ] Cancelamento/opt-out/ponto compartilhado/progresso atrasado corretamente tratados.

## Riscos

Falso verde por versão/dispositivo diferente, mistura de dados/atores, mudança preexistente incorporada sem revisão ou alteração externa implícita. Mitigar com manifest, escopos mínimos, testes descritos e prova física quando exigida. Os riscos específicos estão nos passos/aceites e no [registro](../RISK_REGISTER.md).

## Validação

Gate backend, replay sintético, T19–T22/T25/T43; ensaio final no card 180 com canal real.

Registrar comandos/resultados reais, SHA/digest, testes executados e não executados. A evidência canônica a criar é `program/evidence/VR-WA-130-APPROACH.md`; não inserir caminho em `evidence` nem status done antes do arquivo real validado e dos commits. Não usar GPS bruto, telefone ou segredo nas provas.

## Rollout

Trabalho local autorizado segue sem reconfirmação. Se houver alteração externa, primeiro preparar artefato, impacto, verificação e rollback; obter autorização específica conforme skill de VPS. Ter acesso não é autorização para publicar. Um card ativo por vez.

## Rollback

Desligar flag de aviso mantendo acompanhamento e parada; voltar política anterior só para novas sessões após validação.

## Encerramento

Commits próprios por repositório, QA Log, evidência canônica e sucessor elegível atualizados juntos. Não declarar entrega/piloto/produção por um gate parcial.
