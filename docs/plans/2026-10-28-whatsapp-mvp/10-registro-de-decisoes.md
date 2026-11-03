> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# Registro de decisões do MVP pelo WhatsApp

Consolidação retrospectiva: **30/10/2026**. Este registro explica as escolhas
documentadas nos planos e na implementação020. Não cria uma nova fila, substitui
os cards ou concede aprovação de piloto/produção. O estado operacional continua
no [programa](../../../program/README.md); os resultados ficam nas evidências e
nos runbooks da execução.

Cada entrada separa **pedido do proprietário**, **decisão de engenharia** e
**hipótese de ensaio**. Uma preferência confirmada não prova funcionamento;
uma escolha técnica implementada não significa que o proprietário escolheu cada
detalhe interno. Quando a fonte registra o recorte, mas não sua motivação
comercial, essa ausência é mantida. As datas dos planos identificam o registro
disponível, sem inventar a hora de cada decisão no chat.

## RD01 — WhatsApp, passageiro próprio e rotina recorrente

**Origem:** pedidos confirmados do proprietário nos planos de 28–30/10.
WhatsApp é a interface principal; o próprio passageiro se cadastra e responde;
o primeiro cenário usa motorista conhecido, pessoas, horários e pontos
recorrentes. Responsáveis, dependentes, avulsos, app do passageiro e grupos como
interface operacional ficam adiados. [Preferências confirmadas](README.md),
[fluxos F01–F05](01-fluxos-e-onboarding.md).

**Motivo e alternativas:** o objetivo documentado é uma rotina simples, sem
formulários repetidos nem ação por parada. O motorista aceita uma instalação
inicial e passagem pelo VanRides. Não há estudo comparativo de personas ou razão
comercial adicional registrada para excluir responsáveis nesta fase; trata-se
do recorte confirmado, não de incapacidade definitiva do produto.

**Consequência/revisão:** medir cadastro, retomada, instalação e toques por saída;
qualquer expansão para responsáveis, avulsos ou outra experiência exige novo
escopo. Não acrescentar um aplicativo obrigatório ao passageiro por conveniência
da implementação.

## RD02 — Sem empresa ou jornada legada na experiência

**Origem:** pedido do proprietário; adaptação técnica definida no plano.
Cadastro, presença e início não apresentam empresa, conta administrativa ou
criação manual de viagem. A API ainda precisa representar rotina, ocorrência e
execução datada para separar pessoas, ida/volta, respostas e avisos.
[Resultado e recorte](README.md), [arquitetura §2](02-arquitetura-e-contratos.md).

**Motivo e alternativas:** o legado inspecionado exigia vínculos empresariais e
credenciais que não atendiam a esse fluxo. O plano prefere um contexto próprio
com autorização explícita a criar empresa/senha fictícias ou tornar campos
obrigatórios nulos e presumir compatibilidade. Reaproveitar os repositórios e
infraestrutura continua sendo a orientação; regras antigas devem permanecer
compatíveis. [Baseline inspecionado](05-fontes-e-baseline.md).

**Consequência/revisão:** 040 deve materializar contratos/modelo sem esse vínculo
obrigatório. Necessidade real de empresa no produto futuro requer decisão de
produto; um obstáculo do modelo legado, sozinho, não muda a experiência pedida.

## RD03 — Silêncio mantém o habitual previsto, com estado pendente

**Origem:** pedido confirmado do proprietário em **29/10/2026**, registrado como
D03. Não responder mantém o passageiro habitual previsto e identificado como
pendente; não equivale a confirmação. “Não vou” o retira antes do corte da lista.
[D03](README.md), [F06](01-fluxos-e-onboarding.md).

**Motivo e alternativas:** a decisão preserva explicitamente a participação
habitual; excluir por silêncio ou converter silêncio em “vou” contrariaria a
regra escolhida. Horário da pergunta, uma pergunta por ocorrência e tratamento
de respostas ambíguas são padrões de construção, não novas respostas atribuídas
ao proprietário.

**Consequência/revisão:** pendentes permanecem elegíveis para aviso quando as
outras condições forem válidas. Testar data/sentido e disputa com o início.
Alterar a regra de silêncio exige decisão explícita; o card100 implementa a
política já definida, não reabre a pergunta de preferência.

## RD04 — Ida/volta, vínculo e snapshot explícitos

**Origem:** padrões de engenharia D02/D04 e contratos propostos. Um motorista
ativo por passageiro, um aparelho ativo por motorista, sentidos configurados
separadamente e sequência aprovada. Ativação congela participantes, pontos,
ordem e política; correções tardias não mudam silenciosamente o percurso ativo.
[D02/D04](06-fechamento-e-prontidao.md), [F05–F07](01-fluxos-e-onboarding.md).

**Motivo e alternativas:** evitar ambiguidade de dono, sobreposição de saídas e
uma ida invertida automaticamente sem aprovação. O corte transacional dá
resultado determinístico a presença/ponto concorrentes ao início. Cancelamento
tardio pode impedir novos avisos sem prometer desvio imediato da van.
[Estado e concorrência](02-arquitetura-e-contratos.md).

**Consequência/revisão:** testar unicidade e corrida em banco real na integração.
Múltiplos vínculos/aparelhos e mudança dinâmica de rota são ampliações a avaliar
quando houver necessidade comprovada; não estão confirmados pelo proprietário
como capacidade da primeira entrega.

## RD05 — GPS próprio, independente de Maps e WhatsApp

**Origem:** decisão técnica adotada no plano executável; o requisito explícito
é automação sem envio manual por parada, em Android e iPhone. Flutter oferece a
interface; serviços nativos coletam e enviam por HTTPS. Maps/Waze são navegação
opcional. Links comuns de mapa e localização compartilhada pelo WhatsApp não
tinham um contrato de telemetria contínua validado para esse requisito.
[Escolha e comparação](06-fechamento-e-prontidao.md),
[fonte de GPS](07-plano-executavel.md).

**Motivo e alternativas:** Navigation Connect foi considerado inicialmente para
reduzir código de captura. A comparação registrada favoreceu independência da
navegação e da sequência de múltiplas paradas, em troca de mais trabalho nativo
com permissões, energia e recuperação. Os pré-requisitos Google e limitações
de plataforma descritos naquela pesquisa são uma fotografia da comparação,
não uma verificação nova de disponibilidade neste registro.
[Fontes da comparação](05-fontes-e-baseline.md).

**Consequência/revisão:** 030 precisa provar o coletor nos dois sistemas. Se
reprovar, diagnosticar e comparar correção, outro componente ou Navigation
Connect com ensaio próprio. Não construir dois rastreadores simultâneos nem
adotar scraping, página web sempre aberta ou envio manual sem nova decisão.

## RD06 — O ciclo do sensor pertence ao nativo

**Origem:** decisão de implementação020. O serviço Android e Core Location iOS
mantêm coleta/parada; Flutter observa o estado. Sair do widget não encerra o
stream. [Contrato do spike](../../../integrations/whatsapp/gps-spike/CONTRACT.md),
[resultado020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md).

**Motivo e alternativas:** o stream legado inspecionado estava ligado à tela e
ao contexto empresarial. Timer somente em Dart ou uma declaração de background
não provariam execução com UI suspensa. Bridges nativas aumentam o código
específico por plataforma, mantendo o contrato do produto compartilhado.
[Baseline](05-fontes-e-baseline.md), [coleta nativa](08-contratos-configuracao-e-workflows.md).

**Consequência/revisão:** testes automatizados e integração em emulação passaram
no escopo020. Isso não prova sensor físico, bateria, tela bloqueada ou expiração
offline. Force-stop/reboot não têm promessa de continuidade: reabrir exige
reconciliação e início autorizado, sem reviver sessão terminal.

## RD07 — Instalação direta e Personal Team para o teste do proprietário

**Origem:** aparelhos disponíveis e Apple ID sem programa pago foram informados
pelo proprietário; APK assinado e Xcode/Personal Team são a solução técnica
de distribuição dessa etapa. Play/TestFlight não são pré-requisitos desse
aceite. [Recursos confirmados](07-plano-executavel.md),
[builds e instalação](../../runbooks/whatsapp-mvp/010-builds-e-instalacao.md).

**Motivo e alternativas:** permitir teste assistido com os recursos disponíveis,
sem pressupor contratação ou publicação em loja. A troca é renovação do
provisionamento gratuito e verificação das capabilities/links em cada build.
O fallback HTTPS com botão/custom scheme pode exigir mais de um toque; primeiro
pareamento confiável e abertura diária são problemas distintos.

**Consequência/revisão:** registrar validade e renovação reais, sem publicar
perfil privado ou identificadores do aparelho. Expansão de distribuição,
necessidade de capability indisponível ou operação além desse teste reabre a
escolha com custo/conta próprios. Instalação010 não comprova o coletor020/030.

## RD08 — Receptor Python isolado no laboratório; Spring no plano integrado

**Origem:** escopo de implementação020. Python/Gunicorn e SQLite permitem
ingestão autenticada de ensaio sem depender do cadastro completo; o Spring
permanece preservado. [Impact Analysis020](../../../program/cards/VR-WA-020-GPS-SPIKE.md),
[evidência020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md).

**Motivo e alternativas:** provar o risco central de GPS antes de construir
todo o domínio. A autenticação/empresa legadas não atendiam ao laboratório.
Implementar primeiro o contexto Spring completo atrasaria essa prova; reutilizar
contas fictícias esconderia o problema. A troca é um receptor temporário e
operação própria de ensaio, com uma instância/worker e SQLite local privado.

**Consequência/revisão:** isso não decide substituir a API do produto. O plano
040 volta ao contexto WhatsApp separado dentro do backend existente, com
PostgreSQL, autorização e migrations compatíveis. Reavaliar a transição após
030; retirar/desabilitar entradas e credenciais de prova na entrega integrada.
[Reaproveitamento e sequência](07-plano-executavel.md).

## RD09 — Identidade de ensaio não equivale a pareamento de produção

**Origem:** decisão de isolamento020 e contrato futuro de pareamento. O spike
usa `TEST_BEARER` individual, aleatório, de curta duração, provisionado por CLI
privada. Não faz alegação de device binding. A credencial fica no armazenamento
seguro nativo, nunca em URL, argumento, env de GPS, log ou fonte.
[Contrato020](../../../integrations/whatsapp/gps-spike/CONTRACT.md).

**Motivo e alternativas:** o ensaio precisa de dono/autenticação sem esperar
o cadastro e o bot. Compartilhar uma credencial entre aparelhos perderia essa
separação. O futuro vínculo por chave/PKCE, confirmação cruzada no WhatsApp e
revogação é mais completo e pertence ao app de uso real; custom scheme não
pode criar o primeiro vínculo ou substituir sua chave.
[Pareamento planejado](08-contratos-configuracao-e-workflows.md).

**Consequência/revisão:** não promover o bearer de teste a autenticação final.
060 deve comprovar o pareamento e os ataques de replay/interceptação antes do
uso integrado. Preview/GET de um link não inicia GPS nem consome autorização.

## RD10 — GPS volátil, ACK durável e lease limitado

**Origem:** decisões do contrato020. Fila móvel só em RAM, até 120 amostras ou
dois minutos; posição/progresso do receptor também em RAM. SQLite guarda
recibos/hashes/estados/eventos sanitizados, com commit antes do ACK individual.
Replay conserva corpo/identidade e retorna controle atual.
[Contrato020](../../../integrations/whatsapp/gps-spike/CONTRACT.md).

**Motivo e alternativas:** reduzir retenção de GPS sem perder integridade de
confirmação. A alternativa de fila persistida poderia recuperar mais dados
após morte do processo, mas ampliaria retenção e recuperação sensível; não foi
adotada no spike. Em troca, encerrar o processo perde amostras não confirmadas e
exige reconciliação. Reiniciar o receptor perde progresso/posição, não os recibos.

