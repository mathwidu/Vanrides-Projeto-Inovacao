# Roteiro de validação do fluxo simples

Roteiro de 17/11/2026 para validar o fluxo publicado com pessoas que não
conhecem o projeto. Ele planeja a rodada; os resultados reais serão registrados
separadamente, depois da instalação dos builds compatíveis.

O fluxo publicado acrescenta ponto por trajeto, finalização pelo link e app,
conexão do celular sem copiar códigos e links próprios para várias saídas.
**API V48 e APK 0.5.0+16 publicados; iOS 0.5.0+16 compilado sem assinatura,
ainda precisa ser assinado e instalado no aparelho. Builds anteriores não
comprovam os incrementos V45–V48.**
Antes da rodada, o responsável deve identificar as versões compatíveis da API,
do bot, da página do link e do aplicativo. Código implementado ou compilação
aprovada não comprovam esses percursos. Com componente antigo, registrar
“bloqueado pela versão”; não testar uma experiência diferente como substituta.

A [proposta](proposta-fluxo-simples.md) explica as decisões de produto. O
[guia publicado](cadastro-guiado-whatsapp.md) descreve o fluxo atual. O
[roteiro de campo](rodada-final.md) detalha bateria, duração e evidências
técnicas; usar este roteiro curto primeiro, sem repetir toda aquela matriz.

## Pessoas, números e aparelhos

- São **quatro números distintos**: bot, motorista, passageiro P1 e passageiro
  P2. O número do bot não pode ser usado como participante.
- O motorista pode conversar com o bot pelo WhatsApp no Mac enquanto o app
  coleta no Android ou iPhone conectado ao cadastro dele. Não é necessário
  manter o WhatsApp do motorista aberto no aparelho coletor.
- P1 e P2 precisam receber mensagens em suas próprias contas. Para observar
  avisos em pontos diferentes durante um percurso, usar **três celulares**:
  coletor, P1 e P2. O WhatsApp do motorista pode ficar no Mac.
- Para conferir somente a navegação e os destinatários, parado, dois celulares
  e o Mac podem bastar se o app coletor estiver em um dos celulares dos
  passageiros, com as contas já preparadas. Essa composição não comprova a
  chegada a dois passageiros esperando em pontos distintos.
- Testar Android e iPhone em sequência. Antes de trocar o coletor, confirmar
  que a coleta anterior parou e que sua confirmação chegou ao servidor.

## Preparar sem sair para o percurso

O facilitador prepara o candidato, o acesso do motorista, o app conectado e as
permissões necessárias. Confere internet, bateria e nomes legíveis dos trajetos.
Não pede que a pessoa leiga opere o painel interno ou copie códigos técnicos.
O caminho normal de conexão deve dispensar códigos. Se for necessário recorrer
à contingência técnica, registrar a falha, a ajuda e as ações adicionais; não
considerar o cadastro guiado aprovado.

Combinar uma ida e uma volta com horários e dias reconhecíveis. Usar pontos
de embarque diferentes quando isso fizer sentido, sem deduzir a volta pelo
nome ou pela seta. Preparar uma saída pronta para o primeiro ensaio e pelo
menos duas opções prontas para o segundo. O facilitador confere a aprovação
dos pontos e a ordem antes do início cotidiano.

Usar P1 para cadastro novo. P2 pode retornar a um cadastro existente; se não
houver um, cadastrá-lo e depois repetir a entrada. Não apagar um cadastro real
para fabricar esse cenário. Nas evidências compartilhadas, usar P1/P2 e nomes
de teste, sem links completos de acesso, telefones ou coordenadas pessoais.

## Conduzir sem explicar o caminho

Entregar uma tarefa por vez, como “Entre nos seus trajetos com este motorista”.
Deixar a pessoa ler as mensagens e agir. O facilitador observa e anota; não
antecipa o botão ou a resposta certa. Se precisar ajudar, registrar onde ajudou
e depois repetir a etapa com o candidato corrigido. Uma tarefa concluída com
explicação do desenvolvedor não prova uso sem ajuda.

### 1. Cadastro e retorno

1. **Motorista:** cadastrar um trajeto e depois o retorno. Conferir se nome,
   ida/volta, horário e dias ajudam a diferenciá-los. Salvar somente após ler o
   resumo. Criação e primeiras permissões têm contagem própria.
2. **Conexão do celular do motorista:** enviar “Conectar meu celular” ao bot.
   Abrir o link e tocar em “Conectar este celular” no app. Conferir o retorno
   à conversa, ler o pedido recebido e enviar “Conectar este celular”. Abrir
   “Concluir no app” e conferir “Aparelho vinculado”, com retorno à conversa.
   No iPhone, contar também o botão intermediário ao abrir o app. Nenhuma
   dessas ações pode ligar GPS. Esse é o preparo inicial, com contagem própria.
