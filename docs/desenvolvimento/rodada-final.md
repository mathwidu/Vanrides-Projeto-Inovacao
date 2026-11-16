# Rodada final conjunta no Android e no iPhone

Este é o roteiro único da rodada com deslocamento real e dois passageiros nos
seus próprios telefones. A entrega técnica de referência é o PR #128, main
`7531991`, CI `43912785096` aprovada em 74 s. Os apps normais 0.4.0+15 já têm prova
estacionada; a composição sintética IDA/VOLTA passou 2/2 e o canal real conciliou
22 callbacks, nove entregas e quatro leituras. As versões e os limites dessas
provas estão no [registro da entrega](../../program/evidence/mvp-entrega-20261116.md);
não aprovam campo, rede, tela bloqueada, bateria ou utilidade do aviso.

Reservar **45–60 minutos por plataforma**, conforme o percurso e o preparo.
É estimativa de organização, não prazo nem promessa de aprovação. Os blocos
Android e iPhone são sequenciais: o mesmo motorista só pode ter um aparelho
vinculado ativo. Preparo comum ou dificuldades podem exigir tempo adicional.

O iPhone está liberado para uso pessoal. O preparo antecipado abaixo é remoto;
não exige USB, tela desbloqueada ou qualquer ação no aparelho agora. Instalação,
renovação ou interação presencial só entram na janela combinada para a rodada.

## 1. Preparo do operador, antes do encontro

- Conferir saúde e versões efetivas da API, worker/bot, n8n e aplicativos;
  registrar SHA/digest/JAR, schema, bundle e hash dos artefatos. A referência
  acima não dispensa conferir o que estará instalado no dia.
- Conferir `WA_MVP_REHEARSAL_ROUTINES`, `WA_APPROACH_ENABLED` e
  `WA_AUTO_FINISH_ENABLED`: estão ON para elegíveis da **coorte compartilhada**,
  sem whitelist por rotina. Identificar os participantes/rotinas elegíveis;
  não presumir que os efeitos estão limitados apenas ao percurso escolhido.
  IA e limpeza real de privacidade permanecem OFF. Rotação de backups fica
  em catálogo/prévia; esta rodada não executa exclusões.
- Conferir cadastro, aceite, vínculo motorista/passageiros, pontos aprovados,
  ordem, política de presença e geografia revisada. Preparar as ocorrências
  separadas da seção 3, sem iniciar coleta nem emitir antecipadamente o link
  que será usado no encontro.
