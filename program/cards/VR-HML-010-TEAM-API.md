# VR-HML-010-TEAM-API — Homologação técnica da API para o grupo

## Resultado esperado

Todos os integrantes podem usar a API implementada do MVP no mesmo endereço HTTPS, inclusive rotas de atendimento e do app do motorista. Issues existentes: [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) e [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90).

## Estado

`in_progress`. O controlador mantém um único card ativo. A decisão RD47 registra a finalidade desse ambiente.

## Ajuste de prioridade (RD48)

Publicar a API completa do MVP primeiro. O cenário de cadastro restrito abaixo é uma prova opcional da etapa anterior. A publicação usará o modo online da API com autenticação normal dos atores e acesso para toda a equipe. Worker, n8n/Evolution e APK do domínio entram após a API. A pedido do proprietário, limitar as verificações operacionais a artefato, destino, startup/HTTPS e fronteira de acesso.

## Atualização de 04/11/2026

API, PostgreSQL, APK e canal publicados em homologação. Saúde e download
reconferidos; perfil básico e quatro interações após a correção validados no
WhatsApp real. [Guia atual da equipe](../../docs/desenvolvimento/homologacao-da-equipe.md).
A prova real do canal está na issue #91; não altera retroativamente o escopo
restrito descrito neste card. Faltam motorista participante, jornada completa
e revisão/aceite do colega. O card permanece em andamento. A integração local
com main/PR #105 não substitui a API acc572f ainda instalada na VPS.
O pedido de 04/11 inclui integrar as melhorias do colega e atualizar README,
CONTRIBUTING, arquitetura, resultados e plano de entrega para orientar o time.
Esses caminhos foram acrescentados ao escopo documental deste mesmo card;
nenhuma nova funcionalidade ou promoção do piloto foi autorizada por isso.
A conferência da CI também inclui alinhar o texto esperado no ensaio
`070-worker-scenarios.mjs` ao comportamento publicado, mantendo os checks.

### Simplificação da CI autorizada em 04/11

Prioridade atual: antecipar verificações rápidas e reduzir ruído de logs,
conforme a [revisão da CI](../../docs/desenvolvimento/revisao-ci-e-testes.md).
Impacto restrito aos workflows e auxiliares de validação: o gate local completo
mantém todos os checks; na CI, a fase rápida é pré-requisito da matriz PostgreSQL
e das seis jornadas. Contratos, runtime, limites de memória, contagens, skips,
locks e proveniência permanecem inalterados. O limite de 350 linhas continua
vigente, inclusive sua reprovação já conhecida de `WaPassengerSetup.java`.

Validação desta etapa: execução dos scripts com processos simulados para provar
ordem e propagação de falhas; lint dos workflows; fixtures de sucesso/falha para
os reporters; gate rápido real. Não repetir a suíte Java ou acessar a VPS para
uma mudança apenas de orquestração e apresentação. Reverter os commits desta
etapa restaura a ordem e o formato anteriores. Sem push, merge ou deploy nesta
autorização; sem novo card vertical concorrente.

### Continuação: refatoração do cadastro e reaproveitamento do JAR

O proprietário autorizou resolver a reprovação de 362 linhas sem perder as
melhorias do colega e continuar a simplificação. `WaPassengerReview` recebe
a montagem do resumo anteriormente em `WaPassengerSetup`, com corpo e ordem
preservados. O limite de 350 linhas permanece; Setup passa a 303 linhas.
Impacto funcional esperado: nenhum. Testar Wizard/Help e integrações de
cadastro, negócio e isolamento/versionamento; preservar as provas da API instalada.

CI: cache nativo de dependências Maven e target Docker que copia o JAR já
ensaiado, conferido pelo recibo das jornadas e pelo checksum durante o build.
O build padrão por fontes permanece disponível. Testar rejeição de recibo,
revisão e binário divergentes, mais montagem Docker local sem iniciar a API.
Reversão: reverter os commits locais desta etapa; não há alteração de schema,
contrato público ou VPS. A publicação GitHub e a CI remota permanecem pendentes.

