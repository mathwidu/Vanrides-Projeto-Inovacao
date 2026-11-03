> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Validação, operação e passagem ao piloto

Parte da [proposta do MVP](README.md). Cenários e valores deste documento são **critérios propostos**, não testes executados nem SLA. Os defaults estão fechados para construção em 08; a calibração física registra ajustes antes da avaliação congelada.

## 1. Gates de decisão

M0–M4 são checkpoints desta proposta de MVP. Não renomeiam nem substituem os gates canônicos G0–G7 de `program/QUALITY_GATES.md`.

| Gate | Evidência necessária | O que libera |
|---|---|---|
| M0 — Recorte e acesso | D01–D10 resolvidas no grau necessário; canal/aparelhos Android+iPhone conhecidos; coletor, assinatura e distribuição das duas plataformas preparados; autorização para o ensaio | Executar P01 conectado, sem autorizar piloto |
| M1 — Viabilidade física | Ativação pelo WhatsApp/app, GPS contínuo, segundo plano, recuperação, pontos intermediários e conclusão exercitados separadamente em Android e iPhone | Investir na implementação integrada P02–P11 |
| M2 — Comportamento integrado | Cadastros, isolamento, presença, snapshot, aproximação, envio e fim passam primeiro em integração local com stubs declarados; o card 180 repete com canal/builds reais | Preparar implantação de teste; entrega exige revalidação externa e física |
| M3 — Prontidão operacional | Retenção, suporte, custos, observabilidade, restore/rollback, contratos externos e gates completos | Solicitar autorização concreta de piloto |
| M4 — Aceite do piloto | Uso limitado observado, métricas e feedback aceitos; pendências classificadas | Decisão de continuidade/expansão, separada de produção ampla |

Preparação documental, teste local, publicação, piloto e produção são marcos distintos. O programa atual mantém HOLD de piloto/produção; este plano não o altera.

## 2. Prova física de P01

Executar em aparelhos físicos Android e iPhone representativos do uso real, com instalação assinada e versões registradas. No caminho recomendado, testar coleta própria sem depender de navegador externo. Pelo menos uma execução deve usar o aparelho efetivo de cada motorista participante; emulador/simulador não prova GPS, bateria ou segundo plano. Usar observador responsável por registrar horários durante o trajeto; o motorista não deve operar telefone para gerar evidência enquanto conduz.

**Sequência mínima proposta**

1. Instalação limpa, identidade autenticada de teste no spike (pareamento completo no ensaio integrado), permissões graduais e teste parado com início, posição recebida e parada confirmada.
2. Percurso conhecido com pontos intermediários, sem ação por parada; sair da tela VanRides e usar WhatsApp antes de conduzir ou por observador autorizado.
3. Trecho com tela bloqueada e VanRides em segundo plano; proposta: pelo menos 15 minutos de cada condição, podendo coincidir, e pelo menos trinta posições distintas geradas durante movimento com bloqueio, validadas pelo instante de aquisição com desvio de relógio controlado. Em trecho parado, uma fixação nova pode ter coordenadas iguais; não confundir isso com replay. Não aceitar apenas “o app ficou aberto”.
4. Perda de rede por pelo menos três minutos; comprovar degradação e recuperação com nova posição. Exercitar separadamente encerramento forçado do VanRides, reboot, revogação de permissão e retomada; falta de sinais não pode aparentar funcionamento normal.
5. Chegada final, passagem próxima sem chegada e interrupção antes do fim.
6. Repetição suficiente para separar comportamento estável de uma execução que funcionou por acaso. Proposta: pelo menos três percursos e vinte oportunidades de aviso por plataforma; ampliar conforme os cenários reais. Relatórios e cobertura separados para Android e iOS; média combinada não compensa uma plataforma reprovada.
7. Perder ACK depois de a API gravar o lote; repetir os mesmos IDs sem duplicar amostras/ações. Testar término online, fim sem rede e expiração da autorização local; registrar separadamente conclusão do run e parada confirmada do coletor.

Evidência registra modelos/versões, manifest, tempos relativos, quantidade e idade de amostras, resultado de cada parada e número de intervenções. Coordenadas/pontos reais permanecem em armazenamento operacional restrito com retenção; o relatório usa identificadores sintéticos.

