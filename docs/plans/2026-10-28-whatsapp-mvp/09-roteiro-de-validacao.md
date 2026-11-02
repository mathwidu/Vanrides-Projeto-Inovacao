> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Roteiro executável de validação e entrega

30/10/2026. **Todos os ensaios do produto abaixo ainda devem ser executados.** A revisão/validação do plano não muda esse estado. Os critérios complementam [T01–T43 e M0–M4](04-validacao-e-operacao.md).

## 1. Dados e identidade da execução

Cada rodada usa `testRunId`, três SHAs, digest backend, hash APK, identificador/hash do build iOS de origem e versão instalada, workflow hashes, versões n8n/Evolution, schema/política, Android/MIUI/iOS, permissões e configuração de energia. Não registrar serial/UDID, telefone, GPS bruto, credenciais ou conversa completa em evidência destinada ao Git.

Seed sintético local: dois motoristas A/B; passageiros confirmado, pendente, ausente, vínculo aguardando, vínculo encerrado, ponto compartilhado e aparelho revogado; duas rotinas de sentidos distintos; ponto final separado de pontos intermediários. Seed por lote, repetível e reversível. `reset-test-data` precisa de ambiente/lote explícitos e recusa produção. Nenhuma fixture local gera envio real.

No ensaio físico, usar número do bot e contatos que o proprietário autorizou; guardar configuração restrita fora do Git. Usar contatos suficientes para testar destinatários diferentes, inclusive dois no mesmo ponto. Os dois aparelhos podem trocar o papel de motorista em rodadas separadas; o passageiro precisa ser um contato distinto na rodada. Ausência de contatos suficientes é impedimento explícito, não teste simulado declarado real.

## 2. Preparar e instalar

1. Congelar manifest e confirmar ambiente/base URL de teste nos binários; nenhuma URL legada de produção herdada.
2. Executar gates canônicos. Backend com PostgreSQL real, explicitando base local no `qa_gate_card.sh`; app com `./scripts/qa_gate_card.sh --no-e2e`, análise/testes, builds Android/iOS e E2E afetados. Rodar também `python3 scripts/program_control_test.py`, `python3 scripts/workspace_profile_test.py` e `python3 scripts/program_control.py check --workspace core` no coordenador. Sem skips ocultos ou arquivo local não versionado necessário.
3. Redmi: observar versão/ABI, conferir assinatura/checksum do APK, instalar, abrir sem Flutter debugger e conferir notificações/permissões. Testar o flavor correto, não app legado por engano.
4. iPhone: assinar com Personal Team do proprietário, instalar e abrir sem debugger, registrar validade real do perfil e build. Testar relançamento após desconectar cabo. Simulador não cobre essa etapa.
5. Abrir link pelo WhatsApp a frio e a quente; preview não deve ativar. Testar app ausente, link expirado e build desatualizado. No fallback iOS, registrar número de toques e confirmação do sistema.
6. Reinstalar/atualizar de forma controlada e provar recuperação; renovar assinatura gratuita por procedimento documentado, sem presumir que a renovação conserva credencial em todos os casos.

## 3. Prova física que antecede o produto completo — card 030

O card 020 fornece receptor autenticado/HTTPS, identidade e pontos de teste; o ensaio não espera cadastro/LLM prontos. A ativação é um link controlado enviado ao próprio testador. Resultados de proximidade podem usar sink de teste, claramente identificado; entrega WhatsApp real será repetida no card 180.

Para **cada aparelho**, executar três percursos e reunir pelo menos vinte oportunidades elegíveis de aproximação. Passageiro/observador registra chegada e alerta por relógio sincronizado; motorista não opera o telefone durante a direção. Permitir repetir o mesmo circuito; registrar quais condições variaram.

| Execução | Passos | Observação necessária |
|---|---|---|
| A — percurso normal | Iniciar → sair do app → 15 min de tela bloqueada/segundo plano → pontos intermediários → destino | ≥30 fixações distintas em movimento, cadência/frescor, primeiro GPS, progressão e fim |
| B — interrupções | Com run ativo, observador corta rede por 3 min → restabelece dentro do lease → percorre pontos → fim offline separado | Degradação visível, nova posição em ≤30 s após rede, buffer limitado, nenhuma notificação retroativa, parada nativa ao expirar |
| C — ambiguidades | Rua paralela/sentido oposto/parada posterior próxima → parada longa → passagem perto do destino sem chegar → chegada real | Zero aviso indevido, zero fim prematuro; oportunidades suprimidas contam como perdas |

