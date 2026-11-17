# Proposta de cadastro e início simples pelo WhatsApp

Proposta de produto de 16/11/2026, detalhada pelo mantenedor e em implementação
local desde 17/11. A entrada desejada continua sendo o link enviado pelo bot;
abrir o aplicativo pelo ícone não foi escolhido como substituto desse fluxo.
Referência inicial: `ce35224`; a implementação incorpora a `main` em `14cd663`,
incluindo a melhoria do aviso de privacidade do PR #132. Este texto não substitui
o [guia da versão publicada](cadastro-guiado-whatsapp.md) nem comprova publicação.
A prioridade é tornar o uso cotidiano
compreensível para pessoas que não conhecem o projeto e têm pouca familiaridade
com tecnologia.

## Implementação em andamento

- **Uma saída pronta:** “Começar viagem” prepara a autorização e responde com
  trajeto, horário, passageiros previstos e link. Não pede número da saída nem
  `CONFIRMAR` com código. O aplicativo continua exigindo o toque de início;
  receber a mensagem ou abrir o link não liga o GPS.
- **Várias saídas:** o bot ainda pede uma escolha por nome, direção ou opção.
  Nomes repetidos ou ambíguos não são escolhidos automaticamente. Esse caso
  ainda exige uma segunda mensagem e não atende à meta de uma interação.
- **Cadastro existente:** um convite do mesmo motorista permite adicionar outro
  trajeto sem encerrar o vínculo. A pessoa pode responder “MESMO PONTO”. Nome,
  preferências e trajetos anteriores são preservados; novas participações
  continuam passando pela aprovação e preparação da ordem pelo motorista.
  Mudar para outro motorista continua fora deste incremento.
- **Orientações:** AJUDA, lembrete da saída e botão do app usam a mesma linguagem:
  “Começar viagem” e “Começar e compartilhar localização”. As mensagens de
  recuperação também levam de volta à entrada curta.
- **Apresentação:** catálogo do motorista e seleção/resumo do passageiro usam
  blocos com nome, seta, ida/volta, horário e dias disponíveis. As setas não
  inventam origem nem trocam pontos de embarque.
- **Retorno após início:** o app tenta abrir a conversa fixa do bot uma vez
  depois do sucesso explícito do início atual. Não navega por uma consulta,
  resposta antiga ou ao reabrir a tela. Falha de abertura oferece retorno
  manual sem reiniciar a coleta; esperar o WhatsApp não bloqueia o botão de parar.
  Implementado e testado com fakes, ainda sem prova no WhatsApp/aparelho real.

Essas mudanças são locais, com validação registrada nas
[evidências de implementação](../../program/evidence/mvp-ux-20261117.md).
O pareamento sem códigos e o início visual com várias opções ainda precisam de
implementação. A entrada iPhone mantém o botão provisório aceito. Os comandos antigos de
início e retomada continuam compatíveis; não foram removidos.

