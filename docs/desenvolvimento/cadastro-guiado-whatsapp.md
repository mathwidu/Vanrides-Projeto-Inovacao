# Cadastro guiado pelo WhatsApp

O motorista cria a rotina, convida passageiros e aprova seus pedidos na conversa com o VanRides. O passageiro informa o nome, lê o aviso de dados, escolhe seus dias e indica o ponto de embarque pelo próprio WhatsApp. O aplicativo do motorista entra quando for compartilhar o GPS de uma saída.

O cadastro guiado e os convites entraram na homologação com a V41. A [área da
equipe](area-da-equipe.md) também permite praticar os dois papéis no navegador
ou usar um motorista de teste com o WhatsApp real de um passageiro.

## Primeiro cadastro do motorista

1. Use um WhatsApp diferente do número do bot e dos números que testarão como passageiros. A equipe libera o motorista uma vez na [área da equipe](area-da-equipe.md) e entrega o convite individual em privado.
2. No chat com o VanRides, envie o convite recebido. O bot explica o cadastro, pede seu nome e mostra o aviso de dados. Responda `ACEITO` se concordar.
3. Envie `CRIAR ROTINA` e dê um nome ao trajeto. Você pode enviar o sentido, os dias e o horário numa só mensagem, por exemplo `IDA, SEGUNDA A SEXTA, 07:30`. O bot sugere Brasília. Para ajustar o fuso, envie, por exemplo, `FUSO MANAUS` quando ele pedir o destino. Envie o [ponto fixo](#enviar-um-ponto-fixo-no-mac), use **Responder** no resumo e escreva `SALVAR ROTINA`.
4. Pergunte, por exemplo, “Como convido passageiros?” ou “Como vinculo o aplicativo?”. Use `AJUDA` para retomar a etapa. Uma dúvida não aplica mudanças na rotina nem renova um resumo em revisão.

## Convidar e cadastrar passageiros

| Caminho | O que fazer |
| --- | --- |
| Link compartilhado | Envie `CONVIDAR` e encaminhe o link individual em privado. O passageiro abre o link e toca em **Enviar** na mensagem preenchida. Gere um link por pessoa. |
| Convite enviado pelo bot | O passageiro de teste já liberado ou cadastrado no mesmo ambiente envia `ACEITO RECEBER CONVITES` ao bot. O motorista envia `CONVIDAR` seguido do número com DDD, confere os quatro dígitos finais e confirma. |

O opt-in de convites vale por 24 horas e pode ser retirado com `PARAR CONVITES`. Se a pessoa ainda não falou com o bot, não deu permissão ou se ela venceu, encaminhe um link. Se a conexão do bot impedir a apresentação do link, o código em texto continua disponível para um contato liberado pela equipe. A autorização de convites é diferente do aceite do aviso de dados e da escolha de lembretes. [Política do WhatsApp Business](https://business.whatsapp.com/policy/preview?lang=pt_BR)

1. O bot identifica o motorista e pede o nome e o aceite se ainda faltarem. Quando existe uma rotina, sugere os dias dela. O passageiro pode ajustar os dias ou escolher a rotina quando houver mais de uma.
2. O passageiro envia um [ponto fixo](#enviar-um-ponto-fixo-no-mac) e confere um resumo com os dias e o local. Pode mandar `ALTERAR DIAS`, `CORRIGIR PONTO` ou `REFERÊNCIA Portão lateral`. Para escolher avisos, use as opções do resumo. A escolha anterior é preservada até o passageiro alterá-la e os avisos de uma conta nova começam desligados. Envie `CONFIRMAR` para pedir o embarque.
3. O motorista envia `REVISAR PEDIDO`. Se houver um só, o bot mostra o resumo direto. Para consultar a lista, envie `PASSAGEIROS`. No resumo de aprovação, use **Responder** e escreva `APROVAR EMBARQUE`. Para corrigir um ponto inadequado, use a opção de correção na lista. Se o WhatsApp não permitir citar a mensagem, use a confirmação alternativa que o próprio resumo mostra.
4. O passageiro recebe o resultado no mesmo chat. Para verificar presença e GPS depois desse ponto, siga o [roteiro de homologação](homologacao-da-equipe.md).

## Enviar um ponto fixo no Mac

Quando o bot pedir o destino ou o ponto de embarque, abra o Google Maps no
computador, clique com o botão direito no ponto desejado e clique nas
coordenadas para copiá-las. No WhatsApp, digite `PONTO`, dê um espaço e cole.
Exemplo com um ponto fictício: `PONTO -29.68123, -51.13123`. Confira o ponto
no mapa antes de enviar. Pelo celular, você também pode usar o botão de
localização e escolher um ponto fixo. [Ajuda do Google Maps](https://support.google.com/maps/answer/18539?hl=pt-BR)

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
