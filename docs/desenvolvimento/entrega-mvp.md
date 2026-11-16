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

- API V43 publicada em `880ea56`, com presença, cancelamento, suporte e
  encerramento manual/automático. Aproximação e fim automático seguem OFF.
- Quatro agendas n8n ativas. Convite por link, vínculo e aprovação/correção
  do ponto validados no WhatsApp real em 15/11.
- APK Android 0.4.0+14 publicado e instalado por atualização no Redmi;
  smoke de GPS e jornada do novo build ainda pendentes.
- Build iOS 0.4.0+14 completo sem assinatura aprovado em 45,36 s, com fontes
  e lockfiles intactos. Não é instalável: a assinatura aguarda sessão do Mac
  desbloqueada e conta Apple/Personal Team disponível no Xcode.
- V44 implementada e avaliada em recuperação e migração reais isoladas na
  VPS, ainda não publicada nesta atualização. Runtime privado preparado e
  OFF; limpeza real permanece desabilitada.

## Sequência de entrega

1. **Integrado e publicado:** presença e avisos dos PRs #113 e #121, com
   autoria preservada; V42 mantém o cadastro V41 e acrescenta os avisos.
2. **Integrado e publicado na V43:** fim manual/automático, progresso,
   destino e permanência válidos, STOP/ACK recuperável e suporte preservado.
   O automático continua desligado até habilitar o ensaio.
3. **Ensaio V44 aprovado, publicação pendente:** retenções 7/30/90 dias e
   restauração de dump antigo com journal atual passaram no conjunto isolado.
   API, worker, quatro agendas n8n em fila e Redis retomaram sem duplicar os
   dois efeitos do canal simulado. A cópia do banco ativo também passou nos
   probes anterior/V43, candidata/V44 e anterior/V44, sem alterar serviço live.
