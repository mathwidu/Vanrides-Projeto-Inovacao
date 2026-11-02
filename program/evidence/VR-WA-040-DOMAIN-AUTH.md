# Recibo histórico — VR-WA-040-DOMAIN-AUTH

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-040-DOMAIN-AUTH.md`. SHA-256 do original: `7d90f2a9665ed546e9b4caa6e456b6f103d5e2feb3c3bb238d2fede6da44023c`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

Data: **31/10/2026**. **PASS local** de identidade, autorização e pareamento.
Não é entrega do MVP completo, teste Android/iPhone ou integração WhatsApp real.
O desenvolvimento local prossegue conforme ADR-007, com030 ainda pendente.

## Fontes e artefatos

- Coordenação/smoke/evidências: `90c488838174be7b699bdad757d9b2cf4197058f`;
  fechamento de estado posterior não altera backend/JAR/script avaliados.

- Backend técnico: `8b5676b1868b30785497cf81a7ff1d67db1d0332`, checkout limpo
  durante build, gate e smoke; tree `2667a8a24f3036d02ed307fdb008a9800726bed4`.
- Backend documental: `a25978a529a797d045ed6898791e24a1bf8459f6`, somente três
  documentos/QA/backlog, sem substituir a fonte técnica avaliada.
- JAR SHA256: `0e18437e02461a6d892b4843368df88504e2a2c04947efc6b015dbe510147502`.
- Smoke da coordenação: `integrations/whatsapp/scripts/040-foundation-smoke.mjs`,
  SHA256 `1f37e90ec76790bdf162bae625082137a4ff34bfeff84014ef6befa874b86b69`.
- App permanece `b89e0fa532e437beb680a963ff31b84345c61b8e`; nenhum teste ou
  alteração de aplicativo/dispositivo foi realizado nesta etapa040.

## Revisões e reprodução históricas

- `coordination:90c488838174be7b699bdad757d9b2cf4197058f`
- `backend:8b5676b1868b30785497cf81a7ff1d67db1d0332`
- `backend:a25978a529a797d045ed6898791e24a1bf8459f6`

- Comando registrado na origem: `./mvnw -B -q clean package -DskipTests`.
- Comando registrado na origem: `node integrations/whatsapp/scripts/040-foundation-smoke.mjs <runtime privado> <SHA técnico>`.
- Comando registrado na origem: `node --test integrations/whatsapp/scripts/040-foundation-smoke.test.mjs`.
- Comando registrado na origem: `python3 scripts/program_control_test.py`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
