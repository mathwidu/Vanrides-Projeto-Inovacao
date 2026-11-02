# Mapa do código do backend

API Java 17, Spring Boot 3.4.5 e PostgreSQL. O código foi transferido do commit
`e25ed4e0131bc253b7ccea2fa4ab793e69a89af9`; o manifesto da migração registra
alterações de topologia e exemplos. Use os fontes atuais para versões e contagens.

| Local | Responsabilidade |
|---|---|
| `src/main/java/com/vanrides/vanrides/controller/` | Contratos HTTP |
| `service/`, `repository/`, `model/` no pacote base | Regras e persistência |
| `auth/`, `filtros/`, `config/` | Sessões, auth, CORS e políticas |
| `websocket/` | STOMP/SockJS de localização e viagem |
| `whatsapp/` | Contexto isolado, comandos, transporte, presença e rastreamento |
| `src/main/resources/db/migration/` | Migrations Flyway imutáveis |
| `src/test/` | Regras, contratos, WebMvc e matriz PostgreSQL |
| `contracts/whatsapp-mvp/` | Contratos compartilhados canônicos |
| `scripts/`, `quality/` | Gates, proveniência e ratchet |

Os consumidores ficam em `../aplicativo-motorista` e `../whatsapp`. A raiz
Git fica dois níveis acima deste módulo. [Guia de desenvolvimento](../../../docs/desenvolvimento/backend.md).
Módulo próprio de mapas continua adiado; o provider local de rotas é estático.
