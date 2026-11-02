# Fluxo Hibrido de Vinculo Empresa-Motorista no App e Portal

## Objetivo
Traduzir o novo onboarding de motorista para a experiencia mobile e para o
portal administrativo, mantendo o estilo premium/dark/liquid que estamos
consolidando no app.

## Principio de UX
O motorista nao deve sentir que a conta esta quebrada por ainda nao ter viagem.
Os estados precisam responder perguntas simples:
- ja tenho conta?
- ja confirmei meu e-mail?
- estou vinculado a uma empresa?
- tenho viagem atribuida hoje?

## Jornada mobile

### 1. Convite por link
- app abre `vanrides://driver-invite/<token>` em DEV;
- app mostra nome da empresa e contexto `Motorista`;
- se motorista nao estiver logado, conduz para cadastro/login preservando o
  token;
- se e-mail ainda nao estiver verificado, mostra verificacao de e-mail com
  contexto do convite;
- apos e-mail verificado, resgata convite e entra na empresa;
- se nao houver viagem, mostra empty state operacional dentro da empresa.

Nao mostrar `aguardando aprovacao` para motorista que veio por link valido.

### 2. Cadastro sem link
- motorista cria conta normalmente;
- confirma e-mail;
- ao fazer login sem empresa, entra em onboarding de vinculo;
- CTA principal: buscar empresa;
- apos solicitar empresa, mostra status `aguardando aprovacao`.

### 3. Solicitacao aprovada
- app atualiza sessao ou solicita upgrade;
- motorista passa para home da empresa;
- se nao houver rota/viagem, empty state diz que a empresa ainda nao atribuiu
  viagens.

## Jornada do portal

### 1. Tela Motoristas vira gestao de vinculos
Secoes:
- `Ativos`
- `Convites`
- `Solicitacoes`
- `Suspensos/removidos`

CTA principal: `Convidar motorista`.

### 2. Convite de motorista
- admin cria link;
- portal permite copiar link;
- portal pode mostrar texto pronto para WhatsApp/e-mail sem transformar e-mail
  em regra de vinculo;
- detalhe mostra status, validade, data de uso e motorista vinculado quando
  houver.

### 3. Solicitacoes
- admin ve solicitacoes iniciadas por motoristas;
- pode aprovar ou rejeitar;
- aprovacao move motorista para `Ativos`;
- rejeicao aparece para motorista como estado claro e recuperavel.

## Decisoes de interface

### 1. Link e contexto, nao formulario tecnico
Ao abrir o convite, a tela deve comunicar a empresa e a acao em linguagem
humana: `Entrar na Feevale como motorista`.

### 2. Empty state dentro da empresa nao e erro
Depois do convite aceito, o motorista ja esta dentro da empresa. Se nao houver
viagem, a tela deve dizer que nao ha viagem atribuida ainda, mantendo acesso a
conta/perfil.

### 3. Aprovacao aparece apenas no fluxo iniciado pelo motorista
`Aguardando aprovacao` so deve aparecer quando o motorista buscou a empresa e
solicitou entrada.

### 4. Teste antes da App Store
O fluxo deve ser validavel em simulador e iPhone fisico sem loja publica:
- simulador: `xcrun simctl openurl booted "vanrides://driver-invite/<token>"`;
- iPhone: build local/TestFlight instalada e link enviado por WhatsApp/e-mail.

## Impacto tecnico no frontend
- novo modulo `features/driver_onboarding`;
- deep link `DriverInviteDeepLink`;
- preservacao de token de convite durante cadastro/login/verificacao de e-mail;
- gateway para `driver-onboarding`;
- `ExperienceHomeRouter` passa a diferenciar:
  - motorista sem empresa e sem convite;
  - motorista com solicitacao pendente;
  - motorista vinculado sem viagem;
- portal admin passa de formulario de criacao para painel de vinculos.

## Validacao esperada
- `flutter analyze`
- `flutter test`
- widget tests do onboarding de motorista;
- testes de deep link;
- testes da tela admin de motoristas;
- smoke real no iOS Simulator com `xcrun simctl openurl`;
- smoke em iPhone fisico quando disponivel.

## Cards vinculados
- `0972` — DRIVER-LINK-APP-0001: deep link e onboarding mobile de motorista.
- `0973` — DRIVER-LINK-APP-0002: portal admin para convites e solicitacoes.
- `0974` — DRIVER-LINK-APP-0003: smoke visual e validacao real do fluxo.
