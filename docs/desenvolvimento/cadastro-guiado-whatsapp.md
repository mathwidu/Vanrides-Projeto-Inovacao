# Cadastro guiado pelo WhatsApp

O motorista cria a rotina, convida passageiros e aprova seus pedidos na conversa com o VanRides. O passageiro informa o nome, lê o aviso de dados, escolhe seus dias e indica o ponto de embarque pelo próprio WhatsApp. O aplicativo do motorista entra quando for compartilhar o GPS de uma saída.

Este roteiro é da versão em preparação. A homologação compartilhada só terá as novas mensagens e convites depois que a API com a migração V41 e o agente forem publicados juntos.

## Primeiro cadastro do motorista

1. Use um WhatsApp diferente do número do bot e dos números que testarão como passageiros. A equipe libera o motorista uma vez na [área da equipe](area-da-equipe.md) e entrega o convite individual em privado.
2. No chat com o VanRides, envie `AJUDA` para entender o produto. Envie o convite recebido, informe seu nome e leia o aviso. Responda `ACEITO` se concordar com o uso dos dados explicado pelo bot.
3. Envie `CRIAR ROTINA`. O bot pede o nome do trajeto, sentido, dias, horário, fuso e destino. Envie um ponto fixo pela opção **Localização** do WhatsApp. Confira o resumo e envie a confirmação exibida na conversa.
4. Pergunte, por exemplo, “Como convido passageiros?” ou “Como vinculo o aplicativo?”. Use `AJUDA` para retomar a etapa. Uma dúvida não aplica mudanças na rotina nem renova um resumo em revisão.

## Convidar e cadastrar passageiros

| Caminho | O que fazer |
| --- | --- |
| Link compartilhado | O motorista envia `CONVIDAR LINK`, revisa e confirma. Encaminha o link individual em privado. O passageiro abre o chat e toca em **Enviar** na mensagem preenchida. Use um link por pessoa. |
| Convite enviado pelo bot | O passageiro de teste já liberado ou cadastrado no mesmo ambiente envia `ACEITO RECEBER CONVITES` ao bot. O motorista envia `CONVIDAR` seguido do número com DDD, confere os quatro dígitos finais e confirma. O VanRides coloca o convite na fila para esse WhatsApp. |

O opt-in de convites vale por 24 horas e pode ser retirado com `PARAR CONVITES`. Se a pessoa ainda não falou com o bot, não deu permissão ou se ela venceu, encaminhe um link. Se a conexão do bot impedir a apresentação do link, o código em texto continua disponível para um contato liberado pela equipe. A autorização de convites é diferente do aceite do aviso de dados e da escolha de lembretes. [Política do WhatsApp Business](https://business.whatsapp.com/policy/preview?lang=pt_BR)

1. O passageiro confere o motorista indicado e responde às perguntas. Envia um ponto fixo pelo WhatsApp e, se necessário, uma referência como “portão lateral”.
2. Após revisar e confirmar, o passageiro aguarda a aprovação. O motorista recebe uma mensagem, usa `PASSAGEIROS` e escolhe o pedido a aprovar ou corrigir.
3. O passageiro recebe o resultado no mesmo chat. Para verificar presença e GPS depois desse ponto, siga o [roteiro de homologação](homologacao-da-equipe.md).

## Aceite no canal real

Use o número do motorista e dois números de passageiro de teste. Faça um vínculo pelo link e outro pelo envio direto. Confira se cada pessoa recebe as orientações do próprio papel, incluindo quando interrompe e retoma o cadastro. No envio direto, confira a mensagem no WhatsApp do destinatário. A confirmação de que a mensagem entrou na fila apenas indica processamento iniciado.

Teste também um número sem opt-in, a retirada da autorização antes do envio e o uso do mesmo convite em outra conta. O bot deve indicar o link como alternativa para quem não deu permissão. A outra conta não deve conseguir cadastrar-se com um convite já reservado. Registre o cenário e o resultado na issue [#28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) ou [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) sem fotografar códigos, números ou pontos de embarque.

## Publicar para o teste

1. Resolva a base do [PR #114](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/114) antes de integrar este trabalho. Ela corrige a leitura dos identificadores do WhatsApp e orienta a liberação no painel.
2. Faça o backup da homologação e prepare a aprovação da V41 para a revisão que chegará à `main`, conforme o [guia de publicação](../../deploy/homologacao/AUTOMATICO.md). Atualize a API antes do agente. A `main` publica a API. A atualização do agente é uma operação separada.
3. Confira saúde, versão, conexão e `AJUDA`. Execute primeiro o roteiro acima com os números de teste do mantenedor. A liberação para o grupo vem após o aceite desse ensaio.

Para retornar à API V40, pause o processamento do WhatsApp e restaure o agente
anterior. Preserve o receptor com a correção de identidade do #114. A API V40
desconhece a restrição ao destinatário e o envio direto. Depois de usar os novos
convites, prefira corrigir em V41. Se o retorno for indispensável, revogue os
convites da V41 ainda abertos, descarte as revisões de envio direto em aberto e
reconcilie suas entregas antes de reativar o canal. Preserve vínculos aprovados e
use o retorno da homologação com backup verificado.
