# Recibo histórico — VR-WA-030-GPS-PROOF-ios-automation

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-030-GPS-PROOF-ios-automation.md`. SHA-256 do original: `dbebbd814c7b3f7621abb3a36f36807dd1914b360e8892fa606016f027ee80ef`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

Data: 31/10/2026. Estado: testes físicos pausados pelo proprietário;
preparação no Mac. Não é aceite do GPS nem do MVP.

## Pedido e análise de impacto

O proprietário pediu que configuração, início, parada e leitura dos testes no
iPhone fossem operados pelo Mac, evitando cópia manual de credenciais e etapas
intermediárias no chat. Instalação e testes locais já estão autorizados. A frente
permanece no card030 e usa o laboratório HTTPS já publicado, sem nova release
na VPS nem alteração de n8n, Evolution, backend ou mensagens reais.

O executor fica em `integrations/whatsapp/gps-spike/ios-ui-lab/`, como projeto
XCUITest separado. Deve operar a interface normal do release instalado, sem
adicionar comandos secretos, bypass de consentimento, coordenadas simuladas ou
dependência de testes ao app. A implantação do runner não deve reinstalar,
desinstalar ou limpar o app driver. Credenciais, IDs dos dispositivos, signing
team, artefatos e relatórios completos ficam no diretório privado fora do Git.

## Revisões e reprodução históricas

- `app:9fda8dad7c479443996ce026bdf44e593624bee1`
- `coordination:95fe5d99a7db9f469a25d47200b1f2bf91e7781d`
- `coordination:4989a1d17f1cae5861b6baad8bc01268f9128db6`
- `app:24969587151857ba4c1dadc7e4bd0616546f3e96`
- `app:8effd7d11bbb9a1b7b8c56e9ec744e1664af1f99`
- `app:36316c7942b9eb41bcd53154ef8b2f649dab2b85`
- `app:b2fef0c2a751f147bc689e1db73e7362271b9956`
- `app:1dc7c70da27ccfe7bc581fb5ee4f48640f9df884`
- `app:f7aafd0d77f68dda587d46b22c261afc5658c765`
- `app:230e84c588f5ccc45fe05c116d549ecf69136653`
- `app:97936a8cf77d8be3ddb55e767bb4053ea1cb259d`
- `app:a1f071c951cfd21e00162b0375385eba195f147d`
- `app:2e7ad31877a0cb758b2a4b8dd17eee4011f07d2f`
- `coordination:d676a906d5f947fb5c19dce4496c0910ee1958be`
- `coordination:e707d666ec613bf27152d5b7ce9d320dd761585c`
- `app:9b3c1166877f10c7a106f33d23ed3ef5b555d2d7`
- `app:b89e0fa532e437beb680a963ff31b84345c61b8e`
- `coordination:df9492a6d57aa46aa5ede7430c895504e5c036a5`



## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
