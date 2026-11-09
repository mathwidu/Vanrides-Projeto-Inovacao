# Área da equipe e deploy automático — 09/11/2026

## Escopo aprovado

Um ambiente compartilhado, sem produção real, com publicação da API após os
testes de um push na `main`. PRs validam. A primeira integração preserva o
histórico da API já instalada e das melhorias dos colegas; não usar squash
nem rebase para esse merge inicial. Separação de stage/produção fica para v1.0.

A área da equipe usa login individual GitHub e permite aos colaboradores com
escrita preparar passageiros e motoristas. Ela não concede SSH nem acesso aos
painéis de infraestrutura. O cadastro e o aceite permanecem com o participante.

## Implementação e decisões

- API e interface no mesmo JAR, sem novo framework ou serviço web.
- GitHub App dedicado, somente metadados e apenas o repositório acadêmico.
  Sessões em memória expiram em 30 minutos e desaparecem no reinício.
- Confirmação do telefone canônico pela Evolution. Identificadores históricos
  vazios são preservados, mas não dão autorização de ingresso.
- Admissão estrita exige o coorte autorizado e prova positiva de liberação:
  operação da equipe, perfil legado elegível ou convite de motorista legado.
  A ativação confere que os elegíveis legados são exatamente os dois contatos
  da lista atualmente ativa, sem listar seus dados na evidência pública.
- LID ainda livre só é associado quando o webhook autenticado apresenta o par
  com PN já admitido. Identificadores conflitantes e remetentes desconhecidos
  são ignorados sem criar identidade ou inbox.
- O receptor dinâmico usa `/team-events`. Uma API antiga não conhece essa rota;
  não pode processar o corpo pela regra antiga durante retorno de versão.
- A cota dos participantes é restituída em recibos IGNORED válidos; transporte
  (240 tentativas/minuto) e limites por remetente continuam consumidos.
- V39 adiciona auditoria GitHub e permite convite emitido por esse ator.
  Operações usam UUID estável para reconsulta após perda de resposta.
- Actions publica o mesmo JAR aprovado, com imagem por digest, e usa uma chave
  SSH exclusiva limitada ao atualizador deste serviço. Não copia a chave do Mac.
- O CD desta etapa atualiza **API e interface da equipe**. Worker/receptor,
  fluxos n8n e APK conservam os procedimentos próprios; alterações neles não
  são anunciadas como publicadas só por entrar na `main`.

## Verificação local

A compilação passou. A rodada focada concluiu 43 testes Java em 30,56 s, sem
falhas, erros ou skips: seis cenários de admissão com PostgreSQL, sete novos
unitários e regressões de CLI, transporte, isolamento e migrações.

Três testes de interface verificam repetição idempotente, rejeição de resposta
ambígua, renovação e expiração de sessão. O navegador confirmou liberação e
consulta com servidor local e dados sintéticos; isso não comprova OAuth real.
A revisão independente encontrou e corrigiu o limite de requisições da área,
a rejeição do PN legado órfão, a primeira associação LID e a rota de versão mista.

A rodada integral do commit `f355c0f` executou **1.814 testes** em 656,2 s:
1.811 passaram e três falharam em `WaAttendanceOwnershipIntegrationTest`.
A entrega do resumo adicionada pelo PR #106 avançava o relógio um segundo;
a fixture materializava a presença antes da validade do vínculo/ponto. Apenas
o relógio simulado passou para a próxima janela. Os sete testes dessa classe
passaram na reexecução em 27,4 s. O runtime e as asserções foram preservados.
Não foi repetida a suíte inteira por esse ajuste de fixture; a CI integral
remota da revisão final continua pendente. Não agregar os dois ensaios como
um único gate canônico aprovado.

Também passaram o gate leve (49 verificações de wiring e demais auxiliares),
os 35 testes do atualizador, a suíte WhatsApp offline, actionlint, consistência
documental e controle do programa. O Graphify estrutural foi atualizado;
a cobertura semântica continua parcial.

O JAR candidato `ead942a539bdaf2e629b0fa14596a8b40a4c516e15173e574485ba2df3e7fb56`
passou em cinco verificações locais sequenciais: API anterior com V38,
migração pelo candidato para V39, backup/restauração do banco sintético,
API anterior iniciando com V39 restaurada e fronteira HTTP da área habilitada.
O JAR anterior foi comparado ao checksum do serviço hospedado. A nova área
respondeu 200 para a tela, 303 para iniciar login, 401 sem sessão e rejeitou
POST sem origem válida. Isso não usa nem comprova login real no GitHub.
Os contêineres e arquivos temporários próprios do ensaio foram removidos.

O GitHub App real e a publicação continuam pendentes. Não houve mudança na
VPS nesta implementação local. A API hospedada respondeu UP novamente.

## Ativação inicial

1. Registrar o GitHub App e instalar somente no repositório acadêmico. Validar
   login do mantenedor e de um colaborador com escrita; conta sem escrita deve
   receber recusa. Entradas de credenciais ficam privadas, fora do chat e do Git.
2. Congelar revisão, conferir CI e guardar o plano com SHA e digest produzidos.
   Provar migração V39 e compatibilidade da versão anterior em banco restaurável.
3. Preparar Actions Environment `homologacao`, somente `main`, sem aprovação
   recorrente; variável da imagem Java por digest e chave SSH dedicada.
4. Na VPS, instalar o atualizador e seu checkout Git; conferir host e serviço.
   Usar o mesmo `deploy.lock` em toda alteração manual da API, inclusive bootstrap.
5. Preparar a API na rede privada do canal e montar somente chave da instância,
   CA interna e segredo do GitHub App. A configuração usa `ENABLED=true` e
   `STRICT_INGRESS=true`, coorte existente e instalação GitHub fixada.
6. Preservar imagem/especificação anterior, backup e receita do receptor estático.
   Provisionar a aprovação única do SQL V39, vinculada ao SHA exato da `main`.
7. Publicar o primeiro SHA e conferir migração, tarefa, digest, checksum e HTTPS.
   Trocar o receptor para o código novo e configuração `admissionMode=API`.
   Não divulgar a área nem admitir novos contatos antes do receptor saudável.
8. Conferir os dois participantes já permitidos e a recusa de desconhecidos;
   executar liberação/consulta por login real. Só então registrar a área ativa.
9. Atualizar guias e issues com versão instalada e limites; testar o fluxo
   automático por um push real revisado. Saúde HTTP não substitui jornada.

Se o bootstrap falhar, retornar primeiro o receptor ao código/configuração
estáticos e desativar a área antes de restaurar a API anterior. O retorno de
imagem não remove a V39. Nenhum volume, conta do bot ou fluxo de outro projeto
é removido. A compatibilidade de schema precisa ser comprovada antes da troca.

## Referências

- [Operação da equipe](../../desenvolvimento/area-da-equipe.md).
- [Contrato HTTP da área](../../../src/backend/contracts/whatsapp-team-web.md).
- [Instalação do CD](../../../deploy/homologacao/AUTOMATICO.md).
- Issues existentes: #89, #90, #91; o card VR-HML-010 continua em andamento.
