# Roteiro Manual — Fluxo Central de Viagem em DEV

Data de criação: 2026-05-26

Branch app: `feature/central-trip-flow-ux`
Branch backend esperada: `feature/central-trip-flow-ux`
Backend DEV: `http://127.0.0.1:8080`

Este roteiro valida o fluxo principal:

`servico recorrente -> presenca por servico -> viagens do dia -> execucao da viagem -> tracking passageiro/responsavel`

Tambem cobre os ajustes de UX/UI e contrato feitos para reduzir confusao entre empresa, motorista, passageiro, servico e viagem.

---

## Como usar este roteiro

Execute primeiro o smoke remoto do backend. Depois valide o portal/plataforma e, por fim, o app no iPhone.

Para cada caso, preencher:

- **Resultado real**: comportamento observado
- **Status**: `PASS`, `FAIL` ou `SKIP`
- **Evidencia**: print, video, log, request id ou observacao curta

Smoke executavel no backend, apos deploy DEV:

```bash
./scripts/qa_central_trip_flow_dev_smoke.sh
```

Smoke com validacao opcional de vinculo pela empresa:

```bash
RUN_ADMIN_LINK_SMOKE=1 \
VANRIDES_ADMIN_EMAIL=<email-da-empresa> \
VANRIDES_ADMIN_PASSWORD=<senha-da-empresa> \
./scripts/qa_central_trip_flow_dev_smoke.sh
```

---

## Pre-condicoes

- Stack DEV atualizada com o backend desta branch.
- App iOS instalado apontando para o backend DEV.
- Google Places configurado no backend DEV.
- Acesso a uma conta de empresa/admin no portal.
- E-mails de teste unicos para passageiro e motorista.
- Permissao de localizacao habilitada no iPhone para o app.

---

## Bloco A — Smoke remoto do backend DEV

### A-01 — Cadastro publico de passageiro com email real nao deve retornar 500

**Passos:**
1. Criar um passageiro pelo app com um email que nao use o dominio `vanrides.dev`.
2. Usar senha forte valida.

**Resultado esperado:**
- O app nao exibe `Internal Server Error`.
- O backend retorna sucesso ou estado de verificacao pendente.
- Se o envio de email falhar, a conta fica criada/pendente e o erro nao quebra o cadastro.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### A-02 — Cadastro publico de motorista com email real nao deve retornar 500

**Passos:**
1. Criar um motorista pelo app com um email que nao use o dominio `vanrides.dev`.
2. Entrar com esse motorista depois do cadastro.

**Resultado esperado:**
- O cadastro nao retorna `500`.
- O motorista nao fica vinculado automaticamente a uma empresa padrao.
- Ao logar, o app mostra uma tela simples informando que falta vinculo com empresa.
- O motorista nao ve fluxo operacional de viagem antes do vinculo.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### A-03 — Empresa vincula motorista existente pelo email

**Pre-condicao:** A-02 passou.

**Passos:**
1. Entrar como empresa/admin no portal.
2. Abrir "Motoristas".
3. Criar/vincular motorista usando o mesmo email do motorista criado no app.
4. Deixar a senha em branco.

**Resultado esperado:**
- O portal aceita vincular o motorista existente sem exigir nova senha.
- O motorista passa a aparecer na lista da empresa.
- O motorista consegue logar no app com a senha original.
- Ao logar, o app libera a experiencia operacional da empresa vinculada.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

## Bloco B — Portal da empresa

### B-01 — Tela de motoristas esta direta e sem informacao falsa

**Passos:**
1. Entrar no portal como empresa/admin.
2. Abrir "Motoristas".
3. Observar a lista e o formulario de criacao/vinculo.

**Resultado esperado:**
- A tela prioriza lista, detalhe e acao de adicionar/vincular motorista.
- Nao aparecem metricas ou cards operacionais sem dado real.
- O formulario explica que email existente sera vinculado e senha vazia e aceita nesse caso.
- Para motorista novo, o portal ainda pede senha inicial.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### B-02 — Criacao de servico recorrente usa Google Places no destino

**Passos:**
1. Abrir "Servicos recorrentes".
2. Criar um novo servico.
3. No destino final, digitar parte de um endereco real.
4. Selecionar uma sugestao do Google Places.
5. Salvar o servico.

**Resultado esperado:**
- Sugestoes aparecem enquanto digita.
- Ao selecionar uma sugestao, nome/endereco/coordenadas sao preenchidos.
- O servico salva sem erro.
- Ao reabrir o detalhe/edicao, o destino permanece preenchido.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### B-03 — Edicao de servico preserva e altera destino corretamente

**Pre-condicao:** B-02 passou.

**Passos:**
1. Editar o servico criado.
2. Confirmar que o destino anterior aparece carregado.
3. Trocar o destino usando autocomplete.
4. Salvar e reabrir o servico.

**Resultado esperado:**
- O destino antigo aparece ao entrar em edicao.
- O novo destino e salvo com dados coerentes.
- O portal nao exige recriar o servico para mudar destino.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### B-04 — Viagens do dia mostra dados operacionais reais

**Passos:**
1. Gerar ou abrir viagens do dia a partir dos servicos recorrentes.
2. Abrir "Viagens".
3. Filtrar pela data atual.
4. Abrir uma viagem.

**Resultado esperado:**
- A tela mostra contadores simples baseados em viagens reais.
- A lista exibe status, motorista, servico/rota e horario quando disponivel.
- Nao aparecem capacidade, paginacao ou metricas artificiais.
- O detalhe permite entender passageiros/paradas/status sem sobrecarga visual.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

