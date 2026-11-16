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

- API V44 publicada em `40ab6c2`, com schema e HTTPS saudáveis. O worker
  publicado usa as 54 fontes de `6100e32`. Privacidade, aproximação, fim
  automático e rotinas de ensaio seguem OFF.
- Quatro agendas n8n ativas. Convite por link, vínculo e aprovação/correção
  do ponto validados no WhatsApp real em 15/11.
- APK Android 0.4.0+14 publicado e instalado por atualização no Redmi;
  smoke de GPS e jornada do novo build ainda pendentes.
- Build iOS 0.4.0+14 completo sem assinatura aprovado em 45,36 s, com fontes
  e lockfiles intactos. Não é instalável: a assinatura aguarda sessão do Mac
  desbloqueada e conta Apple/Personal Team disponível no Xcode.
- Recuperação e migração reais isoladas na VPS aprovadas. O ensaio 006 repetiu
  o conjunto com o JAR efetivamente publicado: journal reaplicado, Alfa ausente,
  Beta e controles preservados, zero duplicados. Limpeza real continua OFF.
- Diagnóstico e guia de pausa preparados; 11 testes Python e um cenário
  PostgreSQL passaram. A CLI ainda precisa de execução operacional isolada;
  isso não foi contabilizado como diagnóstico aprovado da VPS.

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
4. **Fase atual:** concluir o ensaio operacional de alerta/pausa, assinar e
   instalar no iPhone, conferir GPS do novo Android e executar a nova jornada real com os dois
   papéis. Registrar a combinação de versões e flags; habilitar aproximação
   e fim automático somente no ensaio controlado.
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
| [#26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26) | [T02] Cadastrar motorista | Cadastro implementado; conferir a prova de motorista no WhatsApp real quando o Mac estiver disponível. |
| [#27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27) | [T03] Restringir ações por perfil | Autorização implementada e publicada; revisar a prova correspondente na issue. |
| [#28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) | [T04] Cadastrar passageiro e ponto | Vínculo e ponto validados no WhatsApp real em 15/11; cadastro simplificado com perfil novo, jornada completa e revisão humana pendentes. |
| [#29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29) | [T05] Convidar e vincular passageiro | Convite e vínculo validados em 15/11; conferir a nova jornada integrada no WhatsApp real quando o Mac estiver disponível. |
| [#30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | [T6] Responder presença na viagem certa | Presença publicada na V43; conferir ida e volta no WhatsApp real quando o Mac estiver disponível. |
| [#31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | [T7] Receber GPS do aparelho vinculado | Receptor de GPS implementado; smoke do APK14 pendente e percurso deferido ao usuário. |
| [#33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | [T9a] Autorizar consulta da posição | Consulta autorizada implementada; provar o pin no WhatsApp real quando o Mac estiver desbloqueado. |
| [#34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | [T9b] Responder consulta de localização | Resposta de localização implementada; provar recebimento no WhatsApp real quando o Mac estiver desbloqueado. |
| [#35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | [T10] Guardar a última posição válida | Armazenamento e consulta implementados; conferir o fluxo real do pin quando o Mac estiver disponível. |
| [#36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | [T11] Preparar aviso de aproximação | Política/produtor implementados e publicados; flags OFF, utilidade e destinatário ficam para percurso deferido. |
| [#49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | [T23] Explicar cadastro e correção do ponto | Mensagens de cadastro/correção implementadas; conferir compreensão na nova jornada real, ainda pendente. |
| [#50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | [T24] Criar calendário de ida e volta | Calendário implementado; conferir ida e volta na jornada real quando o canal estiver disponível. |
| [#51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | [T25] Enviar lembrete de presença | Lembretes implementados; conferir execução no ambiente da rodada e documentar o resultado. |
| [#52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | [T26] Testar respostas antigas e repetidas | Cenários automatizados existentes; preservar regressões e revisar a prova da revisão atual. |
| [#53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | [T27] Preservar versões e recuperação | Fontes e recuperação preservados; conferir a rastreabilidade do candidato integrado. |
| [#54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | [T28] Preparar aplicativo mínimo Android | APK14 instalado e atividade aberta; smoke GPS do novo build ainda pendente. |
| [#55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | [T29] Preparar aplicativo mínimo iPhone | Build iOS compilado sem assinatura; aguardar conta/Personal Team e Mac disponível para assinar/instalar. |
| [#56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | [T30] Coletar GPS no Android | Coletor Android implementado; confirmar GPS do APK14 no novo smoke, ainda pendente. |
| [#57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | [T31] Coletar GPS no iPhone | Coletor iOS implementado; assinatura, instalação e prova física dependem da conta e do aparelho. |
| [#58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | [T32] Receber GPS em ambiente de prova | Receptor isolado implementado; manter evidência sintética separada da jornada real. |
| [#59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | [T33] Verificar localização em percurso Android | Percurso Android pendente; rodada de campo foi deferida ao usuário. |
| [#60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | [T34] Verificar localização em percurso iPhone | Percurso iPhone pendente; requer assinatura/instalação e rodada deferida ao usuário. |
| [#61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | [T35] Medir bateria e recuperação | Medição bilateral de bateria pendente; campo foi deferido ao usuário. |
| [#62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | [T36] Evitar ações duplicadas na entrada | Deduplicação implementada; conferir eventos reais quando o Mac/WhatsApp estiver disponível. |
| [#63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | [T37] Guardar e enviar respostas | Fila/adaptador implementados; comprovar envio e retorno no WhatsApp real quando o Mac estiver disponível. |
| [#64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | [T38] Interpretar mensagens com IA de forma limitada | Interpretação limitada implementada; IA continua OFF. Conferir o bot determinístico na nova jornada real, sem gasto de modelo. |
| [#65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | [T39] Preparar automações importáveis | Oito exports preparados e quatro agendas ativas; ensaio 006 aprovado. Revisar a configuração publicada sem ampliar flags. |
| [#66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | [T40] Configurar rotinas do motorista | Configuração de rotina implementada; validar com participantes na nova jornada real, ainda pendente. |
| [#67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | [T41] Mostrar vínculo e recuperação no aplicativo | Estados de vínculo/recuperação implementados; conferir no iPhone após assinatura e na rodada do usuário. |
| [#68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | [T42] Vincular o aparelho correto | Pareamento implementado; validar a jornada no WhatsApp real quando o Mac estiver disponível. |
| [#69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | [T43] Fixar a lista da partida | Snapshot da partida implementado; conferir a lista na nova jornada real. |
| [#70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | [T44] Confirmar início somente com GPS válido | Início só após GPS válido implementado; smoke do APK14 ainda pendente. |
| [#71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | [T45] Recuperar a parada sem internet | Parada durável implementada; conferir STOP/ACK com o APK14 na rodada física. |
| [#72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | [T46] Repetir envios somente com segurança | Retry seguro implementado; comprovar callback real da Evolution quando o Mac estiver disponível. |
| [#73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | [T47] Distinguir envio de entrega | Estado de entrega implementado; conferir o callback real da Evolution na nova jornada. |
| [#74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | [T48] Encerrar avisos sem utilidade | Expiração e elegibilidade implementadas; validar o aviso real após habilitação autorizada e percurso. |
| [#75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | [T49] Avaliar aproximação por ponto e sentido | Regra de aproximação implementada/publicada; flag OFF, calibração e utilidade ficam para percurso deferido. |
| [#76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | [T50] Respeitar progressão e cancelamento | Progressão/cancelamento implementados; flag OFF e prova de percurso permanecem pendentes. |
| [#77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | [T51] Testar destinatário e repetição do aviso | Testes dirigidos existentes; destinatário/utilidade serão conferidos na rodada de campo deferida. |
| [#78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | [T52] Encerrar a viagem automaticamente | Fim automático publicado na V43; flag OFF até a rodada controlada de percurso deferida. |
| [#79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | [T53] Confirmar que a coleta parou | Parada/ACK do fim implementados; conferir com o APK14 e na rodada física deferida. |
| [#80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | [T54] Oferecer ajuda e recuperação | Ajuda/pausa/retomada implementadas; conferir a jornada real quando o Mac estiver disponível. |
| [#81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | [T55] Autorizar as automações de presença | Autorização própria implementada; conferir os efeitos no ensaio operacional sem ampliar flags. |
| [#82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | [T56] Preservar automações ao reiniciar n8n | Fila e reinícios aprovados nos ensaios 005 e 006; o 006 usou o JAR efetivamente publicado. Revisar a evidência na issue. |
| [#83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | [T57] Limitar dados e aplicar exclusão | Retenção 7/30/90 publicada na V44; manter limpeza real OFF e revisar apenas a prévia. |
| [#84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | [T58] Provar cópia, restauração e exclusão | Dump/restore e journal atual reaplicados nos ensaios 005 e 006 aprovados; revisar a prova. Rotação física dos backups ainda pendente. |
| [#85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | [T59] Orientar operação e parada segura | Guia/helper implementados; 11 testes Python e um cenário PostgreSQL aprovados. Executar diagnóstico e ensaio isolado de alerta/plano; WhatsApp real não comprovado. |
| [#86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | [T60] Identificar os arquivos da versão de teste | API V44 e worker de 54 fontes identificados; ensaio 006 com JAR publicado aprovado. Revisar manifestos e rastreabilidade. |
| [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | [T61] Preparar instalação de teste Android | APK14 assinado, instalado e aberto; falta smoke GPS do novo build. |
| [#88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | [T62] Preparar instalação e renovação no iPhone | Build iOS sem assinatura; aguardar conta/Personal Team para assinar e instalar. |
| [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | [T63] Planejar instalação e retorno no servidor | Migração V44 e retorno da API ensaiados; revisar recibos da migração e do ensaio 006 aprovado com o binário publicado. |
| [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | [T64] Instalar no ambiente autorizado de teste | API V44 e worker publicados; revisar saúde/versões e manter flags de efeito OFF. |
| [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | [T65] Conferir o número dedicado | Instância dedicada configurada; comprovar envio e callback da nova jornada no WhatsApp real quando o Mac estiver disponível. |
| [#92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | [T66] Testar agendas no ambiente escolhido | Quatro agendas n8n em fila aprovadas nos ensaios 005 e 006; revisar a evidência da versão publicada. |
| [#93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | [T67] Testar a jornada completa no Android | Jornada Android completa não comprovada; smoke GPS e percurso ficam para o usuário. |
| [#94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | [T68] Testar a jornada completa no iPhone | Jornada iPhone pendente de assinatura/instalação; percurso fica para o usuário. |
| [#95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | [T69] Medir se o aviso ajuda no embarque | Utilidade/destinatário dependem de percurso e avaliação do usuário, ainda deferidos. |
| [#96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | [T70] Consolidar guia e resultados | Guias atualizados com V44 publicada e ensaio 006 aprovado; acrescentar jornada real, campo e revisão humana. |
| [#97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | [T71] Reproduzir a instalação final | Reprodução do pacote final pendente; depende da assinatura iOS e da rodada deferida. |
| [#98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | [T72] Proposta: cancelar ou corrigir presença | Integrada/publicada na V42; conferir cancelamento/correção no WhatsApp real quando o Mac estiver disponível. |
| [#99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | [T73] Proposta: definir limite da alteração | Integrada/publicada na V42; conferir regra de alteração na jornada real quando o Mac estiver disponível. |
| [#100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | [T74] Proposta: avisar cancelamento ao motorista | Aviso ao motorista integrado/publicado na V42; falta comprovar entrega no WhatsApp real. |
| [#101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | [T75] Mostrar lista de presença ao motorista | Lista de presença implementada; conferir a mesma consulta no WhatsApp real quando o Mac estiver disponível. |
| [#102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | [T78] Conferir contrato, permissões e banco | Contratos/permissões/migrações têm testes dirigidos; revisar evidência sem alegar execução integral da suíte. |
