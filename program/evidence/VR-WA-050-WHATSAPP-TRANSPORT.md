# Recibo histórico — VR-WA-050-WHATSAPP-TRANSPORT

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-050-WHATSAPP-TRANSPORT.md`. SHA-256 do original: `0bcd7bf4592323635df0bdb81eab84d1f24bc084166f40e6e102360a51e666fa`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

Data: **2026-10-31**. **PASS local**. Filas duráveis, normalização Evolution,
processamento serial e envio controlado concluídos. Não equivale à entrega do
MVP, importação n8n ou teste WhatsApp real. iPhone físico permanece pausado.

## Proveniência

- Backend técnico: `b69a39b4e9ab2b83de022af6df3ff01f9614bcd0`, tree
  `99378f63c17e6d37e6d89cc0b2d4783943c9e731`; checkout limpo durante build,
  gate e smoke. Implementação em `de3aa5ffb316410f615bcc44b0c4f06242aa784c`;
  b69 corrige duas expectativas de versão da migration nos testes legados.
- Fechamento documental backend: `6b884427e85ac9129f1023597b8b2af2375512c5`,
  sem mudança de código, contrato ou artefato avaliado.
- JAR SHA256: `3ecb69a298790f7ee973b8e9e7c680c74c223e9147fa4398a49070ceebcebe92`.
- Script smoke050 SHA256:
  `904f0ad661ed7ffd4ce9e86bffaa24e6a14622b9b03ccc2ab98b9340da9813a1`.
- Adapter Evolution SHA256:
  `80f2482e8fc51405a7c37ca2ccc8864a7fcbe988095d64c9d4ac644ad697decd`.
- App preservado em `b89e0fa532e437beb680a963ff31b84345c61b8e`.

O manifest sanitizado (referência histórica no original) registra
commit da coordenação, versões, hashes, recibo e recursos. Artefatos, logs
originais, configurações e credenciais sintéticas ficam em diretório privado,
fora de Git. Nenhum GPS, telefone ou segredo real está nesta evidência.

## Revisões e reprodução históricas

- `coordination:79702396c3f3514f898054e439946dee31aae645`
- `backend:de3aa5ffb316410f615bcc44b0c4f06242aa784c`
- `backend:b69a39b4e9ab2b83de022af6df3ff01f9614bcd0`
- `backend:6b884427e85ac9129f1023597b8b2af2375512c5`

- Comando registrado na origem: `VANRIDES_QA_REQUIRE_CLEAN=1`.

- Comando histórico transcrito do original (sem executar nesta migração): `VANRIDES_BACKEND_BASE_URL=http://127.0.0.1:18082 VANRIDES_QA_REQUIRE_CLEAN=1 ./scripts/qa_gate_card.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
