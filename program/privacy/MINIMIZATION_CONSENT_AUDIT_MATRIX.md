# Matriz de minimização, transparência e auditoria — VanRides

## Controle do artefato

- card: `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001`;
- início da execução: `2026-07-18`;
- inventário pai: commit
  `a3f7140e0a25fe5c440700146a42401500a87e4a`, blob
  `3f557da866c61a3a13e5bde103218b7ced7f2205`, SHA-256
  `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`;
- estado desta revisão: `TECHNICAL_RECOMMENDATION / NOT_APPROVED`;
- decisões humanas: todas permanecem `PENDING_*` ou
  `NOT_APPLICABLE_CURRENT` justificável;
- piloto e produção: `HOLD`.

O inventário pai é imutável nesta revisão. Divergência de fonte, família,
finding ou digest exige reabrir e versionar `VR-PRIV-001`; não é corrigida
silenciosamente aqui.

## Semântica fail-closed

| Estado | Significado |
|---|---|
| `TECH_KEEP_MINIMUM` | recomendação técnica para preservar somente o subconjunto necessário; não é aprovação humana |
| `TECH_REDUCE` | recomendação técnica para remover campo, cópia, audiência ou duração excedente |
| `TECH_RESTRICT` | recomendação técnica para restringir papel, objeto, tenant, viagem, aparelho ou provider |
| `TECH_PROTECT` | recomendação técnica para proteger storage, trânsito, chave, log, backup ou lifecycle |
| `TECH_REMOVE_OR_DISABLE` | recomendação para remover capacidade sem consumidor necessário ou mantê-la desligada até decisão |
| `TECH_FUTURE_GATE` | superfície ausente; qualquer implementação futura exige novo card e decisão anterior à coleta |
| `PENDING_PRODUCT` | Produto precisa validar necessidade, experiência e consumidor |
| `PENDING_PRIVACY` | Privacidade precisa validar minimização, transparência, titular e audiência |
| `PENDING_LEGAL_DPO` | Jurídico/DPO precisa validar a decisão aplicável sem inferência técnica de base ou consentimento |
| `PENDING_SECURITY_OPS` | Segurança/Operações precisa validar controle, acesso, provider, retenção operacional ou incidente |
| `NOT_APPLICABLE_CURRENT` | não há tratamento implementado no corte; não autoriza coleta futura |

Nenhuma célula desta matriz possui status `APPROVED`. Uma recomendação completa
pode preparar um pedido humano e continuar `PENDING_*`; somente aceite imutável,
com autoridade, revisão, digest e timestamp, pode mudar esse estado.

## Envelope mínimo de auditoria

Quando a linha pede `AE1`, o evento candidato contém somente:

- `eventId`, `eventType`, `occurredAt`, `outcome` e `reasonCode` enumerado;
- referências internas necessárias de tenant, ator, papel, objeto e policy
  version, sem nome, e-mail, telefone, endereço ou label humana;
- `requestId`/idempotency key quando necessário para correlação;
- categoria de campo ou provider, nunca valor, request/response, token, URI
  completa, coordenada, texto de suporte, conteúdo de notificação ou mídia.

O acesso ao próprio log, exportação de evidência, mudança de política e purge
também precisam ser auditáveis. Prazo, hash/pseudonimização e exceções do AE1
permanecem `PENDING_PRIVACY + PENDING_SECURITY_OPS`.

## Índice normalizado de cobertura

Cada D-ID aparece exatamente uma vez neste índice. `MINOR`, `GPS`, `PROVIDER`,
`BACKUP`, `EXPORT`, `MEDIA` e `INCIDENT` são flags de decisão/propagação, não
afirmações de uso real nem aprovação.

| ID | Titular/categoria | Stores e cópias observadas | Flags de decisão | Entrada e rota principal |
|---|---|---|---|---|
| D01 | PAX/DRV/GUA/ADM; identidade/contato | PostgreSQL, HTTP, app, backup | `IDENTITY`, `PUBLIC`, `BACKUP`, `INCIDENT` | inventário D01; F-PRIV-015/016; diretório público + DTO REST |
| D02 | PAX/DRV/GUA/ADM; credencial | PostgreSQL, memória, app, backup | `CREDENTIAL`, `BACKUP`, `INCIDENT` | D02; F-PRIV-001; remoção senha/Basic + coordenador + storage/refresh |
| D03 | PAX/DRV/GUA/ADM; sessão/token | PostgreSQL, trânsito, app, backup | `SESSION`, `CREDENTIAL`, `BACKUP`, `INCIDENT` | D03; F-PRIV-001; coordenador + refresh + auth epoch + storage seguro |
| D04 | titular de e-mail; verificação | PostgreSQL, app/navegador, Resend, backup | `PROVIDER`, `BACKUP`, `INCIDENT` | D04; confirmação + email outbox + retenção + log redaction |
| D05 | membros ADM/PAX/DRV/GUA; tenant | PostgreSQL, token/HTTP, backup | `TENANT`, `BACKUP`, `INCIDENT` | D05; bootstrap trust + core tenant projection + lifecycle ADM/tenant + retenção |
| D06 | PAX, inclusive menor | PostgreSQL, HTTP, app, Google, backup | `MINOR`, `GPS`, `PROVIDER`, `BACKUP`, `INCIDENT` | D06; F-PRIV-003/005/016/017; projeções e localização |
| D07 | DRV/GUA/ADM; perfil/avatar | PostgreSQL, HTTP, app, filesystem, backup | `MEDIA`, `BACKUP`, `INCIDENT` | D07; F-PRIV-007/016; avatar + REST |
| D08 | PAX/GUA; vínculo/menor | PostgreSQL, HTTP, app, backup | `MINOR`, `BACKUP`, `INCIDENT` | D08; F-PRIV-017; projeção GUA + retenção |
| D09 | convidados/revisores; convite | PostgreSQL, HTTP, app, URI, clipboard, log, backup | `TOKEN`, `BACKUP`, `INCIDENT` | D09; F-PRIV-006; invite + verified deep link |
| D10 | PAX/DRV; serviço/elegibilidade | PostgreSQL, HTTP, app, backup | `OPERATION`, `BACKUP`, `INCIDENT` | D10; F-PRIV-018; projeção DRV→PAX |
| D11 | participantes de rota/parada | PostgreSQL, HTTP, app, Maps/Google, backup | `MINOR`, `GPS`, `PROVIDER`, `BACKUP`, `INCIDENT` | D11; F-PRIV-003/005/009; location + Maps |
| D12 | participantes da viagem | PostgreSQL, REST/STOMP, app, Maps, backup | `MINOR`, `GPS`, `PROVIDER`, `BACKUP`, `INCIDENT` | D12; F-PRIV-003/005/018; realtime/projeções |
| D13 | PAX e operadores; presença | PostgreSQL, HTTP, app, backup | `MINOR`, `BACKUP`, `INCIDENT` | D13; F-PRIV-016; DTO REST + retenção |
| D14 | DRV e participantes; GPS bruto | sensor/memória, STOMP, PostgreSQL, HTTP, provider, backup | `MINOR`, `GPS`, `PROVIDER`, `BACKUP`, `INCIDENT` | D14; F-PRIV-003/004/005; location/realtime |
| D15 | participantes; realtime | JVM/STOMP, app memory/history, log | `MINOR`, `GPS`, `INCIDENT` | D15; F-PRIV-003/005; realtime + app adoption |
| D16 | participantes; geometria/waypoints | HTTP, app memory | `MINOR`, `GPS`, `INCIDENT` | D16; F-PRIV-003/005; location/adoption |
| D17 | participantes/fixtures; planejamento Maps | backend/Maps/provider memory e access logs | `MINOR`, `GPS`, `PROVIDER`, `INCIDENT` | D17; F-PRIV-009/014; Maps guard + S2S |
| D18 | titular/aparelho; device token | PostgreSQL, HTTP, backup, provider futuro | `TOKEN`, `PROVIDER`, `BACKUP`, `INCIDENT` | D18; F-PRIV-013; push lifecycle |
| D19 | destinatário; notificação/outbox | PostgreSQL, backup, provider futuro | `PROVIDER`, `BACKUP`, `INCIDENT` | D19; F-PRIV-013; `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001`; provider futuro depende também de D18 |
| D20 | PAX/GUA/ADM; suporte | PostgreSQL, HTTP, app, backup | `FREE_TEXT`, `BACKUP`, `EXPORT`, `INCIDENT` | D20; `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` + retenção |
| D21 | titular de avatar | filesystem/URL, PostgreSQL, app, backup | `MEDIA`, `BACKUP`, `INCIDENT` | D21; F-PRIV-007; avatar lifecycle |
| D22 | titular/aparelho; storage local | SharedPreferences/secure store e memória | `CREDENTIAL`, `TOKEN`, `INCIDENT` | D22; F-PRIV-001/006; password/storage/deep link |
| D23 | DRV/sessão; fila offline | SharedPreferences e memória | `TENANT`, `INCIDENT` | D23; F-PRIV-002; queue boundary |
| D24 | usuário/aparelho; permissões | estado do SO e memória do app | `MINOR`, `GPS`, `INCIDENT` | D24; F-PRIV-006/011/012; permissions/release/link |
| D25 | usuário; Google queries/coords | app/backend/provider memory e logs | `MINOR`, `GPS`, `PROVIDER`, `INCIDENT` | D25; F-PRIV-004/011; redaction + permissions |
| D26 | destinatário; e-mail Resend | processo, provider e erro/log | `PROVIDER`, `INCIDENT` | D26; F-PRIV-004; email outbox + redaction + retenção |
| D27 | origem de request; IP/digest | memória do filtro e métricas | `SECURITY`, `INCIDENT` | D27; rate-limit trust + retenção |
| D28 | titulares possíveis; logs/métricas | app/backend/Maps/ingress/provider | `MINOR`, `GPS`, `PROVIDER`, `INCIDENT` | D28; F-PRIV-004; log redaction + retenção |
| D29 | overlay de todas as famílias persistidas | dump/checksum/filesystem/restore | `MINOR`, `GPS`, `BACKUP`, `INCIDENT` | D29; F-PRIV-008; retention + backup/restore-purge |
| D30 | metadados QA/CI | Git, output, GitHub artifact, GHCR | `EVIDENCE`, `GPS_POSSIBLE`, `INCIDENT` | D30; F-PRIV-010/014; risco de cópia até bootstrap + evidence ratchet + Maps guard provarem sanitização |
| D31 | credenciais operacionais | secret stores/config/runtime | `SECRET`, `PROVIDER`, `INCIDENT` | D31; F-PRIV-011; checkpoint global próprio + mobile keys + `VR-SEC-005-SECRET-SCOPE-ROTATION-001` |
| D32 | superfícies futuras ausentes | nenhuma store atual aplicável | `EXPORT`, `FUTURE`, `INCIDENT` | D32; `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` antes de instrumentar |
| D33 | pessoas/dados visíveis em mídia | Git, worktree, render/export/publicação | `MINOR`, `GPS`, `MEDIA`, `EXPORT`, `INCIDENT` | D33; F-PRIV-019; media provenance/quarantine |
| D34 | titulares das entidades REST | JPA/PostgreSQL, HTTP, app, backup indireto | `MINOR`, `GPS`, `BACKUP`, `INCIDENT` | D34; F-PRIV-003/016; slices DTO + ratchet REST |

