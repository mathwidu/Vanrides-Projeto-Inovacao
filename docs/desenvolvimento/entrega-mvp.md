# Entrega do MVP para a rodada final

Objetivo ativado em 16/11/2026. Entregar o ambiente compartilhado, o bot e os
aplicativos prontos para a rodada final do mantenedor no Redmi Note 8 e no
iPhone 13. A lista abaixo acompanha as 65 issues abertas com a etiqueta MVP.
As sete issues históricas de mapas e e-mail ficam no planejamento posterior.

O mantenedor deixou percurso, bateria, utilidade do aviso e aceite final para
quando a entrega técnica estiver pronta. Esses resultados terão registro
próprio. As 65 issues continuam abertas: uma tarefa só será encerrada depois
de conferir seus critérios, a evidência correspondente e a revisão humana.

O [roteiro da rodada final](rodada-final.md) reúne instalação, tela bloqueada,
interrupção de rede, percurso, aviso, encerramento e bateria, com registro
separado por plataforma e pelas issues correspondentes.

## Preparação conferida

Corte deste registro: candidato `efd7d8b`, enviado no PR #126, com CI em curso.
A `main` e a API da VPS permanecem em `a2b77ed`, schema V44; as correções abaixo
não exigem nova migração e ainda não foram publicadas neste corte.

- API/worker publicados e quatro agendas n8n ativas. Convite, vínculo,
  aprovação/correção do ponto e presença tiveram resposta no WhatsApp real.
  Privacidade, aproximação, fim automático e rotinas de ensaio seguem OFF.
- iOS 0.4.0+14 no iPhone 13: vínculo, GPS, pin recebido pelo passageiro,
  parada e ACK reais passaram. Na retomada, `FINALIZAR` pelo WhatsApp levou a
  viagem a `COMPLETED`, com ACK e confirmação do bot. Após parar, a consulta
  de localização ficou indisponível. A UI de continuidade da parada exigiu correção.
- iOS 0.4.0+15 corrigido em `efd7d8b`: 33 testes Session, 20 Core e 13 do
  helper aprovados; app assinado em 70,42 s e runner em 21,36 s. Ambos ainda
  sem instalação/execução no aparelho; falta validar UI e recuperação do recibo.
- Android: GPS, pin e STOP/ACK passaram com um APK diagnóstico temporário,
  sem dados pessoais nos logs. O APK normal 0.4.0+14 foi restaurado e seu
  ensaio de GPS está em curso; o diagnóstico não aprova o APK final.
- Recibo terminal de parada (`1724fe8` + `2355e54`) e preferências posteriores
  de avisos (`21a0d76` + `593cc89`) integrados no candidato e aprovados em
  testes dirigidos. O package com oito testes de AJUDA passou em 10,88 s;
  isso não representa execução integral da suíte.
- Recuperação/migração V44 aprovadas em isolamento, inclusive no ensaio 006
  com o JAR publicado. Diagnóstico e plano de pausa conferidos; limpeza real OFF.

O recibo usa prova da própria chave retida apenas para conferir um ACK terminal
já gravado. A validade/revogação dos tokens permanece; a rota não autoriza GPS,
nova coleta ou renovação. O passageiro já cadastrado pode mudar os avisos de
chegada e os lembretes de presença por comando explícito posterior, preservando
o aceite e os padrões do cadastro.

## Sequência de entrega

1. **Integrado e publicado:** presença e avisos dos PRs #113 e #121, com
   autoria preservada; V42 mantém o cadastro V41 e acrescenta os avisos.
2. **Integrado e publicado na V43:** fim manual/automático, progresso,
   destino e permanência válidos, STOP/ACK recuperável e suporte preservado.
   O automático continua desligado até habilitar o ensaio.
3. **V44 publicada e ensaiada:** retenções 7/30/90 dias e restauração de dump
   antigo com journal atual passaram no conjunto isolado, repetido no ensaio
   006 com o JAR publicado.
   API, worker, quatro agendas n8n em fila e Redis retomaram sem duplicar os
   dois efeitos do canal simulado. A cópia do banco ativo também passou nos
   probes anterior/V43, candidata/V44 e anterior/V44, sem alterar serviço live.