### Compartilhamento do candidato e redução de repetição

Continuação autorizada: um JAR candidato na execução principal, compartilhado
por ID imutável e checksum com integração e homologação reutilizável. A suíte
PostgreSQL roda independentemente e continua obrigatória. O resumo exige todos
os consumidores aprovados. Não procurar builds de outra execução/branch.

O teste 130 já inclui a jornada 110 completa: remover a segunda execução dessa
mesma jornada, preservando suas seis asserções e as cinco de localização.
Recibo V2 distingue cinco execuções e seis conjuntos de checks; V1 histórico
permanece estrito. Sem mudança de quota, relógio de produção ou dados de envio.
Impacto: workflows, auxiliares e recibos de laboratório. Runtime/contratos da
API permanecem. Validação: casos negativos de artefato/recibo, actionlint,
testes dos auxiliares e um ensaio JAR/PostgreSQL completo da nova sequência.
Rollback: reverter esta etapa restaura workflows e execução duplicada, sem
migração ou alteração da VPS. Não remover validações para obter aprovação.

## Impact Analysis da preparação inicial

A base local f85ec3e contém o mesmo runtime de dd12cf0, aprovado nas seis jornadas. A primeira fatia muda o perfil público do backend, o comando de teste para o colega e a receita de implantação. O domínio vanrides-inovacao-api.matheusduarte.dev.br resolve para a VPS. O serviço publicado ainda precisa de configuração, certificado válido e teste funcional. PostgreSQL e credenciais serão específicos da nova stack.

## Dependências

040 (autenticação) e 070 (contratos de conversa) concluídos. A homologação usa dados fictícios e tem aceite próprio. Os aceites de canal, percurso, iPhone, encerramento e piloto permanecem no plano do MVP.

## Escopo

Backend: perfil homolog restrito, bloqueio de e-mail, rotas permitidas e testes de isolamento. Coordenação: stack da API e banco, imagem que preserva o JAR aprovado, preparo/retorno, runner do colega e instruções. Os diretórios permitidos constam de program.json.

## Fora de escopo

Dados de passageiros reais, disparos de WhatsApp, GPS de aparelhos, serviços pagos, acesso do grupo por SSH administrativo e alterações na API antiga. A integração real da Evolution tem prova separada na issue #91.

## Contratos

Usar o perfil Spring homolog e um modo explícito da API para o time. Preservar os controles públicos e negar configuração ambígua, e-mail, cadastros legados e rotas fora da lista. No perfil restrito, permitir saúde e quatro operações de conversa. No perfil online, permitir as rotas disponíveis de WaRoutes com a autenticação original e bloquear administração/legado. Credencial nominal por integrante e instância sintética distinta. Restringir os comandos a operações sintéticas do cenário, tanto no contexto quanto na execução. Não persistir conteúdo de conversas ou tokens em evidência.

## Riscos

Uma rota legado exposta pelo proxy, uso de provedor real, vazamento entre integrantes ou troca do binário após o teste invalidam a publicação. Aplicar restrições na API e no ingresso, revisar casos negativos, conferir o digest instalado e manter o volume anterior na recuperação.

## Definition of Ready

- [x] Base e dependências conferidas. Checkout limpo antes desta abertura.
- [x] Contrato, owners por frente, testes, domínio, limites e rollback definidos.
- [x] Preparação local autorizada; publicação exige plano do SHA, artefatos e ambiente concretos.

## Critérios de aceite

- [ ] Perfil restrito passa nos testes de configuração pública, isolamento, autorização, replay e bloqueio de e-mail.
- [ ] Receita reproduzível preserva o JAR testado e testa a stack e o retorno em dados sintéticos.
- [ ] A revisão e as imagens aprovadas respondem em HTTPS na nova stack. Credencial inválida, expirada ou de outro integrante é negada.
- [ ] Um colega reproduz o cenário e a persistência do resultado e registra sua revisão.

## Validação