3. **Convite:** o motorista pede um link individual e o compartilha em privado
   com P1. P1 abre e envia a mensagem preenchida. Deve reconhecer quem convidou,
   informar só os dados que faltam e aceitar o aviso de dados conscientemente.
4. **P1:** escolher ida e volta, indicando os números mostrados, como `1,2`.
   Informar os dias de cada trajeto e enviar um ponto fixo para cada um pelo
   mapa do WhatsApp. Não usar localização ao vivo. Conferir os dois embarques
   no resumo antes de enviar.
5. **Motorista:** o convite já autoriza a entrada da pessoa. A revisão restante
   deve identificar **qual ponto e trajeto** precisa conferir. Aprovar um ponto
   não pode aprovar o outro silenciosamente; preparar a ordem dos trajetos.
   Se pedir correção da ida, a volta deve permanecer como estava.
6. **P2, retorno:** abrir um novo convite do mesmo motorista e acrescentar um
   trajeto. Nome, escolhas de avisos e trajetos anteriores devem continuar.
   Reutilizar um ponto aprovado somente ao escolhê-lo explicitamente; enviar
   outro ponto deve mantê-lo pendente de revisão. Não encerrar o cadastro para
   continuar. Um convite de outro motorista não pode realizar uma troca silenciosa.

O envio pelo número é uma alternativa separada: hoje exige contato conhecido,
admitido e com aceite prévio para receber convites. Não apresentar convite a
um número novo como pronto nem usar o número do bot para contornar esse limite.

### 2. Presença e avisos seletivos

Na saída desta rodada, usar a regra em que participantes habituais continuam
previstos enquanto não respondem. P1 confirma que vai; P2 deixa a presença
pendente. **Ambos devem aparecer entre os previstos**, com os estados claros.
“Pendente” não significa ausência; a preferência de receber avisos continua
independente e precisa estar ligada, com ponto e viagem elegíveis.

Ligar os avisos de chegada de P1 e deixar os de P2 desligados. Conferir que
uma escolha não altera a outra pessoa nem os lembretes de presença. O recebimento
de aproximação será observado no percurso: P1 recebe o aviso elegível e P2 não.
Repetir em outra saída com as escolhas invertidas: P2 continua pendente e
com avisos ligados; P1 fica com avisos desligados. Conferir P2 entre os previstos
e o aviso somente a ele, depois de reconciliar mensagens pendentes da anterior. Parado, conferir a preferência
salva; não chamar ausência de aviso sem aproximação de prova de envio seletivo.

### 3. Começar: uma saída pronta e depois várias

Pedir: **“Comece a viagem pelo WhatsApp.”** A pessoa pode enviar “Começar viagem”,
conferir o resumo, abrir o link e confirmar no app. O nome e o horário devem
corresponder à saída escolhida. Abrir o link, consultar a tela ou retornar ao
WhatsApp não pode ligar o GPS por si.

| Ação do motorista | Android | iPhone provisório |
| --- | --- | --- |
| Enviar a mensagem ao bot | 1 | 1 |
| Abrir o link recebido | 2 | 2 |
| Tocar em “Abrir VanRides” na página intermediária | Não previsto no teto | 3 |
| Confirmar o início e compartilhamento no app | 3 | 4 |

Metas: **até três ações no Android e quatro no iPhone**, incluindo o botão
intermediário aceito provisoriamente. Escrever e enviar a mesma resposta contam
juntos; mensagens automáticas não contam. Permissões extras, abertura manual
do WhatsApp e qualquer intervenção entram no registro, sem esconder ações.
Não exigir uma confirmação equivalente com código no WhatsApp.

Conferir o retorno à conversa do bot. Só considerar GPS ativo quando a primeira
posição válida da sessão atual tiver sido recebida pelo servidor; o facilitador
confere essa evidência. Link aberto ou tela com contador não bastam.

Repetir com **várias saídas prontas**, inclusive nomes parecidos. A pessoa deve
identificar a correta e abrir diretamente o link daquela saída, com o mesmo
nome e horário no app. O candidato envia até três opções por mensagem, sem uma
segunda escolha por texto; repetir com pelo menos quatro saídas para avaliar
os vários blocos. Não omitir opções nem escolher uma ambígua automaticamente
para reduzir a contagem. Um início aceito invalida as demais opções da oferta;
reabrir outra não pode criar uma segunda coleta. Não prometer botões nativos
sem prova no canal real.

### 4. Finalizar pelo link e pelo app

Com a saída em andamento e GPS válido, pedir: **“Finalize a viagem pelo WhatsApp.”**
O bot identifica a viagem e oferece o link. Abrir esse link deve apenas mostrar
a finalização correspondente no app. A pessoa toca na ação de finalizar a viagem
e parar a localização; não precisa redigitar uma confirmação técnica no chat.

Passa quando a viagem correta termina, o sensor para, o app retorna à conversa
e a confirmação exibida corresponde à ação atual. O facilitador confere também
o recibo da finalização e a confirmação de parada do aparelho no servidor
(ACK). Essa sigla não é uma instrução a ser dada ao motorista.