## Matriz A — necessidade e minimização técnica

| ID | Estado/proposta técnica | Mínimo recomendado | Consumidores e impacto | Roteamento |
|---|---|---|---|---|
| D01 | `TECH_REDUCE + TECH_RESTRICT` | ID interno, nome de exibição e canal de contato verificado somente quando a operação exigir; remover diretório anônimo de DRV | auth, conta, perfil e administração; restringir `GET /motoristas` exige contrato de compatibilidade | `F-PRIV-015`, `F-PRIV-016`, `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001`, `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` |
| D02 | `TECH_REDUCE + TECH_PROTECT` | senha bruta somente durante autenticação; hash somente no backend; remover senha da sessão, navegação, storage e fallback pós-login | login, cadastro, troca, headers e upgrade do app; migração pode deslogar de forma controlada | `F-PRIV-001`, `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`, `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001`, `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` |
| D03 | `TECH_REDUCE + TECH_PROTECT` | access token efêmero; refresh somente se consumido com rotação/revogação e storage protegido, senão remover emissão/persistência | login, restauração, REST/STOMP e logout; retry não pode reutilizar refresh gasto | `F-PRIV-001`, `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`, `VR-SES-001-REFRESH-ROTATION-001`, `VR-SES-001-AUTH-EPOCH-001`, `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` |
| D04 | `TECH_KEEP_MINIMUM + TECH_PROTECT` | e-mail snapshot estritamente necessário, token somente hash no backend e segredo one-time no cliente; remover segredo de log/erro/provider | confirmação/troca de e-mail, navegador/app e gateway de entrega | `VR-SES-003-EMAIL-CONFIRM-INTENT-001`, `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001`, `VR-OPS-001-SENSITIVE-LOG-REDACTION-001` |
| D05 | `TECH_KEEP_MINIMUM + TECH_RESTRICT` | ID, nome/slug e vínculo tenant estritamente necessários; nenhuma leitura ou escrita cross-tenant | auth, segregação, administração e encerramento da empresa | `VR-SEC-004-BOOTSTRAP-TRUST-001`, `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001`, `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001`, `VR-PRIV-003-TENANT-OFFBOARDING-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D06 | `TECH_REDUCE + TECH_RESTRICT` | separar projeções de conta, parada/rota e detalhe GUA; endereço, contato, nascimento e coordenada somente no fluxo que os consome | onboarding PAX, perfil, rota, Google e detalhe GUA; mudança exige DTOs e compatibilidade app/backend | `F-PRIV-003`, `F-PRIV-005`, `F-PRIV-016`, `F-PRIV-017`, `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001`, `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` |
| D07 | `TECH_REDUCE + TECH_RESTRICT` | perfis por papel; contato/nascimento/avatar somente ao titular e operação necessária; URL de mídia opaca | conta, administração e avatar de DRV/GUA/ADM | `F-PRIV-007`, `F-PRIV-016`, `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001`, `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` |
| D08 | `TECH_KEEP_MINIMUM + TECH_RESTRICT` | IDs da relação, autoridade/status e timestamps necessários; perfil PAX permanece em projeção separada | vínculo GUA↔PAX, autorização e acompanhamento | `F-PRIV-017`, `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D09 | `TECH_REDUCE + TECH_PROTECT` | token/código de alta entropia, hash server-side, status/origem mínimos; não persistir URI completa, clipboard ou token além da janela | emissão/revisão ADM, deep link, onboarding, retry e upgrade | `F-PRIV-006`, `VR-SEC-004-INVITE-REDEMPTION-001`, `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` |
| D10 | `TECH_REDUCE + TECH_RESTRICT` | agenda/destino/capacidade/vigência e referência do motorista; remover e-mail/telefone DRV de payload PAX sem consumidor | provisionamento, presença, viagem e UI PAX | `F-PRIV-018`, `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001`, `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001` |
| D11 | `TECH_REDUCE + TECH_RESTRICT` | stop IDs pseudônimos, ordem e coordenadas somente durante rota; provider não recebe nome do titular | administração, motorista, mapa, navegação e providers | `F-PRIV-003`, `F-PRIV-005`, `F-PRIV-009`, `VR-SEC-001-LOCATION-DOMAIN-001`, `VR-MAP-001-LOCATION-S2S-001` |
| D12 | `TECH_REDUCE + TECH_RESTRICT` | DTO por papel/viagem; estado/ETA/geometria e hashes somente se consumidos; remover contato DRV de PAX | DRV/PAX/GUA/ADM, REST/STOMP, snapshots e Maps | `F-PRIV-003`, `F-PRIV-005`, `F-PRIV-018`, `VR-SEC-003-LOCATION-REALTIME-001`, `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` |
| D13 | `TECH_KEEP_MINIMUM + TECH_RESTRICT` | referência de serviço/viagem, data, status e timestamps; nunca entidade PAX aninhada | confirmação PAX, lista operacional, viagem e controllers | `F-PRIV-016`, `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D14 | `TECH_REDUCE + TECH_RESTRICT + TECH_PROTECT` | GPS somente na viagem/janela autorizada, sem broadcast tenant-wide, histórico amplo ou backup não aprovado | sensor DRV, STOMP, banco, tracking, rota e restore | `F-PRIV-003`, `F-PRIV-004`, `F-PRIV-005`, `VR-SEC-001-LOCATION-DOMAIN-001`, `VR-SEC-003-LOCATION-REALTIME-001` |
| D15 | `TECH_REDUCE + TECH_RESTRICT` | tópico por usuário/viagem e projeção mínima; autorização server-side; histórico de UI limitado | broker/STOMP, app tracking e GUA; substitui filtro somente no cliente | `F-PRIV-003`, `F-PRIV-005`, `VR-SEC-003-LOCATION-REALTIME-001`, `VR-APP-001-LOCATION-ADOPTION-001` |
| D16 | `TECH_REDUCE + TECH_RESTRICT` | waypoint somente do titular/viagem autorizada; label não identificável e densidade mínima | mapas/listas PAX/GUA/DRV e route snapshots | `F-PRIV-003`, `F-PRIV-005`, `VR-APP-001-LOCATION-ADOPTION-001`, `VR-SEC-003-LOCATION-REALTIME-001` |
| D17 | `TECH_REDUCE + TECH_RESTRICT + TECH_PROTECT` | identidade S2S, stop IDs/labels pseudônimos e apenas coordenadas necessárias; adapter default-off até boundary/log comprovados | facade backend, Maps, Valhalla/OSRM e app consumidor | `F-PRIV-009`, `F-PRIV-014`, `VR-MAP-001-EVIDENCE-DATA-GUARD-001`, `VR-MAP-001-LOCATION-S2S-001` |
| D18 | `TECH_REDUCE + TECH_PROTECT` | token/hash, plataforma e binding conta/aparelho; remover e-mail do contrato e desvincular no refresh/logout/revogação | SO/app, backend e push gateway futuro | `F-PRIV-013`, `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`, `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` |
| D19 | `TECH_REDUCE + TECH_PROTECT` | destinatário interno, tipo/template e variáveis com schema fechado; título/corpo são materializados no boundary e provider fica noop | outbox e provider futuro; não possui token D18 | `F-PRIV-013`, `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001`; ativação real depende também de `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` |
| D20 | `TECH_REDUCE + TECH_RESTRICT` | categoria, descrição estritamente necessária, status e atribuição; sem copiar conta/viagem além do contexto | PAX/GUA, ADM de suporte e backend | `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D21 | `TECH_REDUCE + TECH_PROTECT + TECH_RESTRICT` | arquivo normalizado sem EXIF, ID opaco, entrega privada/autorizada, replace/delete e storage durável | upload de perfil, filesystem e UI | `F-PRIV-007`, `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` |
| D22 | `TECH_REDUCE + TECH_PROTECT` | somente material de sessão necessário em Keychain/Keystore; remover senha, resíduos e convite fora do lifecycle | sessão, headers, deep link, onboarding e logout | `F-PRIV-001`, `F-PRIV-006`, `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`, `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001`, `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001`, `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` |
| D23 | `TECH_REDUCE + TECH_RESTRICT + TECH_PROTECT` | owner/tenant/schema version/TTL; IDs internos e ação, sem nome; limpar no logout e migração fail-safe | fila offline DRV e sincronização posterior | `F-PRIV-002`, `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001`, `VR-APP-002-OFFLINE-QUEUE-SESSION-BOUNDARY-001` |
| D24 | `TECH_REDUCE + TECH_RESTRICT` | permissão just-in-time por feature; background location desligada sem decisão; foto/push/deep link separados | Android/iOS, GPS, avatar, push e link | `F-PRIV-006`, `F-PRIV-011`, `F-PRIV-012`, `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001`, `VR-REL-001-ANDROID-RELEASE-BOUNDARY-001` |
| D25 | `TECH_REDUCE + TECH_PROTECT` | query/endereço/waypoints mínimos, chaves separadas/restritas e logs redigidos; provider só pelo fluxo necessário | autocomplete/geocode/directions no app/backend e Google | `F-PRIV-004`, `F-PRIV-011`, `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`, `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` |
| D26 | `TECH_REDUCE + TECH_PROTECT` | destinatário, template e link one-time mínimos; erro sem corpo/endereço/token | backend, Resend e titular | `F-PRIV-004`, `VR-BE-005-EMAIL-VERIFICATION-OUTBOX-001`, `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D27 | `TECH_KEEP_MINIMUM + TECH_PROTECT` | IP somente na resolução; HMAC truncado, store limitado e métricas agregadas; sem raw IP persistido | filtros, rate limit, métricas e OPS | `VR-SEC-005-RATE-LIMIT-TRUST-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D28 | `TECH_REDUCE + TECH_PROTECT` | log estruturado com códigos/classes/status; remover GPS, endereço, coordenada, token, URI completa e corpos/provider URLs | app/backend/Maps/ingress/providers e operador | `F-PRIV-004`, `VR-OPS-001-SENSITIVE-LOG-REDACTION-001`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` |
| D29 | `TECH_REDUCE + TECH_PROTECT + TECH_RESTRICT` | catálogo, criptografia, checksum, acesso mínimo, prune e restore-purge; excluir classes proibidas conforme decisão | PostgreSQL DEV, filesystem e restore isolado | `F-PRIV-008`, `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001`, `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001` |
| D30 | `TECH_KEEP_MINIMUM + TECH_PROTECT` | SHA/digest, comandos, resultado, versão e metadado de run; nunca payload, PII, GPS ou segredo; enforcement fail-closed | gates, Git/GitHub artifacts, GHCR e release | `F-PRIV-010`, `F-PRIV-014`, `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001`, `VR-MAP-001-EVIDENCE-DATA-GUARD-001` |
| D31 | `TECH_REDUCE + TECH_PROTECT + TECH_RESTRICT` | uma credencial por ambiente/provider/escopo, owner e rotação; nunca compartilhar valor entre app/backend/CI | GitHub/Swarm/config local, banco, Google, Resend, GHCR e SSH | `F-PRIV-011`, `VR-SEC-005-SECRET-SCOPE-ROTATION-001`, `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001`, `VR-REL-001-ANDROID-RELEASE-BOUNDARY-001` |
| D32 | `TECH_FUTURE_GATE` | nenhuma coleta/SDK/export/pack/telemetria antes de manifest, card, minimização e decisão humana específica | futuras features e providers; impacto atual `NOT_APPLICABLE_CURRENT` | `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` |
| D33 | `TECH_REMOVE_OR_DISABLE + TECH_PROTECT` | somente corpus sintético ou autorizado, redigido e manifestado por digest; quarentena até prova de origem/uso/publicação | captura, Git, Remotion, render, apresentação e publicação | `F-PRIV-019`, `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` |
| D34 | `TECH_REDUCE + TECH_RESTRICT` | DTOs explícitos por papel/endpoint; nenhuma entidade JPA ou relação aninhada em request/response | controllers REST, app e compatibilidade de API | `F-PRIV-003`, `F-PRIV-016`, slices de diretório/GUA/DRV/location/support/core + ratchet `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` |

## Matriz B — acesso, auditoria e decisão humana

| ID | Audiência mínima proposta | Evento AE1 candidato | Decisão humana ainda pendente | Owner da próxima decisão |
|---|---|---|---|---|
| D01 | titular; ADM tenant por função; internet anônima nenhuma | `ACCOUNT_PROFILE_READ/UPDATE`, `PUBLIC_DIRECTORY_DENY` | necessidade de contato/diretório, transparência e decisão aplicável | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D02 | auth subsystem; titular durante entrada; suporte/admin nunca recebe segredo | `AUTH_RESULT`, `CREDENTIAL_CHANGED`, sem senha/hash | necessidade do fallback/migração e controles de autenticação | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D03 | titular/aparelho e auth subsystem; token bruto nunca para suporte | `TOKEN_ISSUED/ROTATED/REVOKED`, sem token | necessidade de refresh, sessão persistida, transparência e lifecycle | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D04 | titular, auth e delivery gateway mínimo | `EMAIL_VERIFICATION_ISSUED/CONSUMED/EXPIRED` | prazo/provider, comunicação e decisão aplicável | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D05 | membro tenant; ADM somente no tenant; OPS somente suporte autorizado | `TENANT_LINK_CREATED/REVOKED`, `TENANT_ACCESS_DENIED` | necessidade por papel e transparência administrativa | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D06 | titular; GUA ligado em subset aprovado; ADM/DRV somente operação atribuída | `PASSENGER_PROFILE_ACCESS/UPDATE`, `SENSITIVE_FIELD_DENY` | menores, endereço/contato/coordenada e autoridade GUA | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D07 | titular; ADM tenant em subset operacional; público nenhum | `ROLE_PROFILE_ACCESS/UPDATE`, `AVATAR_MUTATED` | nascimento/contato/avatar por papel e transparência | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D08 | PAX/GUA vinculados e ADM tenant autorizado | `GUARDIAN_LINK_CREATED/REVIEWED/REVOKED` | prova de autoridade, menor, visibilidade e prazo | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D09 | emissor/revisor e claimant; token somente no canal de resgate | `INVITE_ISSUED/REDEEMED/REVOKED/DENIED` | canal, clipboard, expiração, comunicação e autoridade | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D10 | ADM, DRV atribuído e PAX/GUA apenas subset do próprio serviço | `SERVICE_ASSIGNMENT_READ/CHANGED/DENIED` | necessidade por papel, vigência e exibição do motorista | `PENDING_PRODUCT + PENDING_PRIVACY` |
| D11 | ADM/DRV atribuídos; PAX/GUA somente própria parada/rota; provider mínimo | `ROUTE_ACCESS`, `ROUTE_PROVIDER_EGRESS`, `ROUTE_DENY` | necessidade de endereço/coordenada/label, menor e provider | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D12 | participantes da viagem com DTO por papel/objeto | `TRIP_ACCESS/STATE_CHANGED`, `TRIP_ROUTE_EGRESS`, `TRIP_DENY` | audiência, contatos, snapshots/hashes, transparência e prazo | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D13 | titular/GUA autorizado, DRV atribuído e ADM tenant | `PRESENCE_READ/CONFIRMED/CHANGED/DENIED` | visibilidade, menor, prazo e comunicação | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D14 | DRV escreve próprio GPS; somente participantes ativos autorizados leem | `LOCATION_SHARE_STARTED/STOPPED/READ/DENIED/PURGED` | finalidade, janela, menor, histórico, retenção, backup e incidente | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D15 | subscription por usuário/viagem/papel; nenhum tenant-wide | `REALTIME_SUBSCRIBE/UNSUBSCRIBE/DENY` | audiência realtime, transparência e lifecycle de memória/log | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D16 | somente waypoints do titular/viagem autorizada | `MAP_GEOMETRY_ACCESS/DENY` | labels/pins, outros passageiros, acessibilidade e transparência | `PENDING_PRODUCT + PENDING_PRIVACY` |
| D17 | somente identidade de serviço backend↔Maps e provider necessário | `MAPS_REQUEST_ACCEPTED/DENIED/PROVIDER_EGRESS`, sem coords/labels | provider, dado de menor/GPS, termos, retenção e ativação | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D18 | titular/aparelho, auth e push subsystem; admin não vê token | `DEVICE_BOUND/REFRESHED/UNBOUND/REVOKED` | necessidade do push, compartilhamento de aparelho e provider | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D19 | destinatário e worker de notificação; OPS somente status agregado | `NOTIFICATION_ENQUEUED/DELIVERED/FAILED/DROPPED`, sem conteúdo | tipos/conteúdo, opt-in/out aplicável, provider e prazo | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D20 | solicitante PAX/GUA e `COMPANY_ADMIN` do próprio tenant; papel dedicado nenhum no corte | `SUPPORT_TICKET_CREATED/ACCESSED/STATE_CHANGED/DENIED` | conteúdo necessário, SLA, acesso, comunicação, retenção e eventual papel dedicado | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D21 | titular e viewers explicitamente autorizados; mutação pelo titular/ADM autorizado | `AVATAR_UPLOADED/REPLACED/DELETED`; leitura comum não é logada por padrão | audiência, moderação, retenção e publicação | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D22 | titular/aparelho e auth subsystem; nenhum outro app/backup | `LOCAL_SESSION_MIGRATED/CLEARED/FAILED`, sem material de sessão | persistência, backup/reinstall, transparência e suporte mínimo de SO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D23 | somente sessão/tenant/DRV que criou a ação | `OFFLINE_ACTION_ENQUEUED/SYNCED/DROPPED/CLEARED`, sem nome | TTL, conflito, troca de conta, comunicação de perda/retry | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D24 | usuário e OS por permissão/feature; backend recebe somente efeito necessário | `PERMISSION_PROMPTED/GRANTED/DENIED` por categoria, sem sinal do aparelho desnecessário | background, push, foto, deep link, texto de transparência e decisão aplicável | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D25 | serviço autorizado e provider estritamente necessário | `PROVIDER_EGRESS/RESULT/FAILURE` com operação/provider, sem query/endereço/coords | necessidade, termos/provider, separação de chaves e transparência | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D26 | auth/email service, provider e titular destinatário | `EMAIL_DELIVERY_REQUESTED/SUCCEEDED/FAILED`, sem destinatário/corpo/token | conteúdo, provider, retenção externa e comunicação | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D27 | SEC/OPS em agregado; raw IP não persistido | `RATE_LIMIT_ALLOWED/DENIED/STORE_SATURATED`, digest somente se indispensável | finalidade/necessidade do digest, janela e acesso operacional | `PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| D28 | OPS/SEC com necessidade e segregação de ambiente | `LOG_ACCESSED/EXPORTED/POLICY_CHANGED/PURGED` | categorias, prazo, incident hold, provider/ingress e acesso | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D29 | OPS/DBA nomeados, acesso segregado e dois-pares para produção | `BACKUP_CREATED/VERIFIED/PRUNED/RESTORED/PURGED` por manifest | RPO/RTO, prazo normal, criptografia/chaves, restore e exclusão; hold/release fica fora desta célula em `INCIDENT-HOLD-001` | backup normal: `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS`; hold/release: `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D30 | QA/REL/SEC; público somente artefato aprovado e sanitizado | `EVIDENCE_CREATED/VERIFIED/PUBLISHED/DELETED` | retenção, publicação, classes proibidas, dado pessoal residual e decisão aplicável | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D31 | owner nominal por credencial e runtime específico | `SECRET_ACCESSED/ROTATED/REVOKED/SCOPE_CHANGED`, nunca valor | owner, escopo, rotação, ambiente, possível vínculo a pessoa e decisão aplicável | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| D32 | nenhuma audiência porque não implementado | `FUTURE_DATA_FEATURE_PROPOSED/APPROVED_OR_REJECTED` antes de coleta | necessidade, titular, transparência, decisão aplicável e provider futuro | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` antes de implementação |
| D33 | equipe de mídia autorizada; publicação somente após aprovação específica | `MEDIA_CAPTURED/QUARANTINED/APPROVED/EXPORTED/PUBLISHED/DELETED` por digest | origem sintética/real, autorização, redaction, retenção e canal | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| D34 | papel/tenant/objeto autorizado por endpoint e DTO | `API_OBJECT_ACCESS/DENY/CONTRACT_VERSION` sem payload | audiência campo a campo, compatibilidade e transparência | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` |

## Matriz de propagação por store/cópia

Uma decisão por família não está completa até alcançar cada cópia. Valores de
prazo/destino continuam humanos `PENDING`; a ação abaixo é candidata técnica.

| Store/cópia | D-IDs | Ação técnica candidata | Decisão humana necessária | Rota |
|---|---|---|---|---|
| PostgreSQL primário | D01–D14, D18–D20, URL D21 | classificar por agregado, DTO e lifecycle; nenhuma cascade ad hoc | finalidade, prazo, marco, destino, exceção e hold | cross-family retention + ADR-002 |
| app local persistido | D03, D09, D22, D23 | secure/session-bound, owner/tenant/TTL, migração e clear | necessidade offline, transparência, reinstall/backup | session coordinator + storage + queue + deep link |
| memória app/JVM | D02–D28 conforme fluxo | lifetime explícito, bounded history e zero cópia acidental em log | necessidade da duração e incidente | cards verticais + log redaction |
| REST/STOMP | D01, D06, D10–D16, D18–D20, D34 | DTO/evento por papel/objeto, default-deny e contract version | audiência/experiência por titular/papel | ownership REST + realtime |
| filesystem/avatar | D21 | storage privado/durável, EXIF strip, replace/delete e ACL | audiência, prazo e moderação | avatar lifecycle |
| logs/ingress/runtime | D09, D14–D17, D25–D28 | allowlist estruturada, redaction e acesso auditado | prazo, provider, incidente/hold e comunicação | sensitive log redaction + incident decision |
| Google | D06, D11, D12, D25 | query/coords mínimos, chave restrita, redaction e egress event | finalidade, termos, transparência, prazo/delete | permissions + provider decision |
| Maps/Valhalla/OSRM | D11, D12, D14, D17 | S2S, labels pseudônimos, default-off e no sensitive logs | GPS/menor, provider, prazo e ativação | Maps guard + S2S + ADR-005 candidate |
| Resend | D04, D26 | outbox durável com template/recipient/link mínimos e erro redigido | conteúdo, termos, retenção/delete e comunicação | email verification outbox + log redaction + provider decision |
| push futuro | D18, D19 | bind/unbind por sessão, template/data allowlist e provider noop | necessidade, opt-in/out aplicável, conteúdo e prazo | session coordinator + push + outbox minimization |
| backup/PITR/restore | D01–D14, D18–D21, D29; D34 como overlay das famílias persistidas | criptografia, catálogo, prune e reaplicar purge antes de servir; URL D21 e projeções D34 não ficam implícitas | RPO/RTO, prazo normal, restore, exclusão e propagação D29; hold/release é `INCIDENT-HOLD-001`, sem Produto como autoridade | backup decision/implementation + incident decision separada |
| evidência local/Git | D30; possível overlay D06/D14/D33 até F-PRIV-014 | metadata allowlist, digest e scanner fail-closed | acesso, retenção, publicação e delete/history | evidence ratchet |
| GitHub Actions artifacts externos | D30; possível overlay D06/D14/D33 até F-PRIV-014 | acesso candidato somente a QA/REL/SEC nomeados; upload público bloqueado | ACL real, retenção, publicação e delete permanecem `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | evidence ratchet |
| GHCR externo | D30 image/digest/proveniência; PII/GPS/segredo são classes proibidas | scanner antes de push/promoção e referência por digest | visibilidade/acesso, retenção, publicação/promoção, delete/untag/GC permanecem `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | evidence ratchet + release |
| secret stores/config | D31 | owner/escopo/ambiente/rotação e zero valor em evidência | autoridade, break-glass e incidente | `VR-SEC-005-SECRET-SCOPE-ROTATION-001` + mobile/release slices |
| Git/mídia/render/publicação | D33 | quarentena, corpus por digest, redaction e publication gate | origem, autorização, prazo, canal e delete/history | media provenance + incident decision |
| export do titular | D01–D34 por aplicabilidade; D20 é somente canal candidato e D32 registra mecanismo ausente | formato mínimo, canal protegido, manifest, idempotência e roteamento por família/store | escopo, autoridade, SLA, exceções, comunicação e aplicabilidade por D-ID | `EXPORT-SUBJECT-001` + ADR-002 + cross-family retention |

## Registros especiais de menores, GPS e incidente

| Decision ID | D-IDs | Valor candidato técnico | Status/owner humano | Destino |
|---|---|---|---|---|
| `MINOR-001-AUTHORITY` | D06, D08, D13, D24, D34 | vínculo/autoridade verificados e default-deny quando ausentes | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | ADR-002 + guardian projection |
| `MINOR-002-AUDIENCE` | D06, D11–D17, D34 | projeção por menor/titular/viagem; nenhum dado de outro PAX | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | location/realtime/REST slices |
| `MINOR-003-PROVIDER` | D06, D11, D12, D17, D25 | IDs/labels pseudônimos e egress mínimo/default-off | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | provider decisions + Maps S2S |
| `MINOR-004-COPIES` | D28, D29, D30, D33 | zero menor em log/evidência/mídia; D30 permanece `PENDING` até sanitização provada; backup/hold por política | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | redaction + backup + evidence ratchet + media + incident |
| `GPS-001-COLLECTION` | D06, D14, D24, D25 | coletar somente na viagem/janela autorizada, frequência/precisão mínimas | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` | ADR-005 acceptance + location domain |
| `GPS-002-AUDIENCE` | D06, D14–D16, D34 | leitura trip-scoped e subscription server-side por objeto | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_SECURITY_OPS` | location/realtime/REST slices |
| `GPS-003-PROVIDER` | D06, D11, D12, D14, D17, D25 | provider mínimo via S2S, default-off e egress audit sem coords | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | Maps S2S + provider decisions |
| `GPS-004-COPIES-RETENTION` | D06, D14, D28–D30, D33, D34 | purge ativo; D30 permanece risco de cópia até F-PRIV-014 provar sanitização; D33 até F-PRIV-019 provar origem/redaction; restore-purge antes de tráfego | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | ADR-005 + retention + backup + evidence/media guards |
| `INCIDENT-HOLD-001` | D01–D34 por escopo do evento | hold/release versionado e fail-closed, sem alterar prazo normal silenciosamente | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` |
| `INCIDENT-FORENSIC-EXTRACTION-001` | somente famílias/stores atingidos | extração mínima, acesso nomeado, cadeia de custódia, destino e delete pós-release; nunca usa o canal de exportação do titular | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` | `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` |

### Aplicabilidade cross-cutting por família

`YES` registra propagação observada no inventário, não aprovação. `PENDING`
impede excluir a família sem prova de titular/fluxo/store. `NO` só é usado em
D32 com `NOT_APPLICABLE_CURRENT`, porque o mecanismo não existe; qualquer
implementação reabre a decisão pelo future-data gate.

Regra fail-closed transversal de exportação: senha, access/refresh/device/
invite/verification token, link utilizável, chave operacional, segredo e seus
derivados (incluindo hash/verifier) são sempre `TECH_EXCLUDE`, mesmo quando
aparecem misturados a outra família. Somente metadados e eventos não secretos
permanecem `PENDING` para decisão humana; essa pendência nunca autoriza exportar
material autenticador.

| ID | Menor — estado e fonte | Exportação do titular — estado e fonte | GPS/cópia — estado e fonte |
|---|---|---|---|
| D01 | `PENDING` — D01/H-003; PAX pode ser menor, idade não provada | `PENDING` — D01/H-007 | `PENDING` — D01/H-004 |
| D02 | `PENDING` — D02/H-003; credencial pode pertencer a PAX menor | `PENDING` — D02/H-007; senha, hash/verifier e segredo bruto/derivado são `TECH_EXCLUDE`; metadados/eventos permanecem pendentes | `PENDING` — D02/H-004 |
| D03 | `PENDING` — D03/H-003; sessão pode pertencer a PAX menor | `PENDING` — D03/H-007; access/refresh token, hash/verifier e segredo bruto/derivado são `TECH_EXCLUDE`; metadados/eventos permanecem pendentes | `PENDING` — D03/H-004 |
| D04 | `PENDING` — D04/H-003; titular pode ser PAX menor | `PENDING` — D04/H-007; token/hash/link de verificação bruto ou derivado é `TECH_EXCLUDE`; e-mail snapshot e eventos permanecem pendentes | `PENDING` — D04/H-004 |
| D05 | `PENDING` — D05/H-003; vínculo de PAX menor não excluído | `PENDING` — D05/H-007 | `PENDING` — D05/H-004 |
| D06 | `YES` — D06/MINOR/GPS | `PENDING` — D06/H-007 | `YES` — D06/GPS |
| D07 | `PENDING` — D07/H-003; perfil/avatar exige prova por papel | `PENDING` — D07/H-007 | `PENDING` — D07/H-004 |
| D08 | `YES` — D08/MINOR | `PENDING` — D08/H-007 | `PENDING` — D08/H-004 |
| D09 | `PENDING` — D09/H-003; convite pode vincular PAX menor | `PENDING` — D09/H-007; código/token/hash/link de convite bruto ou derivado é `TECH_EXCLUDE`; metadados/eventos permanecem pendentes | `PENDING` — D09/H-004 |
| D10 | `PENDING` — D10/H-003; serviço pode incluir PAX menor | `PENDING` — D10/H-007 | `PENDING` — D10/H-004 |
| D11 | `YES` — D11/MINOR | `PENDING` — D11/H-007 | `YES` — D11/GPS |
| D12 | `YES` — D12/MINOR | `PENDING` — D12/H-007 | `YES` — D12/GPS |
| D13 | `YES` — D13/MINOR | `PENDING` — D13/H-007 | `PENDING` — D13/H-004 |
| D14 | `YES` — D14/MINOR | `PENDING` — D14/H-007 | `YES` — D14/GPS |
| D15 | `YES` — D15/MINOR | `PENDING` — D15/H-007 | `YES` — D15/GPS |
| D16 | `YES` — D16/MINOR | `PENDING` — D16/H-007 | `YES` — D16/GPS |
| D17 | `YES` — D17/MINOR | `PENDING` — D17/H-007 | `YES` — D17/GPS |
| D18 | `PENDING` — D18/H-003; aparelho pode ser de PAX menor | `PENDING` — D18/H-007; device token/hash bruto ou derivado é `TECH_EXCLUDE`; metadados/eventos permanecem pendentes | `PENDING` — D18/H-004 |
| D19 | `PENDING` — D19/H-003; destinatário pode ser PAX menor | `PENDING` — D19/H-007 | `PENDING` — D19/H-004 |
| D20 | `PENDING` — D20/H-003; solicitante pode ser PAX menor | `PENDING` — D20/H-007; somente canal candidato | `PENDING` — D20/H-004 |
| D21 | `PENDING` — D21/H-003; avatar pode ser de PAX menor | `PENDING` — D21/H-007 | `PENDING` — D21/H-004 |
| D22 | `PENDING` — D22/H-003; storage pode conter sessão de menor | `PENDING` — D22/H-007; senha, access/refresh token e segredo bruto/derivado são `TECH_EXCLUDE`; metadados/eventos permanecem pendentes | `PENDING` — D22/H-004 |
| D23 | `PENDING` — D23/H-003; payload/owner precisa de prova | `PENDING` — D23/H-007 | `PENDING` — D23/H-004 |
| D24 | `YES` — D24/MINOR | `PENDING` — D24/H-007 | `YES` — D24/GPS |
| D25 | `YES` — D25/MINOR | `PENDING` — D25/H-007; chave operacional/segredo é `TECH_EXCLUDE`; request/response do titular permanece pendente | `YES` — D25/GPS |
| D26 | `PENDING` — D26/H-003; destinatário pode ser PAX menor | `PENDING` — D26/H-007; token/hash/link de verificação bruto ou derivado é `TECH_EXCLUDE`; metadados de entrega permanecem pendentes | `PENDING` — D26/H-004 |
| D27 | `PENDING` — D27/H-003; origem pode ser PAX menor | `PENDING` — D27/H-007 | `PENDING` — D27/H-004 |
| D28 | `YES` — D28/MINOR | `PENDING` — D28/H-007 | `YES` — D28/GPS |
| D29 | `YES` — D29/MINOR overlay | `PENDING` — D29/H-007 | `YES` — D29/GPS overlay |
| D30 | `PENDING` — D30/F-PRIV-014; presença de dado de menor não foi observada nem excluída até sanitização provada | `PENDING` — D30/H-007; qualquer segredo/token residual segue a regra transversal `TECH_EXCLUDE` | `PENDING` — D30/F-PRIV-014; risco de cópia GPS |
| D31 | `PENDING` — D31/H-003; vínculo a pessoa não foi excluído | `PENDING` — D31/H-007; valor secreto e derivado são `TECH_EXCLUDE`; metadado ligado ao titular permanece pendente | `PENDING` — D31/H-004 |
| D32 | `NO` — D32/`NOT_APPLICABLE_CURRENT`; future-data gate | `NO` — D32/`NOT_APPLICABLE_CURRENT`; mecanismo ausente | `NO` — D32/`NOT_APPLICABLE_CURRENT`; future-data gate |
| D33 | `YES` — D33/MINOR | `PENDING` — D33/H-007 | `YES` — D33/GPS |
| D34 | `YES` — D34/MINOR overlay | `PENDING` — D34/H-007 | `YES` — D34/GPS overlay |

### Candidatos históricos da ADR-005, superseded e ainda não aceitos

Fonte histórica `SUPERSEDED / DO_NOT_ACCEPT`:
`program/decisions/ADR-005-trip-scoped-location-access.md`, commit
`7d12d250f50a1e2a3a8a9a045da603f9c77a339a`, blob
`40941da83f52748344e0161c69ff3152bcff23c8`, SHA-256
`b8387d219eccfe09c2da04ff9a75e2070df3c1fada9d514448a4babf3d870f8b`.

| Candidato | Valor técnico proposto na ADR-005 | Estado nesta matriz |
|---|---|---|
| janela operacional/lease | viagem `IN_PROGRESS`, lease máxima de 12 horas e cutoff local | `CANDIDATE_FROM_ADR005 / NOT_ACCEPTED` |
| banco ativo | purge após 72 horas | `CANDIDATE_FROM_ADR005 / NOT_ACCEPTED`; não é prazo legal |
| cópia física/PITR | autoexpira em sete dias da criação e não ultrapassa `locationClosedAt + 10 dias` | `CANDIDATE_FROM_ADR005 / NOT_ACCEPTED`; depende de backup/hold |
| audiência | autorização trip-scoped por usuário/papel e deny-by-default | `CANDIDATE_FROM_ADR005 / NOT_ACCEPTED` |

Esses identificadores preservam proveniência histórica, não uma revisão
aceitável. A ADR-005 e seu checkpoint estão `REFREEZE_REQUIRED`; somente nova
revisão imutável, sem número Flyway reservado antes de `ready`, pode retornar a
`waiting_human`. Esta matriz não aceita prazo, acesso, provider, backup ou
incidente em nome dos decisores.

## Cobertura canônica dos findings

Todos permanecem `OPEN / ROUTED`. A matriz não os corrige nem muda o estado de
seus cards.

| Finding | Cobertura canônica | Tipo de cobertura | Partição verificável de subescopo |
|---|---|---|---|
| F-PRIV-001 | `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` + `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` + `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` + `VR-SES-001-REFRESH-ROTATION-001` | três novos + existente | coordinator possui state machine/generation/hooks/composition; password card possui senha/Basic; storage possui vault/envelope/migração app; refresh possui família/rotação backend |
| F-PRIV-002 | coordinator + `VR-APP-002-OFFLINE-QUEUE-SESSION-BOUNDARY-001` | novos | coordinator emite teardown/switch; queue possui envelope, owner, tenant, TTL e retry, sem alterar state machine |
| F-PRIV-003 | `VR-SEC-001-LOCATION-DOMAIN-001` + `VR-SEC-003-LOCATION-REALTIME-001` + `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` | existentes + ratchet | location possui `/api/localizacao/**`, persistência/histórico/retention; realtime possui `/ws-location` e destinos STOMP; ratchet apenas valida cobertura |
| F-PRIV-004 | log redaction + location domain | novo + existente | redaction possui sinks/mensagens/provider URLs; location elimina origem GPS indevida e prova erros REST redigidos |
| F-PRIV-005 | location + realtime + app adoption | existentes | backend define audiência/evento; app limita seleção, histórico e render do titular; cada boundary tem owner único |
| F-PRIV-006 | `VR-SEC-004-INVITE-REDEMPTION-001` + `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` | existente + novo | invite possui entropia/hash/redemption server-side; deep-link possui associação, parser, clipboard/storage e logging no app |
| F-PRIV-007 | avatar lifecycle | novo | um owner cobre upload, storage, EXIF, URL/ACL, replace/delete e consumidor app |
| F-PRIV-008 | decisão cross-family + backup restore-purge + lifecycles PAX/DRV/GUA/ADM + tenant offboarding | novos + PAX existente | decisão possui prazo/marco/destino por família; backup possui cópia física/restore-purge; cada lifecycle executa somente a decisão aceita e tenant orquestra sem cascade |
| F-PRIV-009 | Maps S2S precedido pelo Maps data guard | existente + novo guard | guard prova fixture/evidência sanitizada; S2S possui auth, egress, labels/coords e provider boundary |
| F-PRIV-010 | `VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001` + evidence semantic ratchet | novos | bootstrap possui somente trust root/scanner/staging anterior à ativação; ratchet possui schema/validador/waiver/migração de evidência; nenhum sanitiza dado de produto |
| F-PRIV-011 | `VR-SEC-005-SECRET-SCOPE-ROTATION-001` + `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` + `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` | novos | secret card possui owner/escopo/rotação por workload; permissions possui background/Google key declarations; deep-link possui custom scheme/association |
| F-PRIV-012 | Android release boundary | novo | owner único de signing, rede, ambientes, defaults e prova de distribuição |
| F-PRIV-013 | `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` + `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` + `VR-BE-005-PUSH-PROVIDER-ACTIVATION-001` | novos | push token possui D18/bind-unbind; outbox possui D19/template/TTL; provider real depende de ambos e possui apenas egress/credencial/ativação |
| F-PRIV-014 | Maps evidence data guard | novo | owner único do scan repo-wide e classificação de fixtures; D30 permanece risco até a prova |
| F-PRIV-015 | public driver directory | novo | owner único de `GET|HEAD /motoristas` anônimo e compatibilidade de consumidor |
| F-PRIV-016 | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` + `VR-SEC-001-LOCATION-DOMAIN-001` + `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` | novos + existente | core possui REST DRV/PAX/presença sem GPS; location possui REST GPS `/api/localizacao/**`; ratchet só valida cardinalidade e não edita produto |
| F-PRIV-017 | guardian-passenger projection | novo | owner único do subset GUA, autoridade, menor e consumidores correspondentes |
| F-PRIV-018 | driver-passenger projection | novo | owner único de contato/identidade DRV em payloads vistos por PAX |
| F-PRIV-019 | demo media provenance | novo | owner único de inventário/digest/proveniência/quarentena/publicação; conteúdo permanece fechado até autorização |

### Registro de partição dos findings multi-owner

| Partição | Owner | Subescopo exclusivo |
|---|---|---|
| `P-F001-A` | session coordinator | state machine, generation, hooks e composition root; não persiste credencial |
| `P-F001-B` | password/Basic removal | writers/readers de senha e Basic; não implementa refresh/storage |
| `P-F001-C` | secure storage | envelope local, Keychain/Keystore e migração; não define protocolo de refresh |
| `P-F001-D` | refresh rotation | emissão/rotação/reuso/revogação backend; não escolhe storage app |
| `P-F003-A` | location domain | autorização, janela, retenção e non-enumeration REST GPS |
| `P-F003-B` | location realtime | STOMP, subscription, topic, audience e history; não REST |
| `P-F003-C` | REST ratchet | verificação/manifest; zero product path |
| `P-F006-A` | invite redemption | emissão/hash/redeem/replay/expiry server-side |
| `P-F006-B` | verified deep-link | association, URI parsing, storage/clear/log app; não redefine redeem |
| `P-F008-A` | cross-family retention | decisão humana D01–D34; zero implementação |
| `P-F008-B` | backup restore-purge | mecanismo D29 após decisão; não define política |
| `P-F008-C` | lifecycles PAX/DRV/GUA/ADM | conta, referências e cópias por papel; nenhum card fecha outro papel ou tenant |
| `P-F008-D` | tenant offboarding | state machine e orquestração cross-role/provider; não redefine políticas nem implementa lifecycle interno de cada papel |
| `P-F010-A` | evidence v2 bootstrap | tool/policy/scanner mínimo e cerimônia/staging selados no parent; não implementa schema v2 completo |
| `P-F010-B` | evidence semantic ratchet | schema/runner/registry/waiver/migração e enforcement v2; consome e não fabrica o bootstrap |
| `P-F011-A` | secret scope/rotation | metadata/owner/escopo/dual-key/revoke D31; zero rotação live sem autorização |
| `P-F011-B` | mobile permissions | D24/D25, prompt just-in-time e restrição de chave cliente |
| `P-F011-C` | verified deep-link | scheme/verified-link/token URI; não governa Google/secret rotation |
| `P-F013-A` | push-token lifecycle | somente D18: bind/refresh/unbind/revoke/device |
| `P-F013-B` | outbox minimization | somente D19: schema/template/retry/TTL/purge/noop |
| `P-F013-C` | provider activation | join posterior dependente de D18+D19; não bloqueia concluir a outbox |
| `P-F016-A` | core projections | DRV/PAX/presença não-GPS |
| `P-F016-B` | location domain | DTO REST GPS `/api/localizacao/**`, sem entidade/relação aninhada |
| `P-F016-C` | REST ratchet | scanner/coverage/manifest; zero product path |

## Trilhas topológicas de entrega

Estas trilhas registram precedências semânticas mínimas. Elas não criam uma
ordem total entre cards independentes, não substituem `dependsOn` e não
autorizam paralelizar WIP vertical. O próximo card continua sendo escolhido
pelo programa somente quando todas as dependências e o DoR estiverem verdes.

| Trilha | Sequência semântica mínima |
|---|---|
| governança | PRIV-002 → bootstrap PGM-004 → ratchet semântico PGM-004 → gates semânticos dos sucessores |
| REST/lifecycle | cada slice REST necessário precede o lifecycle correspondente; lifecycles PAX/DRV/GUA/ADM convergem no offboarding do tenant e, com os slices, alimentam o ratchet REST terminal |
| sessão | coordinator → password e queue; refresh é independente do coordinator; coordinator + password + refresh → secure storage → push-token; notification outbox é independente do token; provider activation junta push-token + outbox |
| retenção | decisão cross-family → decisão de incidente; cross-family + incidente → lifecycles PAX/DRV/GUA/ADM → offboarding do tenant; todo card que executa purge/delete físico é sujeito a hold; support projection não executa purge |
| localização | aceite ADR-005 → location domain → realtime e Maps S2S → app adoption → integração ponta a ponta |

Cada seta significa apenas que o sucessor depende do predecessor naquela
trilha. Cards em trilhas distintas podem não depender entre si; o limite global
de um card vertical ativo permanece obrigatório.

## Freeze de Impact Analysis dos 31 cards sucessores

O freeze abaixo fixa a superfície que precisa ser detalhada antes de `ready`.
Ele não preenche paths de produto nem conclui DoR. Os 17 cards originados pelo
inventário e os quatorze gaps separados nesta revisão continuam
`analyzed`, com blocker e allowlists de backend/app/Maps vazias.

| Card | Finding | Repositórios candidatos | Contrato/prova obrigatória antes de `ready` |
|---|---|---|---|
| `VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001` | F-PRIV-010 | coordination | trust boundary legado, request/assertion/root pin, scanner mínimo, staging selado, testes positivos/negativos e revisão adversarial independente; não implementa o ratchet v2 |
| `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` | F-PRIV-010 | coordination | schema/versionamento/waiver; testes de ausência, formato, malformação e versão; rollback do ratchet |
| `VR-UX-002-MOBILE-PRIVACY-PERMISSIONS-001` | F-PRIV-011 | app/coordination | manifests/plist/chaves por escopo, UX just-in-time, device matrix, revoke/deny e rollback |
| `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001` | F-PRIV-015 | backend/app/coordination | consumidor, default-deny/remoção, DTO, compatibilidade, contract test e smoke anônimo/autenticado |
| `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001` | F-PRIV-017 | backend/app/coordination | campo↔consumidor GUA, menor/autoridade, DTO versionado, UI/contract tests e rollback |
| `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` | F-PRIV-018 | backend/app/coordination | campo DRV↔consumidor PAX, DTO, compatibilidade tracking/presença e testes de ausência |
| `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` | F-PRIV-016/F-PRIV-003 | coordination | ownership endpoint+método+papel, cobertura dos slices, ratchet contra entidade JPA/campo proibido e manifesto same-version; nenhum product path |
| `VR-OPS-001-SENSITIVE-LOG-REDACTION-001` | F-PRIV-004 | backend/app/maps/coordination | sinks/classes, redaction central, canários positivos/negativos, exceção/provider e rollback observável |
| `VR-SES-002-PUSH-TOKEN-SESSION-LIFECYCLE-001` | F-PRIV-013 | backend/app/coordination | consumo do coordinator, refresh/bind/unbind, aparelho compartilhado, provider noop e device smoke |
| `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` | F-PRIV-001 | app/coordination | envelope de token versionado, Keychain/Keystore, refresh rotativo, backup/reinstall, migração e upgrade/downgrade |
| `VR-APP-002-OFFLINE-QUEUE-SESSION-BOUNDARY-001` | F-PRIV-002 | app/coordination | owner/tenant/TTL/schema, conflito/retry, logout/troca de conta, migração e smoke offline |
| `VR-REL-001-ANDROID-RELEASE-BOUNDARY-001` | F-PRIV-012 | app/coordination | signing não-debug, network/env, secrets, verified links, build/install/upgrade e rollback de distribuição |
| `VR-SES-003-VERIFIED-DEEP-LINK-LIFECYCLE-001` | F-PRIV-006/F-PRIV-011 | backend/app/coordination | App/Universal Links, AASA/assetlinks, token/fragment/log/storage, device proof e fallback seguro |
| `VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001` | F-PRIV-019 | app/coordination | corpus HEAD+dirty isolado por path/digest, origem, redaction, autorização, retenção e publication gate |
| `VR-MAP-001-EVIDENCE-DATA-GUARD-001` | F-PRIV-014 | maps/coordination | tracked+generated outputs, canários, symlink/binário/manifest, proveniência independente e testes negativos |
| `VR-PRIV-003-CROSS-FAMILY-RETENTION-DECISION-001` | F-PRIV-008 | coordination | D01–D34, stores/providers/backups/mídia, owner/célula, alternativas, digest e aceite humano imutável |
| `VR-OPS-002-BACKUP-RETENTION-RESTORE-PURGE-001` | F-PRIV-008 | backend/coordination | catálogo/chaves/RPO-RTO, prune, restore isolado, tombstone/purge, corrupção e autorização remota separada |
| `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` | F-PRIV-007 | backend/app/coordination | nome opaco, normalização/EXIF, ACL/URL, replace/delete, volume/backup e compatibilidade app |
| `VR-SES-002-SESSION-LIFECYCLE-COORDINATOR-001` | F-PRIV-001/002/013 | app/coordination | state machine, generation, hooks ordenados/idempotentes, A→logout→B, callback stale, timeout e zero segredo/PII |
| `VR-SES-003-APP-PASSWORD-BASIC-REMOVAL-001` | F-PRIV-001 | app/coordination | writers/readers de senha e Basic, bearer-only, migração one-way, token ausente sem rede e smoke por papel |
| `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | F-PRIV-016 | backend/app/coordination | endpoints centrais DRV/PAX/presença sem GPS, DTOs por papel, consumer fixtures, mass assignment, non-enumeration e rollout por família |
| `VR-BE-005-NOTIFICATION-OUTBOX-MINIMIZATION-001` | F-PRIV-013/D19 | backend/app/coordination | schema/template tipado, expand/contract, recipient interno, retry/idempotência, TTL/purge e provider noop |
| `VR-BE-005-PUSH-PROVIDER-ACTIVATION-001` | F-PRIV-013/D18/D19 | backend/app/coordination | provider/credencial/egress default-off, dependência conjunta token+outbox, canary sintético, opt-in/out aplicável, rollout e kill switch |
| `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` | D20 | backend/app/coordination | endpoint×papel×campo, free-text limitado, DTO summary/detail, auditoria sem mensagem, compatibilidade e retenção separada |
| `VR-SEC-005-SECRET-SCOPE-ROTATION-001` | F-PRIV-011/D31 | backend/app/maps/coordination | metadata por ambiente/workload/provider, escopo, owner, dual-key/revoke, fixtures falsas e autorização separada para rotação live |
| `VR-PGM-004-FUTURE-DATA-COLLECTION-GATE-001` | D32 | coordination/backend/app/maps | manifest `dataImpact`, diff/scanners, decisão/digest, waiver expirável, fixtures positivas/negativas e zero coleta real |
| `VR-PRIV-003-INCIDENT-HOLD-DECISION-001` | H-011/D01–D34 | coordination | gatilho, autoridade, escopo, hold/release/restore/provider/auditoria/comunicação todos `PENDING_*`; nenhum path de produto |
| `VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001` | D01–D34 por aplicabilidade DRV | backend/app/coordination | dois deletes, matriz por família/overlay, auth/realtime/providers, anonimização/exclusão, retenção/export, non-enumeration, compatibilidade e rollback |
| `VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001` | D01–D34 por aplicabilidade GUA | backend/app/coordination | delete GUA real, vínculos/tickets/sessão/GPS/outboxes/avatar/backups, matriz por família, non-enumeration, reconciliação e rollback |
| `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001` | D01–D34 por aplicabilidade ADM | backend/app/coordination | offboarding/transferência ausentes, último ADM, reviewer/creator, sessão/outboxes/avatar, matriz por família e continuidade do tenant |
| `VR-PRIV-003-TENANT-OFFBOARDING-001` | D01–D34 por boundary tenant | backend/app/maps/coordination | state machine ACTIVE→CLOSING→CLOSED, drain de membros/operação/providers, zero cascade, restore-purge, rollout e reabertura segura |

### Ownership sem sobreposição dos boundaries

A unidade de ownership é endpoint + método + papel. O WIP global evita edição
simultânea, mas não substitui esta separação contratual.

| Boundary | Owner vertical | Limite preservado |
|---|---|---|
| `POST /motoristas` e `POST /passageiros` | cadastro público existente | fora dos slices de projeção |
| `GET|HEAD /motoristas` | `VR-SEC-002-PUBLIC-DRIVER-DIRECTORY-001` | diretório anônimo/default-deny |
| `GET /motoristas/{id}` — PAX/GUA/DRV/COMPANY_ADMIN autenticados no tenant atual | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | audiência observada é ampla; owner deve congelar DTO e autorização mínima |
| `GET|POST /company-admins/motoristas` e `PUT /company-admins/motoristas/{id}` — COMPANY_ADMIN | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | DTO por tenant; delete é boundary separado |
| `DELETE /motoristas/{id}` e `DELETE /company-admins/motoristas/{id}` — COMPANY_ADMIN | `VR-PRIV-003-DRIVER-DATA-LIFECYCLE-001` | autorização, referências, anonimização/exclusão, retenção e non-enumeration |
| `DELETE /company-admins/responsaveis/{id}` — COMPANY_ADMIN | `VR-PRIV-003-GUARDIAN-DATA-LIFECYCLE-001` | vínculo GUA↔PAX, sessão/tickets/GPS/outboxes/avatar/backups, reconciliação e non-enumeration |
| offboarding/transferência da conta COMPANY_ADMIN — endpoint ainda ausente | `VR-PRIV-003-COMPANY-ADMIN-DATA-LIFECYCLE-001` | conta pessoal e autoridade administrativa; nunca fecha tenant por inferência |
| fechamento/reabertura de company/tenant — endpoint ainda ausente | `VR-PRIV-003-TENANT-OFFBOARDING-001` | state machine e orquestração de todos os lifecycles; nenhuma cascade |
| `GET /passageiros` e `GET /passageiros/{id}` — GUA somente para vinculados | `VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001` | autoridade, menor e subset GUA |
| `GET /passageiros` e `GET /passageiros/{id}` — COMPANY_ADMIN/PAX; PAX somente próprio | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | DTO por papel/tenant; PAX fail-closed no próprio ID |
| `PUT /passageiros/{id}` — COMPANY_ADMIN efetivo; GUA é rejeitado pela mutation policy | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | request allowlisted, tenant e non-enumeration |
| `DELETE /passageiros/{id}` — COMPANY_ADMIN efetivo; GUA é rejeitado pela mutation policy | `VR-PRIV-003-PASSENGER-DATA-LIFECYCLE-IMPLEMENTATION-001` | autorização, referências, exclusão/anonimização e retenção; fora de projeções |
| `POST /presencas`, `GET /presencas`, `GET /presencas/passageiro/{passageiroId}` e `DELETE /presencas/{id}` — PAX próprio/GUA vinculado/COMPANY_ADMIN | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | DTO request/response por tenant, owner e papel |
| `GET /presencas/data` — COMPANY_ADMIN; `GET /presencas/motorista/{motoristaId}` — DRV próprio/COMPANY_ADMIN | `VR-SEC-002-CORE-ENTITY-PROJECTIONS-001` | projeção mínima por data/motorista, sem relação JPA aninhada |
| passageiro recebe motorista em serviços/viagens | `VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001` | contato DRV ausente |
| `POST /support/tickets`, `GET /support/tickets`, `GET /support/tickets/{id}` | `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` | PAX/GUA; somente próprio ticket e tenant, create/summary/detail |
| `GET /company-admins/support/tickets`, `GET /company-admins/support/tickets/{id}`, `POST /company-admins/support/tickets/{id}/responses`, `POST /company-admins/support/tickets/{id}/status` | `VR-SEC-002-SUPPORT-MINIMUM-PROJECTION-001` | `COMPANY_ADMIN` do próprio tenant; nenhum papel dedicado/assignment inferido |
| `POST /api/localizacao/{idMotorista}` — DRV próprio | `VR-SEC-001-LOCATION-DOMAIN-001` | writer trip-scoped, janela, DTO mínimo e non-enumeration |
| `GET /api/localizacao/{idMotorista}` — DRV próprio/PAX autorizado/GUA vinculado/COMPANY_ADMIN do tenant | `VR-SEC-001-LOCATION-DOMAIN-001` | reader trip-scoped, freshness, DTO mínimo e non-enumeration |
| `GET /api/localizacao/{idMotorista}/historico` — hoje DRV próprio/GUA vinculado/COMPANY_ADMIN; contrato alvo nega todos | `VR-SEC-001-LOCATION-DOMAIN-001` | endpoint geral de histórico deve ficar opaco/indisponível |
| `/ws-location` e destinos STOMP | `VR-SEC-003-LOCATION-REALTIME-001` | autorização e projeções realtime |
| consumo/render de GPS Flutter | `VR-APP-001-LOCATION-ADOPTION-001` | estado e acessibilidade no app |
| `POST /account/me/avatar` e projeção em `GET|PATCH /account/me` — titular autenticado; `GET /uploads/avatars/**` hoje anônimo até replacement | `VR-BE-004-AVATAR-PRIVACY-LIFECYCLE-001` | upload/download, storage, URL/chave, acesso e purge; acesso anônimo é gap, não contrato aceito |
| regra cross-boundary e cobertura | `VR-SEC-002-REST-MINIMUM-PROJECTIONS-001` | coordenação/ratchet; zero código de produto |

Antes de qualquer um avançar, seu próprio card precisa substituir repositórios
candidatos por paths exatos, declarar contratos, testes, smoke same-version,
rollout e rollback. A matriz não autoriza essa transição.

### Quarentena conservadora de F-PRIV-019

Além da mídia rastreada, o freeze precisa catalogar por path relativo, tipo,
digest, tamanho e destino — sem abrir conteúdo — as superfícies preexistentes:

- `docs/analysis/evidence/visual-matrix/**`;
- `docs/faculdade/entrega-final-2026-06/evidencias/**`;
- `docs/faculdade/entrega-final-2026-06/rendered-report/**`;
- `docs/faculdade/entrega-final-2026-06/video/**`;
- `.playwright-mcp/**`;
- `src/aplicativo-motorista/integration_test/recording_first/**`;
- `src/aplicativo-motorista/marketing/vanrides-product-video-remotion/**`;
- `src/aplicativo-motorista/scripts/capture_recording_first_mobile_takes.sh`;
- `src/aplicativo-motorista/tool/recording_first_admin_takes.mjs`;
- `src/aplicativo-motorista/docs/superpowers/plans/2026-06-23-recording-master-v4-audio-led-edit.md`;
- `src/aplicativo-motorista/docs/superpowers/specs/2026-06-23-recording-master-v4-audio-led-edit-design.md`.

Screenshots, renders, gravações e publicação pertencem a F-PRIV-019. Logs e
page captures textuais também propagam F-PRIV-010/F-PRIV-004. Nenhum conteúdo,
OCR, exclusão, reescrita de histórico ou publicação é autorizado por PRIV-002;
asset sem origem/uso/autorização comprovados permanece quarentenado.

## Fila de decisões humanas

| ID | Decisão a preparar | Autoridade necessária | Estado |
|---|---|---|---|
| H-001 | finalidade humana e necessidade campo a campo D01–D34 | Produto + Privacidade | `PENDING_PRODUCT + PENDING_PRIVACY` |
| H-002 | transparência e decisão jurídica aplicável, inclusive se consentimento é ou não o mecanismo correto | Privacidade + Jurídico/DPO | `PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| H-003 | menor, autoridade GUA, comunicação e acesso a endereço/GPS | Produto + Privacidade + Jurídico/DPO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| H-004 | janela/atores/retention de localização live/histórica e Maps | Produto + Privacidade + Jurídico/DPO + SEC/OPS | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| H-005 | prazo, marco, destino e exceção normal por família/store; hold fica exclusivamente em H-011 | Produto + Privacidade + Jurídico/DPO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| H-006 | egress, termos, acesso e delete por provider: Google, Maps engines, Resend e push | Produto + Privacidade + Jurídico/DPO + SEC/OPS | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| H-007 | contrato de solicitação do titular: autoridade, export, estados, SLA e comunicação | Produto + Privacidade + Jurídico/DPO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| H-008 | envelope AE1, acesso, pseudonimização, prazo e purge de auditoria | Privacidade + SEC/OPS | `PENDING_PRIVACY + PENDING_SECURITY_OPS` |
| H-009 | catálogo, criptografia, RPO/RTO, prune e restore-purge de backup; hold fica exclusivamente em H-011 | Produto + Privacidade + Jurídico/DPO + SEC/OPS | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| H-010 | origem, autorização, redaction, retenção e publicação de mídia | Produto + Privacidade + Jurídico/DPO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |
| H-011 | incidente/hold: gatilho, autoridade, escopo, conservação, release, comunicação e auditoria | Privacidade + Jurídico/DPO + SEC/OPS | `PENDING_PRIVACY + PENDING_LEGAL_DPO + PENDING_SECURITY_OPS` |
| H-012 | revisão final, digest, timestamp e declaração de que aceite não concede GO técnico | Produto + Privacidade + Jurídico/DPO | `PENDING_PRODUCT + PENDING_PRIVACY + PENDING_LEGAL_DPO` |

## Handoff para ADR-002 e decisões sucessoras

ADR-002 não pode combinar finalidade técnica e decisão jurídica numa única
célula. O handoff deve separar, por linha:

1. IDs Dxx/Fxx, fontes e cards;
2. finalidade/necessidade proposta por Produto;
3. minimização/acesso/auditoria recomendados tecnicamente;
4. transparência e decisão aplicável por Privacidade/Jurídico-DPO;
5. prazo, marco, destino, export, provider, backup e incidente/hold;
6. valor candidato separado do `acceptanceStatus=PENDING_*`;
7. autoridade, revisão, digest e timestamp somente no registro de aceite.

Exportação do titular, egress para provider, backup/PITR/restore e
incidente/hold são linhas distintas. Nenhum prazo, base, consentimento,
exceção, provider real ou obrigação é inferido por esta matriz.

## Limites e rollback

- nenhum arquivo de produto, dado, ambiente remoto, mailbox, analytics, backup
  real, log de produção ou GPS real foi aberto/alterado; nenhum valor de
  credencial foi registrado ou reutilizado, e a verificação final de config
  local limita-se a presença/ignore; nenhum conteúdo de mídia foi aberto;
- nenhuma recomendação muda estado dos 19 findings ou de seus cards;
- nenhuma célula humana está aceita;
- G6/G7, piloto e produção continuam `HOLD`.

Rollback é reverter esta matriz e seus handoffs coordenadores. Não remover,
anonimizar, migrar ou reter dado para “reverter” documentação. Uma divergência
reabre a revisão e produz novo digest antes de qualquer aceite.
