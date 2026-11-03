# Recibo histórico — VR-WA-080-DRIVER-ROUTINES

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-080-DRIVER-ROUTINES.md`. SHA-256 do original: `5310656fcd296fe8e9327b50b1ae98988fc9e6e21d5fcce7ae908a1329466f98`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, FAIL pertencem às respectivas revisões históricas e não aprovam este checkout.

Data de conclusão local: 2026-10-31. **PASS** para cadastro, rotinas e emissão
de convites do motorista no laboratório. A jornada inclui API Spring, PostgreSQL,
worker e transporte Evolution simulado. O registro verificável contém os hashes,
os recibos de limpeza e os resultados das tentativas anteriores.
Manifest da execução (referência histórica no original).

## Fonte avaliada

- Backend técnico `aec364e2265bd1a7988b5c9c7fb06533ccb25140`, árvore
  `5793da02005e21edc831e0e96c6edc4799ab99ee`, limpa no início e ao final do CI.
- Backend documental `8411f9ac47fb59517e57aa7165d97d7c8e55c66e`.
- JAR dos dois ensaios `b7ec8bd01c5f98bbcce65fe759b4b60af9fcaf6d57c4d629327bae56a0389a9e`.
- Coordenação técnica `9dc92ff9be57a4aea8281de1b05a43781d7d6303`. O
  manifest confere os hashes do agente, adapter, worker e executor de cada rodada.
  A imagem `sha256:2d5dd306895baca1dada323db325620ff30150fe4180492e7fbd232773a3a3df`
  foi testada em `085b5970debef52c636af70ec8f0cc018df4a95a`.
- App observado `9fda8dad7c479443996ce026bdf44e593624bee1`.

## Comportamento entregue

## Revisões e reprodução históricas

- `coordination:085b5970debef52c636af70ec8f0cc018df4a95a`
- `coordination:d4b37115ea96fe9d1cab6339c2772477f1c84610`
- `backend:bd9bbb73ac7256f58f935936664197c53f0d8ec6`
- `backend:930aeb992cbf64fc5bccaf8eea0c7ea4a87a18e3`
- `backend:4e118268c75b4c2a5f63365e0198c8bad5ed2252`
- `coordination:5c034f985737c7a6e008c1774b66be55935c2d41`
- `coordination:9dc92ff9be57a4aea8281de1b05a43781d7d6303`
- `backend:b1d8081a3786d9b8bc7fa43da105bfa6a7fd3456`
- `backend:502e4adeaafa42ff7e811555213fa234bb138725`
- `coordination:f77dd4b094694a51b3f585670705c9737cc7bf26`
- `backend:1e022e83c4d2d0980874dbd2d105121b5950491e`
- `backend:aec364e2265bd1a7988b5c9c7fb06533ccb25140`
- `backend:8411f9ac47fb59517e57aa7165d97d7c8e55c66e`
- `coordination:56ffd5e200cf5d40e2f0b5e01d9f0f87bf1cdb05`

- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1`.
- Comando registrado na origem: `node --test`.
- Comando registrado na origem: `node integrations/whatsapp/scripts/080-driver-smoke.mjs <runtime-privado> <SHA>`.
- Comando registrado na origem: `python3 integrations/whatsapp/scripts/070-n8n-smoke.py`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
