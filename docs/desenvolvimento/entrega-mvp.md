# Entrega do MVP para a rodada final

Objetivo ativado em 16/11/2026. Entregar o ambiente compartilhado, o bot e os
aplicativos prontos para a rodada final do mantenedor no Redmi Note 8 e no
iPhone 13. A lista abaixo acompanha as 65 issues abertas com a etiqueta MVP.
As sete issues históricas de mapas e e-mail ficam no planejamento posterior.

Atualização após o ensaio de 16/11: a tentativa em deslocamento foi interrompida
antes de iniciar a nova coleta. A experiência não recebeu aceite: houve muitas
etapas de preparo e a inclusão em nova rotina exigiu refazer um vínculo de
teste. Falhas da automação do Mac também consumiram a janela; não representam,
por si, defeitos do produto. A prioridade seguinte é a
[jornada simples de cadastro e início](proposta-fluxo-simples.md). As evidências
técnicas anteriores permanecem válidas nos seus próprios escopos e não aprovam
essa tentativa como percurso ou teste de usabilidade.

O mantenedor deixou percurso, bateria, utilidade do aviso e aceite final para
quando a entrega técnica estiver pronta. Esses resultados terão registro
próprio. As 65 issues continuam abertas: uma tarefa só será encerrada depois
de conferir seus critérios, a evidência correspondente e a revisão humana.

O [roteiro da rodada final](rodada-final.md) reúne instalação, tela bloqueada,
interrupção de rede, percurso, aviso, encerramento e bateria, com registro
separado por plataforma e pelas issues correspondentes.

## Preparação conferida

A integração do PR #128 foi publicada em `7531991`, com schema V44, CI/deploy
aprovados em 74 s e HTTPS UP. O runtime backend permaneceu igual ao PR #127
(`2a211d1`); o worker saudável usa 56 fontes de `efd7d8b`. Não houve nova migração.
O [pacote identificado da rodada](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/releases/tag/mvp-0.4.0-15)
reúne manifesto, fontes, hashes, configuração e instruções. O manifesto registra
o corte conferido; novos merges atualizam a API e exigem nova conferência.

- `WA_MVP_REHEARSAL_ROUTINES`, `WA_APPROACH_ENABLED` e
  `WA_AUTO_FINISH_ENABLED` estão ON para rotinas elegíveis da coorte compartilhada,
  sem whitelist por rotina. Limpeza real de privacidade e IA permanecem OFF.
- Convite, vínculo, ponto, presença de ida/volta, lista e pin tiveram resposta
  no WhatsApp real. Preferência de chegada ON/OFF/ON passou com ACK do bot após
  publicar a correção de elegibilidade de `SET_PREFERENCE`.
- iOS normal 0.4.0+15 instalado e aberto: recuperação do recibo terminal e UI
  de parada confirmadas, sem apagar o journal. A geração 3 recebeu o primeiro
  GPS em 5,651 s, com 57 amostras aceitas e uma STALE; STOP pelo app recebeu ACK
  e a posição ficou indisponível. Perfil válido até 23/11 às 08:27 (Brasília).
- Android normal 0.4.0+14, preservado para retorno: primeiro GPS em 2,803 s,
  451 amostras aceitas, zero rejeitadas e STOP/ACK com UI parada.
- Android normal 0.4.0+15 instalado com a mesma assinatura e dados preservados:
  geração 4, primeiro GPS em 2,738 s, 81 aceitas e um frame tardio RUN_TERMINAL.
  Pin recebido e FINALIZAR/revisão/CONFIRMAR reais levaram a COMPLETED,
  revogação e STOPPED/ACK, com confirmação do bot e UI concluída sem pendência.
  Checkpoint passou em 12 casos host e revisão independente; cold start
  expirado mostrou parada. APK +15 publicado e hash HTTPS conferido com o
  artefato instalado; +14 preservado para retorno.