Além dos percursos, fazer ensaios estacionados de permissão negada/revogada, localização aproximada, force-stop, reboot, app morto e reabertura. Testar modo de economia de bateria e registrar exceções específicas MIUI. Não é obrigatório sobreviver a force-stop/reboot; é obrigatório não fingir continuidade nem retomar run terminal. A retomada autorizada deve funcionar.

Critérios iniciais: primeiro GPS ≤30 s após permissão; idade aceitável ≤20 s para decisão; recuperação ≤30 s; fim online com stop ACK ≤30 s; autoencerramento ≤180 s após chegada válida; zero ação por parada. Medir bateria em sessões de pelo menos 60 min por aparelho, sem exigir navegação externa; alvo ≤10 pontos percentuais/hora. Registrar rede, brilho, saúde/configuração de bateria e carregamento para comparação válida.

A utilidade exige ≥90% de **todas** as oportunidades elegíveis com aviso entre dois e cinco minutos antes da chegada; denominador inclui perdas por rede/GPS/supressão e é separado por plataforma. Para vinte oportunidades, são pelo menos dezoito úteis. Registrar mínimo/mediana/pior atraso, falsas aproximações, ausência e entrega tardia. Não remover casos difíceis para elevar a porcentagem. Ajustar parâmetros em rodada de calibração e repetir a avaliação congelada; não avaliar nos mesmos dados usados para ajustar limiares.

O card 030 aprova apenas viabilidade do coletor e algoritmo de prova. O card 180 repete condições críticas e utilidade usando **os binários e canal finais**; não transportar um PASS antigo para outro build.

## 4. Funcionalidades de ponta a ponta — card 180

Executar a sequência completa com motorista no iPhone e repetir com motorista no Redmi. Para mensagens recebidas, conferir também apresentação/ação pelo WhatsApp em ambos.

1. **Habilitação e motorista:** remetente desconhecido recebe onboarding; dizer “sou motorista” não habilita privilégio. Operador autoriza motorista de teste. Cadastro interrompido continua sem duplicar. Configurar rotina/horário/sentido/destino e ordem; corrigir e confirmar resumo.
2. **Passageiro:** motorista gera convite; passageiro entra, informa nome, manda pin estático e referência. Bot não exige localização ao vivo. Motorista aprova vínculo/ponto. Outro motorista não lê/edita esse registro. Testar convite vencido/revogado, duas pessoas no mesmo ponto e alteração posterior.
3. **Presença:** controlar relógio no teste automatizado e usar janela curta configurada no ensaio. W04 pergunta à pessoa certa pela ocorrência certa. Confirmado entra, ausente sai, pendente permanece identificado. “Sim” de pergunta antiga/ida não confirma volta. Restart do n8n não duplica pergunta.
4. **Pareamento:** instalar, abrir diretamente pelo ícone, criar pedido/chave, gerar código, confirmar no WhatsApp e concluir no aparelho. Preview/GET não consome. Código errado, interceptação do scheme antes de begin/no retorno, substituição da chave, outro aparelho, replay e link vencido falham. Troca de aparelho revoga anterior.
5. **Início:** solicitar pelo WhatsApp, abrir link e conceder permissões. Antes da posição válida, bot não diz que acompanha. Duplo clique/timeout produz um run. Presença simultânea ao início respeita snapshot. Não criar empresa/jornada legada para passar.
6. **Percurso:** executar o circuito validado, com WhatsApp/navegador em primeiro plano ou tela bloqueada. Comparar GPS com pontos habituais; confirmar no destinatário horário, texto e unicidade do aviso. Ausentes/opt-out não recebem. Consultar “onde está” duas vezes em posições diferentes: respostas distintas, frescas e autorizadas; replay não duplica. Atrasar a fila além de 20 s: atualizar pin na autorização ou informar indisponibilidade, nunca submeter localização vencida. Pin modificado durante o run vale para próxima ocorrência.
7. **Falhas:** reiniciar worker, devolver webhook repetido, atrasar callback entregue/lido, causar timeout depois de possível envio e desconectar sessão de teste. Suspender n8n e cessar amostras: watchdog da API detecta frescor excedido sem depender de novo pacote/W08. Não inferir entrega de HTTP 200; UNKNOWN não gera retry cego. Recuperação não envia backlog vencido.
8. **Encerramento:** passar perto do destino sem concluir, depois chegar e permanecer. Bot informa fim após regra válida; app interrompe sensor/envio. Fim offline expira autorização local; estado backend distingue stop solicitado/confirmado. Posição atrasada não reabre. Testar “finalizar” apenas no cenário de contingência.
9. **Suporte e saída:** AJUDA pausa conversa automatizada, operador trata e retoma; corrigir ponto para próxima saída; revogar aparelho/vínculo; excluir dados de teste conforme lifecycle. Solicitação de exclusão não apaga arbitrariamente histórico compartilhado.
10. **Operação:** observar logs/exports sem segredos, interromper flags com run ativo mantendo parada disponível, restaurar backup isolado, reaplicar exclusões e ensaiar rollback. Conferir que n8n/Evolution e serviços alheios continuam saudáveis.