**Consequência/revisão:** lease de até cinco minutos limita autorização; relógio
monotônico, checks nativos e controle atual evitam renovação por replay. Parar
localmente vem antes de confirmar STOPPED; silêncio/expiração não são prova de
sensor parado nem conclusão da viagem. A garantia offline com tela bloqueada,
principalmente iOS, permanece teste obrigatório030. Persistir GPS futuramente
exige necessidade medida, finalidade/retenção e nova revisão, sem fallback oculto.

## RD11 — Pontos privados configurados separadamente do GPS recebido

**Origem:** correção de implementação020 necessária ao campo. A rota global
vem de `/data/route-v1.json` privado, validado e carregado uma vez. Ausência
desabilita aproximação; arquivo inválido impede startup. Não há fallback
sintético no serviço real. [Configuração privada](../../../integrations/whatsapp/gps-spike/README.md),
[prova da rota personalizada](../../../program/evidence/VR-WA-020-GPS-SPIKE.md).

**Motivo e alternativas:** uma fixture fixa no código não permitiria testar o
percurso escolhido. Configuração privada fornece os pontos sem colocá-los em
imagem/Git, env ou endpoint administrativo. A troca é reprovisionamento explícito:
parar sessões e reiniciar para mudar rota, sem hot reload durante RUNNING.

**Consequência/revisão:** pontos fixos deliberadamente configurados ficam nesse
arquivo; amostras recebidas continuam voláteis. Backup SQLite não contém rota.
Restore usa volume novo, revoga identidades restauradas e requer rota e novas
credenciais antes do campo. Isso não implementa os pins/vínculos do produto,
que serão tratados no domínio e snapshot integrados.

## RD12 — Aproximação sem ETA inventado; fim automático exige evidência

**Origem:** aproximação automática sem ação por parada é requisito do
proprietário. Aviso textual sem minutos e regras geográficas são decisões de
engenharia; limiares são hipóteses. Três fixes, tendência, sentido, frescor,
passagem e permanência no final compõem a base v1.
[D05/D06 e comparação](06-fechamento-e-prontidao.md),
[aproximação v1](08-contratos-configuracao-e-workflows.md).

**Motivo e alternativas:** distância reta, ETA estático legado ou ETA até o
destino final não fornecem previsão individual com paradas/espera. Círculo
geográfico isolado também confunde rua paralela, sentido e viaduto. A regra
inicial reduz dependência de provedor pago, com capacidade limitada de distinguir
cenários ambíguos; suprimir uma decisão incerta conta como perda de oportunidade.

**Consequência/revisão:** cobertura/antecedência, bateria, falsos positivos e
fim prematuro são avaliados030, depois130/140/180 no produto. Os critérios de
aviso útil e os raios/tempos não são SLA medido. Se falhar, corrigir/contextualizar
ou comparar provedor com custo/contrato explícitos. “Finalizar” é contingência;
torná-lo obrigatório a cada saída exige aceite do proprietário. Passagem não
prova embarque; silêncio e expiração não provam chegada.

## RD13 — API decide; n8n e modelo conduzem a conversa

**Origem:** arquitetura e contratos a implementar após a prova. API mantém
identidade, autorização, presença, snapshot, progresso e mensagens aprovadas.
n8n orquestra conversa/envio; modelo interpreta intenção sob schema e contexto
limitados. Coordenadas vêm do pin/provedor, nunca de geração de texto.
[Responsabilidades](02-arquitetura-e-contratos.md),
[principals e W01–W08](08-contratos-configuracao-e-workflows.md).

**Motivo e alternativas:** memória do chat ou execuções abertas do n8n não
substituem estado durável; texto livre não concede papel. Inbox antes de ACK,
outbox/leases e fallback determinístico para respostas inequívocas permitem
retomada sem o modelo decidir privilégios ou cada atualização GPS virar prompt.
A troca é mais contrato e reconciliação na API.

**Consequência/revisão:** o baseline de produto em 08 é `gpt-6-luna`, Responses,
raciocínio `low` e Structured Outputs; isso registra o plano, não acesso/crédito
ou agente real comprovados. HTTP Request no n8n foi escolhido para evitar depender
do suporte recente do nó de agente. Evolution precisa de contrato testado na
conexão efetiva. HTTP200 não prova recebimento; `UNKNOWN` não autoriza
retry cego. Não há promessa de exactly-once entre todos os serviços. Rever após
050/070/120, com payloads/status observados e teste de custo/qualidade permitido.

## RD14 — Escopo de publicação isolado e imagens iguais às testadas

**Origem:** decisão de preparação de release020. Branch isolada contém o spike
e dois workflows, sem levar os 330 commits históricos da coordenação. Fonte
limpa é extraída por commit; gates e artefatos têm proveniência explícita.
[Evidência020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md),
[plano externo](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md).

**Motivo e alternativas:** limitar o diff e conferir o artefato que recebe o
consentimento. PR/push fazem CI; publicação usa dispatch manual com SHA/ambiente.
O gate exporta as duas imagens, o publisher confere artifact ID/hashes/image IDs
e publica sem rebuild. Config ID local não é digest de registry. A troca é
transporte/retenção de artifacts e mais uma conferência antes de aplicar a stack.
[Receita técnica e CI](../../../integrations/whatsapp/gps-spike/deploy/README.md).

**Consequência/revisão:** fixar os digests produzidos e verificar saúde/HTTPS no
mesmo artefato. Teste amd64 emulado no Mac não comprova CI nativa nem VPS.
Environment/dispatch não são, sozinhos, prova de aprovação humana ou reviewers
ativos. Mudança de fonte/ambiente/efeitos exige reavaliar o plano autorizado.

## RD15 — Autorização externa específica de 30/10/2026

**Origem:** autorização explícita do proprietário no chat de **30/10/2026**,
informada ao executor para realizar o plano já apresentado. O alvo autorizado é
`mathwidu/vanrides-program`, fonte **`9fd20d9140291c082a1f8630e94dffbe4ee38ce6`**,
laboratório `gps-spike-isolated-lab`, VPS **`VPS_DE_TESTE`**.

**Plano exato:** [020 — publicação do laboratório GPS](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md),
documentado no commit `abbe926bd5ed63028919dfd40e03c9020dc35efb`. Este é o commit
da referência documental consultada; o SHA de fonte autorizado é o de publicação
acima. Escopo: branch/PR/CI/merge preservando a fonte, Environment, dispatch,
GHCR privado, acesso de leitura na VPS, stack nova, identidades/rota de prova,
smoke e retorno descritos no plano. DNS fica com o proprietário; a resolução
para a VPS já foi informada como confirmada antes desta consolidação.

**Motivo e alternativas:** o receptor precisa ser alcançável por rede móvel para
030; localhost/LAN não bastam. A stack separada permite essa prova preservando
backend, n8n/Evolution e proxy compartilhado. O job GitHub publica imagens;
deploy segue pelo operador/SSH existente, sem copiar a chave do Mac para Actions.
Primeiro deploy tem retorno pela retirada apenas da stack nova, mantendo volume.

**Consequência/revisão:** esta entrada registra **autorização, não execução**.
Não declara push, merge, package, deploy, TLS ou percurso concluídos. Resultados,
digests e eventuais bloqueios são registrados pelo executor no runbook/evidência.
Textos anteriores “PENDING” são a fotografia pré-aprovação; não anulam o novo
consentimento nem constituem resultado. A autorização não inclui outra release,
lojas, mudanças no legado ou na configuração global do proxy, gestão de DNS pelo
agente ou promoção de piloto/produção.

## RD16 — Retenção do laboratório e política futura são etapas distintas

**Origem:** minimização implementada no spike; períodos de retenção do produto
são baseline de construção/revisão. Sem GPS bruto, tokens, telefone, conversa
integral, serial/UDID ou perfil privado nas evidências versionadas. Material
privado fica fora de Git/artifacts públicos; logs/exports do canal também
precisam de minimização. [Contrato020](../../../integrations/whatsapp/gps-spike/CONTRACT.md),
[evidência020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md).

**Motivo e alternativas:** manter o necessário para finalidade/diagnóstico e
recuperação, em vez de histórico completo por padrão. O laboratório mantém
metadados no volume até limpeza autorizada; não se deve atribuir a ele um purge
automático de sete dias que só aparece na política planejada do produto.
[Operação do receptor](../../../integrations/whatsapp/gps-spike/README.md),
[retentiva planejada](08-contratos-configuracao-e-workflows.md).

**Consequência/revisão:** 150 fecha notice, fornecedores, papéis, retenção,
exclusão/restore e custo antes de ampliar uso. Prazos documentados e `store:false`
não comprovam retenção zero nem conformidade jurídica. Alterar configurações
globais do n8n/proxy exige avaliar os outros serviços e plano próprio.

## RD17 — Limitar memória e executar ferramentas pesadas em sequência

**Origem:** decisão operacional durante020, após pressão de memória e
interrupções locais. Gradle passou a heap2 GiB/metaspace768 MiB, dois workers,
sem paralelismo/daemon persistente; Kotlin no processo e testes Dart com
concorrência2. Build, simulador/emulador e Docker são sequenciais.
[Recuperação e continuidade](../../../program/evidence/VR-WA-020-GPS-SPIKE.md).

**Motivo e alternativas:** reduzir risco de nova interrupção e manter gates
reproduzíveis, aceitando menor paralelismo. A contribuição exata da concorrência
para a pressão é hipótese; não foi demonstrada como causa única. Encerramento
de apps/daemons ociosos teve autorização e identificação, com configurações
anteriores preservadas privadamente; não equivale a licença para fechar qualquer app.

**Consequência/revisão:** checkpoints em commits e artefatos preservados permitem
retomar. Rever limites só com medição de pressão/tempo e sem concorrer com outro
gate pesado. Essa política de recursos do Mac é separada da RAM de amostras GPS
da RD10 e não promete eliminar toda falha de ferramenta.

Na revisão mobile `2496958`/build `0.1.0+3`, uma nova interrupção levou ao
override por comando de heap 1 GiB, metaspace 512 MiB, code cache 128 MiB e um
worker. Não substituiu o arquivo global nem a configuração versionada anterior.
O build Android terminou em 40,1 s e o iOS, com `-jobs 1`, em 50,1 s; o monitor
permaneceu normal nos dois. A tentativa interrompida não foi aprovada pelo
exit0 do processo filho, pois não havia novo artefato. Conferência de versão,
assinatura e hash passou a ser explicitamente vinculada ao registro de conclusão.

## RD18 — Corrigir o workflow sem mudar a fonte autorizada das imagens

**Origem:** falha real da execução remota, seguida de autorização adicional
explícita do proprietário em 30/10. GitHub recusou o publisher antes de criar
jobs: `runner.temp` não é contexto válido no `env` do job. O diagnóstico foi
reproduzido com `actionlint 1.7.12` e confrontado com a documentação de contextos.
[Registro da falha e aprovação](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md).

**Motivo e alternativas:** mover `DOCKER_CONFIG` ao `env` dos dois steps que o
usam resolve a disponibilidade do contexto, preservando diretório e limpeza
`always()`. A correção `dc567d94b7fdb3a66e26f669030462295da6473e` altera somente
o workflow (+3/-2 linhas), foi revisada independentemente e teve continuidade
autorizada. Validação sintática anterior não havia coberto essa regra do GitHub;
não se deve tratá-la como comprovação da aceitação remota do workflow.

**Consequência/revisão:** registrar separadamente revisão do workflow e fonte das
imagens. O `source_sha` permanece **9fd20d9140291c082a1f8630e94dffbe4ee38ce6**;
a correção não substitui receptor/gateway por outra fonte. Alterações adicionais
de conteúdo ou efeito precisam de seu próprio enquadramento no consentimento.

## RD19 — Exceção explícita aos escopos mínimos da credencial GHCR

**Origem:** autorização específica do proprietário em 30/10, depois de a
primeira entrada privada ser recusada. O auxiliar exigia exatamente
`read:packages`; não houve transferência da credencial nessa tentativa. O
proprietário informou ter concedido mais permissões e aceitou seu uso.
[Registro da exceção](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md).