- Recuperação/migração V44 aprovadas em isolamento, inclusive no ensaio 006
  com o JAR publicado. Os três critérios de cópia/restauração/reaplicação de
  exclusões possuem prova. O executor de retenção foi preparado e passou em
  nove testes sintéticos no Linux da VPS. O catálogo/prévia reteve os 14 dumps
  dos 11 escopos, sem exclusão real, aprovação ou cron.

O recibo usa prova da própria chave retida apenas para conferir um ACK terminal
já gravado. A validade/revogação dos tokens permanece; a rota não autoriza GPS,
nova coleta ou renovação. O passageiro já cadastrado pode mudar os avisos de
chegada e os lembretes de presença por comando explícito posterior, preservando
o aceite e os padrões do cadastro.

A consulta atual das issues encontrou 65 MVP, todas abertas e nenhuma fechada.
A estimativa anterior de 90% do código e 80% da entrega descreveu um corte
intermediário. O inventário atual distingue 59 itens implementados, quatro
parciais e dois planejados; os seis últimos correspondem à rodada física.
Isso não mede critérios aprovados nem encerra issues. Campo, bateria, utilidade
do aviso e aceite do proprietário permanecem para a rodada conjunta.

## Sequência de entrega

1. **Integrado e publicado:** presença e avisos dos PRs #113 e #121, com
   autoria preservada; V42 mantém o cadastro V41 e acrescenta os avisos.
2. **Integrado e publicado na V43:** fim manual/automático, progresso,
   destino e permanência válidos, STOP/ACK recuperável e suporte preservado.
   O automático está habilitado para a coorte compartilhada; a prova de campo segue pendente.
3. **V44 publicada e ensaiada:** retenções 7/30/90 dias e restauração de dump
   antigo com journal atual passaram no conjunto isolado, repetido no ensaio
   006 com o JAR publicado.
   API, worker, quatro agendas n8n em fila e Redis retomaram sem duplicar os
   dois efeitos do canal simulado. A cópia do banco ativo também passou nos
   probes anterior/V43, candidata/V44 e anterior/V44, sem alterar serviço live.
4. **Fase atual:** os apps normais +15 têm GPS/STOP/ACK estacionados e o APK
   Android +15 está publicado com hash HTTPS conferido. O Android também passou
   em FINALIZAR pelo WhatsApp. Faltam tela bloqueada, rede e campo nas duas
   plataformas. Aproximação/fim automático estão ON, ainda sem prova real de
   utilidade e encerramento automático em campo.
5. **Rodada do mantenedor:** aplicar o roteiro já preparado de percurso,
   bateria e utilidade do aviso, registrar o aceite e conferir cada issue
   com revisão humana antes do encerramento.

A integração permanece sob o card ativo do programa. As frentes paralelas
convergem no mesmo candidato. Migrations são publicadas em etapas para manter
o retorno testado e compatível com o banco. O
[registro de evidências](../../program/evidence/mvp-entrega-20261116.md)
identifica versões e resultados; os ensaios isolados não comprovam mensagens
no WhatsApp real nem autorizam piloto/produção. A rotação física dos backups
tem executor e prévia preparados, mas não está ativada globalmente. A aprovação
de qualquer exclusão real permanece uma ação operacional explícita sobre lista
e hash, fora do teste de percurso; nenhum backup foi apagado. A criação histórica
dos dumps legados continua desconhecida. Esse controle não impede a rodada técnica.

## Matriz das issues

O grupo indica a próxima ação predominante, atualizado em 16/11. Código,
publicação e ensaios são estados distintos. A prova de fechamento continua
na própria issue.