**Parâmetros para calibrar e fechar antes do gate**

| Parâmetro | Ponto de partida proposto | Como avaliar |
|---|---|---|
| Primeiro sinal após ativação | Até 30 s, com serviço de localização disponível | Medir início → posição recente recebida; registrar primeira fixação e falhas, sem esconder aquecimento de GPS |
| Idade máxima para aviso | 20 s; envio alvo de 5–10 s em movimento | Usa instante de aquisição corrigido/limitado, não apenas recebimento; incerteza de relógio ou precisão incompatível bloqueia aviso |
| Antecedência útil | Janela inicial de 2–5 min antes da chegada observada | Validar com motorista/passageiros; a mensagem pode continuar sem promessa numérica |
| Cobertura de avisos | Pelo menos 90% das oportunidades elegíveis pelo roteiro/presença recebem aviso dentro da janela acordada | Ausência de sinal conta como oportunidade perdida; mostrar cobertura global e diagnóstico por qualidade de sinal |
| Latência interna | p95 de evento persistido → tentativa de envio até 10 s sob carga do piloto | Separar latência interna de entrega externa do WhatsApp |
| Atualidade operacional | Ciclo de monitoramento até 10 s; detectar degradação até 10 s após o limite de idade | Monitor não renova GPS antigo nem dispara aviso de aproximação |
| Recuperação de rede | Posição nova recebida até 30 s após reconectar durante sessão válida | Não aceitar só backlog; posição precisa ter sido gerada após retorno da rede |
| Conclusão automática | Até 180 s após chegada final observada, cumpridos os demais critérios | Registrar casos inconclusivos; não confundir conclusão do run com ACK de parada no telefone |
| Parada da coleta | ACK de parada até 30 s após fim online; autorização local inicial de até 5 min, com proteção de expiração testada sem rede | Medir último GPS coletado/enviado; se o limite local não for cumprido, reprovar o mecanismo e corrigir antes do piloto |
| Energia | Proposta inicial: até 10 pontos percentuais de bateria/hora com tela bloqueada, aparelho em condição representativa | Comparar com baseline do mesmo aparelho, rede e modo de navegação; registrar aquecimento e uso de dados; ajustar antes do aceite |
| Duplicação/erro crítico | Zero no conjunto de testes: destinatário errado, ponto errado, nova submissão vencida, término prematuro | Entrega tardia de mensagem antes submetida é medida separadamente e pode reprovar a utilidade; teste finito não prova impossibilidade em produção |
| Ação humana em percurso normal | Zero por parada; uma ativação por saída | Contar consentimentos/retomadas e não escondê-los como “automático” |

Esses números não substituem avaliação de utilidade. Calibrar frequência/precisão/energia pelos aparelhos reais e avaliar seus efeitos juntos. Se houver falsos positivos ou necessidade de intervenção por parada, M1/M2 falham. Não alargar o raio até “passar” sem verificar ruas, sequência e sentido.

Autoencerramento só passa com evidência de destino final, progresso compatível, precisão e permanência suficientes. Proximidade isolada, falta de sinal ou fim da autorização de coleta não bastam. Se pontos finais ficarem indeterminados, a operação fica pendente de resolução; a contingência manual depende da D06 e não é declarada equivalente ao fluxo automático.

## 3. Matriz rastreável de cenários

Os testes abaixo têm dados sintéticos em automação. Testes físicos usam somente participantes autorizados. Um teste de unidade não comprova entrega WhatsApp, tela bloqueada ou aprovação de app.

