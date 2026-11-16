# Proposta de cadastro e início simples pelo WhatsApp

Proposta de produto de 16/11/2026, após o ensaio interrompido pelo mantenedor.
Referência técnica examinada: `ce35224`. Este texto descreve a próxima experiência;
não substitui o [guia da versão publicada](cadastro-guiado-whatsapp.md) nem comprova
que as mudanças foram implementadas. A prioridade é tornar o uso cotidiano
compreensível para pessoas que não conhecem o projeto e têm pouca familiaridade
com tecnologia.

## Regra de aceitação do início

- Motorista cadastrado, trajeto pronto, aparelho autorizado e permissões concedidas:
  **uma mensagem do motorista ao bot e até três ações no total**.
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

1. O motorista escreve **“Começar viagem”**.
2. O bot mostra o trajeto, o horário, a quantidade de passageiros previstos e a
   ação **“Abrir viagem”**. O motorista toca e entra direto no aplicativo.
3. O aplicativo mostra o nome do trajeto e uma única decisão:
   **“Começar e compartilhar localização”**. Somente após o backend aceitar a
   primeira posição válida da sessão atual, o bot informa
   **“Viagem iniciada. Vou avisar seus passageiros.”**

São uma mensagem e dois toques. Não pedir um código de confirmação no WhatsApp
e outra confirmação equivalente no aplicativo.
Essa contagem depende de o link abrir diretamente o app. Se houver uma página
intermediária exigindo tocar em “Abrir VanRides”, esse toque também conta e o
fluxo passa de três ações. Resolver e provar a abertura a partir do WhatsApp
em cada plataforma é condição de aceite, incluindo as limitações da distribuição
iOS usada no piloto. Não contar abertura por ferramenta de desenvolvimento
como prova desse percurso.

Na referência examinada, o iOS registra o esquema `vanridesdriver`, mas não há
entitlement de Associated Domains nem arquivo AASA servido. A página HTTPS
exige um toque para abrir esse esquema. O Android declara App Links, sem prova
atual de associação a partir do WhatsApp. Portanto, o teto é um requisito a
implementar e provar, não uma capacidade já demonstrada. Se a abertura direta
não for viável no modo de distribuição escolhido, revisar o desenho da
confirmação antes de aprovar o limite; não omitir o clique intermediário.

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
3. Trajeto: destino, dias e horário. Aceitar uma resposta que já contenha vários
   desses dados e perguntar somente o que falta. Ida e volta devem aparecer com
   esses nomes. Reutilizar dados ao criar o retorno, com revisão do motorista.
4. Um resumo claro para salvar. O aviso de dados e as escolhas necessárias são
   explícitos, curtos e acessíveis; não são aceitos silenciosamente.
5. Instalação e conexão do aplicativo guiadas no mesmo contexto, com uma ação
   “Conectar meu celular”. Mensagem pronta e retorno automático substituem
   mostrar/copiar/colar oito letras. O mecanismo sem segredo visível precisa
   de desenho técnico próprio; apenas preencher as oito letras não cumpre a meta.
6. O bot informa quando o preparo terminou: “Tudo pronto. Na hora de sair,
   escreva Começar viagem.”

**Passageiro**

1. Abre o convite do motorista e já vê quem o convidou e qual é o trajeto.
2. Informa apenas nome/dados que faltarem e suas escolhas necessárias.
3. Envia o ponto fixo pelo recurso de localização do próprio WhatsApp. Orientação
   curta e específica para o celular, com uma imagem de apoio quando necessário.
   Coordenadas e o comando `PONTO` ficam no guia técnico de testes pelo computador.
4. Confere um resumo de motorista, trajeto, dias, ponto e avisos e envia o pedido.
   O motorista recebe o pedido diretamente, com ações de aprovar ou pedir ajuste;
   não precisa descobrir que deve escrever `REVISAR PEDIDO`.
5. Recebe a confirmação ou a instrução de correção no mesmo chat.

O usuário não deve precisar conhecer GitHub, o painel da equipe ou a configuração
de testes. Controles do piloto permanecem na operação interna.

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
| 5 | Ensaiar a jornada completa parado, com pessoas que não conhecem o projeto, antes de novo percurso | #95, #96 |

As referências não alteram critérios nem fecham issues; a equipe deve conferir
a distribuição na especificação antes de implementar. O card vertical ativo
continua sendo `VR-HML-010-TEAM-API`.

A prova deve registrar ações, mensagens e tempo até GPS válido em cadastro novo,
pessoa já cadastrada, uma/várias saídas, acesso expirado, link vencido, clique
repetido e perda de internet. É falha de experiência depender de explicação do
desenvolvedor, apagar vínculo para continuar ou exceder três ações no início
cotidiano preparado. Cadastro inicial e recuperação têm contagem própria.

Esta revisão altera somente documentação local. Não há mudança de runtime,
schema, CI, VPS ou aparelhos. Reverter os arquivos desta proposta restaura a
documentação anterior; a versão publicada mantém seu comportamento atual.
Antes de outro ensaio, conferir a pausa da rotina temporária da tentativa
interrompida: ela não ficou confirmada na automação. Não usar essa pendência
como motivo para retomar testes nos aparelhos agora.
