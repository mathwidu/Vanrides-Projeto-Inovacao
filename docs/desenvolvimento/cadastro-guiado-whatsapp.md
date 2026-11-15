# Cadastro guiado pelo WhatsApp

O motorista cria a rotina, convida passageiros e aprova seus pedidos na conversa com o VanRides. O passageiro informa o nome, lê o aviso de dados, escolhe seus dias e indica o ponto de embarque pelo próprio WhatsApp. O aplicativo do motorista entra quando for compartilhar o GPS de uma saída.

O fluxo está publicado na [homologação da equipe](homologacao-da-equipe.md).
Para experimentar sozinho, use [Testar com o WhatsApp](area-da-equipe.md#testar-o-passageiro-com-o-motorista-de-teste).
Você participa pelo seu WhatsApp e faz as ações do motorista pelo painel.
Para praticar os dois papéis com dados fictícios, abra **Simular conversas**.

## Primeiro cadastro do motorista

1. Use um WhatsApp diferente do número do bot e dos números que testarão como passageiros. A equipe libera o motorista uma vez na [área da equipe](area-da-equipe.md) e entrega o convite individual em privado.
2. No chat com o VanRides, envie o convite recebido. O bot explica o cadastro, pede seu nome e mostra o aviso de dados. Responda `ACEITO` se concordar.
3. Envie `CRIAR ROTINA` e dê um nome ao trajeto. Você pode enviar o sentido, os dias e o horário numa só mensagem, por exemplo `IDA, SEGUNDA A SEXTA, 07:30`. O bot sugere Brasília. Para ajustar o fuso, envie, por exemplo, `FUSO MANAUS` quando ele pedir o destino. Envie o [ponto fixo](#enviar-um-ponto-fixo-no-mac), use **Responder** no resumo e escreva `SALVAR ROTINA`.
4. Pergunte, por exemplo, “Como convido passageiros?” ou “Como vinculo o aplicativo?”. Use `AJUDA` para retomar a etapa. Uma dúvida não aplica mudanças na rotina nem renova um resumo em revisão.

## Convidar e cadastrar passageiros

| Caminho | O que fazer |
| --- | --- |
| Link compartilhado | Envie `CONVIDAR` para receber o link sem confirmação intermediária. Encaminhe em privado. O link tem um uso e vale 24 horas. O passageiro abre o link e toca em **Enviar** na mensagem preenchida. Gere um link por pessoa. |
| Convite enviado pelo bot | O passageiro de teste já liberado ou cadastrado no mesmo ambiente envia `ACEITO RECEBER CONVITES` ao bot. O motorista envia `CONVIDAR` seguido do número com DDD, confere os quatro dígitos finais e confirma. |

A autorização para receber convites diretos vale por 24 horas e pode ser
retirada com `PARAR CONVITES`. Use o link quando for o primeiro contato do
passageiro. Se o bot só conseguir fornecer o código em texto, libere o contato
na área da equipe antes de usá-lo. O aviso de dados e os lembretes têm suas
próprias escolhas. [Política do WhatsApp Business](https://business.whatsapp.com/policy/preview?lang=pt_BR)

1. O bot identifica o motorista e pede o nome e o aceite se ainda faltarem. Quando existe uma rotina, sugere os dias dela. O passageiro pode ajustar os dias ou escolher a rotina quando houver mais de uma.
2. O passageiro envia um [ponto fixo](#enviar-um-ponto-fixo-no-mac) e confere um resumo com os dias e o local. Pode mandar `ALTERAR DIAS`, `CORRIGIR PONTO` ou `REFERÊNCIA Portão lateral`. Para escolher avisos, use as opções do resumo. A escolha anterior é preservada até o passageiro alterá-la e os avisos de uma conta nova começam desligados. Envie `CONFIRMAR` para pedir o embarque.
3. O motorista envia `REVISAR PEDIDO`. Se houver um só, o bot mostra o resumo direto. Para consultar a lista, envie `PASSAGEIROS`. No resumo de aprovação, use **Responder** e escreva `APROVAR EMBARQUE`. Para corrigir um ponto inadequado, use a opção de correção na lista. Se o WhatsApp não permitir citar a mensagem, use a confirmação alternativa que o próprio resumo mostra.
4. O passageiro recebe o resultado no mesmo chat. Para verificar presença e GPS depois desse ponto, siga o [roteiro de homologação](homologacao-da-equipe.md).

## Corrigir o ponto ou retomar o cadastro

- **Antes de enviar o pedido:** mande `CORRIGIR PONTO` no resumo e envie
  o novo ponto. Se o bot recusar a localização, siga a instrução na conversa
  e reenvie um ponto fixo.
- **Se o motorista pedir correção:** siga o aviso recebido no WhatsApp.
  O pedido continua aguardando aprovação. O motorista pode encontrar o pedido
  em `PASSAGEIROS` e escolher `RETIFICAR` com o número da opção.
- **Depois de aprovado:** o passageiro envia `CORRIGIR PONTO` e confirma o
  novo local no resumo. O motorista usa `REVISAR PEDIDO` e responde à
  revisão com `APROVAR PONTO`. O ponto anterior vale até a aprovação.
- **Se parou no meio:** envie `AJUDA` para ver a etapa atual. Quando uma
  revisão expirar, use `CONTINUAR` para receber um novo resumo. A revisão
  vale cinco minutos.

## Enviar um ponto fixo no Mac

Quando o bot pedir o destino ou o ponto de embarque, abra o Google Maps no
computador, clique com o botão direito no ponto desejado e clique nas
coordenadas para copiá-las. No WhatsApp, digite `PONTO`, dê um espaço e cole.
Exemplo com um ponto fictício: `PONTO -29.68123, -51.13123`. Confira o ponto
no mapa antes de enviar. Pelo celular, você também pode usar o botão de
localização e escolher um ponto fixo. [Ajuda do Google Maps](https://support.google.com/maps/answer/18539?hl=pt-BR)

## O que já foi validado

Em 15/11/2026, o [PR #118](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/118)
publicou o link padrão, o resumo único do passageiro e a aprovação por
resposta ao resumo. No WhatsApp do Mac, o motorista gerou o link, o passageiro
abriu o convite e enviou o pedido, e a confirmação chegou aos dois depois da
aprovação. O [PR #119](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/pull/119)
publicou instruções para reenviar um ponto inválido. Repetimos o cenário no
WhatsApp, aprovamos o ponto corrigido e encerramos o vínculo de teste.

A rodada real usou dois perfis já cadastrados. Os testes PostgreSQL cobriram
nome e aceite de um perfil novo, escolhas de dias, autorização e reuso de
convite. O envio direto foi verificado antes da simplificação.
[Cadastro #28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28#issuecomment-6903265559)
· [Correção do ponto #49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49#issuecomment-6903265816)
· [Publicação da revisão final](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/actions/runs/43775243359)

Na próxima rodada, use um perfil novo para conferir o nome e o aceite no canal
real. Para alterações no envio direto, cheque a entrega ao número autorizado,
a revogação da autorização e a recusa do mesmo convite por outra conta.
Registre cenário, horário e resultado nas issues, usando dados de teste.