| ID | Cenário / condição de aceite | Pacotes / fluxos |
|---|---|---|
| T01 | Motorista cadastra, interrompe e retoma sem duplicar perfil nem exigir empresa | P02–P04 / F01 |
| T02 | Convite revogado/expirado recusado; convite válido não revela passageiros | P04/P06 / F03 |
| T03 | Passageiro recorrente conclui cadastro pelo WhatsApp; motorista aprova vínculo/ponto | P06 / F03–F04 |
| T04 | Pin estático, pin/endereço divergentes, ponto compartilhado e entrada ambígua tratados sem coordenada inventada | P03/P06 / F04 |
| T05 | Editar ponto/rotina depois do início não modifica run ativo | P06–P08 / F04–F07 |
| T06 | Ida/volta, datas e “sim” de pergunta antiga não se misturam; timezone e exceção de calendário corretos | P03/P07 / F05–F06 |
| T07 | Repetição de lembrete/resposta é idempotente; pendente segue política aprovada | P07/P09 / F06 |
| T08 | Corrida entre presença e ativação tem corte transacional reproduzível | P07/P08 / F06–F07 |
| T09 | Instalação ausente/limpa, link a frio/quente, permissão ausente e retorno da loja têm recuperação | P01/P05 / F02 |
| T10 | Preview do link não resgata desafio nem inicia; desafio vencido/reutilizado/aparelho errado falha | P02/P05 / F02/F07 |
| T11 | Troca de aparelho revoga anterior; credencial segura, renovação e atualização do app preservam vínculo válido | P05/P11 / F02/F10 |
| T12 | Dois inícios produzem um run; timeout/ACK perdido reconcilia sessão/lote; reinício não cria coleta paralela nem falsa ativação | P01/P08 / F07 |
| T13 | Permissão negada/revogada, link só aberto e GPS não recebido nunca confirmam acompanhamento | P01/P05/P08 / F02/F07 |
| T14 | Tela bloqueada e VanRides em segundo plano mantêm telemetria em Android/iPhone, conforme M1; sair do widget não cancela coleta | P01/P08 / F07–F08 |
| T15 | GPS fora de ordem, futuro, inválido, sessão desconhecida ou geração antiga não atualizam o estado atual | P08 / F08 |
| T16 | Lote repetido, concorrente, fora de ordem ou de aparelho/generation incorretos preserva ingestão única; token inválido/revogado é rejeitado | P08 / F08 |
| T17 | Webhook repetido/eco/grupo não cria ação dupla; LID não vira telefone; indisponibilidade antes/depois de persistir testa reentrega real do provedor e perda visível | P02/P03 / F01–F03 |
| T18 | Passageiro A não acessa B; motorista A não opera B; n8n/LLM não troca ator por payload; endpoints novos exigem auth | P02/P03 / Todos |
| T19 | Rua paralela, viaduto, sentido contrário e proximidade de parada posterior não geram aproximação indevida | P01/P10 / F08 |
| T20 | Cada passageiro do mesmo ponto recebe seu aviso; duplicação de decisão ou mudança de política não repete | P09/P10 / F08 |
| T21 | Desvio, parada longa e salto de GPS suspendem decisão incerta; watchdog API degrada sem novas amostras e com n8n parado; reconexão descarta pontos/alertas vencidos | P08/P10 / F08 |
| T22 | Antecedência e cobertura atendem valores acordados; distância isolada ou ETA constante não é divulgado como ETA individual | P01/P10 / F08 |
| T23 | Restart entre envio e ACK, timeout, lease vencido e ACK antigo tratados; resultado desconhecido não é entrega confirmada | P09 / F08 |
| T24 | Callbacks entregue/lido fora de ordem são preservados sem regredir; canal desconectado não libera backlog vencido | P09/P12 / F08 |
| T25 | Cancelamento individual/opt-out/fim de vínculo impede novos avisos sem falsificar presença/embarque | P07/P09/P11 / F06/F10 |
| T26 | Chegada final real conclui; passagem perto, suspensão e silêncio não concluem; telemetria tardia não reabre | P01/P11 / F09 |
| T27 | Atendimento humano pausa bot; correção, recuperação, exclusão e retomada possuem trilha mínima | P11/P12 / F10 |
| T28 | Logs/exports/evidências sanitizados, restore/rollback testados, carga e orçamento suportam piloto sem prejudicar serviços existentes | P12/P13 / Operação |
| T29 | iPhone via Xcode/Personal Team recebe link a frio/quente por Universal Link quando disponível ou fallback HTTPS/scheme, inicia coleta e permite retorno; app ausente/versão antiga têm recuperação | P01/P05 / F02/F07 |
| T30 | Sessão de outro aparelho/run ou geração não é atribuída ao motorista; credenciais e associação do link são da distribuição efetiva | P02/P05/P08 / F02/F07 |
| T31 | Atualização/expiração da distribuição e troca de aparelho preservam ou recuperam vínculo com revogação do anterior, nas duas plataformas | P05/P11/P12 / F02/F10 |
| T32 | Permissões/indicadores de coleta correspondem ao onboarding; sem pedido legado de fotos ou privilégio desnecessário; localização aproximada não se passa por precisa | P01/P05 / F02 |
| T33 | Fim online/offline, expiração de autorização, parada local e reabertura confirmam desligamento sem reativar run terminal; backend distingue stop solicitado e confirmado | P01/P08/P11 / F09 |
| T34 | Remetente desconhecido conclui onboarding restrito; autodeclaração não habilita motorista; bootstrap do operador é autenticado e auditado | P02–P04 / F01 |
| T35 | Jobs SYSTEM materializam calendário/lembretes/expirações sem ator humano falso; repetição e janela atrasada são idempotentes | P02/P03/P07/P12 |
| T36 | Modelo retorna intenção estruturada, trata ambiguidades/negações; timeout/recusa/injeção não executam ação indevida; fallback funciona | P03 / Todos |
| T37 | Áudio/imagem/live location orientam texto/pin sem download/transcrição implícitos; fixtures reais de versão/canal testadas | P03/P06 |
| T38 | Export/import W01–W08 preserva subworkflows, credenciais por ambiente, Error Workflow e limites; restart de worker recupera fila | P03/P09/P12/P13 |
| T39 | Primeiro vínculo nasce no app aberto pelo ícone; scheme interceptado antes de begin ou no retorno, substituição de chave, preview/replay e código de outro pedido não concedem sessão; logs não revelam URI/chaves; iOS gratuito é renovável | P02/P05/P11 |
| T40 | Redmi 8 com MIUI observada, bateria padrão/economia, force-stop/reboot e retomada não fingem GPS ativo; ajustes necessários documentados | P01/P05/P08 |
| T41 | Flags permitem stop/revoke/cleanup com novos starts/avisos bloqueados; segredos _FILE e ingress fail-closed; boot vazio/upgrade legado validados | P02/P12/P13 |
| T42 | Seed/reset recusa produção; retenção/purge e restauração isolada reaplicam exclusões; carga não envia mensagens nem afeta serviços alheios | P02/P12/P13 |
| T43 | Duas consultas “onde está?” geram pins atuais distintos por comando, sem duplicar replay; fila não submete pin vencido (revalida aquisição); somente ator autorizado, sem sinal informa indisponibilidade e fim impede consulta GPS | P08/P10 / F08 |

