# ADR-008 — Validação remota e ambiente do piloto WhatsApp

> Registro histórico da autorização e das observações de 01/11/2026 nas
> origens. A migração acadêmica não executa nem transfere o deploy: não é uma
> nova autorização de infraestrutura ou evidência do SHA do monorepo.

- Data: 01/11/2026.
- Estado: autorizado pelo proprietário; execução e aceite registrados por card.
- Escopo histórico: VanRides nas origens privadas de coordenação, backend e app;
  VPS pessoal com endereço omitido na cópia pública. A identificação original
  permanece no acervo e é conferível pelo hash do documento no inventário.

## Contexto

O Mac interrompeu compilações e ensaios integrados por pressão de memória, mesmo
limitando a concorrência. O proprietário usa outros aplicativos durante o
desenvolvimento e pediu que permanecessem abertos. A CI remota passou no backend,
nos contratos e nos builds; faltava executar o produto candidato junto ao sensor
real do aparelho. Uma evidência de CI não substitui essa observação.

## Decisão e autorização

O proprietário autorizou o ensaio de infraestrutura preparado no card 110 e
ampliou expressamente a autorização para alterações nessa VPS necessárias a
um ambiente funcional de produção/piloto, visando testes com usuários reais.
Isso cobre preparar, publicar, corrigir e validar os componentes do MVP no
ambiente pessoal, com versões identificadas e procedimentos de recuperação.
Não pedir novamente a mesma autorização para passos técnicos desse escopo.

A cada mudança, preparar e registrar o artefato, destino, efeito nos dados,
testes e retorno antes de executá-la. Manter um card vertical ativo e as
validações de cada módulo. Não interpretar autorização como resultado de teste,
aceite do produto ou permissão de usar credenciais/dados de outros projetos.

Primeiro executar o ensaio já revisado: API candidata e PostgreSQL descartável,
sem telefone, e demonstrar encerramento e limpeza. Depois integrar a ponte do
Redmi e avançar os cards do MVP. O ambiente destinado ao piloto precisa ser
durável e separado do laboratório descartável, com backups/restauração,
segredos, observabilidade e controle de acesso preparados antes da entrada dos
participantes. O recebimento de mensagens e o percurso em aparelhos são provas
distintas dos testes automáticos.

## Alternativas consideradas

- Continuar as cargas completas no Mac: rejeitado após interrupções observadas.
- Fechar os aplicativos do proprietário: incompatível com sua instrução.
- Usar a CI como prova de GPS real: insuficiente; o runner não é o celular.
- Reutilizar diretamente o backend/banco em funcionamento: risco de mistura de
  versões e dados. Começar com serviços e dados exclusivos do MVP.
- Fonte de GPS apenas no WhatsApp/links de mapas: a coleta contínua usa o
  companion já desenvolvido; n8n e o agente organizam a conversa e os eventos.

## Limites e consequências

### Ampliação explícita da execução — 01/11/2026

O proprietário reiterou autorização para realizar todas as ações necessárias
à conclusão do MVP, com o máximo de agentes em paralelo, e pediu contato apenas
para novos gastos ou ações que dependam dele. Isso inclui a continuidade das
publicações, correções e testes nos repositórios e na VPS deste documento;
não solicitar novamente autorização para cada SHA dessa implementação. Cada
operação continua exigindo preparação concreta, identificação da versão,
verificação e recuperação. Não contratar serviços, ampliar planos pagos ou
assumir novos custos sem consultá-lo.

O runtime permite três subagentes simultâneos além do principal. Manter a
propriedade de arquivos separada e um card vertical ativo; paralelizar correção,
preparação e revisão dentro desse card. Os compiladores e ensaios pesados ficam
na CI/VPS, preservando a memória do Mac e os aplicativos abertos do proprietário.
O iPhone físico continua pausado até instrução específica de retomada.

Depois da funcionalidade validada, organizar a entrega com um ponto de entrada
central, documentação, versões e procedimentos reproduzíveis. A intenção de
centralização foi registrada; a topologia atual de repositórios independentes
é preservada durante a implementação. A organização final deve explicitar como
obter, executar e validar todos os componentes, sem perder seus históricos.

Os testes físicos do iPhone permanecem pausados por instrução vigente. Isso
não impede preparar o código e o build iOS. Maps próprio continua adiado.
DNS novo, entrada de credenciais e decisões de produto que dependam do
proprietário serão solicitados com o trabalho técnico preparado.

