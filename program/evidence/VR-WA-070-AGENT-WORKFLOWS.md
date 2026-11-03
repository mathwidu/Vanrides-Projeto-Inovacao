# Recibo histórico — VR-WA-070-AGENT-WORKFLOWS

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-070-AGENT-WORKFLOWS.md`. SHA-256 do original: `1fc4e465d69b976c3224f0a974552945543b9a4056cee5b79ad9ad73ffaa5a33`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, FAIL pertencem às respectivas revisões históricas e não aprovam este checkout.

Data: 2026-10-31.
Estado: implementação e validação locais070 concluídas, incluindo acesso à
OpenAI e avaliação real na fonte f5377d0. A atualização final abaixo substitui
as pendências históricas de chave/modelo. Piloto/produção permanecem HOLD.

Manifest sanitizado (referência histórica no original) contém hashes,
versões, resultados e limites. Runbook (referência histórica no original)
e RD29 (referência histórica no original)
registram configuração, decisões e recuperação.

## Checkpoint determinístico anterior — fonte e resultado

- Backend técnico limpo: `aa3d97712607d0538d1dbadde6d164581a3ea312`.
- Tree: `15afe7a2189a5d9aa52521c9bae6bcd92b1ff255`.
- Backend documental posterior: `013aaa6ae2cf4871b0db773bbfc4050e78a4ebce`.
- JAR: `39daa25fef852f4b50a91d5385d9ea0d35bef4f0792411b69dc190010affec60`.
- Coordenação técnica: `12ccde9a4ee6aff75242809a561c27bd2f847753`.
- App preservado: `b89e0fa532e437beb680a963ff31b84345c61b8e`.
- SHA de cada asset Node/auxiliar e dos oito exports n8n consta no manifest.
  O hash da entrada do smoke não substitui os hashes dos helpers.

## Revisões e reprodução históricas

- `coordination:f5377d0ee14b787dc384213eb2c82fab68a4e8fb`
- `coordination:09825a6751fccb509e718e5ad2458bfe670af6ad`
- `coordination:384ee10201becf6daca22025ed9ebf597122010a`
- `coordination:89cfa9f9fbcb1152eef6d2e4b87547a4bb626113`
- `backend:aa3d97712607d0538d1dbadde6d164581a3ea312`
- `backend:367ec0fcb9b154bc03b387db97bf39719b2b46d1`
- `backend:013aaa6ae2cf4871b0db773bbfc4050e78a4ebce`
- `coordination:12ccde9a4ee6aff75242809a561c27bd2f847753`

- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1`.
- Comando registrado na origem: `node --test --test-concurrency=1`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