Além da matriz, executar regressões das rotas/auth legados afetados por alterações compartilhadas. Testar migrations/concorrência em PostgreSQL real: banco em memória não prova índices parciais, locks ou unicidade sob corrida.

## 4. Gates de engenharia e evidências

Na implementação, usar instruções atuais de cada repositório e URLs locais explícitas. Comandos abaixo são referências de execução futura, **não foram rodados como validação do MVP nesta entrega**.

| Escopo | Verificação |
|---|---|
| Coordenador/governança | `python3 scripts/program_control_test.py`; `python3 scripts/workspace_profile_test.py`; `python3 scripts/program_control.py check --workspace core` |
| Backend | Gate atual do módulo, incluindo `./scripts/ci_quality_gate.sh`; depois `VANRIDES_BACKEND_BASE_URL=http://127.0.0.1:<porta> ./scripts/qa_gate_card.sh` e smoke HTTP do artefato avaliado |
| App | `./scripts/qa_gate_card.sh --no-e2e`, análise/testes exigidos pelo módulo, builds Android/iOS, integração afetada e ensaio físico dos APK assinado e build iOS de desenvolvimento efetivamente instalados |
| n8n/Evolution | Contratos de payload com fixtures sanitizadas, export/import e retomada no ambiente de teste; envio real apenas a destinatários autorizados |
| Contratos cruzados | Produtor e consumidor, fluxo ponta a ponta e manifest com versões exatas |

O gate do backend tem default de URL externa: nunca rodá-lo sem configurar a base local ao validar localmente. `core` não inclui executar/clonar mapas, que continua adiado. Um smoke antigo não serve para nova versão.

