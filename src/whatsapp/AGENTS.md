# Atendimento WhatsApp — instruções do módulo

Leia `../../AGENTS.md`, o card e `../../docs/desenvolvimento/whatsapp.md`.
Este diretório pertence ao mesmo Git que `src/backend`; os contratos canônicos
são `../backend/contracts/whatsapp-mvp`.

- Gate offline, na raiz Git: `python3 scripts/check_whatsapp.py`.
- Integração real sintética: `bash scripts/check_backend.sh` (Java 17/Docker).
- Preserve `/integrations/whatsapp/v1`, estados, auth e idempotência; migração de
  arquivos não altera a API. Rode testes dos produtores e consumidores.
- Docker do worker tem contexto `src/whatsapp`, inclui agent e transport completos.
- Workflows n8n permanecem inativos e fixados por manifest; não ativar/publish.
- Modelos ao vivo, Evolution real, dispositivos e VPS exigem escopo próprio.
- Preserve guardrails de arquivos privados, UID/permissões, sinais e cleanup.
- Nunca inclua tokens, GPS real, dados de WhatsApp ou logs brutos no Git/CI.
- Código de lab e manifests antigos não comprovam este checkout nem ambiente novo.
- Só altere arquivos atribuídos e preserve o trabalho concorrente.