O número e os participantes reais do piloto precisam ser identificados antes
de ativar envios. A autorização de infraestrutura não decide esses destinatários.
Não enviar campanhas ou mensagens a contatos preexistentes por inferência.

Fontes e evidências sanitizadas ficam no Git privado. Binários do Actions têm
retenção limitada; a preservação da versão entregue será parte da publicação.
Não guardar segredos, telefone ou GPS bruto nos materiais acadêmicos. Registrar
o que foi escolhido, o motivo, alternativas, medições e limites, incluindo
tentativas que falharam. Os resultados continuam no card/evidência que os produziu.

## Referências

- [Ensaio concreto da VPS](../../docs/runbooks/whatsapp-mvp/110-laboratorio-temporario.md).
- [Evidência do card 110](../evidence/VR-WA-110-RUN-TRACKING.md).
- [Operação do programa](../OPERATING_MODEL.md).

## Primeiras observações da execução

No ensaio de 01/11, o limite inicial de PSI interrompeu duas tentativas. Uma
observação separada de 180 segundos registrou ao menos 11.260 MiB disponíveis,
com pico de PSI full avg10 de 1,44 e retorno ao normal. Houve bloqueio até no
preflight, antes de iniciar contêineres. A hipótese de indisponibilidade
persistente de memória não é sustentada por essa observação; o pico transitório
e a condição compartilhada do host precisam ser considerados. Isso não prova
a causa do pico nem autoriza relaxar a proteção do Mac.

Nas duas tentativas, o encerramento e a remoção foram confirmados, e a
comparação dos 38 serviços existentes permaneceu idêntica. Não houve GPS ou
mensagem externa. O resultado é de proteção/recuperação; não é aprovação da API.

A interpretação de PSI segue a
[documentação do kernel Linux](https://docs.kernel.org/accounting/psi.html):
as médias representam percentuais de tempo de espera, não a porcentagem de
RAM ocupada. Limiares de operação devem ter critérios registrados e testes de
interrupção, além da observação no ambiente real.

### Refinamento após observar os contêineres próprios

O ensaio `a5` acompanhou os cgroups isolados antes do encerramento. API e banco
ficaram abaixo dos limites, sem eventos de limite/OOM, reinícios ou PSI full,
enquanto o agregado do host acionava o corte. Portanto, usar apenas o PSI global
para atribuir uma falha à API não é uma conclusão sustentada. A medição também
não prova que os outros serviços não sentiram impacto.

Decidimos separar três momentos: admissão com janela global normal; execução
limitada com piso de 2 GiB disponíveis no host e observação dos cgroups próprios;
conclusão com nova janela global normal e health/SQL/hold aprovados. Os limites
de contêiner, os prazos e o rollback permanecem. Durante a execução, métricas
ausentes, cgroup substituído, OOM, eventos de limite, saída/reinício inesperados
ou pressão própria sustentada interrompem. O PSI global continua registrado.
Sem recuperação dentro do prazo, a rodada não recebe aprovação.

Essa revisão trata a atribuição e o significado da medição, em vez de aumentar
o limiar global a cada tentativa. Deve ser testada e executada antes de qualquer
afirmação de melhora. As métricas seguem a
[interface de memória do cgroup v2](https://docs.kernel.org/admin-guide/cgroup-v2.html#memory-interface-files).

### Publicação de loopback e rede do laboratório

Um probe controlado comprovou que, nesta VPS, a publicação da porta não era
efetivada com o servidor ligado somente à rede Docker `internal`. A mesma
porta respondeu ao conectar uma segunda bridge não interna. A API do ensaio
passará a usar as duas redes; o PostgreSQL permanece apenas na interna.
Somente a porta explicitamente ligada a `127.0.0.1` é publicada. A guarda deve
conferir também o binding efetivo em `NetworkSettings`, além da intenção no
`HostConfig`, e rejeitar o banco na rede de acesso.

Trade-off: a segunda rede permite saída de rede da API. O ensaio continua com
coorte sintética, integrações externas desativadas, imagens fixadas e recursos
exclusivos; não altera firewall nem redes dos serviços atuais. O bind local
segue a [documentação de publicação de portas](https://docs.docker.com/engine/network/port-publishing/).
Esta decisão só recebe prova funcional após novo smoke Spring/PostgreSQL;
o probe de Nginx comprova o comportamento de rede, não o MVP.
