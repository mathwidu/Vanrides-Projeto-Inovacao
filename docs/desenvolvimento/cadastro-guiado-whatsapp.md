# Cadastro guiado pelo WhatsApp

O motorista cria a rotina, convida passageiros e aprova seus pedidos na conversa com o VanRides. O passageiro informa o nome, lê o aviso de dados, escolhe seus dias e indica o ponto de embarque pelo próprio WhatsApp. O aplicativo do motorista entra quando for compartilhar o GPS de uma saída.

A [homologação da equipe](homologacao-da-equipe.md) identifica o pacote disponível.
O fluxo do PR #133 já foi publicado: API `5ef07cb`, banco V48 e APK Android
**0.5.0+16 disponível para instalar**. A conexão sem códigos, os pontos por
trajeto e os novos links de início/fim estão no pacote compatível. A publicação
foi conferida; a jornada nova no WhatsApp e nos aparelhos ainda precisa ser
validada. O histórico de provas da versão anterior fica no fim deste documento.

Para experimentar sozinho, use [Testar com o WhatsApp](area-da-equipe.md#testar-o-passageiro-com-o-motorista-de-teste).
Você participa pelo seu WhatsApp e faz as ações do motorista pelo painel.
Para praticar os dois papéis com dados fictícios, abra **Simular conversas**.

## Primeiro cadastro do motorista

1. Use um WhatsApp diferente do número do bot e dos números que testarão como passageiros. A equipe libera o motorista uma vez na [área da equipe](area-da-equipe.md) e entrega o convite individual em privado.
2. Abra o link recebido pelo próprio WhatsApp e envie a mensagem pronta para o VanRides. O bot explica o cadastro, pede seu nome e mostra o aviso de dados. Responda `ACEITO` se concordar.
3. Envie `CRIAR ROTINA` e dê um nome ao trajeto. Você pode enviar o sentido, os dias e o horário numa só mensagem, por exemplo `IDA, SEGUNDA A SEXTA, 07:30`. O bot sugere Brasília. Para ajustar o fuso, envie, por exemplo, `FUSO MANAUS` quando ele pedir o destino. Envie o [ponto fixo](#enviar-um-ponto-fixo), use **Responder** no resumo e escreva `SALVAR ROTINA`.
4. Instale o aplicativo da rodada no Android ou iPhone. Pelo WhatsApp do
   motorista, envie “Conectar meu celular” e abra o link recebido. No app,
   toque em **Conectar este celular**. De volta à conversa, leia o pedido e
   envie “Conectar este celular” somente se acabou de pedi-lo. O bot envia
   **Concluir no app**; abra, aguarde **Aparelho vinculado** e o retorno à
   conversa. Não há oito letras para copiar. O link dura cinco minutos e sua
   abertura não liga GPS. O iPhone usa provisoriamente o botão **Abrir VanRides**
   na página intermediária. Autorize localização e notificações na preparação.
5. Pergunte, por exemplo, “Como convido passageiros?” ou “Como conecto meu
   celular?”. Use `AJUDA` para retomar a etapa. Uma dúvida não muda a rotina
   nem renova um resumo em revisão.

A liberação inicial do motorista no piloto ainda usa um convite individual
entregue pela equipe; não confundir esse preparo com a conexão do celular.
Uma [melhoria do painel](area-da-equipe.md#convite-inicial-do-motorista-por-link)
prepara esse convite como link; ela só deve ser usada depois da publicação
da nova interface.

Depois de cadastrado e conectado, o motorista não repete nome, permissões e
convite a cada viagem. Os códigos antigos do aparelho ficam nos detalhes como
contingência, não como instrução normal.

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

1. O bot identifica o motorista e pede nome e aceite somente se faltarem.
   Mostra seus trajetos, com nome, ida/volta, horário e dias. O passageiro pode
   selecionar mais de um, por exemplo `1,2`, e escolher os dias de cada trajeto.
2. Envia um [ponto fixo](#enviar-um-ponto-fixo) de embarque por trajeto,
   pelo WhatsApp do celular e sem localização ao vivo. Ida e volta podem ter
   pontos diferentes. Quando o bot oferecer, “MESMO PONTO” reutiliza um ponto
   aprovado por escolha explícita. Depois, confere trajetos, dias e locais no
   resumo e confirma o pedido. Para mudar algo, envia `AJUSTAR`: o cadastro
   recomeça pelo nome. As escolhas de avisos anteriores são preservadas. O
   passageiro sempre recebe um lembrete antes de cada viagem para informar se
   vai. Conta nova começa com os avisos de chegada da van desligados; no resumo,
   `AVISOS CHEGADA` ou `AVISOS NENHUM` mudam essa escolha.
3. O convite já autoriza entrar com aquele motorista. Falta conferir os pontos
   novos e a ordem dos embarques. O motorista envia `REVISAR PEDIDO`; se houver
   vários, escolhe o correto. No resumo, usa **Responder** e `APROVAR EMBARQUE`,
   ou pede a correção indicada. A revisão identifica o trajeto: aprovar a ida
   não aprova silenciosamente o ponto da volta. Se não puder citar a mensagem,
   use somente a alternativa de confirmação mostrada pelo bot.
4. O passageiro recebe o resultado no mesmo chat. O bot não deve anunciar
   embarque pronto enquanto faltar a preparação necessária. Para presença,
   início e GPS, siga a [jornada completa](homologacao-da-equipe.md#jornada-completa-motorista-e-passageiro).

Quem já estiver cadastrado pode abrir um novo convite do mesmo motorista para
adicionar outro trajeto, sem encerrar o vínculo nem repetir os dados conhecidos.
O convite não troca silenciosamente de motorista. Cada convite continua
individual, de um uso; não é um link público ilimitado.

O lembrete antes de cada viagem é sempre enviado e não pode ser desligado.
Depois de concluir o cadastro, só os avisos de chegada da van podem ser
ligados ou desligados, com `LIGAR AVISOS CHEGADA` ou `DESLIGAR AVISOS CHEGADA`.
O bot confirma o resultado. Termine ou abandone uma revisão em andamento antes
de alterar esse aviso. Mensagens já enviadas podem chegar depois de desligar.

## Corrigir o ponto ou retomar o cadastro

- **Antes de enviar o pedido:** mande `AJUSTAR` no resumo. O cadastro
  recomeça pelo nome, e você escolhe de novo trajetos, dias e ponto. Se o bot recusar a localização, siga a instrução na conversa
  e reenvie um ponto fixo.
- **Se o motorista pedir correção:** siga o aviso recebido no WhatsApp.
  O pedido continua aguardando aprovação. O motorista pode encontrar o pedido
  em `PASSAGEIROS` e escolher `RETIFICAR` com o número da opção.
- **Depois de aprovado:** o passageiro envia `CORRIGIR PONTO` e confirma o
  novo local no resumo, escolhendo o trajeto correspondente. O motorista usa
  `REVISAR PEDIDO` e responde à revisão com `APROVAR PONTO`. O ponto anterior
  daquele trajeto vale até a aprovação; os outros embarques não são trocados.
- **Se parou no meio:** envie `AJUDA` para ver a etapa atual. Quando uma
  revisão expirar, use `CONTINUAR` para receber um novo resumo. A revisão
  vale cinco minutos.

## Enviar um ponto fixo

**Passageiro:** envie o ponto de embarque pelo WhatsApp do celular. Toque no
clipe ou no +, escolha Localização, ajuste o ponto no mapa e envie. Não use a
localização em tempo real. Coordenadas digitadas com `PONTO` não são aceitas
no cadastro do passageiro.

**Motorista:** quando o bot pedir o destino ou um ponto comum, também é possível usar o
computador. Abra o Google Maps, clique com o botão direito no ponto desejado e
clique nas coordenadas para copiá-las. No WhatsApp, digite `PONTO`, dê um
espaço e cole. Exemplo com um ponto fictício: `PONTO -29.68123, -51.13123`.
Confira o ponto no mapa antes de enviar. [Ajuda do Google Maps](https://support.google.com/maps/answer/18539?hl=pt-BR)

## Começar e terminar uma viagem já preparada

O cadastro é feito uma vez. No dia a dia, com trajetos, pontos e celular prontos:

1. O motorista envia “Começar viagem”. Recebe as saídas elegíveis com nome,
   sentido e horário, cada uma com seu link. Havendo várias, são até três
   opções por mensagem, sem outro menu por texto.
2. Abre o link correto e toca em **Começar e compartilhar localização** no app.
   O aplicativo retorna à conversa. A confirmação do bot só vale depois da
   primeira posição válida recebida; abrir o link não liga GPS.
3. Para terminar, envia “Quero finalizar a viagem”, abre o link e toca em
   **Finalizar viagem e parar localização**. O sensor para e o app confirma a
   operação com o servidor antes de retornar à conversa.

A meta do início é uma mensagem e dois toques: **três ações no Android**.
O botão provisório da página intermediária leva a **quatro no iPhone**.
Instalação, primeiras permissões e recuperação de falhas têm contagem própria.
Esses tetos precisam ser medidos no canal e nos aparelhos com a nova versão.

Sem internet, **Parar localização** continua disponível e desliga o sensor;
não prova que a viagem foi concluída no servidor. O app mantém a finalização
pendente e oferece tentar confirmar novamente. Depois de reconectar o celular,
se a parada anterior já estiver confirmada, o bot pode oferecer **Finalizar
esta viagem** na conversa. A revisão não vale para uma coleta posterior.

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
