# Recibo histórico — VR-SEC-004-EMAIL-NAMESPACE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SEC-004-EMAIL-NAMESPACE-001.md`. SHA-256 do original: `51ad86717210973ecade0d6bbf7ea0a98b7bbf36b7e28bd7d4c4faa1804669ae`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_LOCAL / READY_TO_CLOSE` em 2026-07-17. O backend `4824f54` fecha a
invariável local no mesmo commit/tree com gate canônico, matriz PostgreSQL,
RBAC, smoke funcional do mesmo JAR e consumidores Flutter focados. O P2 de
confirmação concorrente encontrado em `47157c2` foi corrigido, provado em cinco
cenários PostgreSQL e reavaliado de forma independente com `GO`.

Este resultado não executa preflight ou reconciliação em banco persistente e
não autoriza push, deploy, migration remota, release, piloto ou produção.
`pilot=HOLD` e `production=HOLD` permanecem inalterados.

## Freeze da versão avaliada

## Revisões e reprodução históricas

- `532152a3843ba9eaf27cda1942ee0b779952c2ec`
- `426b01383c3f2b1cc514bbb741fee95f0828de4b`
- `e4d08aa697a1dcb1f40c82b2cd768dba609a6120`
- `cdb334f026d728606a7f9977995c769ae36be384`
- `47157c29af868b7aee4dfc9fa881069b79cec965`
- `59a9705252f8bf384f1e9fd3a2b7bd5faf8763cd`
- `4824f54abab0ad978502cb2e056115c5c9348a28`
- `2fe351b8e28606e69aa121d2fe5f1ff2679b36d5`

- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_rbac_suite.sh`.
- Comando registrado na origem: `VANRIDES_EXPECTED_BACKEND_SHA=4824f54abab0ad978502cb2e056115c5c9348a28 VANRIDES_EXPECTED_POSTGRES_IMAGE_ID=sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777 ./scripts/qa_email_namespace_smoke.sh`.
- Comando registrado na origem: `flutter pub get`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