| Issue | Entrega | Próxima ação |
| --- | --- | --- |
| [#25](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/25) | [T01] Preparar API e banco | API V44 publicada; revisar a versão e o resultado do preflight na issue. |
| [#26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26) | [T02] Cadastrar motorista | Motorista cadastrado e vinculado usado na rodada real; revisar a prova específica de cadastro na issue. |
| [#27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27) | [T03] Restringir ações por perfil | Autorização implementada e publicada; revisar a prova correspondente na issue. |
| [#28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) | [T04] Cadastrar passageiro e ponto | Vínculo e ponto validados no WhatsApp real em 15/11; cadastro simplificado com perfil novo, jornada completa e revisão humana pendentes. |
| [#29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29) | [T05] Convidar e vincular passageiro | Convite e vínculo reais conferidos; faltam perfil novo, dois passageiros e revisão dos critérios completos. |
| [#30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | [T6] Responder presença na viagem certa | Presença de ida e volta, alteração para ausência e lista conferidas no WhatsApp real; faltam limites após início. |
| [#31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | [T7] Receber GPS do aparelho vinculado | GPS/STOP/ACK reais nos apps normais iOS/Android +15; APK Android +15 publicado e hash HTTPS conferido. Campo deferido. |
| [#33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | [T9a] Autorizar consulta da posição | Pin autorizado recebido pelo passageiro; consulta indisponível após STOP. Revisar os critérios de autorização na issue. |
| [#34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | [T9b] Responder consulta de localização | Pin real recebido e indisponibilidade após STOP conferidos; preservar a evidência e revisar os limites da resposta. |
| [#35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | [T10] Guardar a última posição válida | Posição válida produziu pin real; após STOP a consulta ficou indisponível. Revisar frescor e critérios restantes. |
| [#36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | [T11] Preparar aviso de aproximação | Política e preferência posterior publicadas; ON/OFF/ON com ACK real. Aproximação ON na coorte compartilhada; falta utilidade em campo. |
| [#49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | [T23] Explicar cadastro e correção do ponto | Mensagens de cadastro/correção implementadas; conferir compreensão na nova jornada real, ainda pendente. |
| [#50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | [T24] Criar calendário de ida e volta | Calendário e presença de ida/volta exercitados no canal real; revisar critérios da jornada completa. |
| [#51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | [T25] Enviar lembrete de presença | Lembretes implementados; conferir execução no ambiente da rodada e documentar o resultado. |
| [#52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | [T26] Testar respostas antigas e repetidas | Cenários automatizados existentes; preservar regressões e revisar a prova da revisão atual. |
| [#53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | [T27] Preservar versões e recuperação | Fontes e recuperação preservados; conferir a rastreabilidade do candidato integrado. |
| [#54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | [T28] Preparar aplicativo mínimo Android | Android normal +15 instalado e publicado, com GPS/pin/FINALIZAR/STOPACK/UI; assinatura/dados preservados e +14 retido para retorno. |
| [#55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | [T29] Preparar aplicativo mínimo iPhone | iOS +15 instalado/aberto, com recibo recuperado, GPS e STOP/ACK reais; falta rodada de campo e reprodução final. |
| [#56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | [T30] Coletar GPS no Android | Android normal +15: primeiro GPS 2,738 s, 81 aceitas/uma RUN_TERMINAL após fim. STOP/ACK e UI concluída; falta campo. |
| [#57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | [T31] Coletar GPS no iPhone | iOS +15: geração 3 com 57 aceitas/uma STALE, STOP/ACK e posição indisponível após parada; faltam rede/tela bloqueada/campo. |
| [#58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | [T32] Receber GPS em ambiente de prova | Receptor isolado implementado; manter evidência sintética separada da jornada real. |
| [#59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | [T33] Verificar localização em percurso Android | Percurso Android pendente; rodada de campo foi deferida ao usuário. |
| [#60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | [T34] Verificar localização em percurso iPhone | iOS +15 instalado e com GPS/STOP/ACK reais; percurso continua reservado à rodada do usuário. |
| [#61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | [T35] Medir bateria e recuperação | Medição bilateral de bateria pendente; campo foi deferido ao usuário. |
| [#62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | [T36] Evitar ações duplicadas na entrada | Deduplicação implementada; revisar as regressões e os eventos reais da rodada, sem presumir prova de todos os callbacks. |
| [#63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | [T37] Guardar e enviar respostas | Pin e confirmação de FINALIZAR chegaram no WhatsApp real; revisar os estados de entrega e os callbacks correspondentes. |
| [#64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | [T38] Interpretar mensagens com IA de forma limitada | Bot determinístico respondeu à consulta e à finalização reais; IA continua OFF, sem gasto de modelo nesta rodada. |
| [#65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | [T39] Preparar automações importáveis | Oito exports e quatro agendas ativos; ensaio 006 aprovado. Flags de jornada ON na coorte compartilhada; revisar escopo e efeitos. |
| [#66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | [T40] Configurar rotinas do motorista | Rotina usada na rodada real; conferir os critérios de configuração completa com os participantes. |
| [#67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | [T41] Mostrar vínculo e recuperação no aplicativo | iOS +15 recuperou recibo sem apagar journal. Android +15 teve checkpoint/cold start e UI concluída sem pendência; faltam falhas de rede físicas. |
| [#68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | [T42] Vincular o aparelho correto | Vínculo e GPS reais nos apps normais iOS/Android +15; revisar a prova da combinação final publicada. |
| [#69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | [T43] Fixar a lista da partida | Snapshot da partida implementado; conferir a lista na nova jornada real. |
| [#70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | [T44] Confirmar início somente com GPS válido | Primeiro GPS: iOS +15 em 5,651 s e Android +15 em 2,738 s. Cache STALE e frame RUN_TERMINAL corretamente recusados. |
| [#71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | [T45] Recuperar a parada sem internet | Recibo terminal recuperado no iOS +15; checkpoint Android +15 passou 12 casos host e cold start expirado. Falta recuperação offline física. |
| [#72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | [T46] Repetir envios somente com segurança | Retry seguro implementado; conferir callback real da Evolution e cenários de falha na rodada final. |
| [#73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | [T47] Distinguir envio de entrega | Estado de entrega implementado; conferir o callback real da Evolution na nova jornada. |
| [#74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | [T48] Encerrar avisos sem utilidade | Expiração e elegibilidade implementadas; validar o aviso real após habilitação autorizada e percurso. |
| [#75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | [T49] Avaliar aproximação por ponto e sentido | Aproximação ON para elegíveis da coorte compartilhada, sem whitelist por rotina; calibração/utilidade aguardam campo. |
| [#76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | [T50] Respeitar progressão e cancelamento | Progressão/cancelamento implementados; flags ON na coorte compartilhada, com prova real de percurso ainda pendente. |
| [#77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | [T51] Testar destinatário e repetição do aviso | Testes dirigidos existentes; destinatário/utilidade serão conferidos na rodada de campo deferida. |
| [#78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | [T52] Encerrar a viagem automaticamente | Fim automático ON para elegíveis da coorte compartilhada; falta comprovar encerramento e ACK em campo. |
| [#79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | [T53] Confirmar que a coleta parou | STOP/ACK e UI parada/concluída reais nos apps normais iOS/Android +15; campo/rede/tela bloqueada continuam pendentes. |
| [#80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | [T54] Oferecer ajuda e recuperação | AJUDA real conferida; oito testes de AJUDA aprovados com orientação de preferências. Pausa/retomada real ainda requer conferência. |
| [#81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | [T55] Autorizar as automações de presença | Autorização própria implementada; conferir os efeitos no ensaio operacional sem ampliar flags. |
| [#82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | [T56] Preservar automações ao reiniciar n8n | Fila e reinícios aprovados nos ensaios 005 e 006; o 006 usou o JAR efetivamente publicado. Revisar a evidência na issue. |
| [#83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | [T57] Limitar dados e aplicar exclusão | Retenção V44 e executor de catálogo/plano implementados, com testes sintéticos Linux e prévia sem exclusão. Qualquer ativação real exige decisão operacional explícita. |
| [#84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | [T58] Provar cópia, restauração e exclusão | Três critérios de cópia/restore/exclusões possuem prova nos ensaios 005/006; revisar critérios. Rotação física de sete dias é pendência operacional. |
| [#85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | [T59] Orientar operação e parada segura | Guia/helper, 11 testes Python e cenário PostgreSQL aprovados; diagnóstico e plano de leitura conferidos na VPS. Revisão humana e jornada WhatsApp real permanecem separadas. |
| [#86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | [T60] Identificar os arquivos da versão de teste | Manifesto conjunto da release identifica servidor, apps, canal, automações e hashes. Conferir o corte antes do percurso; novo merge exige reconferência. |
| [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | [T61] Preparar instalação de teste Android | APK normal +15 publicado e hash HTTPS igual ao instalado; GPS/fim/ACK/UI aprovados estacionado, com +14 preservado para retorno. |
| [#88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | [T62] Preparar instalação e renovação no iPhone | iOS +15 instalado/aberto e perfil até 23/11 às 08:27 BRT; conferir renovação e reprodução final. |
| [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | [T63] Planejar instalação e retorno no servidor | Migração/retorno V44 ensaiados; PRs126/127 publicados sem nova migração. Preservar os recibos e o retorno. |
| [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | [T64] Instalar no ambiente autorizado de teste | API PR127/main 2a211d1 e worker saudáveis. Ensaio/aproximação/fim automático ON na coorte compartilhada; limpeza real OFF. |
| [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | [T65] Conferir o número dedicado | Vínculo, presença, pin, FINALIZAR e preferências ON/OFF/ON tiveram resposta real; revisar callbacks e critérios restantes. |
| [#92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | [T66] Testar agendas no ambiente escolhido | Quatro agendas n8n em fila aprovadas nos ensaios 005 e 006; revisar a evidência da versão publicada. |
| [#93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | [T67] Testar a jornada completa no Android | Android normal +15 publicado passou em GPS/pin e FINALIZAR/COMPLETED/STOPACK/UI; campo/rede/tela bloqueada/bateria deferidos. |
| [#94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | [T68] Testar a jornada completa no iPhone | iOS +15 recuperou recibo e teve GPS/STOP/ACK reais; faltam tela bloqueada, rede, bateria e percurso. |
| [#95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | [T69] Medir se o aviso ajuda no embarque | Utilidade/destinatário dependem de percurso e avaliação do usuário, ainda deferidos. |
| [#96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | [T70] Consolidar guia e resultados | Guias, recibos e pacote preparados. Aplicar a rodada conjunta e registrar os resultados reais por plataforma antes de fechar critérios. |
| [#97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | [T71] Reproduzir a instalação final | Apps normais +15 instalados e abertos; instruções de reprodução/reassinatura disponíveis. Conferir abertura pelo ícone sem USB na rodada e renovar o perfil iOS se necessário. |
| [#98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | [T72] Proposta: cancelar ou corrigir presença | Presença de ida/volta e alteração para ausência na ida conferidas no WhatsApp real; faltam limites após início. |
| [#99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | [T73] Proposta: definir limite da alteração | Regra de limite integrada/publicada na V42; falta conferir alterações após início na jornada real. |
| [#100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | [T74] Proposta: avisar cancelamento ao motorista | Aviso ao motorista integrado/publicado na V42; falta comprovar entrega no WhatsApp real. |
| [#101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | [T75] Mostrar lista de presença ao motorista | Lista real do motorista refletiu presença/ausência; ida/volta exercitadas. Faltam cenários após início. |
| [#102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | [T78] Conferir contrato, permissões e banco | Contratos/permissões/migrações têm testes dirigidos; revisar evidência sem alegar execução integral da suíte. |