Manifest mínimo: SHA de coordenador/backend/app, hash do AAB/APK e do artefato iOS de origem, build numbers e método de instalação APK/Xcode Personal Team, assinaturas/provisionamento identificáveis sem chave privada, hash/export do workflow, versões n8n/Evolution/Android/iOS e bibliotecas nativas de localização, versão de schema e política de aviso, ambiente e data. No iOS registrar o build instalado pelo Xcode/Personal Team e validade do provisionamento; hash de archive isolado não comprova instalação ou funcionamento sem debugger. Relatório lista PASS/FAIL/NOT RUN por cenário e plataforma, com razão de qualquer dispensa; não preencher lacuna com hipótese.

## 5. Observação, métricas e suporte

**Painel mínimo para operação:** instância WhatsApp conectada; runs aguardando início/ativos/degradados; idade do último sinal; fila de conversa/envio; resultados desconhecidos; alertas descartados; casos de suporte. Interface administrativa completa não é necessária: métricas e consulta restrita podem atender o piloto.

Alertas operacionais são para pessoa/canal autorizado pela equipe. Não enviar mensagens a terceiros só porque um erro ocorreu. Agregar por incidente, com recuperação, evitando uma mensagem a cada amostra perdida.

| Incidente | Resposta do sistema | Ação operacional |
|---|---|---|
| GPS antigo/perdido | Suspende aproximação; marca degradado; resposta de consulta informa indisponibilidade | Verificar sessão/provedor; orientar retomada em condição segura |
| Coletor suspenso ou permissão revogada | Não afirma viagem concluída | Recuperação explícita; registrar limitação se exigir intervenção frequente |
| Evolution desconectada | Fila com TTL; status de envio pendente/desconhecido | Reconectar pelo procedimento do provedor; descartar avisos vencidos |
| API/n8n indisponível | Eventos já persistidos recuperáveis; antes do ACK depende da reentrega efetiva do provedor; sem confirmação fictícia | Restaurar serviço; reprocessar elegíveis e tratar perda detectada conforme contrato testado |
| Provedor opcional de rotas indisponível | Não substituir por ETA estático | Usar somente política de aproximação independente já validada, ou suspender avisos dependentes desse dado |
| Identidade/aparelho comprometido | Revoga acesso e links, pausa operação afetada | Recuperação assistida e auditada |
| Aviso errado ou término prematuro | Desabilitar política/saída afetada; preservar evidência sanitizada | Corrigir e repetir matriz antes de retomar |

Medir: conclusão/abandono do onboarding, tempo e toques para ativar, intervenções por percurso, atraso de GPS, aviso útil/perdido/incorreto, tempo de entrega, duplicidade, suporte, consumo e custo por saída. Feedback distingue “mensagem enviada” de “passageiro conseguiu se preparar”.

## 6. Custos, capacidade e limites

Não há orçamento contratado nesta proposta. P00/P12 registram preços e limites vigentes das contas antes do piloto. O caminho principal não exige Navigation Connect/Pub/Sub nem Google Cloud para receber GPS. Custos de distribuição, infraestrutura, modelo, canal, rotas opcionais e eventual licença de biblioteca continuam a avaliar. Conta Google para rotas é dependência separada da fonte de posição.

Modelo simples de estimativa:

- Saídas/mês = motoristas × saídas por dia × dias de operação.
- Amostras = soma da duração em segundos ÷ cadência efetiva, acrescida de eventos de estado.
- Mensagens = lembretes + respostas + avisos individuais + início/fim/suporte. Ida/volta contam separadamente.
- Modelo de linguagem = chamadas por conversa × tokens médios medidos; comandos determinísticos não precisam de nova geração a cada evento.
- Rotas/geocodificação opcionais = consultas por destino/trecho; evitar recalcular todas as paradas em toda amostra sem necessidade.
- Rede, logs e banco = volume/retentiva medidos; GPS não deve virar execução do n8n por amostra. Biblioteca paga, se necessária, entra na avaliação antes da escolha.

