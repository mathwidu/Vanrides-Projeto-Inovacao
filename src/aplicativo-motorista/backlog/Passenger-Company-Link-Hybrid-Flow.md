# Fluxo Hibrido de Vinculo Empresa-Passageiro no App e Portal

## Objetivo
Traduzir o novo onboarding hibrido para a experiencia mobile do passageiro e para o portal administrativo da empresa.

## Principio de UX
O usuario nao deve sentir que houve erro so porque ainda nao existe tenant operacional. O produto precisa comunicar claramente que:
- a conta ja existe
- o e-mail ja foi validado
- falta apenas concluir o vinculo corporativo

## Jornada mobile
### 1. Cadastro publico do passageiro
- remove selecao obrigatoria de motorista
- mantem endereco, telefone, senha forte e verificacao de e-mail

### 2. Primeiro login sem empresa
- tela de bloqueio operacional
- CTA principal: buscar empresa
- CTA secundario: inserir codigo

### 3. Busca e solicitacao
- pesquisa por nome da empresa
- cria solicitacao autenticada
- redireciona para estado de aguardando aprovacao

### 4. Convite por codigo
- usuario informa o codigo
- erro visual claro para codigo invalido/expirado
- sucesso com resumo da instituicao

### 5. Pos-vinculo
- se empresa vinculada e sem motorista: estado de aguardando atribuicao
- se empresa vinculada e com motorista: segue para a experiencia operacional existente

## Jornada do portal
### 1. Login admin com e-mail pendente
- tela dedicada de verificacao pendente
- reenviar e-mail
- alterar e-mail com autenticacao segura

### 2. Solicitacoes de acesso
- lista de pendentes
- aprovacao
- rejeicao
- aprovacao com motorista opcional

### 3. Convites
- lista de codigos ativos
- geracao de novo codigo
- detalhe com validade, limite e historico
- cancelar e regenerar

## Decisoes de interface
### 1. Onboarding vira uma experiencia propria
Em vez de mandar o passageiro para `UnsupportedRoleScreen`, o app passa a ter uma shell de onboarding dedicada. Isso ensina o estado do sistema ao usuario, em vez de tratá-lo como excecao.

### 2. Aprovacao e motorista sao momentos separados
Conceito:
- empresa = acesso
- motorista = operacao

Separar essas etapas reduz atrito operacional e impede que a empresa precise resolver tudo de uma vez para o passageiro entrar no ecossistema.

### 3. O portal trata convites dentro do dominio de vinculos
Isso reduz espalhamento cognitivo: aprovacao manual e codigo automatico sao duas formas de resolver o mesmo problema de vinculo.

## Impacto tecnico no frontend
- `CadastroPassageiroScreen` deixa de depender de `GET /motoristas`
- `ExperienceHomeRouter` passa a resolver onboarding do passageiro
- novo modulo de onboarding do passageiro para:
  - status
  - busca
  - solicitacao
  - convite
  - upgrade de sessao
- `features/admin` ganha estados de solicitacoes, convites e e-mail pendente

## Validacao esperada
- `flutter analyze`
- `flutter test`
- smoke manual do cadastro, login sem empresa, solicitacao, aprovacao e convite

## Rastro academico
Este documento complementa os updates do backend porque mostra a parte observavel da mudanca:
- nova jornada do passageiro
- nova jornada do administrador
- nova semantica do estado `sem empresa`
