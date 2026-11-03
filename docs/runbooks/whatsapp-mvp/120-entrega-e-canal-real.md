> Registro histórico sanitizado. Não é receita de execução deste checkout nem prova da nova revisão. Use os guias em `docs/desenvolvimento/`.

# Card 120 — Entrega confiável e prova do canal real

Estado: gates backend e smoke integrado aprovados; pacote do laboratório
fixado para revisão e preflight. Não há instância VanRides pareada nem mensagem
real enviada nesta etapa. Ver [evidência](../../../program/evidence/VR-WA-120-DELIVERY-RECOVERY.md)
e [card](../../../program/cards/VR-WA-120-DELIVERY-RECOVERY.md).

## Sequência e versões

1. Gate backend completo da candidata `e25ed4e0131bc253b7ccea2fa4ab793e69a89af9`,
   execução `41366547009`, sem build/deploy automático. Confirmar resultado e
   matriz PostgreSQL 71/531 do mesmo SHA; não assumir aprovação pelo disparo.
2. Gate da coordenação com contratos byte a byte desse backend, incluindo o
   filtro descartável. Fixar SHA/tree após revisão e commit; não exportar a
   working tree em alteração.
3. Harness Linux 120: materializar somente blobs Git revisados, executar as 11
   verificações de recuperação com Spring/PostgreSQL/worker e exportar o mesmo
   JAR que passou. Os totais esperados são 9 eventos, 7 autorizações, 3 POSTs de
   loopback e 21 consultas de conexão. Essa prova não usa WhatsApp real.
4. Preparar os recursos privados do ensaio na VPS VPS_DE_TESTE. Conferir a imagem
   Evolution 2.3.7, recursos disponíveis, ausência de colisões e baseline dos
   serviços existentes. Não iniciar canal antes das verificações abaixo.
5. Definir privadamente Redmi como bot e WhatsApp do proprietário no iPhone como
   passageiro. A configuração aceita somente aliases comprovados desses atores.
   Não interpretar essa etapa como retomada dos testes físicos do companion iOS.
6. Parear a conta do bot, abrir uma janela curta e executar uma resposta real.
   Comprovar separadamente persistência, submissão, callback correlacionado e
   estado final. HTTP 200/201 e SERVER_ACK isolados não provam entrega.
7. Encerrar ingresso/dispatch, reconciliar tentativas em voo, desvincular a
   instância da rodada e remover somente recursos próprios comprovados. Conferir
   serviços compartilhados e ausência de coleta/envios residuais.

## Topologia delimitada

Evolution temporária própria e worker compartilham somente namespace de rede;
o worker consulta/envia por loopback já permitido. API, banco(s), cache, filtro,
volumes e redes são exclusivos do ensaio. Sem porta publicada, Traefik, DNS novo
ou conexão às redes compartilhadas existentes. Ver justificativas RD40/RD41 no
[registro de decisões](../../plans/2026-10-28-whatsapp-mvp/10-registro-de-decisoes.md).

Imagem Evolution observada:
`evoapicloud/evolution-api:v2.3.7@sha256:1bd8afc4a6cf48822e6cf02469aeae7bd35a12a6b616eacd1291926307f4d339`.
Demais imagens precisam de pins conferidos antes de executar a receita. Não
usar latest, artefatos de outro card ou chaves/bancos de outros projetos.

O filtro tem bearer de webhook próprio. Somente filtro/API conhecem o CHANNEL
real; somente worker conhece seus principals. Segredos e números ficam fora
do Git, argumentos visíveis, logs e artefatos públicos. Configuração privada
é carregada uma vez, com janela de até 30 min e no máximo dois contatos.

## Gates antes do pareamento

- Suíte do filtro: negativos não alcançam a API; aliases alternativos inválidos
  ou de outro contato rejeitam a mensagem inteira; projeção não contém apikey,
  perfil ou conteúdo citado. Tokens distintos nas duas etapas.
- API recusa o bearer do webhook. Pessoas reais não pertencem a coorte
  sintética; eventual seed vazio serve apenas às credenciais técnicas.
- Namespace/rede efetivos correspondem ao plano; API/bancos não têm porta
  publicada. Evolution não recebe o CHANNEL do backend.
- Retenção opcional, telemetria e WebSocket da Evolution desligados; logs não
  persistem conversas, credenciais ou payloads. Sessão/autenticação continuam
  privadas e sujeitas à limpeza.
- Consulta de conexão loopback comprovada. Falha antes de POST impede envio;
  falha depois da invocação permanece incerta e não autoriza retry.
- Limites de recursos, prazo da rodada e limpeza são executáveis e testados.

## Recuperação e limites

Falha interrompe novos envios e preserva os metadados necessários à reconciliação.
Não reabrir outbox terminal, reaproveitar token de tentativa nem repetir envio
incerto. Evolution recriada exige worker recriado no namespace atual. Filtro
não reinicia silenciosamente: seus limites são da rodada, não persistentes.

Remoção usa identificadores/labels de propriedade da rodada, nunca prune ou
limpeza de serviços alheios. Metadados sanitizados de versões, testes e retorno
ficam na evidência; material privado de sessão segue remoção própria comprovada.
O ambiente durável do piloto 170 e a validação física 180 continuam separados.


## Jornada mínima da primeira mensagem real

Depois dos gates e da abertura autenticada do canal, o proprietário enviará
`oi` do WhatsApp do iPhone à conta bot do Redmi. O filtro precisa confirmar a
persistência desse evento na API. O ambiente usa `test-cohort-only=false` para
permitir cadastro orgânico do contato autorizado; não semear esse passageiro
como pessoa sintética. Nenhum colega será incluído nesta primeira rodada.

O principal executará, uma vez por etapa, os controles existentes do worker:

1. `POST /v1/process-inbox`, JSON `{}`, bearer privado de controle: a saudação
   em WELCOME produz START_ONBOARDING determinístico, sem chamada de IA, e
   cria a resposta de cadastro na outbox.
2. `POST /v1/dispatch-one`, JSON `{}`: consulta conexão, reserva/autoriza e faz
   uma única submissão permitida. SUBMITTED não é aprovação da entrega.
3. Aguardar callback real `messages.update` persistido e executar
   `POST /v1/reconcile-delivery`, JSON `{}`. O job usa SYSTEM_JOB e não cria
   outra submissão. Se retornar MORE, continuar somente essa reconciliação.

A evidência mínima registra status/contagens dos controles, ID opaco da outbox,
finalidade RESPONSE, estado da tentativa e da outbox, horários e booleano de
correlação com callback DELIVERY_ACK (ou READ/PLAYED como evidência mais forte).
A junção deve comparar instância e ID externo da tentativa ao callback sem
exportar esse ID externo, destinatário, alias, texto ou payload. SERVER_ACK
isolado comprova submissão; a prova termina em DELIVERED ou READ correlacionado.

A resposta usa o texto fixo de START_ONBOARDING. Isso comprova transporte e
início do cadastro; não comprova o onboarding completo, presença, aproximação
ou o modelo OpenAI ao vivo. As jornadas completas permanecem nos cards próprios.
Configuração do canal/pareamento e execução desses controles ficam separadas:
nenhuma chamada de readiness dispara mensagem automaticamente.