4. **Fase atual:** publicar a revisão avaliada, assinar e instalar no iPhone,
   conferir GPS do novo Android e executar a nova jornada real com os dois
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
| [#25](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/25) | [T01] Preparar API e banco | Conferir implementação e evidências |
| [#26](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/26) | [T02] Cadastrar motorista | Conferir implementação e evidências |
| [#27](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/27) | [T03] Restringir ações por perfil | Conferir implementação e evidências |
| [#28](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/28) | [T04] Cadastrar passageiro e ponto | Conferir implementação e evidências |
| [#29](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/29) | [T05] Convidar e vincular passageiro | Conferir implementação e evidências |
| [#30](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/30) | [T6] Responder presença na viagem certa | Conferir implementação e evidências |
| [#31](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/31) | [T7] Receber GPS do aparelho vinculado | Conferir implementação e evidências |
| [#33](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/33) | [T9a] Autorizar consulta da posição | Conferir implementação e evidências |
| [#34](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/34) | [T9b] Responder consulta de localização | Conferir implementação e evidências |
| [#35](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/35) | [T10] Guardar a última posição válida | Conferir implementação e evidências |
| [#36](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/36) | [T11] Preparar aviso de aproximação | Conferir implementação e evidências |
| [#49](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/49) | [T23] Explicar cadastro e correção do ponto | Conferir implementação e evidências |
| [#50](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/50) | [T24] Criar calendário de ida e volta | Conferir implementação e evidências |
| [#51](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/51) | [T25] Enviar lembrete de presença | Conferir implementação e evidências |
| [#52](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/52) | [T26] Testar respostas antigas e repetidas | Conferir implementação e evidências |
| [#53](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/53) | [T27] Preservar versões e recuperação | Conferir implementação e evidências |
| [#54](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/54) | [T28] Preparar aplicativo mínimo Android | Conferir implementação e evidências |
| [#55](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/55) | [T29] Preparar aplicativo mínimo iPhone | Assinar, instalar e conferir o novo build |
| [#56](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/56) | [T30] Coletar GPS no Android | Conferir implementação e evidências |
| [#57](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/57) | [T31] Coletar GPS no iPhone | Validar GPS após assinatura e instalação |
| [#58](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/58) | [T32] Receber GPS em ambiente de prova | Conferir implementação e evidências |
| [#59](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/59) | [T33] Verificar localização em percurso Android | Preparar ensaio e rodada final |
| [#60](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/60) | [T34] Verificar localização em percurso iPhone | Preparar ensaio e rodada final |
| [#61](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/61) | [T35] Medir bateria e recuperação | Preparar ensaio e rodada final |
| [#62](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/62) | [T36] Evitar ações duplicadas na entrada | Conferir implementação e evidências |
| [#63](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/63) | [T37] Guardar e enviar respostas | Conferir implementação e evidências |
| [#64](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/64) | [T38] Interpretar mensagens com IA de forma limitada | Conferir implementação e evidências |
| [#65](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/65) | [T39] Preparar automações importáveis | Conferir implementação e evidências |
| [#66](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/66) | [T40] Configurar rotinas do motorista | Conferir implementação e evidências |
| [#67](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/67) | [T41] Mostrar vínculo e recuperação no aplicativo | Conferir implementação e evidências |
| [#68](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/68) | [T42] Vincular o aparelho correto | Conferir implementação e evidências |
| [#69](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/69) | [T43] Fixar a lista da partida | Conferir implementação e evidências |
| [#70](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/70) | [T44] Confirmar início somente com GPS válido | Conferir implementação e evidências |
| [#71](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/71) | [T45] Recuperar a parada sem internet | Conferir implementação e evidências |
| [#72](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/72) | [T46] Repetir envios somente com segurança | Conferir implementação e evidências |
| [#73](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/73) | [T47] Distinguir envio de entrega | Conferir implementação e evidências |
| [#74](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/74) | [T48] Encerrar avisos sem utilidade | Conferir implementação e evidências |
| [#75](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/75) | [T49] Avaliar aproximação por ponto e sentido | Conferir implementação e evidências |
| [#76](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/76) | [T50] Respeitar progressão e cancelamento | Conferir implementação e evidências |
| [#77](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/77) | [T51] Testar destinatário e repetição do aviso | Conferir implementação e evidências |
| [#78](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/78) | [T52] Encerrar a viagem automaticamente | Validar a regra publicada no ensaio controlado e em percurso |
| [#79](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/79) | [T53] Confirmar que a coleta parou | Validar STOP/ACK dos novos builds nos aparelhos |
| [#80](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/80) | [T54] Oferecer ajuda e recuperação | Conferir suporte e retomada publicados na nova jornada real |
| [#81](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/81) | [T55] Autorizar as automações de presença | Conferir implementação e evidências |
| [#82](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/82) | [T56] Preservar automações ao reiniciar n8n | Revisar a evidência do ensaio real isolado aprovado |
| [#83](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/83) | [T57] Limitar dados e aplicar exclusão | Publicar a V44 avaliada e revisar a prévia; limpeza real OFF |
| [#84](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/84) | [T58] Provar cópia, restauração e exclusão | Revisar dump/restore e reaplicação aprovados; rotação física pendente |
| [#85](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/85) | [T59] Orientar operação e parada segura | Revisar o roteiro e validar a parada nos aparelhos |
| [#86](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/86) | [T60] Identificar os arquivos da versão de teste | Conferir manifestos, versões e evidências da entrega |
| [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87) | [T61] Preparar instalação de teste Android | Conferir implementação e evidências |
| [#88](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/88) | [T62] Preparar instalação e renovação no iPhone | Preparar ensaio e rodada final |
| [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) | [T63] Planejar instalação e retorno no servidor | Conferir implementação e evidências |
| [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90) | [T64] Instalar no ambiente autorizado de teste | Conferir implementação e evidências |
| [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) | [T65] Conferir o número dedicado | Conferir implementação e evidências |
| [#92](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/92) | [T66] Testar agendas no ambiente escolhido | Revisar disparos reais das quatro agendas no ensaio isolado |
| [#93](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/93) | [T67] Testar a jornada completa no Android | Preparar ensaio e rodada final |
| [#94](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/94) | [T68] Testar a jornada completa no iPhone | Preparar ensaio e rodada final |
| [#95](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/95) | [T69] Medir se o aviso ajuda no embarque | Preparar ensaio e rodada final |
| [#96](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/96) | [T70] Consolidar guia e resultados | Revisar documentação atualizada e acrescentar a rodada final |
| [#97](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/97) | [T71] Reproduzir a instalação final | Preparar ensaio e rodada final |
| [#98](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/98) | [T72] Proposta: cancelar ou corrigir presença | Integrada e publicada; validar na nova jornada real |
| [#99](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/99) | [T73] Proposta: definir limite da alteração | Integrada e publicada; validar na nova jornada real |
| [#100](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/100) | [T74] Proposta: avisar cancelamento ao motorista | Integrada e publicada; conferir aviso no WhatsApp real |
| [#101](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/101) | [T75] Mostrar lista de presença ao motorista | Conferir implementação e evidências |
| [#102](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/102) | [T78] Conferir contrato, permissões e banco | Conferir implementação e evidências |
