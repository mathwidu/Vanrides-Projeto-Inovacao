# Conversas WhatsApp — implementação local070

O contrato1.1.0 acrescenta `registrationStep` e `eventFreshness` ao contexto e
o comando fechado `RESPOND_TO_MESSAGE`. O consumidor privado Node usa essas
informações para propor um dos comandos autorizados. A API continua responsável
pela identidade, versão, lease, consentimento, recibo e resposta atômica.
O app Flutter e os endpoints legados não são consumidores desse contexto.

`CLAIM_DRIVER_INVITE` e `CONFIRM_PAIRING` aceitam payload vazio: o hash é obtido
da mesma inbox autenticada. As variantes anteriores com código continuam
exigindo correspondência com o hash. Código, telefone, pin e destinatário não
entram no contexto. A fonte normativa é
[OpenAPI e fixtures](../contracts/whatsapp-mvp/README.md).

Um efeito de negócio exige mensagem atual: até300 segundos de idade e até30
segundos no futuro, segundo o relógio da API. Autorizações são revalidadas após
locks. Recibo anterior prevalece no replay, sem executar novamente o efeito.
Mensagens antigas ou sem data recebem orientação; não iniciam cadastro.

Consentimento exige nome, aviso wa-mvp-v1 emitido na conversa com prazo de300
segundos e texto inequívoco ACEITO posterior à emissão. Negação, aviso vencido
ou ausente não ativam o perfil. A emissão registra outbox, não comprova entrega
ou leitura. O texto atual é de ensaio sintético; revisar aviso e retenção antes
de usuários reais. A normalização de intenção replica whitespace ECMAScript,
minúsculas pt-BR, remoção de marcas e uma pontuação final, sem segundo trim.

AJUDA orienta; FALAR COM SUPORTE cria caso deduplicado e pausa a conversa.
O perfil permanece ativo e a confirmação do pedido continua elegível para
envio. Novas mensagens da conversa pausada não são reclamadas. Retomada e
operação completa do atendimento pertencem ao card140. O texto livre de
`detail` não é persistido como evidência do pedido.

Respostas usam `conversation-v1`; outbox/reconciliação continuam aceitando
respostas `foundation-v1` já persistidas. Rejeição durável já tem recibo e
resposta: o consumidor não pode emitir segundo comando para a mesma inbox.
O ACK preserva o recibo confirmado mesmo se a resposta HTTP do comando se perder.

Não há nova migration070. O reset do lote sintético inclui suporte antes da
identidade. O operador permite `messageText` explícito, grava data atual e não
fabrica ACEITO. Instruções em [whatsapp-operator.md](whatsapp-operator.md).

Validação local na fonte limpa `aa3d97712607d0538d1dbadde6d164581a3ea312`:
CI canônico1262/1262, matriz PostgreSQL203/29 classes, zero falha/erro/skip;
três checks HTTP básicos executados depois da suíte, para reduzir memória.
Smoke worker/API/PG no mesmo JAR passou12/12: seis eventos, sete duplicatas,
seis comandos/outboxes/submissões ao provider falso, suporte com NBSP e
reinício sem novos envios. Reset e revogação confirmados; serviços encerrados.
QA Log e card070 registram hashes, interrupção de memória e limites.
Esta implementação não libera modelo, rotinas,
presença, operação real do WhatsApp, piloto ou produção. Rollback: parar somente
os serviços próprios, preservar filas/recibos e reverter a versão compatível;
nunca apagar migration aplicada ou dados compartilhados.
