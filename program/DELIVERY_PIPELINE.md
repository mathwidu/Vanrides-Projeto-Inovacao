# Esteira de Entrega

## Lanes

```text
CARD
  -> INTEGRATION CANDIDATE
  -> RELEASE CANDIDATE
  -> CONTROLLED PILOT
  -> PRODUCTION
  -> OBSERVATION
```

### 1. Card

Valida uma mudança pequena nos módulos afetados do monorepo. Não usa ambiente remoto com
uma versão diferente como prova do código local.

Saída: commit local, gate do card, QA Log e evidência.

### 2. Integration Candidate

Fixa uma combinação exata de backend, app, mapas, schema e configuração. Executa
contratos e jornadas entre componentes.

Saída: manifest de integração reproduzível.

### 3. Release Candidate

Constrói artefatos uma vez e congela contratos. Executa regressão completa,
segurança, acessibilidade, desempenho, backup/restore e rollback.

Saída: artefatos imutáveis e decisão `GO`, `HOLD` ou `NO_GO`.

### 4. Controlled Pilot

Libera para um grupo pequeno, com consentimento, suporte, kill switch, métricas e
critérios de interrupção definidos.

Saída: evidência operacional e decisão de ampliar, corrigir ou reverter.

### 5. Production

Promove os mesmos digests aprovados no release candidate. Não recompila durante
a promoção.

Saída: manifest de produção, smoke pós-deploy e janela de observação.

### 6. Observation

Compara SLOs, erros, segurança, jornadas e feedback com os limites definidos.
Incidentes abrem cards e podem acionar rollback.

## Resultado dos gates

Os únicos resultados válidos são:

- `PASS`: executado e aprovado;
- `FAIL`: executado e reprovado;
- `BLOCKED`: não pôde executar por impedimento;
- `HOLD`: evidência insuficiente para promover;
- `SKIPPED`: deliberadamente não executado, sem equivaler a sucesso;
- `WAIVED`: dispensado temporariamente com aprovação e validade.

## Manifest obrigatório

Cada candidato ou promoção registra:

- `runId`, lane, data e responsável;
- SHA do workspace, backend, app e mapas;
- estado sujo do Git compartilhado, particionado pelos módulos afetados;
- hash da configuração dos gates;
- URL e identidade do ambiente;
- digests das imagens e builds mobile/web;
- versão Flyway/schema;
- versão do dataset e pacote de mapas;
- comandos, testes, skips e resultados;
- dispositivos, navegadores e sistemas operacionais;
- checksums das evidências;
- blockers, holds e waivers;
- alvo e procedimento de rollback.

O template oficial está em `program/templates/RELEASE_MANIFEST.md`.

## Controles da esteira

- CI de pull request executa testes determinísticos e segurança de mudança.
- Merge não promove automaticamente para produção.
- Ambientes usam concorrência exclusiva para evitar deploys sobrepostos.
- Promoções exigem environment approval.
- Artefatos são assinados, identificados por digest e promovidos sem rebuild.
- Migração de banco tem compatibilidade N/N-1 e rollback documentado.
- Backend, app e mapas aceitam versões adjacentes durante rollout mobile.
- Credenciais nunca aparecem em manifests, summaries, logs ou evidências.
- Smoke remoto só prova o SHA/digest implantado no ambiente.

## Rollback por superfície

| Superfície | Estratégia mínima |
|---|---|
| Backend | promover digest anterior e validar compatibilidade do schema |
| App mobile | feature flag, compatibilidade N/N-1 e release corretiva |
| App web | promover build anterior |
| Mapas | reverter serviço/dataset/pacote por versão |
| Banco | expand/contract; evitar migração destrutiva na mesma release |
| Configuração | versão anterior auditável e segredo rotacionável |