**Motivo e alternativas:** a opção de menor privilégio continua sendo credencial
dedicada de leitura. O auxiliar foi ajustado para aceitar escopos adicionais,
exigindo a identidade `mathwidu` e capacidade de leitura dos packages, mantendo
entrada oculta, verificação HTTPS/SSH e arquivo privado na VPS. Os escopos reais
foram depois comprovados pelo principal: identidade `mathwidu`, escopos
`delete:packages`, `repo` e `write:packages`. O auxiliar armazenou o PAT somente
na VPS, em `~/.config/vanrides-gps-spike/docker/config.json`, arquivo0600 e
diretório0700; não o gravou no Mac nem nas evidências. O uso efetivo foi limitado
a metadados/download dos packages privados. O publisher usou separadamente o
token efêmero do Actions, com limpeza confirmada.
[Resultado da credencial](../../../program/evidence/VR-WA-030-GPS-PROOF.md).

**Consequência/revisão:** a exceção aceita o risco de uma credencial mais ampla;
executar somente leituras não remove seus outros poderes. Ela não autoriza usar
essas permissões em outros repositórios/serviços. Reavaliar ao encerrar o
laboratório, rotacionar ou substituir o acesso; não generalizar a exceção como
política de mínimos privilégios para integrações futuras. Foi solicitada validade
de30dias, mas a API não informou expiração: o prazo efetivo **não foi comprovado**.

## RD20 — Mount tipado de16 MiB e validação no Swarm efetivo

**Origem:** falha da primeira implantação, reproduzida pelo principal, seguida
de autorização explícita do proprietário para a correção e nova implantação.
O CLI Swarm observado ignorava `tmpfs:` curto: o receiver terminou com255 sem
diretório temporário utilizável; o gateway também falhou na resolução do upstream.
O retorno autorizado removeu somente stack/rede próprias e preservou o volume.
[Diagnóstico e aprovação](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md),
[evidência030](../../../program/evidence/VR-WA-030-GPS-PROOF.md).

**Motivo e alternativas:** declarar `/tmp` em `volumes` como `type: tmpfs`, com
tamanho16777216 bytes (16 MiB) nos dois serviços, corrige a representação consumida pelo
Swarm sem tornar o root filesystem gravável, trocar usuário ou reconstruir as
imagens. O diagnóstico descartou permissão de `/data`: o volume tinha dono/modo
corretos; a inspeção retornou `OOMKilled=false`. A reprodução com a mesma imagem/volume falhou sem mount
e iniciou com ele. Parse YAML e `docker run --tmpfs` não bastavam: o primeiro
não prova a tradução para a API Swarm, e o segundo testa outro caminho de criação.

A correção `926345067a427cb4c85097ef75ca5e50e746c234` ficou restrita à stack e
ao runbook técnico, passou por revisão e CI e entrou pelo PR2. A fonte das imagens
permaneceu **9fd20d9140291c082a1f8630e94dffbe4ee38ce6**, com os mesmos digests.
Antes da nova implantação, serviços diagnósticos em zero réplicas confirmaram
mount ausente no template antigo e presente/tipado no corrigido. Na implantação
efetiva, o principal conferiu mount16 MiB, modo1777 e escrita temporária sob os
usuários dos dois containers, além de saúde e jornada pública.

**Consequência/revisão:** aceitar configuração de deploy exige conferir mounts
efetivos e comportamento das tasks, mantendo filesystem readonly, capabilities,
recursos e isolamento. Receiver/gateway convergiram1/1 healthy. O histórico do
gateway inclui **duas tasks iniciais encerradas por DNS antes de ficar saudável**;
isso é distinto de zero reinicializações dos containers correntes observados.
Revalidar esse contrato ao mudar Engine/Swarm, template ou runtime, sem apagar
falhas iniciais do histórico nem usar um container atual saudável como prova de
que nunca houve falha de convergência.

## RD21 — Instrumentar a falha física e confirmar a parada perto do controle

**Origem:** resultado do ensaio030 e retorno do proprietário. O iPhone manteve
frescor em15min estacionado/bloqueado; no Redmi houve recibos sem posição
válida e a oscilação também apareceu com o app aberto. Os dois estavam perto
da mesma janela. A evidência não identifica ainda a causa nem permite atribuir
restrição à MIUI. [Rodada física](../../../program/evidence/VR-WA-030-GPS-PROOF-stationary-round1.json).

**Motivo e alternativas:** expor precisão, idades por relógio civil/monotônico
e marcos de remoção já disponíveis ajuda a separar aquisição, qualidade e
transporte. Aumentar limites de idade/precisão para fazer o teste passar
prejudicaria o aviso de aproximação e não corrigiria a causa. O diagnóstico
local usa allowlist, sem coordenadas, credenciais ou IDs; a cópia exige toque.
O APK release mantém suas proteções, sem torná-lo debuggable ou abrir um
endpoint de inspeção. [Implementação e limites](../../../vanrides-app/docs/driver-mvp-development.md).

O proprietário também relatou pouca clareza após Parar nos dois aparelhos.
O servidor confirmou STOPPED; iOS registrou USER_STOP nativo e o serviço Android
foi encerrado. A interface passa a mostrar a espera e a confirmação junto ao
botão, sem dizer que parou antes de terminar a pendência. Esse ajuste não muda
captura, autorização, lease ou retry.

**Consequência/revisão:** a revisão precisa de gate, builds assinados e repetição
nos aparelhos; teste do código anterior não aprova o novo binário. A rodada
com perda de rede só conta quando a interrupção estiver comprovada no receptor.
A primeira tentativa excedeu o lease e verificou parada; a segunda não mostrou
interrupção apesar do relato humano. Nenhuma aprova recuperação em3min.
As amostras reais continuam fora dos registros. [Evidência030](../../../program/evidence/VR-WA-030-GPS-PROOF.md).

## RD22 — Isolar a aquisição Android sem afrouxar os avisos (31/10/2026)

**Origem:** a revisão03 falhou em continuidade antes e depois do reboot, mesmo
em foreground/área aberta informados. Oito recibos em cerca de585s não bastam
para avisos úteis; uma janela de182,2s ficou sem novas amostras. Credencial
expirada foi renovada pelo controle existente; não era ausência de fluxo de UI.

**Escolha e motivo:** testar só `setWaitForAccurateLocation(false)` no próximo
APK Android de laboratório, sem alterar os filtros50m/20s, as renovações, o
lease ou o receptor. O [Google](https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder)
documenta uma pequena espera por precisão inicial; sua responsabilidade pelas
lacunas ainda não está demonstrada. Parecer independente recomendou a mudança
isolada por ser pequena e manter os limites de parada. São hipóteses alternativas
a disponibilidade global do GPS e a substituição frequente do pedido FLP.

**Verificação/revisão:** comparar callbacks, recibos e posição elegível durante
180s, mesma condição, com status aproximadamente a cada5s. Mais callbacks pobres
não aprovam o ensaio. Um sinal positivo exige repetição/retorno A antes de
causalidade. Se negativo, considerar pedido único limitado a uma janela menor
que sua duração nativa; não remover a renovação como solução permanente nem
usar duração infinita. Evidência independente do GNSS seria o passo seguinte,
sem continuar trocando parâmetros sem critério. Retorno de APK release usa
mesma assinatura e versionCode crescente, preservando dados. Resultados e
artefatos: [evidência de aquisição](../../../program/evidence/VR-WA-030-GPS-PROOF-android-acquisition.json).

**Execução:** B e C tiveram gates/builds assinados/instalação, mas suas janelas
de181,3s e181,4s não receberam posição elegível. O auxiliar concluiu a parada
de C pela tela e conferiu receptor/serviço. As duas alterações experimentais
foram removidas dos fontes; retornoA em build6, com assinatura preservada, tem
validação própria. Não manter C como mudança funcional nem atribuir os resultados
a defeito físico sem uma observação independente do GNSS. O risco permanece aberto.

## RD23 — Medir GNSS sem modificar novamente o coletor (31/10/2026)

**Origem:** nenhuma das duas variáveis FLP isoladas resolveu o GPS elegível.
Os callbacks imprecisos e indicadores globais ainda não separam provedor,
condição de sinal e aparelho. O build06 restaurou o código original, abriu e
permaneceu com coleta parada, preservando configuração e assinatura.

**Escolha:** preparar APK separado de instrumentação, assinado pela chave
dedicada, usando diretamente GPS_PROVIDER/GnssStatus por180s. A assinatura
compatível permite instrumentar o alvo release no Android10. A instrumentação
recria/encerra o processo, portanto requer parada prévia e é uma nova janela.
Evitar alterar o Gradle/runtime principal ou instalar aplicativo de terceiros.
Código curto, revisão independente, métricas por allowlist, cleanup/watchdog
e remoção somente do probe ao terminar. IA e fontes estão no
[plano do probe](../../../integrations/whatsapp/gps-spike/android-gnss-probe/README.md).

**Limites/revisão:** execução ainda pendente nesta decisão. Fix ruim também no
GNSS direto reforça dificuldade além do coletor, mas não prova defeito físico.
Dados de localização continuam fora de logs/evidências; o teste não aprova FLP,
background, bateria, rota ou o MVP. Resultado e necessidade de ajuda física
serão registrados depois da execução, sem presumir sucesso pela compilação.

## RD24 — Automatizar o iPhone por um executor de testes separado (31/10/2026)

**Origem:** o proprietário pediu configuração, início, parada e validação pelo
Mac, evitando copiar credenciais e responder a cada etapa do ensaio. O app
apresentara uma recusa de ativação sem motivo HTTP específico no diagnóstico.

**Escolha:** XCUITest em projeto independente, assinado pela conta Apple já
configurada, operando a UI do release existente. O executor não é dependência
do app. Fixture privada de uso único, colagem local com expiração, comandos
separados e verificação de estados pela UI; diagnóstico nativo por USB e estado
do receptor são evidências independentes. `UseDestinationArtifacts` evita
reinstalar o app durante cada teste. Não há URL scheme de testes nem mecanismo
de coleta que pule o consentimento e o fluxo normal do produto.

**Revisão e verificação:** a revisão encontrou e corrigiu mistura possível de
artefatos de rodadas e cobertura insuficiente de aborto do XCTest. O recibo de
privacidade exige identidades coincidentes e três ensaios com dados fictícios:
sucesso, aborto com código de início e aborto com credencial. Esses testes
precisam executar no aparelho antes da colagem de valores reais. Os testes de
controle local não substituem essa execução. A leitura do painel JSON pelo AX
foi abandonada após três tentativas físicas sem conteúdo acessível; observação
por labels fixos passou no quarto `inspect`. O iOS recusou escrita e limpeza do
pasteboard pelo runner em segundo plano. A versão seguinte confirmou ambos com
o runner em primeiro plano, mas o atalho Cmd+V não preencheu o campo; nenhuma
credencial real foi usada nesse experimento. Vazio isolado não prova limpeza:
o auditor agora exige acesso positivo, avanço do contador e retorno à UI.

**Limites:** o cabo continuou detectado, mas o túnel de desenvolvimento caiu;
o proprietário desbloqueou/reconectou uma vez e a comunicação voltou. Desbloqueio,
confiança inicial, movimentação física e bloqueio de tela não são equivalentes
a uma ação Home. Não prometer autonomia sobre esses atos do proprietário.
Testes com executor ativo não comprovam bateria nem continuidade depois que o
executor termina. Procedimento e resultados estão na
[evidência de automação iOS](../../../program/evidence/VR-WA-030-GPS-PROOF-ios-automation.md)
e no [laboratório](../../../integrations/whatsapp/gps-spike/ios-ui-lab/README.md).

## RD25 — Testar FLP a cada segundo antes de trocar o coletor Android (31/10/2026)

**Origem:** o probe direto de GNSS produziu 166 posições elegíveis em 180 s no
Redmi, enquanto o retorno ao coletor A06 recebeu uma posição e perdeu frescor.
Os ensaios não são simultâneos e não comprovam causa em hardware, MIUI ou FLP.
Uma diferença observável era a cadência: 1 s no probe, 5 s no app.