## 5. Automatizar falhas e limites

Testes unitários: calendário/timezone, estados, decisão geográfica, parsing determinístico, schemas e TTLs com relógio controlado. PostgreSQL: locks, snapshot, partial unique indexes, idempotência, leases, concorrência e migrations. Contrato: payloads Evolution, DTOs app, workflows e outputs LLM. Integração: receptor → inbox → comando → outbox → adapter, primeiro com stub e depois com canal autorizado.

Corpus do modelo: exemplos positivos, ambíguos e maliciosos; validação estrutural, timeout, recusa e saída incompleta. Critério de qualidade inicial ≥95% de intenção correta nos casos inequívocos e 100% dos casos ambíguos perigosos pedindo esclarecimento ou recusados; aprovação de negócio continua na API. Ajustar prompt não muda autorização.

Carga inicial de teste sintético: 10 sessões enviando a cada 5 s, 50 passageiros e pico de 100 eventos/min por 15 min. Metas: ingestão p95 ≤500 ms, nenhum dado perdido após ACK durável, filas retornam ao normal e nenhuma regressão de serviços compartilhados. Medir no ambiente aprovado antes de afirmar capacidade; o piloto começa com um motorista. Modelo e envio real ficam desabilitados nesse teste para não gerar cobranças/mensagens acidentais.

## 6. Evidência, falha e entrega limpa

Matriz de resultados: `testId`, `platform`, `artifactManifest`, `executedAt`, `expected`, `observed`, `status`, `evidenceRef`, `issueRef`. Usar PASS/FAIL/BLOCKED/NOT_RUN; cada dispensa precisa de justificativa e não satisfaz função obrigatória. Evidência versionada contém métricas agregadas e correlação pseudônima; material sensível temporário fica restrito com descarte definido.

Reportar impedimento imediatamente quando não puder resolvê-lo: erro/reprodução, impacto no caminho crítico, tentativas, ajuda necessária e próximo passo independente. Permissão ausente, signing, conta/API sem crédito, canal sem conexão, DNS e falta de percurso físico não são PASS. Nenhuma pendência essencial pode ser escondida como “melhoria futura”.

Para encerrar o card 190: checkout reproduzível, commits por repositório, nenhum arquivo necessário só no Mac, artefatos do manifest instalados, T01–T43 pertinentes aprovados, regressões/gates verdes, percurso real aprovado em ambos, rollback/restore ensaiados, nenhuma falha crítica/alta aberta no escopo e guia de teste disponível. O proprietário deve conseguir repetir cadastro → presença → início → aproximação → fim, além de renovar o build iOS gratuito. Registrar o que o usuário testou e o que o executor comprovou; não confundir aceite humano com automação.

Publicação em lojas, novos motoristas e produção ampla exigem seus próprios critérios e autorização posterior. Um MVP funcional para o proprietário não recebe por isso um selo de produção.

## Distribuição das provas de pareamento e GPS

| Etapa | Resultado a conferir |
|---|---|
| 060 | Chave/PKCE criada no ícone, código puro reconhecido pela inbox, vínculo no próprio aparelho, retorno seguro, refresh recuperável, revogação e estado de preparo. Laboratório Android e host iOS registrados separadamente. |
| 110 | Intenção de início, snapshot, amostras do coletor, lease, recuperação, estado de GPS e fim contra o Spring integrado. |
| 030, 170 e 180 | Testes físicos de sinal/bateria, associação publicada, mensagem real e fluxo completo em iPhone e Redmi. O aceite usa os binários finais. |