Testes de configuração/HTTP e de integração com PostgreSQL, gates de contratos, CI, verificação da imagem e restore em banco descartável. Depois da publicação autorizada, confirmar TLS, digest e a operação real do colega. Ver o [roteiro do ambiente](../../docs/runbooks/whatsapp-mvp/homologacao-api.md).

## Rollout

Preparar em branch própria, revisar o PR pelo grupo e testar. A promoção exige fonte e imagens fixadas, escopo de rede/secrets da nova stack, smoke e retorno aprovados. Primeira rodada com um colega e credencial temporária.

## Rollback

Revogar as credenciais da rodada e retirar a nova rota. No primeiro deploy, parar os novos serviços preservando o volume próprio. Em atualização, voltar ao digest anterior apenas se o schema for compatível; quando necessário, restaurar o banco isolado previamente ensaiado.

Validação da consolidação em `657f343` (04/11/2026): 42 verificações integradas
JAR/PostgreSQL aprovadas; cinco execuções cobrem seis conjuntos. Homologação
local e imagem Docker usam o mesmo JAR e passaram, com limpeza confirmada.
Medição e limites em `docs/desenvolvimento/revisao-ci-e-testes.md`.
Transporte de artefato e composição completa no GitHub ainda pendentes;
nenhuma publicação, alteração de VPS ou teste físico iPhone nesta etapa.

## Pilotos de preparação — 04/11/2026

Autorizados localmente após a CI aprovada em 6006b1c/merge de teste 92bd115.
PostgreSQL: opt-in nas classes DriverRoutines, PassengerWizard, PassengerFencing
e InboxTransport; separar migração inicial de limpeza de dados, sem mudar casos,
concorrência, triggers, histórico ou sementes. Demais classes mantêm reconstrução.
Operador: lote limitado na CLI NON-WEB, com autenticação, transação, auditoria e
recibo individual; parar após falha/cancelamento sem declarar sucesso parcial.
O ensaio acadêmico agrupa apenas provisionamento/limpeza. Quotas e relógio ficam.
Comparação antes/depois no mesmo Mac, cargas Java/Docker sequenciais; conferir
estado limpo, ordem inversa, casos negativos, recuperação e jornada completa.
Riscos: dados residuais e sequência/seed divergentes no banco; operação posterior
à falha/interrupção ou recibo anterior ao commit na CLI. Rollback: reverter estes
pilotos para reconstrução de fixtures e chamadas CLI individuais. Sem push,
merge, VPS, canal real ou dispositivos nesta autorização de implementação.

Pilotos implementados: 56 casos PostgreSQL aprovados em 78,130 s (antes
103,449 s), também aprovados com classes invertidas e métodos embaralhados;
dois novos casos do reset aprovados. Operador: dez operações por rodada,
média 50,172 s individual e 9,361 s em lote, 40 recibos/auditorias conferidos.
Gate rápido, 709 testes Node e 303 Python aprovados; revisão independente sem
achados pendentes. Matriz ampliada para 592 casos PostgreSQL, sem exclusões.
Fechamento em `aa4cd5e`: 1.787 testes Java, 592 PostgreSQL/77 classes, sem falhas,
erros ou skips; perfil completo em 11 min 28 s. Jornada integrada: 42 checks,
cinco execuções/seis conjuntos, em 15 min 22,130 s; recibo V2 e limpeza aprovados.
Os 207 testes rápidos e a consistência documental também passaram. Registro de
método, tempos, limites e hashes em `docs/desenvolvimento/revisao-ci-e-testes.md`.
Pilotos prontos localmente; nova CI GitHub ainda não executada. Não houve push,
merge, alteração da VPS ou teste físico. Aceite do MVP continua separado.

## Oito grupos PostgreSQL — 05/11/2026

Continuação autorizada: distribuir a matriz PostgreSQL em oito grupos isolados
na CI, executar os demais testes Java separadamente e conferir a união completa
dos resultados antes de aprovar o backend. O gate local completo permanece
disponível. Ampliar o reset por classe em dez classes compatíveis, com 97 casos,
sem mudar asserções, concorrência, migrations ou o runtime da API.