**Escolha:** variar somente os intervalos desejado e mínimo do pedido FLP para
1 s, preservando duração finita do pedido nativo, lease, renovação, wakelock,
parada e filtros. O GPS direto comprovou capacidade nessa janela, mas sua
adoção no produto exigiria outra análise da proteção de expiração no Android10.
O experimento D é menor e reversível; sua IA e rollback estão no módulo app.

**Evidência:** fonte `a1f071c951cfd21e00162b0375385eba195f147d`, APK0.1.0+7,
gate de 714 testes/ratchet, 10 testes nativos, build e assinatura passaram.
A janela de 181,4 s recebeu 143 amostras; os 21 polls a partir do primeiro
FRESH permaneceram FRESH. Parada pelo botão, serviço ausente e receptor STOPPED
confirmados. O primeiro recibo demorou 48,4 s e o primeiro FRESH foi observado
aos 61,5 s da sessão, acima da meta de 30 s. D permanece uma candidata para
repetição e segundo plano, não uma correção aceita. Resultados completos ficam
na [evidência de aquisição](../../../program/evidence/VR-WA-030-GPS-PROOF-android-acquisition.json).

**Repetições executadas:** D manteve54/54 consultasFRESH por901,6s após Home,
com898 novos recibos e idade máxima observada1,3s. O início dessa rodada teve
primeiro recibo10,8s. A expiração offline também foi observada: pedido de parada
3ms após deadline monotônico e conclusão nativa52ms depois, último callback
atrasado descartado. Rede restaurada ao estado anterior, receptorSTOPPED e
nenhuma retomada automática. USB/estacionado não comprovam bateria, percurso
ou tela bloqueada. Não apagar o resultado inicial acima por causa das repetições.

**Gatilho de revisão:** repetir continuidade, início, expiração e energia no
mesmo binário. Aumento de tráfego/energia é risco mensurável dessa cadência;
não mudar o critério de qualidade para converter a primeira rodada em PASS.

## RD26 — Pausar iPhone físico e avançar desenvolvimento Android (31/10/2026)

**Origem:** pedido explícito do proprietário para pausar os testes no iPhone
físico, avançar no Android e deixar a retomada iOS preparada.

**Decisão:** executar os ensaios Android autônomos ainda possíveis no030,
registrar a pausa iOS e manter sua preparação no Mac. Quando restar participação
física,030 fica em espera e040 pode avançar contra020/fixtures. A
[ADR-007](../../../program/decisions/ADR-007-whatsapp-android-first-development.md)
registra a mudança de precedência, um único WIP, riscos de retrabalho e retorno.
030 passa a ser dependência explícita de160/180; M1 bilateral e HOLDs continuam.

**Evidência disponível:** recuperação D07 em14s na mesma sessão, expiração
nativa offline, force-stop/replay e revogação de permissão com reconciliação.
Primeiro sinal continua variável e acima30s em parte das rodadas. No iPhone,
colagem/limpeza com dados fictícios passou, mas diagnósticos XCTest expuseram
marcadores: recibo bloqueado, sem uso de credencial real pelo runner. Esses
resultados permanecem separados da aprovação de produto e da autorização de
publicação já limitada ao laboratório.

**Revisão:** retomar provas físicas quando o proprietário estiver disponível;
não converter a pausa em aceite nem dispensar bateria, campo ou integração.

## RD27 — Fundação local de identidade, pareamento e operação (31/10/2026)

**Origem e escopo:** execução autorizada do MVP, com iPhone físico pausado e030
aguardando participação, conforme RD26/ADR-007. O040 fica ativo para trabalho
local no backend; nenhuma nova publicação, mensagem real ou coleta física está
incluída nessa troca de card.

**Escolha:** schema `wa` aditivo, preservando contas/empresa e autenticação legadas.
Principal técnico não escolhe a identidade humana: comandos exigem inbox e lease
da instância autorizada, versão e recibo idempotente também por mensagem. Convite
do operador habilita o motorista; dizer “sou motorista” não concede o papel.
Contrato fechado de todas as famílias, mas só cinco comandos de fundação estão
disponíveis no040. Demais handlers devolvem indisponibilidade explícita.

**Pareamento:** DPoP/ES256, PKCE e digest assinado do corpo; chave, desafio e ambiente
do pedido são imutáveis. Nonce/JTI impedem repetição da prova; replay de negócio
tem escopo, prazo e resposta cifrada. Novo aparelho revoga o anterior; refresh
rotativo não estende os30dias originais. A integração com Keychain/Keystore e o
fluxo do app continua no060; o bearer do laboratório não é promovido silenciosamente.

**Operação:** CLI sem servidorHTTP, arquivos privados0600 e coorte sintética exata
no laboratório. Segredos gerados por CSPRNG saem apenas no arquivo privado;
resultado `PREPARED` só vira `COMMITTED` depois do commit. Reset não remove dados
reais/outro lote nem faz DROP/TRUNCATE. A operação de coortes reais e suporte deve
ser concluída antes do ensaio integrado; não marcar pessoas reais como sintéticas.

**Motivo e alternativas:** reutilizar credenciais empresariais ou confiar em
identidade/papel sugerido pelo modelo ampliaria autoridade e acoplamento. Endpoint
administrativo público não é necessário. O custo adicional do protocolo de chave
é aceito para vincular o dispositivo e impedir resgate de link interceptado.

**Verificação e revisão:** gate final1154/1154, incluindo125 PostgreSQL, e smoke
HTTP/CLI do mesmo SHA `8b5676b1868b30785497cf81a7ff1d67db1d0332` passaram.
A revisão motivou revalidação de deadlines após locks, finalização atômica e
encerramento da CLI, além de corrigir o próprio roteiro de smoke. Rodadas
interrompidas por pressão não receberam PASS. A suíte inteira passou com Docker
local2GiB/2CPUs, limites Maven/Surefire e pressão1 durante145,6s, sem dispensar
testes. Backup/restauração e resultados na [evidência040](../../../program/evidence/VR-WA-040-DOMAIN-AUTH.md).
Reabrir decisões conforme evidência do060, inventário real050 e ensaio180;
nada neste registro aprova M1 ou o MVP.

## RD28 — Transporte durável e contrato fixado da Evolution (31/10/2026)

Após040 aprovado localmente,050 avança sem teste físico de iPhone. Inventário
somente leitura confirmou Evolution2.3.7/Baileys7.0.0-rc.9 e n8n2.45.4 na VPS;
o [runbook050](../../runbooks/whatsapp-mvp/050-transporte-local.md) registra
digest/fontes e diferencia versão instalada de sessão WhatsApp validada.

Decisões: webhook autenticado e normalizado antes do ACK; fila por ordem de
recebimento no banco, um lease por identidade; associação PN/LID só com campos
explícitos, conflitos sem merge; pin extraído deterministicamente e excluído
da projeção do modelo. Resposta usa o alias exato da mensagem do comando.

O recibo de comando e sua resposta fixa são uma transação.050 libera apenas
texto da fundação; authorize-submit reserva antes de revelar destinatário e
conteúdo. Timeout após possível envio vira UNKNOWN, sem retry cego. Callbacks
são separados de mensagens humanas e reconciliados pelo ID externo/instância;
um ACK atrasado não apaga uma falha de entrega. Payloads reais, credenciais e
GPS não entram em evidências ou logs. Fixtures usam dados artificiais.

Revisão independente encontrou expiração durante espera de lock e regressão
de FAILED por ACK antigo; ambos foram corrigidos com testes proporcionais.
Uma falha de schema e uma incompatibilidade de annotation na matriz também
foram corrigidas, preservando os testes e o gate. Validação final do mesmo
SHA/JAR é registrada na evidência050; diagnóstico isolado não fecha o card.

O adapter executável local prepara W06, mas não substitui o export n8n070 nem
o teste real160. A autorização de publicação do laboratório GPS não se estende
a essa nova release. Nenhuma sessão/webhook/mensagem real foi alterada nesta
fatia. Os ensaios Android030 continuam pendentes e o iPhone permanece pausado.

## RD29 — n8n como controle e processamento privado das conversas (31/10/2026)

**Pedido:** continuar o MVP e preparar ambas as plataformas, com iPhone físico
pausado. O usuário permite tecnologias auxiliares quando necessárias; esta é
uma decisão de engenharia local, não autorização de nova publicação.

