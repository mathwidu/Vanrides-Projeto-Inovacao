# Motorista e rotinas pelo WhatsApp

Card canônico: `../program/cards/VR-WA-080-DRIVER-ROUTINES.md` no workspace
coordenador. Implementação local; não habilita número, webhook ou piloto.

## Contrato e propriedade

OpenAPI1.2.0 amplia o catálogo para27 comandos. ADVANCE_DRIVER_SETUP recebe
payload vazio; a API lê a mensagem da inbox autenticada. Os demais comandos080
recebem somente referências da proposta autorizada. O worker é o consumidor
alterado; o Flutter legado não consome esses contratos. Auth, CORS, WebSocket,
payloads e entidades públicas legadas permanecem intactos.

O motorista precisa estar ativo, habilitado por bootstrap autorizado e com
aviso aceito. Pode configurar antes de parear o aparelho. Autodeclaração não
habilita esse papel. Contexto e execução repetem as verificações; um comando
fora do oferecido não adquire autoridade por possuir uma referência válida.

`WaDriverSetup` coordena oferta/execução; `WaDriverWizard` interpreta os campos;
`WaDriverDraftStore` persiste a proposta e confirmações; `WaDriverTargets` resolve
alvos com proprietário/versão/locks; `WaDriverMutations` aplica as mudanças;
`WaDriverTexts` produz mensagens. O modelo não recebe nome, endereço, pin,
marcador ou conteúdo do rascunho.

## Rascunho e confirmação

Há um rascunho por motorista em `conversation_state.context_json.driverDraft`.
Não existe rotina incompleta com dados inventados. Nome, sentido, dias, hora,
fuso e destino estático são coletados em etapas. Ajuda preserva o progresso;
suporte pausa e invalida confirmação; voltar/corrigir/continuar/abandonar são
transições explícitas.

A confirmação de cinco minutos vincula ator, ação, revisão/digest, versões dos
alvos, inbox e outbox do resumo. Exige a mensagem CONFIRMAR com o marcador
vigente. Um SIM, marcador antigo, versão ou alvo alterados não aplicam a
proposta. A versão do perfil avança com as transições080. Locks precedem a
revalidação final de TTL, lease e credencial. Recibo, resposta e mudança de
domínio comprometem na mesma transação; replay não duplica efeitos.

Se um alvo mudar ou deixar de ser elegível, ou a confirmação vencer, a recusa
de negócio precisa ser durável. Antes de qualquer mutação, o handler invalida
a proposta e prepara uma resposta de recuperação. Quando a oferta vence entre
contexto e comando, registra uma recusa sem aplicar a proposta. O recibo permite
concluir a inbox e receber a próxima mensagem, sem prender o motorista em retry
ou PAUSED. Credencial/lease inválidos continuam causando rollback integral;
conflito de expectedVersion do perfil mantém o retry com contexto atualizado.

## Rotina, ordem e calendário

Ida e volta são independentes. Os fusos aceitos têm nomes brasileiros; o banco
guarda identificadores IANA. Destino vem somente de pin estático normalizado da
inbox do próprio motorista. Rotina sem passageiros é permitida, com ordem
pendente. A ordem informada deve ser uma permutação completa dos membros
ativos, com vínculos/pontos aprovados e válidos.

Menus/resumos identificam nome, sentido, hora e fuso; pontos mostram passageiro
e descrição; convites mostram criação, validade e usos sem reexibir token.
Uma lista que exceda o orçamento de mensagem não oferece índices ocultos:
retorna aviso e orientação de suporte. Paginação de listas grandes permanece
fora desta implementação. O orçamento de mensagem usa as mesmas unidades UTF-16
do adapter Node, preservando o limite80 codepoints dos nomes. Vazio Unicode,
controles e formatação invisível não são nomes válidos.

Pausa/retomada altera ACTIVE/PAUSED; não encerra execução existente. Cancelar
data cria/atualiza uma ocorrência CANCELED por rotina/data; não cria presença
ou jobs e rejeita execução já iniciada/encerrada. Edição não modifica snapshot
iniciado.090/100/110 devem incrementar versões, respeitar PAUSED/CANCELED e
revalidar essas invariantes com seus fluxos completos. Fixtures deste card
não comprovam ingresso de passageiro nem acompanhamento real.

Data/hora/fuso de cancelamento precisam identificar um instante único. Gap ou
overlap de horário de verão são recusados antes de preparar confirmação, com
recibo e orientação que permitem seguir a conversa. A mutação reutiliza a mesma
resolução sob os locks existentes.

## Convite e entrega privada

PASSAGEIRO usa token de32 bytes, HMAC com propósito `passenger-invite`, prazo e
limite. Defaults mostrados:1uso/24h; limites1–10usos e5–1440minutos. Revogação
pertence ao motorista e não remove vínculos existentes. Uso só será consumido
por REQUEST_LINK durável no090, sem novo consumo em replay.

V28 é aditiva: proveniência do destino/ordem pendente e associação/ciphertext
de convite na outbox. Nenhuma migration aplicada é reescrita.
`WaPrivateInviteDelivery` cifra a mensagem temporária com AAD de ambiente,
outbox, comando e destinatário. Claim não revela o token. Authorize-submit
revalida acesso e convite depois do lock e só então fornece TEXT ao adapter.
Submissão, expiração e cancelamento limpam o ciphertext. Revogação usa
SKIP LOCKED para não inverter os locks do envio; o envio que já passou pelo
ponto de autorização continua sujeito à reconciliação, sem repetição automática.
O purge operacional150 precisa cobrir convites nunca reclamados.

## Validação e retorno

Matriz PostgreSQL obrigatória inclui as classes Wizard, Routines, Invites e
Fencing. Gate canônico, smoke HTTP e worker integrado devem usar o mesmo SHA/JAR.
Resultados, falhas e hashes ficam no QA Log e na evidência080 do coordenador;
este documento não antecipa um PASS.

Desabilitar novos comandos e retornar handlers/worker compatíveis preservando
dados e recibos. Não apagar migration aplicada, restaurar validade de convite
revogado ou reenviar uma tentativa cujo resultado seja desconhecido. Não há
alteração da VPS, dos serviços existentes ou dos aparelhos nesta etapa.