Impacto: workflow backend, seleção/agregação dos testes, dez fixtures Java e
documentação. Riscos: omissão ou duplicação na divisão, aprovação com resultado
ausente ou de outra revisão e contaminação entre cenários. Verificação: seleção
exaustiva, casos negativos do agregador, mesmos 97 testes antes/depois e em ordem
alternativa, matriz completa nos novos grupos e revisão independente. No Mac,
executar um processo Java/Docker de cada vez; o paralelismo é entre runners CI.

Baseline remoto: execução 41918379045, fonte d471624/merge b1b8baf, 1.787 testes
Java e 592 PostgreSQL em 77 classes, todos aprovados; etapa Java/PostgreSQL
23min55s, workflow 24min41s. Medições locais não serão apresentadas como tempo
do GitHub. Integração, aplicativo e publicação ficam fora desta alteração.
Rollback: reverter a divisão e os dez opt-ins restaura a execução sequencial e
a reconstrução das fixtures. Sem VPS, merge, dispositivos ou publicação nesta
etapa de implementação; não concluir o card nem liberar o piloto por esta CI.

Implementado em `d424c68`. Os mesmos 97 testes passaram antes/depois e com
ordem alternativa: soma das classes de 154,127 s para 109,184 s (-29,16%).
Os nove grupos reais passaram sequencialmente no Mac e recompuseram exatamente
246 classes/1.787 testes, incluindo 77 classes/592 PostgreSQL, sem falhas,
erros ou skips. Maior grupo: 115,438 s; rodada sequencial: 828,283 s.
Também passaram 220 testes rápidos, actionlint, sintaxe Bash, consistência e
revisão independente. Tempos remotos do novo desenho ainda pendentes; não
confundir medição local com aprovação da meta de cinco minutos da CI inteira.
Documentação: `docs/desenvolvimento/revisao-ci-e-testes.md`. Sem publicação,
VPS ou dispositivos. O card e o aceite do MVP mantêm seus estados anteriores.


## Desenvolvimento compartilhado e autonomia — 09/11/2026

O proprietário aprovou um único ambiente compartilhado até v1.0, publicação
automática da `main` após testes (demais branches validam) e autonomia equivalente
dos quatro integrantes para operações de teste do produto, sem distribuir acesso
SSH. O CD manual recorrente foi rejeitado. A instalação inicial da esteira,
credenciais dedicadas, atualização por componente e interface autenticada da
equipe ainda estão por implementar; ver a [decisão da CI](../../docs/desenvolvimento/revisao-ci-e-testes.md).

A operação inicial autorizada admitiu o motorista do colega, gerou convite e
incluiu o contato no receptor, preservando o passageiro. Recibos COMMITTED e
remoção dos jobs comprovados; receptor READY, API UP e nove serviços 1/1. Cadastro,
aceite, rotina e jornada completa dependem do próprio participante. A primeira
aplicação do receptor retornou à lista anterior após não confirmar a checagem;
a repetição foi verificada, sem alteração da API ou sessão do bot.

Merge local `a8df3c7` preserva homologação/V38 e integra main `33e6d9f`/PR #106.
Testes leves passaram; a suíte completa e publicação permanecem pendentes.
O card continua em andamento; isso não libera piloto ou produção.

### Implementação da autonomia e do CD — 09/11/2026

O novo pedido autoriza implementar a publicação automática da API pela main e
a área da equipe. Escopo: workflow/CD, interface estática, autenticação GitHub,
admissão e convites, V39, integração do receptor, testes e documentação.
A implementação local e as decisões estão no
[registro desta etapa](../../docs/runbooks/whatsapp-mvp/equipe-ci-automatico.md).
A primeira rodada focada passou 43 testes Java; o login GitHub real e a ativação
na VPS continuam pendentes. O estado permanece in_progress, sem aceite do MVP.