**Evidência:** a fonte n8n2.45.4, commit
`e0c02a617f5e6ede3d6c448aff41f632a782aaca`, pode persistir dados de execução
transitoriamente em erros/cancelamento no modo queue mesmo com save*none.
O CLIexecute imprime o objeto de execução. O HTTP Request4.5 não oferece
comprovação equivalente ao deadline total e limite32KiB do adapter050.
[Lifecycle](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/execution-lifecycle/execution-lifecycle-hooks.ts#L794-L814),
[CLI](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/commands/execute.ts#L126-L143),
[timeout HTTP](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.httprequest#timeout).

**Decisão:** um serviço Node privado, sem banco ou fila próprios, executa duas
operações fechadas: process-inbox e dispatch-one. A API conserva identidade,
permissão, estado, leases, recibos e decisões de domínio. n8n agenda/chama o
serviço usando credencial de controle própria e recebe somente contadores e
estados enumerados; texto, GPS, JID e tokens de lease/tentativa não entram nos
itens dos nós. Manter histórico desligado continua sendo defesa complementar.
Uma tentativa por chamada e mutex local evitam acumulação; a API mantém o
fencing entre processos. Desconexão do n8n não reinicia uma tentativa autorizada.

**Alternativas:** nó privado instalaria código no runtime compartilhado;
portar envio ao Java duplicaria o adapter050 já validado. HTTP nativo direto
não comprovou os limites exigidos. Sol investigou a fonte; parecer Astra
confirmou os conflitos e recomendou o serviço isolado. O principal integra e
executa os testes; parecer não é prova de runtime.

**Conversa:** payload vazio de convite/pareamento consome o hash da própria
inbox. RESPOND_TO_MESSAGE usa templates fixos (catálogo passa a25 comandos),
sem autoridade de alterar ator ou texto livre de saída. REQUEST_SUPPORT abre
caso e pausa; AJUDA oferece orientação. Aceite exige ACEITO após emissão do
aviso versionado; mensagens antigas/desconhecidas não criam efeito de negócio.
Texto do aviso wa-mvp-v1 nesta etapa é operacional para teste sintético, não
política final liberada para contatos reais. Aviso/retention e capacidade de
atendimento devem ser coerentes antes de160.

**Limites:** OpenAI permanece pendente de escolha de chave pelo proprietário;
nenhuma credencial descoberta é reutilizada. W02–W05 são esqueletos contratuais
que falham fechados; W08 inicia com health, jobs/retention em150. Importação
inativa não comprova essas funções futuras. A instalação local usada para
inspeção tem imagem amd642.45.4, executada no Macarm64; registrar essa diferença.

**Rollback/publicação:** parar apenas serviço/containers próprios e restaurar
exports, preservando filas/recibos API. O plano remoto futuro deve apresentar
imagem, rede privada, limites e segredos; sem alterar a encryption key n8n.
Nenhuma mudança na VPS decorre implicitamente desta decisão.

## O que estava comprovado antes da publicação autorizada

Fotografia da [evidência020](../../../program/evidence/VR-WA-020-GPS-SPIKE.md),
sem transportar um PASS para uma versão diferente:

| Evidência disponível | O que permite concluir | O que não comprova |
|---|---|---|
| Gates app/nativos e 44 testes de receptor, incluindo rota personalizada | Implementação/contratos e regressões locais passaram no escopo declarado | GPS físico, energia, MIUI, tela bloqueada ou estabilidade operacional |
| Integração Android emulada e iOS simulada, com falhas de STOP | Transporte e ciclo de parada funcionaram nos cenários automatizados | Expiração offline bloqueada em aparelho físico ou execução arbitrária de timer iOS |
| Imagens amd64 locais, gateway e restore em volume novo | Artefato local e recuperação ensaiados, com recibos preservados e credenciais revogadas | Digest GHCR, CI GitHub nativa, convergência/TLS na VPS |
| Preferências confirmadas, plano e autorização do chat | Recorte escolhido e permissão para executar o plano exato | Aceite de campo, agente WhatsApp implementado ou piloto/produção liberados |

## Execução posterior informada pelo principal em 30/10

Estes fatos foram comunicados pelo executor principal durante a consolidação.
Esta frente fez documentação local; resultados posteriores e estado corrente
continuam no [runbook020](../../runbooks/whatsapp-mvp/020-publicacao-laboratorio.md)
e na [evidência030](../../../program/evidence/VR-WA-030-GPS-PROOF.md), atualizada
pelo principal. A autorização da RD15 permanece um evento distinto. As ações e
medições abaixo foram comprovadas/reportadas por ele; esta frente não executou
publicação, inspeção remota ou teste físico.

| Fato registrado | Fonte e limite |
|---|---|
| CI da fonte9fd aprovada em push e PR | [Push41132562024](https://github.com/mathwidu/vanrides-program/actions/runs/41132562024) e [PR41132600631](https://github.com/mathwidu/vanrides-program/actions/runs/41132600631); não significam publisher ou deploy concluídos |
| Publisher recusado antes de jobs | [Execução41132555457](https://github.com/mathwidu/vanrides-program/actions/runs/41132555457); motivou RD18, não foi falha de GPS nem teste de publicação bem-sucedido |
| CI da correção de workflow aprovada | [Push41133098717](https://github.com/mathwidu/vanrides-program/actions/runs/41133098717) e [PR41133105050](https://github.com/mathwidu/vanrides-program/actions/runs/41133105050); fonte das imagens preservada em9fd |
| PR1 integrado | [PR1](https://github.com/mathwidu/vanrides-program/pull/1), merge `338aeb7ccc8ad7f71b8ebf6f7cc8f68041102c9c`, conforme confirmação do principal; não altera sozinho serviços na VPS |
| Publisher concluído | [41133694515](https://github.com/mathwidu/vanrides-program/actions/runs/41133694515) PASS no gate e publisher, com fonte das imagens9fd20d e workflow338aeb7; packages privados e integridade conferidos pelo principal |
| Credencial validada e configurada após a exceção | Primeira entrada foi interrompida sem transferência; a seguinte comprovou identidade/escopos e armazenamento somente no config privado da VPS. Metadados/download usados; validade30dias não comprovada, conforme RD19 |
| Primeira stack e retorno | Falta do mount temporário no Swarm impediu startup; stack/rede próprias removidas e volume preservado. Diagnóstico/decisão na RD20; nenhum serviço legado atualizado |
| CI da correção Swarm aprovada | [Push41135891803](https://github.com/mathwidu/vanrides-program/actions/runs/41135891803) e [PR41135898500](https://github.com/mathwidu/vanrides-program/actions/runs/41135898500) PASS; sem alterar fonte das imagens9fd20d |
| PR2 integrado | [PR2](https://github.com/mathwidu/vanrides-program/pull/2), merge `a921438d382ea02d0b6b734b5579fdbe86f530ca`; correção tipada9263450 aplicada com os digests já publicados |
| Redeploy convergido | Receiver e gateway1/1 healthy; `/tmp` efetivo16 MiB, modo1777 e escrita conferidos nos dois. Gateway teve duas tasks iniciais encerradas por DNS; containers correntes observados com zero reinicializações |
| Jornada HTTPS sintética aprovada | Certificado validado, 401 sem bearer; start/CONTINUE, accepted1, replay duplicate1, STOPPED/STOP sem reabertura e revogação seguida de403. Prova de transporte/controle público, com dados fictícios; não prova de percurso |
| Builds móveis0.1.0+2 instalados e abertos nos dois sistemas | Principal comprovou instalação/launch; proprietário confirmou ambas as telas abertas e estáveis. Abertura não certifica sensor, segundo plano, bloqueio ou bateria |
| Primeira recepção de GPS real no iPhone | Principal informou sessãoRUNNING, GPS FRESH e11recibos; idade1,1s na observação e primeira recepção0,1s após ativação. Não é prova de15min bloqueado, percurso, bateria, aproximação ou fim; não estende esse resultado ao GPS físico Android |

A ingestão real inicial avança a evidência do iPhone, mantendo030 em andamento.
Ainda são necessários rota privada validada, ensaios de bloqueio/segundo plano,
parada/expiração offline, percursos e bateria conforme o roteiro; a primeira
posição não encerra o gate físico nem libera piloto/produção.

## Próximas fases e por que continuam separadas

A [sequência canônica](07-plano-executavel.md) e os
[checkpoints M0–M4](04-validacao-e-operacao.md) continuam valendo:

| Etapa | Razão da ordem e condição de avanço |
|---|---|
| 020 → publicação do laboratório → 030 | Provar primeiro o risco central, sem esperar cadastro/LLM. Rede móvel, instalação dos builds corretos, rota privada, observador e testes físicos dos dois sistemas são necessários |
| 030 | Medir segundo plano, bloqueio, rede, parada/expiração offline, bateria, aproximação e destino. Critérios/rodadas de calibração e avaliação ficam no [roteiro](09-roteiro-de-validacao.md); falha relevante pode resultar em no-go |
| 040 → 050 → 070 → 080 | Domínio/autorização antes do canal, ferramentas estruturadas e rotinas; presença online do WhatsApp não é presença no transporte |
| 060 e 090–110 | Integrar pareamento/app de uso real, passageiro/ponto, presença e snapshot/telemetria sem exigir empresa. O bearer do spike não encerra essas tarefas |
| 120–140 | Entrega/reconciliação, aproximação por destinatário e fim do negócio; sink sintético e ACK técnico não são mensagem recebida ou transporte concluído |
| 150–170 | Fechar operação, retenção, orçamento, artefatos e ambiente de teste autorizado; expansão não herda automaticamente a autorização específica do laboratório |
| 180–190 | Repetir condições críticas com canal e binários finais e entregar o ciclo completo reproduzível ao proprietário, incluindo renovação iOS. Prova030 não substitui essa regressão |
| Piloto ampliado/produção | Dependem de critérios e autorização próprios; continuam separados do laboratório e da entrega controlada |

Novas decisões devem acrescentar origem, escopo, motivo, alternativas, evidência
e gatilho de revisão. Atualizar resultados nos documentos operacionais sem
transformar retroativamente hipótese em prova ou consentimento em execução.


## RD30 — Modelo limitado e configuração privada por reuso (31/10/2026)

Origem: proprietário escolheu reutilizar chave própria e configurá-la
privadamente. Nenhuma chave descoberta em outro projeto/VPS foi reutilizada;
destino local proposto aguarda confirmação. Integração local pode ser construída
com stubs; smoke real depende dessa configuração. Backend/app não mudam.

Escolha: Responses gpt-6-luna low com schema fechado de intenção/slots, sem
ferramentas, identidade, texto de saída livre ou poderes de cadastro. Regras
inequívocas passam primeiro.070 só traduz propostas válidas a respostas
informativas enumeradas, revalidadas na API. Campos de presença preservam
negação/dia/sentido, mas não executam handler futuro. Isso permite melhorar
linguagem sem conceder à classificação autoridade sobre outro ator.

Alternativa descartada nesta fatia: agente escolher endpoint/ator ou escrever
mensagens diretamente. Seria incompatível com leases, recibos, reconciliação e
segregação por passageiro. Cadastro/nome/aceite/pareamento continuam nas regras;
incorporar novos comandos exige critérios, autorização e testes dos próximos cards.

Prazo total25s preservado; modelo recebe até20s, limitado pelo saldo após
reservar10s para comando/ACK e pelo lease. Retrytransiente único divide o mesmo
orçamento. Auth/redirect/recusa/incompleto/JSON inválido não produzem comando.
Input limitado por bytes/contexto mínimo; códigos/tokens/URLs/IDNs e números
são retidos conservadoramente. Há falsos positivos e a filtragem não garante
eliminação de todaPII. store:false não é promessa de retenção zero do provedor.

Revisão independente encontrou código de pareamento embutido chegando ao
provider: caso reproduzido sem rede, filtro dos formatos reais corrigido, corpus
ampliado. Também encontrou média global mascarando intenção inequívoca incorreta;
avaliador agora exige separadamente >=95% nos inequívocos e100% de ambíguos
perigosos esclarecidos/recusados. Ambos têm regressões verificáveis.

Na conferência final do produtor, a resposta HELP só era autorizada para
comandos literais. A proposta de orientação do modelo passou a usar CONTINUE,
que já é autorizado para texto e produz a mesma orientação. AJUDA literal
continua determinístico; nenhuma regra de autorização do backend foi relaxada.
Fonte384ee102 substitui89cfa9f antes de qualquer chamada real;337 testes locais,
332 na imagem e startup isolado repetidos. Compatibilidade estática revisada;
o smoke Spring/PG anterior não é prova do worker novo.

Evidência: corpus sintético139,82 elegíveis/57 retidos; ver manifest específico
modelo070. Stubs não validam qualidade real. O build08Android foi validado em
smoke estacionadoUSB próprio; campo/bateria/iPhone ainda são gates separados.
Rever esta decisão ao implementar presença/rotinas, liberar idioma/horários
numéricos, medir qualidade/custo real ou ampliar réplicas/ambiente. Não mudar
modelo silenciosamente quando a conta não disponibilizar o escolhido.

## RD31 — Avaliação real, instruções mais precisas e exemplos independentes (31/10/2026)

O proprietário confirmou `.env.local` / `OPENAI_API_KEY`, inseriu a chave
diretamente na entrada oculta do Terminal e autorizou os testes reais. O arquivo
foi verificado como privado0600, ignorado e não versionado. A autenticação e o
acesso ao modelo foram comprovados por chamadas Responses bem-sucedidas; isso
não identifica projeto, dono, escopos adicionais ou crédito restante da conta.

A primeira avaliação completa foi reprovada:55/56 intenções inequívocas e
25/26 ambiguidades perigosas corretas. O caso com dois sentidos da mesma
presença expôs uma lacuna: a instrução falava em múltiplas intenções, sem
explicitar vários valores do mesmo campo. O pedido de cadastro por alguém já
motorista também variou. Repetições isoladas acertaram os dois casos; não há
prova de causa única nem promessa de determinismo.

Decisão: classificar o pedido expresso independentemente do papel existente;
exigir uma ocorrência e um valor por campo; esclarecer pluralidade sem escolher
ou descartar valores. Distinguir orientação geral de pedido explícito de humano.
Mantidos modelo gpt-6-luna, esforço low, schema, orçamento, prazo e autorização
da API. Os139 casos originais e seus rótulos não foram alterados. Não reduzir
o aceite nem trocar o modelo para transformar uma execução falha em PASS.

A rodada intermediária registrou três TIMEOUT de20s, sem comando. A repetição
isolada dos casos demorou1,4–2,5s e não reproduziu o problema; não atribuir esses
timeouts a rede, provedor, prompt ou Mac sem evidência adicional.

Fonte final f5377d0 passou no corpus original:54/56 intenções inequívocas
(96,4%),26/26 ambiguidades perigosas,80/82 propostas exatas, zero comando não
autorizado e zero falha de transporte nessa rodada. Permanecem duas divergências
de intenção dentro do critério>=95%, identificadas no manifest, sem ocultá-las.
Os16 exemplos adicionais da revisão independente passaram com prazo15s, sem
substituir os139 originais. Esse prazo corresponde ao teto de modelo dentro do
processamento25s com reserva10s; contexto/locks reais podem reduzir o saldo.

O revisor detectou uma corrida apenas no coletor privado de tempos: um fetch
tardio poderia registrar no caso seguinte. A captura do coletor antes do await
foi corrigida e testada com resolve/reject atrasados simulados. O suplemento real
usou o helper anterior e não teve timeout; seu hash foi preservado. Isso não
explica os timeouts anteriores nem altera o gate de política do produto.

Provas, rodadas reprovadas, tokens observados e limites estão no
[manifest real070](../../../program/evidence/VR-WA-070-AGENT-MODEL-LIVE.json).
Counters de usage não são cobrança total e excluem respostas não concluídas.
O PASS amostral não garante comportamento futuro; evolução de intenção para
ações de negócio exige autorização e testes próprios nos próximos cards.

A versão final também passou337 testes Node e332 na imagem, além de novo smoke
worker/API/PostgreSQL12/12 com backend013aaa6 e JAR próprio. O primeiro CI foi
interrompido pela guarda de memória; a segunda tentativa passou1262 testes,
incluindo203 PostgreSQL. Evidências de modelo real e integração determinística
permanecem separadas. Não foi realizado E2E OpenAI/WhatsApp, queue/cron ou deploy.
O encerramento local070 mantém W02–W05 fechados até os respectivos handlers e
SYSTEM_JOB reservado à implementação150, conforme IA anterior.

## RD32 — Cadastro e rotinas com confirmação vinculada à proposta (31/10/2026)

O fluxo080 usa um rascunho persistido por motorista na API. O worker recebe
somente o comando permitido e referências autorizadas; nomes, pin, escolhas e
resumo não entram no modelo. ADVANCE_DRIVER_SETUP interpreta a mensagem da
inbox autenticada. A rotina só é criada depois de todos os campos e da confirmação;
não se inserem valores fictícios para representar cadastro incompleto.

Cadastro e configuração podem acontecer antes do pareamento060. O motorista
continua precisando de bootstrap autorizado, nome e aceite. Isso mantém a ordem
do plano e permite preparar rotinas sem prometer GPS ou avisos já habilitados.
Não houve mudança no prompt/modelo aprovado em070.

Uma confirmação identifica ação, ator, rascunho, resumo e versões dos alvos.
O motorista responde CONFIRMAR seguido do marcador mostrado, válido por cinco
minutos e uma aplicação. SIM isolado, marcador antigo, alvo alterado, lease ou
credencial expirados não aplicam a mudança. Ajuda preserva o rascunho; suporte
pausa o fluxo e invalida a confirmação. Essa escolha evita aplicar a resposta
de um resumo anterior a uma configuração nova.

A revisão encontrou uma recusa segura para os dados, mas ruim para a conversa:
conflito de alvo ou expiração sem recibo fazia a inbox serial repetir/pausar a
mensagem e bloquear as seguintes. Decisão: rejeições de negócio esperadas antes
da mutação geram recibo e resposta de recuperação; invalida-se a proposta quando
o alvo mudou. Expiração entre contexto e comando também termina com recusa
durável. Não capturar erro depois de uma mutação parcial nem relaxar autorização:
auth/lease são revalidados e ainda provocam rollback integral quando inválidos.
O teste deve comprovar ausência de mudança e processamento da próxima mensagem.
Gap/overlap de horário de verão também são validados antes de oferecer uma
confirmação de cancelamento; não deixar uma rejeição tardia prender a inbox.

As opções humanas precisam permitir escolha informada: rotinas mostram nome,
sentido, horário e fuso; pontos mostram passageiro e descrição; convites mostram
criação, validade e usos, sem reexibir o código. Nomes vazios Unicode, controles
e formatação invisível são recusados. Listas completas que não cabem no orçamento
da mensagem ficam bloqueadas com orientação; não oferecer seleção de índice
invisível. Paginação não foi implementada nesta fatia. O limite da mensagem usa
UTF-16, igual ao adapter Node; isso inclui corretamente nomes com emoji.

Ida e volta são rotinas independentes. Pausar/retomar preserva execução iniciada;
cancelar uma data persiste uma exceção, sem gerar presença ou percurso. Os
consumidores090/100/110 devem incrementar versões e preservar cancelamentos e
snapshots. Fixtures080 não substituem esses testes de integração posteriores.

Convite de passageiro usa família e hash distintos do bootstrap de motorista.
O token precisa ser entregue uma vez ao dono; por isso sua mensagem fica cifrada
temporariamente na outbox, vinculada ao ambiente, outbox, comando e ator. O claim
continua sem segredo; somente authorize-submit revela o conteúdo após revalidar
autorização e locks. Envio, expiração e cancelamento removem o ciphertext. Hash,
validade, limite e revogação permanecem; revogar não desfaz vínculos existentes.
Não armazenar token em texto aberto nem regenerá-lo num replay. Consumo090 e
purge operacional150 continuam obrigatórios antes da entrega do MVP.

W02 passa a chamar o controle privado comum com corpo vazio. n8n não carrega
rascunho, ator, localização ou segredo da conversa. IDs dos workflows são
preservados; W03–W05 continuam fechados até seus handlers. A reutilização do
laboratório wa070-local mantém os preflights restritos e cria um runtime novo
por SHA do backend; o recibo declara card080 e hashes atuais, sem reaproveitar
um PASS anterior. Validação e resultados ficam na evidência canônica080.

## RD33 — Executar a validação dentro da memória disponível (31/10/2026)

CI, build e API/smoke rodam em sequência, sob uma guarda que interrompe somente
o grupo de processos do ensaio quando há pressão sustentada. O host Codex,
volumes e configurações pessoais são preservados. Não remover casos, dispensar
a matriz PostgreSQL nem aceitar relatórios parciais para contornar a memória.

As duas rodadas iniciais080 e o primeiro ajuste de cache foram interrompidos
e continuam registrados como FAIL. Limitar o cache de contextos Spring a oito
é permitido pela configuração do framework, mas sozinho não resolveu o problema.
O ensaio seguinte seleciona SerialGC somente no fork de testes; a JVM da API
continua intacta. Heaps, metaspace e quantidade de processos são mantidos.

Cada ajuste recebe commit separado, comparação de recursos e retorno próprio
em `vanrides-backend/docs/development.md`. Menor RSS agregado não prova uma causa
única nem substitui o nível de pressão do sistema. O resultado final, a versão
efetivamente testada e todas as tentativas ficam no manifest080. Não tratar uma
proteção que encerrou o teste como defeito funcional ou como aprovação do gate.

Cache8+SerialGC passou no SHA4e11826, mas a repetição após a correção de menu
foi interrompida em b1d8081. A margem do host variou; o novo teste não foi
identificado como causa. Ensaio502e4ad reduz somente o cache para4, com retorno
individual para8, mantendo os demais limites. Coleta separada de RSS das JVMs,
VM, compressão e swap permite comparar o comportamento; não habilitar DEBUG
do cache significa não provar seus acertos/faltas. A regra de aprovação continua
sendo o gate integral do candidato, com a guarda original ativa.

O ensaio de cache4 também foi interrompido. Foi então preparado canário da VM
Docker com1024MiB/2CPU, sem JVM, backup privado e retorno2048 documentados no
Mac-M5. PostgreSQL e SQL sintético passaram com cerca495MiB disponíveis no Linux,
sem swap/OOM/restart; não houve redução comprovada do footprint do host. Isso
permitiu um ensaio integral instrumentado, sem trocar fonte/heaps e sem aprovar
previamente o CI. A proteção original foi preservada e a observação adicionou
heap/GC e interrupção por falta de margem interna no guest. Nenhuma alteração
remota; o resultado de cada rodada permanece separado na evidência080.

### Fechamento das decisões de cadastro e memória em 31/10/2026

A jornada 080 foi aprovada localmente no backend `aec364e` com o worker
determinístico e transporte simulado. O cadastro e a criação da rotina precedem
o pareamento. O app passa ao 060 com o contrato de sessão do 040. O consumo
do convite pertence ao 090. O reinício recuperou margem no host para esta
rodada. As duas contagens de regressão foram corrigidas e a matriz permaneceu
obrigatória. CI e JAR foram testados em sequência com Docker a 2 GiB e guarda
ativa. [Execuções, limites e rollback](../../../program/evidence/VR-WA-080-DRIVER-ROUTINES.md).

## RD34 — Vínculo do celular, recuperação de rede e ensaio no Redmi (31/10/2026)

O motorista inicia o vínculo pelo ícone, revela um código de oito letras e o
envia na conversa que já usa. O aplicativo guarda a chave do aparelho no
Keystore ou Secure Enclave/Keychain. O código confirma um pedido iniciado
naquele celular. Um retorno pelo WhatsApp apenas consulta o pedido existente.
O link e a prévia da mensagem podem circular sem autorizar outro aparelho.
O atalho para conversar usa o número comercial configurado na compilação e
deixa a mensagem para envio explícito pelo motorista.

O aplicativo grava a operação pendente antes de transmiti-la. Se a resposta
de uma renovação se perder, ele pode repetir os mesmos bytes com uma nova
prova de posse da chave. A intenção de desvincular sobrevive a uma renovação
necessária e a um reinício. A tela só informa a conclusão quando recebe o
aceite do servidor. Uma falha de rede mantém a tentativa recuperável. Após
expiração definitiva, a tela oferece novo vínculo.

Para testar o Redmi, usamos um pacote Android separado, com a CA pública do
laboratório restrita à conexão local. O pacote do ensaio de GPS continua
disponível para o 030. Um socket privado permite ao teste ler o código visível
e encaminhá-lo à inbox em memória. Assim o motorista não precisa copiar o
código no chat de suporte. O worker e a API Spring participam do teste.
O envio pelo número de WhatsApp dedicado e a associação pública do domínio
ficam no 170. A prova física do iPhone será retomada com o proprietário.
O [roteiro reproduzível do laboratório](../../../integrations/whatsapp/scripts/060-pairing-lab.md)
descreve os limites e a limpeza.

O 110 usará a mesma chave DPoP para assinar amostras e renovar a autorização
de coleta com a tela bloqueada. Por isso a classe de proteção escolhida para
a chave e o journal iOS é `AfterFirstUnlockThisDeviceOnly`. O acesso fica
disponível após o primeiro desbloqueio depois de reiniciar e os itens ficam
presos ao aparelho. Um vínculo anterior precisa conservar a chave registrada
até a revogação ou expiração; a troca ocorre em novo vínculo explícito. A prova
de assinatura e envio com o iPhone bloqueado continua no ensaio físico.
[Proteção de itens para uso em segundo plano, Apple](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly).

## RD35 — Convite, ponto e aprovação do passageiro

No piloto, o passageiro fala por si e pode usar o WhatsApp durante todo o cadastro. O motorista encaminha um convite individual ou de uso limitado. A API reconhece a mensagem, identifica o motorista e só consome um uso quando o passageiro confirma a solicitação completa. Assim, abrir a conversa ou pedir ajuda deixa o uso disponível. A decisão final sobre vínculo e embarque cabe ao motorista.

O passageiro escolhe os dias de cada rotina de ida ou volta e indica um ponto fixo pelo WhatsApp. Um mesmo portão pode ser publicado pelo motorista como ponto comum. Quem o escolher ganha uma associação própria. Uma referência escrita serve para distinguir acessos. A API conserva o ponto anterior durante a revisão de uma correção. O consentimento para o cadastro e as escolhas de lembrete de presença e de aviso de aproximação são registrados separadamente.

O agente recebe a ação permitida e as referências da mensagem autenticada. O n8n chama o worker para processar a próxima entrada. A API decide quem pode aprovar, guarda o estado e escolhe os destinatários. Esse desenho permite conferir novamente a dupla, a instância e a versão ao entregar uma resposta atrasada. O motorista recebe o link do ponto para revisão. Outra pessoa que apenas conheça o convite não ganha acesso aos dados do passageiro.

A decisão sobre o uso do convite, a mudança de vínculo, as participações e a fila de mensagens precisam ser gravadas na mesma transação. Uma repetição de evento recupera o resultado anterior. Uma autorização de envio já emitida conserva o histórico mesmo se o estado da conversa mudar antes de chegar a confirmação do provedor. O [roteiro do cadastro](../../runbooks/whatsapp-mvp/090-passageiros.md) descreve a jornada e o ensaio reproduzível.

## RD36 — Como a presença entra na lista da van

Quem fica em silêncio continua previsto como pendente. Ida e volta têm listas próprias por data. O calendário considera os próximos sete dias no fuso da rotina, os dias de uso do passageiro, o vínculo, o ponto aprovado e os cancelamentos do motorista. O passageiro pode informar a presença pelo WhatsApp mesmo se tiver desativado o lembrete.

Para o piloto, adotamos uma janela de 30 minutos a partir das 20h locais da véspera, limitada pelo horário de saída. Isso permite recuperar uma interrupção curta e evita enviar lembretes atrasados ao reativar o serviço. A janela de envio é uma decisão operacional revisável com a experiência do piloto. A resposta pode chegar até o fim da data local da ocorrência. Ao mudar horário, fuso, sentido ou dias antes da saída, a confirmação positiva volta a pendente. A desistência explícita é preservada. Uma mudança só de nome ou ponto conserva a escolha.

A API guarda a pergunta e o identificador devolvido pelo canal. Se a resposta cita uma pergunta, ela precisa pertencer ao próprio passageiro, àquela instância e àquela ocorrência. Se falta identificar a data ou o sentido, o bot oferece uma seleção numerada. Somente o motorista pode consultar os nomes e os totais das suas listas. Depois do início, uma desistência pode cancelar uma parada ainda aguardada. Um pedido novo de embarque fica registrado e exige contato com o motorista. O sistema preserva a lista congelada no início e o estado de quem já embarcou.

O W04 desperta o worker uma vez por minuto. A API materializa o calendário e prepara lembretes com uma credencial de serviço da instância, separada das credenciais de conversa e entrega. Repetir a janela recupera o resultado já registrado. A autorização de envio confere novamente o ponto, o vínculo, a preferência e o prazo. O n8n só recebe os contadores necessários para operar.


## RD37 — Integração comprovada e repetição segura de mensagens (2026-11-01)

O card110 foi encerrado no escopo integrado da ADR-007. Na A8, o Redmi enviou
GPS real ao Spring e interrompeu a coleta sem conexão. Ao reabrir, confirmou a
parada remota sem reiniciar o sensor. Sete checks do aparelho e cinco da API
passaram no mesmo manifest; limpeza, quatro revogações e preservação dos 38
serviços existentes foram verificadas. A prova estacionada não substitui
percurso/bateria, iPhone físico ou mensagem real. Esses gates continuam abertos.
A falha A6 motivou a correção mínima do acionamento de envio de parada; a A7
falhou na preparação e não foi reclassificada pelo sucesso posterior.

No120, decidimos suspender novas reservas quando não houver conexão conhecida
e conferir o estado atual no worker antes do claim. Esse GET não conecta o
WhatsApp nem envia mensagem. Evento antigo não deve, sozinho, reabrir a fila.
O resultado de envio continua separado: submissão HTTP, entrega e leitura não
são equivalentes. Um timeout depois da chamada pode ter produzido efeito.

A política de retry em implementação será de três tentativas totais, incluindo
a inicial: somente quando houver prova de POST não invocado (`NOT_SUBMITTED`),
com duas repetições após 5 s/15 s e nova autorização. A menção original a 45 s não
permite uma quarta tentativa. Não reciclar `FAILED/REJECTED`, que também inclui
falhas definitivas, nem `UNKNOWN`. A revisão especializada exigiu impedir
continuação tardia de um probe expirado, preservar ciphertext apenas enquanto
necessário e revalidar TTL/fim/elegibilidade em cada tentativa. Implementação,
testes sintéticos e callback real recebem evidências separadas no120.

O proprietário indicou o WhatsApp Business do Redmi como opção de número do
bot. Metadados do Android confirmaram app/conta presentes, sem exportar número;
conexão efetiva será confirmada no pareamento. O contato que testa a conversa
precisa ser distinto e autorizado. Nenhuma instância existente é reaproveitada
por inferência, e nenhuma mensagem real foi enviada nesta decisão.


## RD38 — Aviso incerto expira sem virar autorização para reenviar (2026-11-01)

Quando o provedor pode ter recebido uma mensagem e não há confirmação suficiente,
a tentativa permanece UNKNOWN. Ao vencer o prazo, a outbox passa a EXPIRED:
isso significa que a janela de envio terminou, não que a mensagem deixou de
chegar. A API preserva a evidência e abre um caso operacional por outbox. Esse
caso é separado do atendimento solicitado por uma pessoa e não pausa sua conversa.

A reconciliação será acionada pelo W08, com SYSTEM_JOB e sem depender do estado
da Evolution ou do próximo claim. O worker pagina no máximo200 itens em18s;
n8n recebe apenas status/contagem. A API continuará autoritativa para escopo,
ordenação, deduplicação e expiração. A consulta privada do operador mostrará
metadados e existência de vínculo externo; não exibirá conteúdo da mensagem
nem tentará adivinhar entrega por semelhança de texto, telefone ou horário.

Um callback correlacionado pode melhorar a evidência da tentativa após o prazo,
sem reabrir a outbox. O primeiro resultado tardio de uma reserva abandonada pode
ser registrado; um recibo de resultado já confirmado permanece imutável. A mesma
requisição recupera o recibo original; outro corpo continua em conflito. Essas
regras evitam transformar perda de ACK em repetição da mensagem.

O código do consumidor e W08 foi implementado e revisado; os testes locais
passaram. Backend, ensaio integrado, execução n8n e callback real ainda precisam
de provas específicas. Nenhuma decisão deste registro promove piloto/produção
nem dispensa os testes físicos que permanecem pendentes.

## RD39 — Taxa de autorização e prioridade da fila (2026-11-01)

Decisão de implementação do card 120, ainda sujeita à suíte integrada: política
própria v1 com no máximo 30 autorizações em 60 segundos por ambiente/instância e
12 no mesmo intervalo por identidade destinatária daquele escopo. Esses valores
não representam um limite oficial da Evolution nem comprovam capacidade do
piloto. O W06 consulta a fila a cada dois segundos; limitar a taxa no backend
também cobre reinícios, chamadas simultâneas e outros acionadores.

O histórico persistido de tentativas serve de contador. Toda reserva consome
uma posição, inclusive quando depois houver prova de zero POST. Isso limita
também ciclos de falha e repetição. Rotação/revogação de credencial e reinício
não reiniciam a contagem. O reset autorizado de uma coorte de laboratório apaga
seu histórico; não se promete preservar a quota através desse reset. Não haverá
desativação do limite para obter um teste verde.

O claim deve pular destinatários saturados antes de aplicar o tamanho do lote.
Na autorização, a API revalida a quota sob o mesmo lock de entrega. Sem vaga,
não produz novo token/tentativa: libera o lease, mantém o prazo original e
agenda a próxima elegibilidade. Essa data é uma previsão, não uma reserva de
capacidade. Se a vaga alcançar o prazo útil, a mensagem expira. O contrato
existente RATE_LIMITED continua válido; não é necessário um novo formato de ACK.

A ordem inicial será aproximação, resposta operacional e lembrete, com FIFO
dentro de cada prioridade. O rank não libera a origem APPROACH, que permanece
dependente do card 130. Essa prioridade é estrita: demanda contínua superior
pode fazer um lembrete vencer. Não se promete atendimento eventual; a medição
de utilidade e capacidade no piloto poderá justificar ajuste explícito.

A revisão também corrigiu uma hipótese de concorrência: a autorização existente
já segura profile FOR SHARE, e os mutadores oficiais de saída usam FOR UPDATE.
Preservar o bloqueio e comprová-lo nas duas ordens, em vez de acrescentar locks
redundantes. Para o aviso de GPS confirmado, o prazo de submissão deve respeitar
a aquisição mais 20 segundos e o lease da sessão. Esse limite de frescor não é
aplicado ao aviso de indisponibilidade como se ele dependesse de GPS recente.

As revisões independentes encontraram um problema real no fechamento de imports
do laboratório e um conflito de locks ao inserir suporte operacional durante
reset. Ambos foram corrigidos antes do ensaio integrado. Também evitaram ampliar
o bloqueio de run/session a partir de uma hipótese incompleta. Testes estáticos,
suíte PostgreSQL, runtime integrado e canal real continuam provas distintas.

## RD40 — Fronteira descartável para a prova real do canal (2026-11-01)

Para concluir o card 120, preparar um filtro exclusivo do laboratório entre
Evolution e API experimental. Ele aceita somente os contatos explicitamente
definidos na configuração privada, durante uma janela curta. Todos os aliases
presentes devem ser válidos e pertencer à mesma pessoa autorizada; o webhook
não serve como prova para aprender um vínculo PN/LID. Texto humano direto,
estado da conexão e callbacks recebem projeções mínimas distintas. Mídia,
grupos, eco e mensagens de desconhecidos não chegam à API.

Motivo: a resolução atual pode criar identidade real ou associar alias antes
do processamento conversacional. Validar depois dessa etapa seria tardio;
o normalizador também ignora um alias alternativo malformado. O filtro precisa
validar os campos originais antes de construir um novo envelope. Confirmar a
persistência no backend antes de confirmar um evento admitido; descartes da
política têm resposta própria, sem inventar recibo persistido.

Alternativa considerada: guarda permanente na API. Ela reduziria componentes,
mas anteciparia uma política de admissão e um contrato de descarte ainda não
necessários à prova delimitada. O filtro descartável foi preferido após revisão
especializada. Ele não é a arquitetura final do piloto e não substitui o card170.

A API e o banco devem ficar inacessíveis diretamente pela rede compartilhada;
somente o filtro recebe os eventos. Pessoas reais não serão cadastradas como
fixtures sintéticas. O banco da rodada é exclusivo e descartável; a CLI pode
provisionar credenciais técnicas com uma coorte vazia. O envio à Evolution usa
o HTTPS já suportado, sem relaxar as restrições HTTP do worker.

Antes do pareamento, resolver logging/persistência na origem e provar que
negativos não alcançam o backend. O filtro não impede a conta WhatsApp de receber
outras conversas e não corrige logs produzidos antes dele. Registrar callbacks
sem correspondência não é entrega nem criação de pessoa. A aprovação do card
exige submissão e callback correlacionado reais no mesmo artefato avaliado.

## RD41 — Evolution própria para o laboratório de entrega (2026-11-01)

A prova real do 120 usará Evolution temporária dedicada, com a mesma imagem
observada na VPS, recursos e credenciais próprios. O serviço compartilhado
guarda metadados e habilita níveis de log inadequados para essa rodada. Alterar
seus globals/reiniciar afetaria outros projetos. A alternativa dedicada permite
limitar retenção, desligar integrações e comprovar a limpeza sem esse efeito.

O worker compartilhará somente o namespace de rede da Evolution, acessando
`http://127.0.0.1:8080` no modo de laboratório já suportado. Não alterar os
validadores para permitir HTTP arbitrário. Processos, arquivos e volumes
permanecem separados; nenhuma porta pública, rede compartilhada, Traefik ou
registro DNS é necessário para essa prova. Egress usa a rede NAT própria.

Uma rota por caminho no domínio HTTPS existente foi descartada: na ausência
do router específico, o catchall poderia receber chave/corpo da rodada.
Prioridade não protege contra ausência. A opção loopback reduz essa ambiguidade.

Compartilhar rede amplia os destinos alcançáveis pela Evolution. Por isso,
o bearer do webhook será diferente do CHANNEL da API. O primeiro só autentica
Evolution→filtro; o segundo fica no filtro/API e autentica o envelope já
validado. Credenciais do worker conservam seus próprios principals. Provar
que o bearer do webhook não autentica diretamente no backend.

Evolution e worker formam uma unidade de execução: parar o worker antes de
recriar a Evolution e vinculá-lo ao namespace atual antes de liberar envio.
Interrupção depois de invocar POST continua UNKNOWN, sem reenvio automático.
O filtro não reinicia durante a rodada: seus limites são por processo/janela,
não uma quota durável. Uma interrupção encerra essa rodada e exige preparação
de outra, sem reaproveitar os contadores como se fossem persistidos.

Verificar configuração efetiva, rede, ausência de publicação de portas,
segredos separados, consulta loopback, interrupção e limpeza antes do QR.
Desligar retenção opcional não elimina o material privado necessário à sessão.
Essa topologia atende ao laboratório 120; o ambiente durável 170 tem decisão e
gates próprios. Revisão especializada aprovou a preparação, não o runtime.

## RD42 — Corroboração privada do contato de teste (2026-11-01)

A consulta oficial `POST /chat/whatsappNumbers/{instanceName}` da Evolution
2.3.7 pode confirmar um número sem devolver LID válido. Não interpretar
`exists=true`, campo ausente ou literal `lid` como vínculo PN/LID comprovado.
Primeiro consultar a API própria no escopo do contato informado privadamente.

Quando isso não bastar, usar desafio privado de uso único, com pelo menos
128 bits e validade curta, gerado fora do webhook. O proprietário recebe esse
valor privadamente e o envia pelo WhatsApp autorizado no iPhone. Um coletor
temporário autenticado, sem encaminhamento à API/worker, aceita somente esse
texto exato, direto e sem eco/mídia. PN presente deve coincidir com o informado;
aliases conflitantes ou candidatos distintos encerram a tentativa.

O webhook oferece um candidato, não a autorização. O proprietário precisa
confirmar que enviou o desafio pela conta informada. Só depois de encerrar o
coletor e invalidar o nonce, o operador registra privadamente o vínculo e abre
uma nova janela do filtro normal com configuração imutável. Nenhuma confirmação
humana é inferida por timeout, recebimento ou execução automática do helper.

Essa confiança combina PN previamente informado, segredo entregue fora do canal
e confirmação do proprietário. Desafio errado, expirado, repetido, vazado ou
com origem duvidosa não libera o alias. Não instrumentar memória ampla da sessão
Baileys nem criar um envio fora do backend apenas para contornar a ausência de
mapeamento. O bootstrap não conta como prova de entrega do card 120 e não é uma
nova etapa do onboarding do produto. Testar negativos e zero encaminhamento
antes de usá-lo; números, nonce e aliases permanecem fora da evidência pública.

## RD43 — Supervisão estável das CLI temporárias (2026-11-01)

O bootstrap do laboratório 120 executa cinco operações Java dentro de um único
contêiner operator temporário, cujo PID1/cgroup permanece vivo entre elas. Cada
`docker exec` tem prazo de até 60 segundos e depende da janela global; o processo
ocioso tem duração limitada ao restante dessa janela e registrada antes de sua
criação. Comando e duração são reconstruídos para conferir ownership e teardown.
O JAR e os argumentos funcionais das CLI permanecem os já testados.

A escolha resolve uma corrida observada no preparo: a CLI pode terminar entre
Docker informar Running e o guard ler `/proc`. Aceitar simplesmente saída zero
e COMMITTED foi rejeitado pela revisão: a perda do processo também poderia
ocultar o último evento de memória ou PSI. Manter o cgroup vivo permite medir
após cada execução e após validar o recibo, usando o guard original sem ignorar
exceções ou simular existência de processo. Falta de medição interrompe a prova.

Cinco recibos técnicos e uma última amostra válida precedem a parada planejada;
o operator é removido antes da API. Não foi acrescentado serviço permanente,
imagem, porta ou arquivo de sincronização. A revisão independente, 36 testes
offline, CI e `readiness-a2` na VPS passaram. Evidência e limites estão no
[registro do card 120](../../../program/evidence/VR-WA-120-DELIVERY-RECOVERY.md).
Essa validação de infraestrutura não substitui entrega/callback do WhatsApp.

## RD44 — Continuidade sem número do bot (2026-11-01)

O proprietário informou que precisará disponibilizar outra linha para o bot,
mas não a possui agora. A indisponibilidade impede registro/pareamento e prova
de entrega real; não impede desenvolver módulos que usam contratos já testados.
Não foi confirmado cancelamento pela operadora, nem adquirido outro número.

**Aquisição recomendada:** linha móvel dedicada ao VanRides, sob controle do
responsável pelo projeto. Um chip pré-pago físico permite manter o WhatsApp
Business no Redmi e o WhatsApp pessoal no iPhone como passageiro de teste, sem
outro aparelho. Comprar por canal oficial/autorizado, completar a identificação
diretamente com a operadora e confirmar recebimento de SMS/ligação antes do
registro. Manter a linha ativa conforme as condições contratadas; valores,
recargas e eventual recorrência precisam ser conferidos pelo proprietário.

Uma nova linha eSIM de operadora é alternativa online, condicionada à
compatibilidade do aparelho e à oferta com número/SMS/voz. A Claro documenta
compra online de eSIM pré-pago. Isso não se confunde com número VoIP ou serviço
temporário de recebimento de SMS: a documentação do WhatsApp exclui VoIP e
exige número próprio e acesso ao mecanismo de verificação. Não compramos nem
ativamos serviço nesta decisão; pagamento/identificação permanecem humanos.

A Meta oferece números de teste gratuitos na plataforma oficial. É alternativa
para uma integração de laboratório desse canal, mas não substitui a prova do
pareamento Baileys usada atualmente. Sua adoção exigiria configurar e validar
outro provedor/caminho. Escolha atual: preservar Evolution/Baileys e preparar
os módulos independentes, sem migrar apenas pela indisponibilidade temporária.

**Replanejamento:** 120 fica waiting_human, mantendo todos os aceites reais.
130 continua analyzed, com dependências técnicas 050/110 concluídas e baseline
de transporte backend `e25ed4e0131bc253b7ccea2fa4ab793e69a89af9`/coordenação
`9912a40a4e06a583baef9d598c1005bb5437bdf4`, já exercitada por gates e smoke.
O DoR de 130 revalidará contrato, escopo, fontes, testes e dirty paths antes de
qualquer implementação. A evidência 120 é precondição, nunca aceite do 130.
160 passa a exigir diretamente o fechamento do 120, preservando a barreira
para candidato integrado, 170, 180 e entrega. É o mesmo princípio de separação
usado na ADR-007, sem waiver de prova real.

Ordem do trabalho independente: preparar DoR e algoritmo/replay/pin autorizado
do 130; em seguida, conforme dependências e DoR próprios, encerramento/suporte
140 e operação/retenção/configuração 150. Podem ser preparados roteiros,
workflows e configuração inativa do ambiente final. Canal real, QR, callback,
entrega no WhatsApp e aceite do percurso continuam pendentes. O critério de
utilidade por plataforma do 130 não foi transferido ou reduzido; testes
sintéticos não o aprovam. O companion iPhone físico permanece pausado.

Revisão independente confirmou o replanejamento documental, com WIP0 e sem
marcar 130 ready. Essa delegação identificou o critério físico do 130 que poderia
ficar oculto em uma simples remoção de dependência. Contatos, credenciais e
coordenadas permanecem fora da documentação versionada.

Fontes oficiais consultadas em 01/11/2026:

- [Registro no WhatsApp](https://faq.whatsapp.com/684051319521343/?cms_platform=iphone&locale=en_US): titularidade, verificação e números não suportados.
- [Compra e ativação do chip Vivo](https://vivo.com.br/para-voce/ajuda/duvidas/celular/vivo-chip): exemplos de canais de venda e cadastro.
- [eSIM Claro](https://www.claro.com.br/celular/esim): nova linha, compra online e compatibilidade.
- [Developer Hub da Meta](https://whatsappbusiness.com/developers/developer-hub/): números de teste e sandbox da plataforma oficial.

## RD45 — Retirar a navegação externa do MVP (2026-11-02)

Por pedido do proprietário, Maps/Waze deixam de ser integração opcional desta
primeira entrega. O app mantém a coleta própria e o link de início recebido no
WhatsApp. Serviços nativos de localização, pontos e ordem necessários aos avisos
permanecem. T76/T77 saem do recorte acadêmico e ficam preservadas em histórico,
sem renumerar tarefas nem declarar conclusão. As sugestões anteriores de abrir
navegação e exigir ensaio com navegador ficam superadas para este MVP.

A [ADR-009](../../../program/decisions/ADR-009-whatsapp-mvp-without-navigation.md)
registra o alcance e a revisão futura. Não houve mudança de código ou serviço,
nem redução dos critérios de GPS, segundo plano, bateria, retomada ou percurso.
A apresentação acadêmica também passa a tons neutros e títulos centralizados,
preservando as seções e colunas do professor.

## RD46 — Retomada pelas issues e aproximação condicionada ao percurso (2026-11-03)

O proprietário confirmou o número dedicado ativo no WhatsApp Business do Redmi
e autorizou continuar a implementação pelas issues existentes do repositório
centralizado. A aquisição da linha deixou de ser impedimento; pareamento e
entrega correlacionada continuam pendentes na issue #91 e no card 120.
O card 130 reúne #33–36 e #75–77. As atribuições anteriores das issues foram
preservadas. Código implementado, revisão e validação em percurso têm registros
separados; os testes físicos do aplicativo iPhone permanecem pausados.

A consulta inclui o motorista dono e passageiros elegíveis. As coordenadas
saem apenas na autorização final do pin estático; fila atrasada exige nova
aquisição válida ou texto de indisponibilidade. O prazo de fila é de cinco
minutos para a resposta e 60 segundos para aproximação. A submissão de posição
continua limitada ao frescor de 20 segundos, sem confundir esses dois prazos.

Avisos ficam desligados por padrão e exigem uma lista explícita de rotinas com
percurso revisado para o ensaio. Essa lista não detecta pisos de viaduto nem
substitui validação geográfica. Sem contexto suficiente, o sistema suprime o
aviso e registra a razão. Os parâmetros permanecem hipóteses de calibração;
nenhuma integração paga de rotas foi introduzida.

A evidência de chegada a um ponto é progresso da operação, não uma posição
atual: uma parada normal não pode apagá-la apenas por durar mais de 20 segundos.
Continuidade, lacuna de GPS e troca de sessão são tratadas separadamente.
Pessoas no mesmo ponto físico compartilham a progressão, mesmo quando aparecem
separadas na lista; cada destinatário mantém autorização e aviso próprios.

O preparo de ensaio do monorepo recebe um perfil de evidência próprio. Recibos
históricos não são convertidos para parecer atuais. A execução manual preparada
confere a suíte completa, seis jornadas e o mesmo JAR, preservando somente o
binário e recibos permitidos. Ela não conecta WhatsApp, publica imagens ou faz
deploy. A aprovação do ensaio externo exige versão concreta e sua prova real.

## RD47 — Homologação compartilhada para os integrantes (03/11/2026)

A equipe escolheu `vanrides-inovacao-api.matheusduarte.dev.br` para testar as versões do MVP. O registro A foi criado e conferido em dois resolvedores públicos e na VPS. O endereço respondeu com o certificado padrão do Traefik antes da implantação. O DNS só reserva o nome.

As APIs anteriores de desenvolvimento e produção já estão ativas com versões próprias. A proposta é publicar a API do monorepo e seu PostgreSQL em uma stack separada, com versão reproduzível e roteiro para um colega conferir uma operação. O worker do n8n executa fluxos da plataforma. O worker do VanRides executa os comandos do atendimento e requer configuração, credenciais e uma rota privada a partir do n8n.

A primeira comprovação de transporte com o número do Redmi segue RD41. Contatos e etiquetas continuam habilitados na persistência da Evolution compartilhada. O ensaio usa Evolution própria e não altera as configurações globais de outros projetos. A janela de transporte tem até 30 minutos e dois contatos. Uma homologação permanente para toda a equipe requer seu próprio controle de participantes, validade e recuperação.

As issues #89, #90 e #91 distinguem preparação e retorno, publicação HTTPS e prova de entrega real. Os critérios de percurso, utilidade, iPhone e aceite do piloto continuam vinculados às etapas próprias. Esta decisão registra o destino e o caminho técnico. A implantação precisa de manifest, versão, revisão e ensaio dos procedimentos. Leia o [guia de homologação da equipe](../../desenvolvimento/homologacao-da-equipe.md).