Comparar cadência real, consumo de bateria/rede e utilidade dos avisos. Aplicar quotas/alertas e interrupção controlada de gasto por backend; alerta de billing sozinho não é um teto rígido. Não instalar Redis/queue mode por antecipação: medir concorrência/latência e preservar automações já hospedadas.

## 7. Retenção e acesso

Antes de piloto, preencher a tabela com valores aprovados; “a definir” impede M3. Os valores iniciais propostos estão no [registro de fechamento](06-fechamento-e-prontidao.md); implementação mantém períodos configuráveis e executa limpeza verificável.

| Dado | Mínimo necessário | Decisão exigida |
|---|---|---|
| Perfil e aliases | Identidade do atendimento e estado de vínculo | Prazo após saída/solicitação de exclusão |
| Ponto habitual | Pin aprovado e referência útil | Vigência e limpeza de versões antigas |
| Última posição | Suporte a acompanhamento/aviso durante run | Prazo curto após fim; sem histórico integral por padrão |
| Eventos de integração | Deduplicação e diagnóstico mínimo | TTL compatível com retries sem payload bruto permanente |
| Conversa/LLM | Etapa e contexto indispensáveis | Conteúdo enviado ao modelo, fornecedor, prazo e opção de suporte |
| Presença/auditoria/envio | Prova operacional mínima e tratamento de contestação | Prazo, papéis autorizados e anonimização/exclusão |
| Backups | Recuperação | Retenção, acesso, criptografia e propagação da exclusão conforme política |

Separar obrigação/termos aplicáveis de escolha de produto; obter revisão específica se necessária. Não declarar conformidade jurídica apenas porque há uma caixa de aceite. Política/explicação acessível ao usuário deve corresponder à coleta real.

## 8. Publicação, piloto e rollback

**Antes de publicar:** fechar decisões; reconciliar controle/dirty paths; executar gates; gerar manifest; preparar backup/restore; verificar recursos e contratos externos; preparar configuração sem segredos; apresentar mudança externa concreta. Usar a skill `publicar-vps-pessoal` na etapa de publicação e aguardar autorização específica do plano/ambiente. Não ativar workflows ao importar por engano.

**Ordem técnica proposta:** migrations aditivas compatíveis → backend com flags desligadas → webhooks/assinaturas e integração de teste → workflows inativos → distribuição do coletor aos testadores Android/iPhone → smoke com contas de teste → habilitar somente coorte autorizada. A sequência final depende do manifest e do ambiente observado.

**Pausa rápida:** impedir novos inícios/avisos na API, solicitar parada dos coletores ativos e conferir ACK/expiração, desabilitar consumo/envio dos workflows afetados e orientar as pessoas autorizadas. O caminho de suporte não deve depender exclusivamente da automação que falhou.

**Rollback:** voltar backend compatível e export anterior dos workflows; manter tabelas aditivas/dados; revogar links/tokens afetados; corrigir app por atualização distribuída. Reativação exige descartar fila vencida, revalidar sessões e repetir smoke. Não enviar backlog de aproximação após recuperação nem presumir que rollback do servidor desligou um aparelho offline.

Critérios propostos de pausa imediata: vazamento entre usuários, sessão atribuída a motorista errado, aviso para ponto errado, término prematuro recorrente, falha de GPS que torne avisos inúteis, necessidade de intervenção por parada ou gasto acima do limite aprovado. O piloto só retoma com causa tratada e evidência proporcional.

## 9. Decisão se o caminho principal falhar

1. Identificar se a falha é permissão, ciclo de vida mobile, biblioteca, cadência, energia, conectividade, algoritmo ou experiência.
2. Corrigir dentro do mesmo caminho apenas quando houver hipótese testável e custo justificável.
3. Se não atender, interromper a promessa de aproximação automática nesta solução. Apresentar ao proprietário alternativas concretas: outro componente nativo, provedor como Navigation Connect com seus pré-requisitos e ensaio próprio, ou mudança explícita do escopo.
4. Não selecionar automaticamente rastreador físico, scraping de Maps, aba web sempre aberta ou envio manual por parada. Essas opções não foram aprovadas e podem contrariar a experiência pedida.

M1/M2 podem resultar em no-go mesmo com API tecnicamente respondendo: o requisito é a experiência completa com aviso útil.