## Bloco C — Passageiro no iPhone

### C-01 — Cadastro e login de passageiro

**Passos:**
1. Criar passageiro pelo app.
2. Confirmar que o cadastro nao retorna erro 500.
3. Fazer login.

**Resultado esperado:**
- Passageiro entra no app sem erro inesperado.
- Se houver exigencia de verificacao de email, a mensagem e clara.
- O app nao mostra funcionalidades de motorista/admin.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### C-02 — Passageiro marca presenca por servico

**Pre-condicao:** Passageiro esta associado a um servico recorrente ativo.

**Passos:**
1. Abrir home do passageiro.
2. Ir para a area de presenca/servicos.
3. Confirmar presenca em um servico disponivel.
4. Trocar para ausencia.
5. Limpar decisao, se a acao estiver disponivel.

**Resultado esperado:**
- O passageiro enxerga os servicos que pode usar.
- A decisao e por servico, nao por uma rota generica confusa.
- Confirmar/ausentar/limpar atualiza a tela sem precisar relogar.
- Erros aparecem em linguagem simples.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### C-03 — Passageiro acompanha viagem do dia

**Pre-condicao:** Existe viagem do dia gerada para o servico e o motorista iniciou a execucao.

**Passos:**
1. Abrir home do passageiro.
2. Acessar acompanhamento/tracking da viagem.

**Resultado esperado:**
- O passageiro entende status da viagem.
- Quando houver localizacao, o mapa/tracking carrega sem travar.
- A informacao apresentada transmite seguranca sem expor controles de operacao.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

## Bloco D — Motorista no iPhone

### D-01 — Motorista sem empresa nao entra em operacao

**Passos:**
1. Criar motorista pelo app.
2. Fazer login antes de vincular na empresa.

**Resultado esperado:**
- O app mostra estado de aguardando vinculo com empresa.
- Nao mostra lista de viagens, iniciar viagem ou controles operacionais.
- A mensagem explica o proximo passo sem assustar o usuario.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### D-02 — Motorista vinculado ve viagens da empresa

**Pre-condicao:** A-03 passou.

**Passos:**
1. Logar no app como motorista vinculado.
2. Abrir home do motorista.
3. Conferir viagens disponiveis.

**Resultado esperado:**
- O motorista ve apenas viagens/operacoes da empresa vinculada.
- A tela e direta: proxima viagem, status e acao principal.
- Nao ha ambiguidade de empresa padrao.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### D-03 — Motorista executa viagem do dia

**Pre-condicao:** Existe viagem do dia com passageiros/paradas e motorista vinculado.

**Passos:**
1. Abrir viagem.
2. Iniciar viagem.
3. Validar lista de paradas/passageiros.
4. Registrar presenca/embarque quando aplicavel.
5. Finalizar viagem.

**Resultado esperado:**
- A viagem inicia sem erro.
- O motorista tem baixa carga cognitiva: proxima acao clara e poucas informacoes simultaneas.
- Passageiros/paradas refletem o snapshot operacional do dia.
- Finalizacao atualiza status para concluida.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

## Bloco E — Responsavel/familia

### E-01 — Responsavel ve presenca do passageiro por servico

**Pre-condicao:** Responsavel esta vinculado a um passageiro com servico recorrente ativo.

**Passos:**
1. Logar como responsavel.
2. Abrir area de presencas.
3. Confirmar/alterar decisao quando permitido.

**Resultado esperado:**
- Responsavel enxerga o servico certo do passageiro.
- A decisao nao mistura rotas antigas com viagens do dia.
- A interface e clara sobre o que vale para hoje/servico.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

### E-02 — Responsavel acompanha viagem em execucao

**Pre-condicao:** Motorista iniciou viagem com passageiro vinculado ao responsavel.

**Passos:**
1. Abrir tracking/acompanhamento no app do responsavel.
2. Observar status e mapa.

**Resultado esperado:**
- Responsavel ve status compreensivel da viagem.
- O app transmite confianca sem excesso de detalhes operacionais.
- Nao aparecem controles de motorista/admin.

**Resultado real:** _______________
**Status:** [ ] PASS  [ ] FAIL  [ ] SKIP
**Evidencia:** _______________

---

## Bloco F — Criterios de aceite para considerar DEV validado

Marcar DEV como validado somente se:

- [ ] Cadastro publico de passageiro nao retorna `500`.
- [ ] Cadastro publico de motorista nao retorna `500`.
- [ ] Motorista publico nasce sem empresa operacional automatica.
- [ ] Motorista sem empresa nao acessa viagens.
- [ ] Empresa consegue vincular motorista existente por email sem trocar a senha dele.
- [ ] Servico recorrente cria/edita destino com Google Places.
- [ ] Passageiro consegue decidir presenca por servico.
- [ ] Responsavel consegue operar/acompanhar presenca do passageiro por servico.
- [ ] Viagens do dia aparecem com dados reais e UX simplificada.
- [ ] Motorista consegue executar viagem do dia.
- [ ] Passageiro/responsavel conseguem acompanhar viagem iniciada.

---

## Falhas que bloqueiam liberacao

- Qualquer `500` em cadastro publico de passageiro/motorista.
- Motorista cair em empresa padrao sem acao explicita da empresa.
- Motorista sem empresa conseguir operar viagem.
- Servico recorrente salvar destino sem coordenadas ou sem Places quando Places esta configurado.
- Presenca ficar vinculada a uma entidade errada ou invisivel para passageiro/responsavel.
- Viagem do dia gerada sem refletir presencas/participantes esperados.
- App travar em tracking, mapa ou inicio/fim de viagem.
