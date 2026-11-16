# Rodada final no Android e no iPhone

Este roteiro está preparado para os testes adiados pelo mantenedor em
16/11/2026. Nenhuma linha abaixo representa um resultado já aprovado. Fazer
um registro separado por plataforma; uma rodada Android não aprova o iPhone.

## Antes de começar

O integrador preenche a versão da API, o schema, o bundle do bot, a versão e
o hash do aplicativo e os flags habilitados. Usar a mesma combinação durante
a rodada. A API e o bot precisam estar saudáveis e a rotina do ensaio deve ter
geografia conferida. Em 16/11, aproximação, fim automático e rotinas de ensaio
foram habilitados para as rotinas elegíveis da coorte compartilhada. Conferir
esses flags antes de sair; habilitação não comprova aviso nem fim automático
em percurso. A limpeza real de dados continua desligada.

Preparar um motorista, dois passageiros autorizados, rotina do dia, pontos
aprovados, ordem de embarque e destino fixo. Um passageiro confirma presença;
o outro pode ficar pendente para conferir que continua previsto. Fazer os
testes de cancelamento em outra ocorrência, sem alterar a lista do percurso
por acidente. O [guia da equipe](homologacao-da-equipe.md#jornada-completa-motorista-e-passageiro)
explica o cadastro, convite por link, presença, vínculo e início.

O Android usa o [APK publicado](https://vanrides-inovacao-api.matheusduarte.dev.br/motorista/VanRides.apk).
Conferir o hash da entrega antes de atualizar; preservar a assinatura. O
iPhone usa o build assinado do flavor `driver`, com a conta/Personal Team do
proprietário. Sua instalação e validade precisam ser conferidas no próprio
artefato. Se a assinatura vencer, repetir o
[procedimento iOS](../../src/aplicativo-motorista/integration_test/driver_mvp_ios/README.md),
sem enviar senha, perfil, aparelho ou credenciais ao Git. Não instalar o APK
no iPhone nem usar um build antigo como prova da versão nova.

Conferir internet, relógio automático, permissões e bateria suficiente para
completar a janela. Registrar as configurações de bateria da MIUI e as
permissões de localização de cada aparelho. A pessoa de apoio opera o teste
durante o percurso. Para medir consumo, usar o aparelho fora do USB e registrar
brilho, rede e tempo de tela; uma rodada carregando não mede autonomia.

## Execução por plataforma

| Etapa | Ação | O que observar e registrar |
| --- | --- | --- |
| Instalação | Abrir pelo ícone, com o cabo desconectado. | Versão correta, abertura estável e vínculo recuperado. |
| Início | Iniciar a saída pelo bot, abrir o link e confirmar no app. | Primeira posição válida e confirmação do início; abrir o link sozinho não basta. |
| Tela aberta, 3 min | Manter a coleta com internet e sinal disponível. | Idade das posições, precisão, posições aceitas/rejeitadas e eventuais lacunas. |
| Tela bloqueada, 15 min | Bloquear sem encerrar o app pela lista. Incluir movimento no trecho combinado. | Continuidade em segundo plano, maior lacuna e mensagens de estado. Não usar somente o contador do app como prova. |
| Sem internet, 3 min | Parado, desligar também Wi-Fi; bloquear sem reabrir o app. | O receptor perde frescor e o passageiro não recebe uma posição antiga como atual. |
| Recuperação | Restabelecer a internet e bloquear sem abrir o app. | Tempo até o primeiro GPS novo aceito. Registrar se precisou abrir, retomar ou gerar outro link; não ocultar essa intervenção. |
| Aproximação | Seguir a ordem dos pontos no trajeto conferido, com a função habilitada. | Aviso no WhatsApp do passageiro correto, uma vez, antes do embarque. Registrar perdas, atrasos e avisos indevidos. |
| Fim automático | Após as paradas, chegar ao destino e aguardar parado com GPS válido. | Viagem encerrada, confirmação no bot e ACK de parada do coletor. Sem evidência suficiente, o fim deve continuar pendente. |
| Contingência manual | Em outra ocorrência, usar FINALIZAR e confirmar a revisão exibida. | A viagem certa encerra e o GPS para; ABANDONAR não encerra. |
| Parada sem rede | Em outra coleta, desligar a internet e tocar em Parar localização; depois reconectar. | A coleta local para e a parada pendente chega ao receptor. Não confundir parar GPS com finalizar viagem. |

A janela de bateria deve durar pelo menos 30 minutos efetivos, incluindo o
trecho em segundo plano. Se o percurso terminar antes, completar a janela
parado, registrando essa diferença. Anotar carga inicial/final, duração e
qualquer interrupção separadamente por aparelho. A medição serve para comparar
as condições observadas, sem extrapolar uma autonomia de dia inteiro a partir
de poucos pontos percentuais.

## Critérios antes de sair

As regras técnicas da versão atual aceitam GPS com idade de até 20 segundos
e precisão de até 50 metros. O início precisa de uma posição aceita dentro
da janela de 30 segundos. Ausência de GPS deve aparecer como desatualizada;
não reiniciar o relógio de captura com um heartbeat ou uma posição repetida.
Esses limites vêm de `WaTrackingPolicy`; não prometem sinal de satélite em
todo ambiente.

A aproximação usa três posições coerentes, progresso e sentido do movimento;
o raio inicial de 800 metros é hipótese de calibração. O fim automático usa
passagem pelas paradas, saída anterior do destino, precisão de até 25 metros,
baixa velocidade e permanência válida de 60 segundos, sem lacunas maiores que
20 segundos. São parâmetros iniciais de `WaApproachPolicy` e
`WaRunFinishPolicy`, sujeitos à avaliação do ensaio.

Antes de cada percurso, combinar e anotar a antecedência que o passageiro
considera suficiente para se preparar. Depois, perguntar: o aviso chegou a
tempo? Ficou claro o que fazer? Chegou cedo ou tarde demais? Conferir o horário
de recebimento com a chegada observada pela pessoa de apoio. Um envio aceito
pela Evolution não comprova recebimento nem utilidade no embarque.

Uma jornada só fica aprovada quando as etapas aplicáveis têm evidência e não
há destinatário errado, efeito duplicado ou posição antiga apresentada como
atual. Etapa não executada permanece pendente. Falha em continuidade,
recuperação ou parada requer diagnóstico e repetição da parte afetada na
versão corrigida; o resultado inicial continua registrado.

## Registro para a issue

Copiar o modelo abaixo, com métricas sanitizadas. Não publicar números,
coordenadas, capturas de conversas, pins, convites, tokens nem identificadores
privados de aparelho. Anexos brutos ficam no diretório privado do operador.

```text
Plataforma / modelo / sistema:
Aplicativo (versão e hash):
API / schema / bundle do bot:
Data, horário e fuso / referência privada do cenário:
Flags e geografia do ensaio conferidos:
Rede / permissões / bateria / condições de tela:
Etapas executadas e etapas pendentes:
Primeiro GPS / maior lacuna / recuperação após rede:
Aviso: destinatário correto? quantidade? antecedência combinada/observada?
Fim da viagem / parada local / ACK no receptor:
Bateria inicial/final / duração sem carregamento:
Avaliação do participante / falhas / intervenções necessárias:
Resultado: aprovado, reprovado ou pendente, por critério:
```

Vincular Android a #59/#93, iPhone a #60/#94, bateria a #61, instalação e
renovação a #88/#97 e utilidade do aviso a #95. A consolidação fica na #96.
Não encerrar essas issues somente por build, testes sintéticos ou roteiro
preenchido sem a execução física correspondente.
