# Recibo histórico — VR-WA-090-PASSENGERS

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-090-PASSENGERS.md`. SHA-256 do original: `9b2a8a3d0c7e011a3f5fc581ca816c41883d993efd927f8b0ee989bee7dcc8a0`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

**PASS no laboratório em 2026-10-31.** O passageiro abriu um convite pelo canal simulado, verificou o nome do motorista, escolheu os dias da rotina e confirmou o ponto de embarque. O motorista pediu uma correção e aprovou o cadastro. Depois da aprovação, testamos a troca para um ponto comum, o encerramento e a recusa de um segundo pedido. O próprio passageiro realizou todas as etapas na conversa.

O ensaio percorreu a entrada de eventos da Evolution, o worker, a API Spring e o PostgreSQL. O provedor simulado em loopback recebeu os envios e conferiu o destinatário de cada mensagem. A importação e a execução dos workflows aconteceram em um n8n local separado. O canal no número de WhatsApp dedicado será validado no 170.

## Resultados

| Ensaio | Resultado verificado |
|---|---|
| Backend | 1.391 testes em 198 classes. Todos passaram e nenhum foi pulado. A matriz obrigatória incluiu 315 testes com PostgreSQL em 41 classes. |
| Cadastro do passageiro | 10 verificações. Foram 81 entradas únicas, quatro repetições e 90 envios. Nove mensagens chegaram à contraparte correta. O terceiro participante, outro motorista, não teve acesso ao pedido. |
| Regressões no mesmo JAR | 12 verificações do agente no 070 e 10 do cadastro do motorista no 080. A consulta de motoristas respondeu 200, o preflight CORS respondeu 200 e o refresh com corpo vazio respondeu 400. |
| n8n | Os oito workflows foram importados e continuaram inativos após o reinício. W03 executou a operação privada de cadastro. W04 e W05 recusaram dependências ainda indisponíveis. W06 também foi testado com o controle indisponível. |
| Worker | 501 testes Node no Mac e 367 na imagem Linux arm64. A imagem iniciou nos modos determinístico e OpenAI com a rede externa desligada. |

## Revisões e reprodução históricas

- `coordination:d4b2694899fc2cf22dbaffb32124bb5101ae6553`
- `coordination:7df7ea12d1a209853319b7cfb3f588c3be19e3ad`
- `coordination:d64ce1e1c0e17b61f1c7240ef5c4575bc3ac5380`
- `coordination:5311fc7c607c13f999e152a4f3277dc56a40f6da`
- `coordination:ee4940ea035490d8569d31fc19c4cec5a39f1582`
- `backend:185cc374e1a4f2f5d28877d375b8a20bbe3ef565`
- `backend:80b2f9febc39d4965d10305b4b5b889da1f923b0`
- `backend:221d039d17743d5dff0015975d094cb71b1a9d6a`
- `backend:dc33ea978a40ccff56730eb0580b35d791a4c8af`

- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.
- Comando registrado na origem: `node --test --test-concurrency=1`.
- Comando registrado na origem: `python3 integrations/whatsapp/scripts/070-n8n-smoke.py`.
- Comando registrado na origem: `node integrations/whatsapp/scripts/090-passenger-smoke.mjs`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
