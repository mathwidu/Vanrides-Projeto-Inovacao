# Histórico importado em 2026-11-02

A migração para `mathwidu/Vanrides-Projeto-Inovacao` preserva o estado do programa,
os contratos dos cards, os gates G0–G7 e as decisões. A conclusão e as novas provas estão em
`program/evidence/VR-PGM-002-ACADEMIC-MIGRATION-001.md` e no registro de migração.

## Evidências e acervo

As evidências anteriores são recibos públicos sanitizados: cada recibo identifica
o caminho de origem e seu SHA-256, cita as revisões históricas quando aplicável e
distingue resultados registrados de execução atual. Os JSONs são recibos, não os
manifests operacionais originais. Caminhos pessoais, diagnósticos de dispositivos,
endpoints de laboratório e logs completos não foram publicados.

Os originais e o inventário por arquivo ficam no acervo local do proprietário,
fora do Git público. Não existe link público para esse acervo; sua disponibilidade
não é requisito para testar o monorepo. A procedência pode ser conferida com o
proprietário pelo hash. O inventário da migração registra inclusões, adaptações e
exclusões. Os verificadores históricos `verify_vr_priv_001.py` e
`verify_vr_priv_002.py`, vinculados à topologia antiga e ao acervo, não integram os
gates atuais e foram preservados somente nesse acervo.

## Estado e validade

O estado `done` anterior continua histórico e ligado aos commits indicados em
cada card. Não foi atribuído um PASS do novo SHA às evidências importadas. Os
mesmos critérios de evidência específica, revisão completa, data, comandos,
limites, checklists e dependências continuam no controlador.

BASELINE, ROADMAP, LEGACY_CROSSWALK e `workspaces/restoration-m5.json` são snapshots
históricos. Planos fora do MVP permanecem registrados e abertos quando aplicável;
não houve conclusão automática, aceite de piloto/produção ou retomada de iOS.
As baselines Git antigas ficam em `origin.baseline` no catálogo dos módulos.

Piloto e produção: HOLD. Testes físicos de iOS: pausados. Mapas: adiado. Nenhum
resultado desta importação autoriza deploy ou promoção.