No iPhone, a conta Apple gratuita usada no projeto não habilita Associated
Domains, conforme a [matriz de capacidades da Apple](https://developer.apple.com/help/account/reference/supported-capabilities-ios).
O link HTTPS atual passa por uma página com outro botão. Em 17/11 o mantenedor
aceitou esse botão intermediário provisoriamente no iPhone, sem contratar uma
assinatura: link → “Abrir VanRides” → confirmação no app. A abertura pelo ícone
foi uma alternativa apresentada, não uma decisão aceita. Abertura direta no
iPhone fica como evolução de distribuição; não bloqueia essa opção provisória.
Qualquer consulta de saída pendente permanece autenticada e não pode iniciar GPS.

## Regra de aceitação do início

- Motorista cadastrado, trajeto pronto, aparelho autorizado e permissões concedidas:
  **uma mensagem do motorista ao bot e até três ações no total**.
- Exceção provisória aceita em 17/11 para o iPhone gratuito: **até quatro ações**,
  incluindo o botão da página intermediária. Não declarar três ações nesse caso.
  Pedidos adicionais do sistema também entram na contagem da prova física.
- Uma ação de jornada é enviar uma mensagem, abrir uma opção/link ou confirmar
  o início no app. Escrever e enviar uma mesma resposta contam juntos; não se
  contam teclas ou o toque de envio novamente. Abrir o link e confirmar no app
  entram na conta. Respostas automáticas não contam como ações do motorista,
  mas seu volume e clareza também serão avaliados.
- Meta preferida: uma interação com o bot. Não apresentar isso como um único
  toque do processo inteiro. Um único toque total depende de outro desenho de
  autorização e ainda não está aprovado.
- Instalação e primeiras permissões ficam no cadastro inicial. Falta de internet,
  autorização vencida ou impedimento do sistema devem ter recuperação clara;
  não prometer o mesmo teto em qualquer falha ou permissão revogada.
- Nenhum código para copiar, identificador técnico ou comando em maiúsculas será
  requisito da jornada nova. As referências de segurança continuam internas.

O caminho atual exige três mensagens (`INICIAR SAÍDA`, número da saída e
`CONFIRMAR` com oito letras), mais abrir o link e tocar em iniciar: pelo menos
cinco ações, fora do preparo e das permissões.

## Uso diário proposto

1. O motorista escreve **“Quero iniciar a rota”** ou **“Começar viagem”**.
2. O bot mostra o trajeto, o horário, a quantidade de passageiros previstos e a
   ação **“Abrir viagem”**. O motorista toca e entra direto no aplicativo.
3. O aplicativo mostra o nome do trajeto e uma única decisão:
   **“Compartilhar localização”**. Depois do sucesso da ativação explícita,
   abre a conversa do bot e permanece coletando em segundo plano. Somente após
   o backend aceitar a primeira posição válida da sessão atual, o bot informa
   **“Você está compartilhando sua localização. Vou avisar os passageiros.
   Quando terminar, me avise por aqui.”** O retorno ao WhatsApp não conta como
   prova de GPS recebido. Não enviar uma mensagem em nome do motorista só
   para anunciar o retorno.

Abrir a conversa usa uma URL fixa do bot, sem copiar autorização, coordenadas
ou identificadores de viagem para o WhatsApp. Fazer uma tentativa por ação
explícita e com a tela ativa; não redirecionar em loop, após uma simples
atualização, ao reabrir o app ou em resposta atrasada depois de sair da tela.
Se falhar a ativação, permanecer no app e orientar a recuperação. Se falhar
somente a abertura do WhatsApp, preservar a coleta e oferecer retorno manual.

São uma mensagem e dois toques (três toques no iPhone provisório). Não pedir um código de confirmação no WhatsApp
e outra confirmação equivalente no aplicativo.
Essa contagem depende de o link abrir diretamente o app. No iPhone, o botão
“Abrir VanRides” aceito provisoriamente leva a quatro ações. Provar a abertura a
partir do WhatsApp em cada plataforma continua condição de aceite. Não contar
abertura por ferramenta de desenvolvimento como prova desse percurso.

Na referência examinada, o iOS registra o esquema `vanridesdriver`, mas não há
entitlement de Associated Domains nem arquivo AASA servido. A página HTTPS
exige um toque para abrir esse esquema. O Android declara App Links, sem prova
atual de associação a partir do WhatsApp. Portanto, os tetos são requisitos a
provar, não capacidades já demonstradas. A exceção provisória do iPhone preserva
a confirmação nativa; não omitir o clique intermediário nem iniciar GPS ao abrir
o link para reduzir artificialmente a contagem.

Se houver várias saídas válidas, cada opção deve abrir diretamente a sua saída,
com nome e horário legíveis. Não acrescentar a sequência “escolher número →
confirmar código → receber outro link”. Nunca adivinhar uma viagem ambígua pelo
horário mais próximo. A forma visual dessas opções depende do canal disponível.
Mostrar somente saídas realmente iniciáveis, excluindo as encerradas. Para
quatro ou mais opções, os cartões precisam continuar abrindo diretamente o
trajeto escolhido, sem esconder saídas nem acrescentar outro menu. Esse cenário
também exige uma prova de contagem e legibilidade antes de aprovação.

Há dependência técnica para várias opções: hoje emitir um `start_intent` revoga
os anteriores pendentes do motorista. Não basta gerar vários links existentes.
É necessário desenhar uma oferta correlacionada com opções próprias, consumo
atômico e uma única ativação, preservando autoria, versão e exclusão mútua.

Exemplo de texto, com dados fictícios:

> Você: Começar viagem
>
> VanRides: Faculdade · ida das 18h40
> 8 passageiros previstos, incluindo 2 que ainda não responderam.
> Ao começar, vou usar a localização deste celular para avisá-los.
> Abrir viagem
>
> Aplicativo: Faculdade · ida das 18h40
> Começar e compartilhar localização
>
> VanRides, depois do primeiro GPS válido: Viagem iniciada.
> Vou avisar seus passageiros quando você estiver chegando.

“Abrir viagem” representa a ação desejada, não um botão já disponível. Links não
podem exigir edição ou cópia de texto. Preview de link e abertura do app não
devem iniciar GPS por si. O aplicativo mantém visíveis o estado e a opção de
parar/encerrar. Iniciar automaticamente ao abrir um link não faz parte da
primeira simplificação.

## Primeiro cadastro e apresentação

O primeiro cadastro é separado do início diário; não será apresentado como três
ações quando houver perguntas, instalação e permissões adicionais. O bot explica
o benefício em poucas linhas e pergunta uma coisa por mensagem. Informações já
conhecidas não são pedidas novamente.

**Motorista**

1. Entrada reconhecida pelo convite/liberação do piloto, sem copiar um código.
   A autorização de motorista continua sendo conferida nos bastidores.
2. Apresentação curta e nome, caso ainda falte. Exemplo: “Oi! Eu sou o VanRides.
   Organizo seus passageiros e aviso quando você está chegando. Como posso chamar você?”
3. Trajeto: nome legível, destino, dias e horário. Aceitar uma resposta que já contenha vários
   desses dados e perguntar somente o que falta. Ida e volta devem aparecer com
   esses nomes. Reutilizar dados ao criar o retorno, com revisão do motorista.
   Permitir criar mais de um trajeto. Setas expressam a direção, sem inventar
   uma origem ou inverter pontos automaticamente quando isso não foi informado.
4. Um resumo claro para salvar. O aviso de dados e as escolhas necessárias são
   explícitos, curtos e acessíveis; não são aceitos silenciosamente.
5. Instalação e conexão do aplicativo guiadas no mesmo contexto, com uma ação
   “Conectar meu celular”. Mensagem pronta e retorno automático substituem
   mostrar/copiar/colar oito letras. O mecanismo sem segredo visível precisa
   de desenho técnico próprio; apenas preencher as oito letras não cumpre a meta.
6. O bot informa quando o preparo terminou: “Tudo pronto. Na hora de sair,
   escreva Começar viagem.”

**Passageiro**

1. Abre o convite e já vê quem o convidou. O convite identifica o motorista;
   depois o passageiro escolhe um ou mais dos trajetos disponíveis dele.
2. Informa apenas nome/dados que faltarem e suas escolhas necessárias.
3. Escolhe os dias por trajeto. Ida e volta são seleções independentes; oferecer
   “mesmos dias” como atalho, sem obrigar a ir em ambas. Envia o ponto fixo de
   embarque de cada trajeto pelo recurso de localização do próprio WhatsApp. Orientação
   curta e específica para o celular, com uma imagem de apoio quando necessário.
   Coordenadas e o comando `PONTO` ficam no guia técnico de testes pelo computador.
4. Confere um resumo de motorista, trajeto, dias, ponto e avisos e envia o pedido.
   O convite autoriza entrar com aquele motorista; não pedir ao motorista que
   aprove novamente a mesma pessoa. A conferência operacional de ponto novo e
   ordem continua necessária e aparece diretamente ao motorista, com ação de
   ajustar, sem descobrir um comando interno. Informar se o embarque ainda
   depende desse preparo; não dizer “tudo pronto” antes da elegibilidade real.
5. Recebe a confirmação ou a instrução de correção no mesmo chat.

Exemplo de apresentação, com texto legível mesmo sem botões nativos:

```text
Trajetos do seu motorista

1 · Centro → Faculdade
Ida · segunda a sexta · 07:00

2 · Faculdade → Centro
Volta · segunda a sexta · 18:00

3 · Bairro Norte → Faculdade
Ida · sábado · 08:00

Em quais você vai? Pode escolher mais de um.
```

O link é o convite padrão e continua individual, de um uso. O desenho assume
autorização ao primeiro destinatário que resgatar esse convite; não é um link
genérico ilimitado. Quando o destinatário já for conhecido, usar a identidade
esperada. O motorista pode revogar um convite ainda não utilizado.
O motorista também pode pedir
o envio pelo número. O código atual restringe esse envio a contato conhecido,
admitido e com autorização prévia de 24 horas. Isso ainda não realiza a ideia
de convidar um contato novo apenas informando o número. É preciso redesenhar
a obtenção/registro da autorização e a admissão antes de oferecer esse caminho
como pronto; não adicionar uma sequência pública de códigos nem fingir envio.

Hoje há um ponto aprovado global por passageiro, embora a participação tenha
referência de ponto por trajeto. Permitir embarques diferentes na ida e na volta
exige rever essa restrição no banco e o escopo da proposta/aprovação. Corrigir
um ponto de uma rota não pode alterar silenciosamente as demais nem o snapshot
de uma viagem já iniciada. Reusar o ponto antigo só mediante escolha explícita.

O usuário não deve precisar conhecer GitHub, o painel da equipe ou a configuração
de testes. Controles do piloto permanecem na operação interna.

## Finalização pelo mesmo caminho

1. O motorista escreve “Quero finalizar a viagem”.
2. O bot mostra qual viagem está em andamento e entrega o link de finalização.
3. No app, uma ação **“Finalizar viagem e parar localização”** confirma as duas
   coisas. Depois da parada e confirmação da operação atual, o app abre a
   conversa do bot. O bot confirma a viagem concluída e a localização desligada.

É o fluxo de produto escolhido pelo mantenedor, ainda não implementado. O botão
atual “Parar localização” só interrompe o sensor; não conclui a viagem. Hoje
`FINALIZAR` abre uma revisão com código e pode solicitar parada remota. Esse
caminho antigo não deve ser apresentado como se fosse a experiência acima.

O novo link prepara a operação, não encerra ao ser aberto. A autorização de
finalização deve ser distinta da de início, ligada à viagem, sessão, aparelho,
ambiente, versão e prazo. Link antigo não encerra viagem nova e repetição não
gera efeito duplicado. O retorno externo nunca carrega o segredo desse link.

Sem internet, parar o sensor localmente continua disponível. Manter o recibo
pendente, mostrar “Localização desligada. Falta confirmar a finalização” e
reconciliar ao reconectar, sem anunciar sucesso remoto nem iniciar outra coleta.
Não usar um estado antigo de parada como prova da ação atual. Se o motorista
sair da tela, não abrir o WhatsApp depois por causa de uma resposta atrasada.

## Manutenção sem refazer o cadastro

- Entrar em outra rotina do mesmo motorista preserva o vínculo, o perfil e as
  escolhas. Reutilizar ponto aprovado quando a pessoa confirmar que é o mesmo.
- Trocar de motorista é uma decisão distinta, com explicação e confirmação do
  passageiro. Não inferir uma troca a partir de um convite ambíguo.
- Corrigir o ponto mantém o ponto anterior até a nova aprovação. Não exigir
  encerrar o vínculo como forma de alterar trajeto, dias ou ponto.
- Uma interrupção retoma a última etapa com uma frase curta: “Falta só o seu
  ponto de embarque”. Sem mandar a pessoa reiniciar todo o cadastro.
- Renovação normal do acesso ao aparelho permanece automática. Acesso expirado
  ou sessão do aparelho revogada pede reconexão guiada, preservando nome,
  rotinas e passageiros, desde que o motorista, o ambiente e a coorte continuem
  autorizados. Revogação administrativa do motorista exige regularização;
  reconectar o celular não pode contornar esse bloqueio.

## Linguagem e recursos visuais

- Preferir “viagem de hoje”, “trajeto” e “conectar celular”. “Ocorrência”,
  “vínculo”, “token”, “ACK”, UUID e nomes de comandos internos não entram no texto.
- Uma pergunta por mensagem e uma ação principal por etapa. Até três ações
  imediatas por cartão/pergunta, com rótulos curtos e específicos. Evitar menus
  extensos; vários trajetos têm cartões identificados, não nomes truncados.
- Usar botões/listas nativos quando comprovadamente suportados; não desenhar
  falsos botões em imagens. Manter uma resposta curta por texto como alternativa.
- Ícones acompanham palavras. Cor, emoji e imagem não são o único meio de
  entender uma instrução. Uma imagem de ajuda sempre tem orientação textual.
- Mostrar um resumo somente quando ele permite tomar uma decisão. Não repetir
  a lista completa e instruções de todos os comandos a cada resposta.
- Áudio é uma possibilidade a estudar; não anunciar compreensão de áudio
  enquanto transcrição, privacidade, custo e resposta não estiverem validados.

## Canal e segurança

A [Meta documenta botões de resposta](https://developers.facebook.com/documentation/business-messaging/whatsapp/messages/interactive-reply-buttons-messages)
com rótulos visíveis e identificadores internos devolvidos no webhook. O
[roteador público da Evolution](https://github.com/evolution-foundation/evolution-api/blob/main/src/api/routes/sendMessage.router.ts)
expõe `sendButtons` e `sendList`. Isso não comprova a renderização e o callback
na versão/modo Baileys instalados. A primeira prova será uma escolha funcionando
no Android, iPhone e cliente do Mac, em janela futura combinada.

Se o canal atual não entregar a interação visual com confiabilidade, comparar
as alternativas e os custos antes de mudar integração, número ou conta. Não
instalar outro serviço, ativar consumo pago ou migrar a conta nesta proposta.
`wa.me?text=` exibe o texto preenchido ao usuário: não presumir que esse recurso
oculta metadados de convite ou pareamento.

Preservar autoria, papel do motorista, ambiente, autorização do aparelho, PKCE e
prova da chave, validade, idempotência, versão da saída/lista, consentimentos e
permissões. A confirmação final no app revalida o resumo mostrado. Clique
repetido não cria outra coleta. GPS só aparece como ativo após a primeira
posição válida da sessão atual ser aceita pelo backend;
parada e sua confirmação permanecem persistidas e não reiniciam ao reconectar.

## Ordem de implementação e prova

| Etapa | Entrega | Issues existentes relacionadas |
| --- | --- | --- |
| 1 | Confirmar capacidade visual do canal; desenhar todas as mensagens e contar ações, incluindo app e exceções | #28, #49, #91, #96 |
| 2 | Acrescentar rotina e reutilizar dados sem fechar vínculo; apresentação e recuperação do cadastro | #28, #49 |
| 3 | Emitir resumo/link da saída sem confirmação duplicada; seleção direta por saída; confirmação final no app | #59, #60, #87, #96 |
| 4 | Conectar/reconectar celular sem copiar código; estados e instruções claras | #59, #60, #87, #97 |
| 5 | Convite autoriza entrada; escolher vários trajetos e dias, com ponto próprio por trajeto e revisão operacional delimitada | #28, #49 |
| 6 | Retorno ao WhatsApp após início explícito; finalização no app com encerramento da viagem e parada confirmada | #59, #60, #87, #96 |
| 7 | Ensaiar a jornada completa parado, com pessoas que não conhecem o projeto, antes de novo percurso | #95, #96 |

As referências não alteram critérios nem fecham issues; a equipe deve conferir
a distribuição na especificação antes de implementar. O card vertical ativo
continua sendo `VR-HML-010-TEAM-API`.

A prova deve registrar ações, mensagens e tempo até GPS válido em cadastro novo,
pessoa já cadastrada, uma/várias saídas, acesso expirado, link vencido, clique
repetido e perda de internet. É falha de experiência depender de explicação do
desenvolvedor, apagar vínculo para continuar ou exceder três ações no início
cotidiano preparado (quatro no iPhone, pela exceção provisória aceita).
Cadastro inicial e recuperação têm contagem própria.

As partes implementadas localmente estão separadas no início desta proposta e
nas evidências; os contratos novos descritos aqui continuam por implementar.
Não há publicação, migração, mudança de CI ou operação nos aparelhos nesta
revisão. A versão publicada não recebe essas mudanças automaticamente.
Antes de outro ensaio, conferir a pausa da rotina temporária da tentativa
interrompida: ela não ficou confirmada na automação. Não usar essa pendência
como motivo para retomar testes nos aparelhos agora.