Reabrir o mesmo link não pode concluir outra viagem, duplicar o efeito ou ligar
GPS. **“Parar localização” isoladamente não comprova viagem concluída**: é uma
contingência de parada do sensor, presente também nos builds anteriores.

### 5. Recuperação, ainda estacionado

- **Convite ou link vencido:** abrir depois do prazo informado. Deve haver
  orientação para obter um convite ou link atual, preservando o cadastro.
  Um link antigo de início ou fim não pode agir sobre outra viagem.
- **Sem internet durante a coleta:** cortar e restabelecer a rede. A posição
  antiga não pode parecer recém-recebida. Observar primeiro com a tela bloqueada,
  sem abrir o app; anotar o tempo e qualquer abertura ou mensagem necessária
  para retomar. Se houve ajuda, não registrar recuperação autônoma.
- **Finalização sem internet:** parar localmente deve continuar disponível.
  Deve ficar claro que a localização parou e falta confirmar a finalização.
  Reabrir ainda sem rede não pode religar o sensor. Ao reconectar, conferir
  a opção de tentar finalizar novamente e a confirmação da mesma parada, sem
  usar um recibo antigo como prova. Este candidato exige essa tentativa explícita;
  não registrar como envio automático em segundo plano.
- **Telefone bloqueado:** bloquear durante uma coleta curta, com rede, e
  observar continuidade antes de desbloquear. Repetir a observação após perda
  de rede. Essa checagem parada não substitui segundo plano em movimento.
- **Acesso expirado ou reconexão:** a renovação normal é automática. Quando
  houver necessidade de nova conexão, pedir o link ao bot e seguir o caminho
  guiado, sem recriar nome, trajetos ou passageiros. Repetir após reiniciar o
  app entre o pedido e a conclusão: deve retomar o mesmo pedido, sem códigos.
  Abertura e consulta não aprovam a conexão nem ligam localização.
- **Troca de celular:** conferir a mensagem que avisa a substituição e a ação
  “Usar este celular”. Durante coleta não confirmada como parada, a troca deve
  ficar bloqueada. Uma resposta antiga ou em outra conversa não pode autorizar
  o novo pedido. Confirmar só o pedido que acabou de ser feito.
- **Viagem parada após reconexão:** se a viagem ainda não estiver encerrada,
  pedir para finalizar e conferir a revisão “Finalizar esta viagem” oferecida
  na conversa. Passa somente se o servidor já tiver a parada daquela sessão
  confirmada. Uma retomada ou nova sessão posterior à revisão invalida essa
  confirmação; não deve finalizar a viagem com uma resposta antiga.
- **Reabrir após parada:** depois de uma parada confirmada, reabrir o app deve
  mantê-lo parado; não começar nova coleta ao consultar ou retomar a tela.

## Só depois: percurso e compreensão

Não sair antes de iniciar **e finalizar parado** na plataforma que será usada.
Falha de abertura, primeiro GPS ausente, coleta que não para ou finalização
pendente exige corrigir e repetir essa etapa. O motorista não opera o telefone
em movimento; o acompanhante registra. No percurso, P1/P2 observam o aviso nos
seus pontos, e o acompanhante verifica destinatário, repetição e antecedência
útil. Usar o [roteiro de campo](rodada-final.md) para tempo de tela bloqueada,
rede, bateria e encerramento automático, em saídas separadas quando necessário.

Ao terminar, fazer poucas perguntas, sem ensinar a resposta:

- “Em quais trajetos você entrou e de onde vai embarcar?”
- “Quando sua localização começa e quando para?”
- “A viagem terminou ou ainda falta confirmar alguma coisa?”

No percurso, acrescentar: “O aviso chegou a tempo e ajudou você a se preparar?”
Uma resposta técnica correta não substitui observar a pessoa completar a tarefa.

## Registro curto e alcance da prova

Registrar por cenário: plataforma e versões; tarefa; ações e total; ajuda
necessária; resultado observado; evidência privada sem segredos; resultado
**aprovado, falhou, pendente ou bloqueado pela versão**. Para início/fim,
registrar também primeiro GPS válido, parada local e confirmação no servidor.

Automação local comprova os casos que executou: escolhas, isolamento entre
trajetos, autorização, versões, repetição e protocolos com serviços simulados.
Não comprova abertura real do WhatsApp para o app, GPS físico, tela bloqueada,
bateria, chegada do aviso ou compreensão. O teste físico parado comprova esse
percurso naquela combinação de aparelho e versão; utilidade da aproximação e
comportamento em movimento exigem campo.

Se uma etapa falhar, corrigir e repeti-la, identificando a nova versão. Registrar
as pendências sem transformar build, teste simulado ou sucesso de outra plataforma
em aceite desta. **Não encerrar uma issue apenas porque a simulação passou.**
