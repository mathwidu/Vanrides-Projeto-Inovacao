# VR-REL-001-DEMO-MEDIA-DATA-PROVENANCE-001 — Provar e proteger dados em mídia de demonstração

## Resultado esperado

Cada MP4, screenshot, áudio/render e fonte de demo possui proveniência, titular,
classificação, autorização, redaction, retenção e destino de publicação. Mídia
sem prova sintética/pública fica quarentenada e não entra em release, apresentação
ou artifact; Git history recebe decisão explícita, nunca limpeza destrutiva tácita.

## Estado

`analyzed` — finding `F-PRIV-019` P1 conservador; 21 MP4 e 12 imagens estão no
HEAD e há mídia preexistente adicional no worktree, mas conteúdo, corpus final,
proveniência e autorização não foram validados.

## Dono operacional

`REL/PRIV`, com `Produto`, `SEC`, `APP`, `Jurídico/DPO` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-019`, `R-007`, `R-010`, `R-021`, `VR-REL-001`, G6 e G7.

## Impact Analysis preliminar

### Mudanças técnicas

- inventariar mídia rastreada, worktree, renders, exports e destinos publicados;
- incluir, por path relativo/tipo/digest/tamanho/destino e sem abrir conteúdo,
  `docs/analysis/evidence/visual-matrix/**`,
  `docs/faculdade/entrega-final-2026-06/evidencias/**`,
  `docs/faculdade/entrega-final-2026-06/rendered-report/**`,
  `docs/faculdade/entrega-final-2026-06/video/**`, `.playwright-mcp/**`,
  `src/aplicativo-motorista/integration_test/recording_first/**`,
  `src/aplicativo-motorista/marketing/vanrides-product-video-remotion/**`,
  `src/aplicativo-motorista/scripts/capture_recording_first_mobile_takes.sh`,
  `src/aplicativo-motorista/tool/recording_first_admin_takes.mjs`,
  `src/aplicativo-motorista/docs/superpowers/plans/2026-06-23-recording-master-v4-audio-led-edit.md`
  e
  `src/aplicativo-motorista/docs/superpowers/specs/2026-06-23-recording-master-v4-audio-led-edit-design.md`;
- produzir manifest por asset com origem, captura, fixture, titulares e digest;
- revisar conteúdo somente depois de autoridade e processo humano seguro; nenhum
  conteúdo/OCR é aberto ou copiado durante o freeze documental;
- redigir/recapturar com fixtures sintéticas e guard automático de publicação;
- decidir retenção e tratamento do Git history com autorização separada;
- impedir status `real` de significar “dado real autorizado”.

### Contratos afetados

Pipeline Remotion/captura, Git/LFS/artifacts, apresentação acadêmica, marketing,
publicação e retenção. Não muda produto runtime.

### Riscos e mitigação

Revisão pode expor mais dado ou reescrita de history destruir trabalho. Limitar
acesso, não copiar frames sensíveis, quarentenar e pedir autorização antes de
delete/history rewrite/publicação.

### Validação mínima

Contagem e digest de assets, cobertura 100% do manifest, revisão humana por
quatro olhos, scans metadata/OCR local sanitizado, testes de publish guard e
prova positiva/negativa com fixtures próprias.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- owners de mídia/privacidade e destinos atuais identificados;
- autorização explícita antes de abrir conteúdo sensível, excluir, reescrever
  Git history ou alterar publicação externa.

## Escopo

- mídia de demo/marketing, manifests, renders, Git/artifacts e publication guard.
- screenshots/renders/publicação roteiam F-PRIV-019; logs/page captures textuais
  também roteiam F-PRIV-010/F-PRIV-004.

## Fora de escopo

- publicar, apagar, reescrever history ou declarar consentimento neste card de
  preparo; conteúdo não é anexado à evidência.

## Definition of Ready

- [ ] HEAD, alterações/untracked do worktree, renders e destinos estão
  inventariados por path/digest em worktree isolada;
- [ ] taxonomy real/sintético/público, autorização e retenção congeladas;
- [ ] paths, ferramentas, testes, quarantine, rollout e rollback definidos;
- [ ] autoridade para qualquer mutação destrutiva/externa obtida separadamente.

## Critérios de aceite preliminares

- [ ] 100% dos assets/fontes/exports têm digest, origem e classificação;
- [ ] sintético/público é provado, não inferido de nome ou `status: real`;
- [ ] PII/GPS/segredo não aparece em mídia, metadata, caption ou artifact público;
- [ ] asset sem prova fica bloqueado e ausente do render/publicação;
- [ ] retenção e Git history possuem decisão explícita e reversível;
- [ ] guard falha para manifest ausente/divergente e passa fixture válida.

## Rollback

Retirar/quarentenar o artifact e bloquear publicação futura, preservando cópia
restrita somente quando necessário à decisão. Não apagar nem reescrever Git sem
autorização e backup verificável.
