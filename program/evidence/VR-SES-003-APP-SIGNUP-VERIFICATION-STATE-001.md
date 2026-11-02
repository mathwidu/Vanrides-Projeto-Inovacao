# Recibo histórico — VR-SES-003-APP-SIGNUP-VERIFICATION-STATE-001

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-SES-003-APP-SIGNUP-VERIFICATION-STATE-001.md`. SHA-256 do original: `9848fb92f735d400e24442c43e3c8ff7c5054a21e5c3a46d62c990812d837a15`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS, HOLD, WAITING_HUMAN pertencem às respectivas revisões históricas e não aprovam este checkout.

## Veredito

`PASS_AUTOMATED / WAITING_HUMAN` em 2026-07-18. O app final limpo passou nos
testes focados, análise estática, quality ratchet, gate canônico completo e
E2E dos cadastros de motorista e passageiro em iPhone Simulator contra um
único JAR backend preservado.

O card ainda não está `done`: a confirmação humana com VoiceOver dos dois
ramos continua obrigatória. Piloto e produção permanecem em `HOLD`; esta
evidência não autoriza push, deploy, banco remoto ou promoção.

## Identidade das versões finais

## Revisões e reprodução históricas

- `app:9e42d48738e5223bdf663ae962325b131863db55`
- `app:b61b2cf5514911be96e8db49c316fc5526d31631`

- Comando registrado na origem: `flutter analyze`.
- Comando registrado na origem: `git diff --check`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