4. **Fase atual:** concluir o ensaio do APK normal 0.4.0+14, instalar e validar
   o iOS 0.4.0+15 com a UI corrigida e conferir a recuperação de parada com a API
   candidata. GPS, pin e encerramento manual reais já têm prova no app iOS +14;
   ida/volta completa, falhas de rede e a combinação final de versões ainda
   precisam de validação. Aproximação e fim automático continuam OFF.
5. **Rodada do mantenedor:** aplicar o roteiro já preparado de percurso,
   bateria e utilidade do aviso, registrar o aceite e conferir cada issue
   com revisão humana antes do encerramento.

A integração permanece sob o card ativo do programa. As frentes paralelas
convergem no mesmo candidato. Migrations são publicadas em etapas para manter
o retorno testado e compatível com o banco. O
[registro de evidências](../../program/evidence/mvp-entrega-20261116.md)
identifica versões e resultados; os ensaios isolados não comprovam mensagens
no WhatsApp real nem autorizam piloto/produção. A rotação física dos backups
ainda não foi implementada: o inventário oferece somente prévia de sete dias.

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
| [#30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | [T6] Responder presença na viagem certa | Presença de amanhã/ida e alteração para ausência conferidas no WhatsApp real; falta volta e limites após início. |
| [#31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | [T7] Receber GPS do aparelho vinculado | GPS e ACK reais no app iOS +14 e Android diagnóstico; ensaio do APK normal +14 em curso, percurso deferido ao usuário. |
| [#33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | [T9a] Autorizar consulta da posição | Pin autorizado recebido pelo passageiro; consulta indisponível após STOP. Revisar os critérios de autorização na issue. |
| [#34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | [T9b] Responder consulta de localização | Pin real recebido e indisponibilidade após STOP conferidos; preservar a evidência e revisar os limites da resposta. |
| [#35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | [T10] Guardar a última posição válida | Posição válida produziu pin real; após STOP a consulta ficou indisponível. Revisar frescor e critérios restantes. |
| [#36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | [T11] Preparar aviso de aproximação | Política publicada; preferência posterior implementada/testada no candidato. Aproximação OFF; utilidade e destinatário dependem do campo. |
| [#49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | [T23] Explicar cadastro e correção do ponto | Mensagens de cadastro/correção implementadas; conferir compreensão na nova jornada real, ainda pendente. |
| [#50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | [T24] Criar calendário de ida e volta | Calendário implementado; falta conferir ida e volta completas na jornada real. |
| [#51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | [T25] Enviar lembrete de presença | Lembretes implementados; conferir execução no ambiente da rodada e documentar o resultado. |
| [#52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | [T26] Testar respostas antigas e repetidas | Cenários automatizados existentes; preservar regressões e revisar a prova da revisão atual. |
| [#53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | [T27] Preservar versões e recuperação | Fontes e recuperação preservados; conferir a rastreabilidade do candidato integrado. |
| [#54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | [T28] Preparar aplicativo mínimo Android | APK normal +14 restaurado após diagnóstico; ensaio de GPS/parada desse artefato em curso. |
| [#55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | [T29] Preparar aplicativo mínimo iPhone | App iOS +14 teve vínculo/GPS/parada reais; app iOS +15 corrigido assinado, ainda sem instalação. Validar UI e abertura pelo ícone. |
| [#56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | [T30] Coletar GPS no Android | GPS/STOP/ACK passaram no Android diagnóstico; falta concluir a prova no APK normal +14 restaurado. |
| [#57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | [T31] Coletar GPS no iPhone | GPS e STOP/ACK reais passaram no app iOS +14; validar a continuidade da parada no app iOS +15 corrigido, ainda não instalado. |
| [#58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | [T32] Receber GPS em ambiente de prova | Receptor isolado implementado; manter evidência sintética separada da jornada real. |
| [#59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | [T33] Verificar localização em percurso Android | Percurso Android pendente; rodada de campo foi deferida ao usuário. |
| [#60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | [T34] Verificar localização em percurso iPhone | App 0.4.0+14 instalado e aberto; percurso iPhone continua reservado à rodada do usuário. |
| [#61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | [T35] Medir bateria e recuperação | Medição bilateral de bateria pendente; campo foi deferido ao usuário. |
| [#62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | [T36] Evitar ações duplicadas na entrada | Deduplicação implementada; revisar as regressões e os eventos reais da rodada, sem presumir prova de todos os callbacks. |
| [#63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | [T37] Guardar e enviar respostas | Pin e confirmação de FINALIZAR chegaram no WhatsApp real; revisar os estados de entrega e os callbacks correspondentes. |
| [#64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | [T38] Interpretar mensagens com IA de forma limitada | Bot determinístico respondeu à consulta e à finalização reais; IA continua OFF, sem gasto de modelo nesta rodada. |
| [#65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | [T39] Preparar automações importáveis | Oito exports preparados e quatro agendas ativas; ensaio 006 aprovado. Revisar a configuração publicada sem ampliar flags. |
| [#66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | [T40] Configurar rotinas do motorista | Rotina usada na rodada real; conferir os critérios de configuração completa com os participantes. |
| [#67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | [T41] Mostrar vínculo e recuperação no aplicativo | Vínculo e ACK reais no app iOS +14; validar UI/continuidade e recuperação após expiração no app iOS +15 corrigido. |
| [#68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | [T42] Vincular o aparelho correto | Vínculo e GPS reais conferidos no app iOS +14 e Android diagnóstico; completar a prova nos artefatos finais. |
| [#69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | [T43] Fixar a lista da partida | Snapshot da partida implementado; conferir a lista na nova jornada real. |
| [#70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | [T44] Confirmar início somente com GPS válido | Primeiro GPS aceito em 0,317 s no app iOS +14 e 3,284 s no Android diagnóstico; concluir o ensaio do APK normal +14. |
| [#71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | [T45] Recuperar a parada sem internet | STOP/ACK online reais passaram; recibo terminal e parada pré-GPS corrigidos/testados. Falta recuperação offline no candidato final. |
| [#72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | [T46] Repetir envios somente com segurança | Retry seguro implementado; conferir callback real da Evolution e cenários de falha na rodada final. |
| [#73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | [T47] Distinguir envio de entrega | Estado de entrega implementado; conferir o callback real da Evolution na nova jornada. |
| [#74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | [T48] Encerrar avisos sem utilidade | Expiração e elegibilidade implementadas; validar o aviso real após habilitação autorizada e percurso. |
| [#75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | [T49] Avaliar aproximação por ponto e sentido | Regra de aproximação implementada/publicada; flag OFF, calibração e utilidade ficam para percurso deferido. |
| [#76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | [T50] Respeitar progressão e cancelamento | Progressão/cancelamento implementados; flag OFF e prova de percurso permanecem pendentes. |
| [#77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | [T51] Testar destinatário e repetição do aviso | Testes dirigidos existentes; destinatário/utilidade serão conferidos na rodada de campo deferida. |
| [#78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | [T52] Encerrar a viagem automaticamente | Fim automático publicado na V43; flag OFF até a rodada controlada de percurso deferida. |
| [#79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | [T53] Confirmar que a coleta parou | STOP/ACK reais no app iOS +14 e Android diagnóstico; validar as correções de continuidade/UI do app iOS +15 e o APK normal +14. |
| [#80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | [T54] Oferecer ajuda e recuperação | AJUDA real conferida; oito testes de AJUDA aprovados com orientação de preferências. Pausa/retomada real ainda requer conferência. |
| [#81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | [T55] Autorizar as automações de presença | Autorização própria implementada; conferir os efeitos no ensaio operacional sem ampliar flags. |
| [#82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | [T56] Preservar automações ao reiniciar n8n | Fila e reinícios aprovados nos ensaios 005 e 006; o 006 usou o JAR efetivamente publicado. Revisar a evidência na issue. |
| [#83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | [T57] Limitar dados e aplicar exclusão | Retenção 7/30/90 publicada na V44; manter limpeza real OFF e revisar apenas a prévia. |
| [#84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | [T58] Provar cópia, restauração e exclusão | Dump/restore e journal atual reaplicados nos ensaios 005 e 006 aprovados; revisar a prova. Rotação física dos backups ainda pendente. |
| [#85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | [T59] Orientar operação e parada segura | Guia/helper, 11 testes Python e cenário PostgreSQL aprovados; diagnóstico e plano de leitura conferidos na VPS. Revisão humana e jornada WhatsApp real permanecem separadas. |
| [#86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | [T60] Identificar os arquivos da versão de teste | API publicada a2b77ed/V44 e candidato efd7d8b/PR #126 identificados; preservar manifestos e distinção entre build e publicação. |
| [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | [T61] Preparar instalação de teste Android | APK +14 normal restaurado e aberto; GPS/ACK passaram só no diagnóstico até este corte. Ensaio normal em curso. |
| [#88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | [T62] Preparar instalação e renovação no iPhone | App iOS +14 instalado; app iOS +15 assinado ainda não instalado. Conferir abertura, renovação e reprodução conforme o roteiro. |
| [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | [T63] Planejar instalação e retorno no servidor | Migração/retorno V44 ensaiados; candidato de parada/preferências não adiciona migração. Publicação ainda pendente neste corte. |
| [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | [T64] Instalar no ambiente autorizado de teste | API a2b77ed/V44 e worker publicados; PR #126 candidato ainda em CI. Manter flags de efeito OFF. |
| [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | [T65] Conferir o número dedicado | Vínculo, presença, pin e FINALIZAR tiveram resposta real; revisar callbacks e critérios restantes do canal. |
| [#92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | [T66] Testar agendas no ambiente escolhido | Quatro agendas n8n em fila aprovadas nos ensaios 005 e 006; revisar a evidência da versão publicada. |
| [#93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | [T67] Testar a jornada completa no Android | GPS/STOP/ACK passaram no diagnóstico Android; ensaio do APK normal +14 em curso. Percurso reservado ao usuário. |
| [#94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | [T68] Testar a jornada completa no iPhone | Vínculo, GPS, retomada, FINALIZAR e STOP/ACK passaram no app iOS +14; validar UI/recuperação do app iOS +15 antes do campo. |
| [#95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | [T69] Medir se o aviso ajuda no embarque | Utilidade/destinatário dependem de percurso e avaliação do usuário, ainda deferidos. |
| [#96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | [T70] Consolidar guia e resultados | Guias incluem GPS/parada reais e limites dos candidatos; faltam validação da UI/build final, campo e revisão humana. |
| [#97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | [T71] Reproduzir a instalação final | App iOS +14 instalado e app iOS +15 apenas assinado; reproduzir instalação, abertura pelo ícone, renovação e rodada final. |
| [#98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | [T72] Proposta: cancelar ou corrigir presença | Ausência e volta a VOU de amanhã/ida recebidas no WhatsApp real; falta volta e limites após início. |
| [#99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | [T73] Proposta: definir limite da alteração | Regra de limite integrada/publicada na V42; falta conferir alterações após início na jornada real. |
| [#100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | [T74] Proposta: avisar cancelamento ao motorista | Aviso ao motorista integrado/publicado na V42; falta comprovar entrega no WhatsApp real. |
| [#101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | [T75] Mostrar lista de presença ao motorista | Lista real do motorista mudou de 1 confirmado para 1 que não vai; falta volta e cenários após início. |
| [#102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | [T78] Conferir contrato, permissões e banco | Contratos/permissões/migrações têm testes dirigidos; revisar evidência sem alegar execução integral da suíte. |
