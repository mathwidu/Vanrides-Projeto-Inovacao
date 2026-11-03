# Registro superseded — ADR-005

- status: `SUPERSEDED / DO_NOT_ACCEPT`
- revisão histórica congelada em: `2026-07-18T13:20:55Z`
- commit histórico: `7d12d250f50a1e2a3a8a9a045da603f9c77a339a`
- blob Git histórico: `40941da83f52748344e0161c69ff3152bcff23c8`
- SHA-256 histórico:
  `b8387d219eccfe09c2da04ff9a75e2070df3c1fada9d514448a4babf3d870f8b`
- card: `VR-SEC-001-LOCATION-READ-SCOPE-001`

## Motivo da invalidação

A revisão histórica congelava números Flyway antes de `ready` e convertia essa
numeração em ordem artificial entre cards. Isso contradiz a política vigente de
alocação: cada card revalida o maior Flyway presente/aplicado e reserva o
próximo número contínuo somente no próprio freeze de `ready`.

Os valores de produto/privacidade da revisão histórica podem permanecer como
candidatos técnicos `NOT_ACCEPTED`, mas este digest não é mais uma revisão
válida para resposta humana. Nenhuma frase de aceite referente ao commit ou
SHA-256 histórico deve ser registrada ou interpretada como autorização.

## Próxima ação humana

Nenhuma enquanto o card estiver `analyzed / REFREEZE_REQUIRED`. O mesmo card
deve produzir nova ADR/DOR, sem número Flyway antecipado, nova revisão
imutável, novo registro de aceite e nova revisão independente antes de voltar a
`waiting_human`.

- aprovador de Produto: `INVALIDATED`;
- autoridade de Produto: `INVALIDATED`;
- aprovador de Privacidade: `INVALIDATED`;
- autoridade de Privacidade: `INVALIDATED`;
- frase recebida: `INVALIDATED`;
- timestamp do registro: `INVALIDATED`;
- declarações obrigatórias: `INVALIDATED`;
- veredito: `SUPERSEDED / DO_NOT_ACCEPT`.
