# Roadmap Integrado

> Histórico preservado da coordenação anterior à migração de 2026-11-02. Prioridade e estado atuais: `program/program.json`; evolução fora do MVP não está concluída por esta importação. Topologia e entrada atuais: [programa no monorepo](../docs/desenvolvimento/programa.md).

O roadmap é orientado por dependências e risco, não por calendário arbitrário.

## Primeira entrega WhatsApp — sequência de 30/10/2026

O pedido atual usa o épico `VR-WA-MVP` e os 20 cards `VR-WA-000` a `VR-WA-190`, detalhados no [plano executável](../docs/plans/2026-10-28-whatsapp-mvp/07-plano-executavel.md). Próximo: `VR-WA-000-PREFLIGHT`; um card ativo por vez. A sequência prova GPS em iPhone/Redmi 8 antes de integrar cadastro/presença/avisos, depois fecha operação, implantação de teste autorizada e validação física.

A primeira entrega é para o proprietário testar em ambiente restrito, com APK direto e iOS Personal Team. Não exige Play/TestFlight e não substitui G6/G7. O backlog legado e os HOLDs são preservados; os riscos compartilhados aplicáveis continuam exigindo mitigação. Endpoints antigos não serão expostos por conveniência no serviço isolado do MVP.

## Frentes de trabalho

| ID | Frente | Resultado |
|---|---|---|
| PGM | Programa e governança | estado, prioridade e evidência confiáveis |
| SEC | Segurança e autorização | acesso mínimo por usuário, objeto e ação |
| SES | Sessão e identidade | ciclo de token seguro e recuperável |
| PRIV | Privacidade e dados | finalidade, minimização e retenção explícitas |
| BE | Plataforma backend | contratos e execução resilientes |
| APP | Arquitetura do app | cliente, estado e navegação previsíveis |
| UX | Produto e jornadas | fluxos claros, responsivos e mensuráveis |
| A11Y | Acessibilidade e linguagem | uso inclusivo comprovado |
| MAP | Mapas e navegação | rota segura, offline e operacional |
| OPS | Operação e confiabilidade | observar, recuperar e escalar |
| QA | Estratégia de qualidade | regressão em camadas e evidência real |
| REL | Release e promoção | build imutável, gates e rollback |

## Sequência de execução

### Onda 0 — Controle

- concluir G0;
- reconciliar fontes legadas;
- congelar um baseline auditável;
- instituir WIP, cards e manifests.

### Onda 1 — Fronteira de confiança

- corrigir os quatro achados altos de localização e STOMP;
- centralizar autorização por objeto;
- proteger convite, vínculo e bootstrap;
- implementar lifecycle completo de sessão;
- criar suíte de regressão de segurança.

### Onda 2 — Contratos e arquitetura

- default deny e validação uniforme;
- OpenAPI, erros e cliente app tipado;
- resiliência de providers;
- uploads e jobs duráveis;
- reduzir hotspots e dividir estado/navegação do app.

### Onda 3 — Produto inclusivo

- corrigir bloqueadores responsivos;
- completar onboarding, recuperação e contexto de privacidade;
- consolidar primitives acessíveis;
- executar matriz manual;
- migrar textos e medir jornadas.

### Onda 4 — Navegação e mapas

- fechar boundary seguro da maps platform;
- validar provider/fallback e semântica de rota;
- preparar offline, cobertura e telemetria;
- executar testes de campo.

### Onda 5 — Operação

- observabilidade e SLOs;
- backup/restore;
- supply chain;
- escala horizontal e runbooks.

### Onda 6 — Prova integrada

- E2E de todos os papéis e dispositivos;
- visual, acessibilidade, desempenho, carga e áudio;
- release candidate imutável;
- decisão de piloto.

### Onda 7 — Piloto e produção

- piloto controlado;
- correções guiadas por evidência;
- produção progressiva;
- observação e melhoria contínua.

## Caminho crítico inicial

```text
GOV-PIPELINE-001
  -> VR-SEC-001-BE-WRITE
  -> VR-BE-RUNTIME-PUSH-001
  -> [prioridade operacional: VR-QA-TESTCONTAINERS-DOCKER-001]
  -> VR-SEC-001-BE-READ
  -> restante de realtime em VR-SEC-001
  -> VR-SEC-002 / VR-SEC-003
  -> VR-SES-001 / VR-SES-002
  -> G1 TRUSTED
```

Os dois cards intermediários foram inseridos porque a primeira prova real de
segurança encontrou um boot padrão quebrado e um gate que permaneceu verde
apesar de dez skips PostgreSQL. Corrigi-los antes das próximas fatias evita
repetir smokes com adapter externo e aceitar gates que não executaram a camada
de persistência. O card Testcontainers depende tecnicamente de
`GOV-PIPELINE-001`; sua posição após o runtime é prioridade de execução, não
dependência técnica.

O próximo card é selecionado por `program/program.json` e validado por
`scripts/program_control.py`; este documento descreve a estratégia, não substitui
o estado legível por máquina.