- Android: conferir o [APK público normal +15](https://vanrides-inovacao-api.matheusduarte.dev.br/motorista/VanRides.apk),
  SHA-256 `7f22ebd465f0c2f9947ee8c9639fb0ee63405bc360e618fa97a1955bf6cba946`;
  preservar assinatura/dados e o +14 de retorno. Não reinstalar por rotina.
- iOS: a referência é o app normal +15, com perfil até **23/11/2026 às 08:27
  (Brasília)**. Se a rodada ultrapassar essa validade, reservar previamente
  a renovação conforme o [guia do aplicativo](aplicativo.md); não ocupar o
  iPhone durante seu uso pessoal para antecipar esse procedimento.
- Preparar a coleta automatizada de metadados: criação/geração da sessão,
  aquisições aceitas/rejeitadas e motivos, frescor, progresso, avisos,
  encerramento, STOP/ACK e callbacks. Usar os helpers e leituras autorizados;
  não alterar banco, relógio, GPS ou guards para obter um resultado esperado.

## 2. No encontro: fechar cenário, papéis e acesso

**Pessoa/motorista e passageiros:** combinar rota real, ordem dos pontos e
destino em canal privado. Usar locais reais conferidos, sem inventar
coordenadas. Combinar horários para P1/P2 aguardarem nos pontos e a antecedência
que cada um considera útil para se preparar para o embarque.

**Operador:** conferir esses dados, elegibilidade e versões antes de iniciar.
O [guia da equipe](homologacao-da-equipe.md#jornada-completa-motorista-e-passageiro)
reúne os passos de cadastro, convite, presença, vínculo e início.
Após aprovar os pontos, o motorista usa `ROTINAS`, `ORDEM N` e a revisão
apresentada pelo bot para confirmar a sequência; N é a opção da lista atual.

Combinar um trajeto que permita observar os pontos separadamente: começar fora
do raio inicial de aproximação de 800 m, passar pelos pontos na ordem revisada
e chegar ao destino após ter estado pelo menos 200 m afastado dele. Esses
valores exercitam a política atual e não garantem antecedência útil. O modo
de deslocamento pode ser caminhada, carona ou veículo, conforme o cenário
combinado; registrá-lo para não comparar condições diferentes como equivalentes.

| Papel | Preparação e ação na rodada |
| --- | --- |
| Motorista/coletor | Conta própria autorizada; leva o aparelho no deslocamento combinado. Se estiver dirigindo, faz ações no app/bot somente com o veículo parado. |
| P1 | Número distinto dos demais, vínculo/ponto aprovados, avisos de chegada aceitos e presença confirmada na ocorrência do percurso. Aguarda no ponto e avalia o aviso. |
| P2 | Outro número distinto, vínculo/ponto aprovados e avisos aceitos; mantém presença pendente para conferir a regra de inclusão. Aguarda no seu ponto e avalia o aviso. |
| Operador | Confere a lista antes da partida, prepara o vínculo de cada plataforma, acompanha metadados e registra tempos/resultados. Automação não substitui a confirmação do passageiro. |

A presença pendente não é aceite de avisos. O operador confere a política
`INCLUDE_BY_DEFAULT` e que **P1 e P2 aparecem incluídos** na revisão da partida.
Se a rotina usar outra política, resolver a diferença antes de começar; não
mudar a regra ou a lista silenciosamente durante o percurso.

Antes de cada bloco, confirmar **onde está acessível a conversa WhatsApp do
motorista** e como o link recém-emitido chegará ao aparelho que fará a coleta.
Não presumir qual telefone contém a conta primária, mover contas pessoais ou
confundir a conta de um passageiro com a do motorista. Combinar explicitamente
o cliente/dispositivo usado para a conversa e a abertura privada do link.

Conferir no aparelho escolhido relógio automático, internet, permissões,
localização e bateria suficiente. Registrar restrições de bateria da MIUI,
brilho/rede e condições de tela. Desconectar o carregamento antes da medição.

## 3. Separar as ocorrências e trocar de aparelho

O operador identifica data, sentido e referência privada de cada cenário.
Usar ocorrências válidas para o dia do início; não reutilizar uma já encerrada.
Como há uma ocorrência por rotina/data, preparar rotinas de ensaio distintas
para os cenários do mesmo dia, inclusive os dois percursos. Conferir vínculo,
pontos, consentimentos e presença em cada uma; não copiar a ausência do cenário
C para o passageiro pendente do percurso.

| Cenário | Uso |
| --- | --- |
| C — presença/cancelamento | Bloco comum, separado do percurso: responder VOU/NÃO VOU à mensagem da ocorrência correta, conferir dia/sentido da resposta e a lista PRESENÇAS. Para o aviso pós-saída, iniciar C com GPS válido, cancelar parada ainda não passada e conferir o aviso único ao motorista; encerrar C e obter STOP/ACK antes de seguir. |
| P — percurso de cada plataforma | P1 confirmado/P2 pendente, aproximação, bateria e fim automático. A ocorrência Android é distinta da ocorrência iPhone; não alterar presença/lista desse percurso por causa do cenário C. |
| M — fim manual de cada plataforma | Outra ocorrência, com coleta iniciada normalmente; testar a contingência manual sem atribuir seu resultado ao fim automático. |
| S — STOP offline de cada plataforma | Outra ocorrência/coleta, para persistência de parada e recuperação após reconectar/reabrir. Parar GPS não encerra a viagem. |

**Troca sequencial:** encerrar o bloco anterior, comprovar sensor parado e
STOP/ACK no servidor e fechar a ocorrência ainda ativa, quando necessário.
Só então o operador prepara o novo vínculo no Redmi ou iPhone e confirma o
aparelho autorizado. Não substituir/revogar o vínculo para trocar de plataforma
enquanto houver coletor ativo ou parada sem ACK. Não apagar o journal para
limpar a tela. Se a reconciliação não fechar, interromper a troca e diagnosticar.

## 4. Bloco por plataforma — estimativa de 45–60 min

Repetir a tabela no Android e depois no iPhone, ou na ordem combinada no dia.
O trecho de aproximação pode coincidir com a janela de tela bloqueada; anotar
os horários reais, sem somar o mesmo intervalo duas vezes. Planejar a perda de
rede em parada segura e fora dos pontos/destino, para não confundir ausência
intencional de conexão com utilidade do aviso.

| Etapa / janela | O que a pessoa faz | O que o operador confere ou automatiza |
| --- | --- | --- |
| Entrada e início, cerca de 5 min | Abre o app pelo ícone e confere o vínculo. No bot, envia INICIAR SAÍDA, escolhe a ocorrência, revisa a lista e confirma com o código recebido. Abre o link recém-emitido nesse aparelho e toca em Iniciar localização desta saída. | Versão/ocorrência/lista, chave do aparelho e nova geração; primeiro GPS aceito, Posição recente recebida no app e confirmação do bot. O link vale cinco minutos; abri-lo sozinho não aprova a coleta. |
| Tela aberta, 3 min | Mantém app em primeiro plano, com internet e sinal disponível. | Frescor de aquisições, aceitas/rejeitadas e motivos. Contador do app ou heartbeat isolados não comprovam GPS recente. |
| Tela bloqueada, 15 min | Bloqueia sem encerrar o app; realiza o trecho combinado, incluindo movimento. | Continuidade no receptor, maior lacuna e estado do coletor; registra qualquer intervenção. |
| Sem rede, 3 min | Em parada segura, desliga dados móveis e Wi-Fi e mantém o app sem abrir. | Posição deixa de parecer atual; consulta não apresenta aquisição antiga como recente. Registra o horário de perda. |
| Recuperação, observar e cronometrar | Restabelece rede e deixa a tela bloqueada, **sem abrir o app**. | Tempo até GPS novo aceito e tratamento da fila. Se exigir abrir/RETOMAR/novo link, registra a falha/intervenção; não chama isso de recuperação autônoma aprovada. |
| Aproximação, conforme percurso | Segue a ordem dos pontos; P1/P2 aguardam e observam o recebimento em seus próprios telefones. | Três aquisições coerentes, sentido de movimento e heading compatível quando fornecido, progresso e elegibilidade. Conferir um aviso para cada passageiro cuja aproximação foi validada, sem duplicar, com destinatário correto e chegada observada; ausência/atraso também fica registrado. Aceite do provedor não prova utilidade. |
| Chegada e fim automático | Após passar pelos pontos, chega ao destino e permanece parado com GPS válido por pelo menos 60 s. | Progresso concluído, saída anterior do destino e permanência válida; COMPLETED, revogação, confirmação no bot e STOP/ACK. Cronômetro sozinho não encerra a viagem. |
| M — fim manual, cerca de 5 min | Em outra ocorrência ativa, envia **FINALIZAR**, lê a revisão e responde ABANDONAR. Depois repete FINALIZAR e envia **CONFIRMAR com o novo código recebido**. | ABANDONAR mantém a viagem. Só a viagem da revisão confirmada encerra, GPS para e ACK chega. Registrar como contingência manual, não como prova automática. |
| S — STOP offline, cerca de 5 min | Em outra coleta, corta a rede, toca em Parar localização e reabre ainda sem rede; depois reconecta. | Sensor permanece parado, pendência persiste sem iniciar nova coleta e recebe ACK/baixa ao reconectar. Sem abrir novamente após restabelecer a rede, observar primeiro a recuperação; qualquer abertura necessária fica registrada. |

A bateria precisa de **pelo menos 30 minutos efetivos por plataforma fora do
USB**, incluindo a janela de segundo plano. Registrar carga inicial/final,
duração, rede, brilho, tempo de tela e quais intervalos tinham GPS ativo. Se o
percurso acabar antes, completar a observação parado e identificar o período
com coleta já encerrada; não misturar os dois consumos nem extrapolar autonomia
de dia inteiro a partir dessa amostra.

Ao terminar S, o operador reconcilia o ACK e encerra sua viagem remanescente
pelo fluxo normal antes de preparar o outro aparelho. Reposicionar P1/P2 nos
pontos e conferir seus estados na nova ocorrência antes do segundo percurso.
Depois do segundo bloco, confirmar ausência de coleta/pedidos pendentes e
devolver os telefones ao uso pessoal.

## 5. Limites técnicos para interpretar o resultado

Os números abaixo são regras da versão consultada, não metas universais de
sinal, atraso, bateria ou antecedência útil. A pessoa registra o observado;
o operador confere a regra e o motivo da decisão no servidor.

- [WaTrackingPolicy](../../src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaTrackingPolicy.java):
  aquisição de até 20 s e precisão até 50 m; janela inicial de 30 s para obter
  GPS válido. Captura repetida ou heartbeat não renova a idade da aquisição.
- [WaApproachPolicy](../../src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaApproachPolicy.java):
  três posições recentes e coerentes, movimento/progresso em direção ao ponto
  e heading compatível quando fornecido; raio inicial de 800 m é hipótese de
  calibração. Geografia sem revisão ou direção incerta não deve forçar aviso.
- [WaRunFinishPolicy](../../src/backend/src/main/java/com/vanrides/vanrides/whatsapp/tracking/WaRunFinishPolicy.java):
  depois das paradas resolvidas, exige saída anterior comprovada a pelo menos
  200 m considerando a precisão; chegada com distância mais incerteza de até
  50 m, precisão até 25 m, velocidade informada de até 2 m/s e permanência de
  60 s em aquisições válidas, sem lacuna maior que 20 s. Falta desses dados
  mantém o fim pendente; um timer não pode substituí-los.

Para utilidade, cada passageiro relata se o aviso chegou antes da van, com a
antecedência combinada, se ficou claro e se ajudou a preparar o embarque.
Comparar recebimento e chegada observados. Uma ausência ou atraso pode reprovar
essa parte mesmo quando a API aceitou GPS e a Evolution confirmou entrega.

## 6. Interrupção, repetição e registro único

Interromper a parte afetada se houver destinatário errado, aviso duplicado,
posição antiga apresentada como atual, coleta que não para, ACK que não fecha,
versão/vínculo inesperado ou condição que impeça uma operação segura. Parar o
veículo antes de mexer no telefone. O operador registra o ocorrido, reconcilia
a coleta/viagem e diagnostica; não contorna a regra para seguir como aprovado.

Retomar somente a parte afetada quando houver versão/condições e nova ocorrência
adequadas. Preservar a tentativa inicial e as intervenções. Se mudarem versão,
regra ou destinatários, reavaliar quais etapas precisam ser repetidas. Etapa
não executada é pendente; cada plataforma e cenário tem resultado próprio.

Copiar o registro abaixo por cenário e plataforma. Telefone, coordenadas, pins,
convites, tokens, áudio ou conversa bruta não entram no Git. Dados necessários
ao ensaio ficam no canal/diretório privado; no registro público, usar apenas
métricas agregadas e referências sanitizadas.

```text
Data / horário / fuso / referência privada do cenário C, P, M ou S:
Tipo: real com deslocamento / real parado / sintético (não intercambiar provas):
Plataforma / modelo / sistema / app versão e hash:
API / schema / digest-JAR / bundle / flags / coorte conferidos:
Aparelho vinculado e acesso ao bot/link conferidos (sem IDs ou credenciais):
P1 confirmado / P2 pendente / inclusão e avisos aceitos conferidos:
Rede / permissões / brilho / restrições de bateria / USB desconectado:
Etapas feitas e pendentes / horários de cada janela:
Primeiro GPS / maior lacuna / recuperação sem abrir / rejeições e motivos:
Aviso P1/P2: destinatário correto, quantidade, recebimento e chegada observados:
Antecedência combinada/observada e avaliação de utilidade de cada participante:
Fim automático ou manual / parada local / ACK / UI final:
Bateria inicial/final / minutos sem USB / intervalos com GPS ativo ou parado:
Falhas e intervenções / repetição apenas da parte afetada:
Resultado por critério: aprovado, reprovado ou pendente:
```

Vincular Android a #59/#93, iPhone a #60/#94, bateria a #61, instalação/renovação
a #88/#97 e utilidade a #95. A consolidação fica na #96. Não encerrar por build,
ensaio sintético ou formulário sem a execução física e revisão correspondentes.

**Decisões para a hora da rodada:** dia/horário e eventual renovação iOS;
rota/pontos/destino reais em privado; dois participantes e antecedência útil;
ordem das plataformas; acesso à conversa do motorista e abertura do link;
locais seguros para os trechos sem rede e duração efetiva disponível. Essas
escolhas serão fechadas juntos no encontro, não exigem ação no iPhone agora.
