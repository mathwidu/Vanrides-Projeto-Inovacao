# Recibo histórico — VR-WA-060-DRIVER-APP

## Proveniência

Importado em 2026-11-02 de `program/evidence/VR-WA-060-DRIVER-APP.md`. SHA-256 do original: `188247b7371f83b36b07128d73b1c79035a1c519937304ab4a88cb03e867cb81`. O documento completo foi preservado no acervo local do proprietário, fora deste repositório público. O hash permite conferir a identidade do original; o acervo não é um link público e não é necessário para executar os testes atuais.

## Resultado registrado na origem

Este é um resumo de evidência anterior à migração; nenhuma execução foi realizada por esta importação. Os marcadores PASS pertencem às respectivas revisões históricas e não aprovam este checkout.

## Resultado do ensaio

**PASS no escopo local do card em 2026-10-31.** O Redmi Note 8 com Android 10 executou o vínculo do motorista contra a API Spring e o PostgreSQL de laboratório. O motorista abriu o app pelo ícone, mostrou o código e voltou ao app depois da confirmação. O servidor associou um único aparelho à conta de teste. A inbox, o worker, os recibos dos comandos e o adaptador nativo foram exercitados no mesmo ensaio. O provedor Evolution e a conversa de WhatsApp foram simulados em loopback.

## Decisões e comportamento

- A chave privada permanece no aparelho. Antes de enviar o primeiro pedido, o app grava a operação localmente. A API exige a chave do pedido e o verificador PKCE para a troca. O código mostrado ao motorista só aparece após uma ação explícita. O ensaio de host verificou que outra chave não consegue assumir o pedido.
- Um link de retorno permite atualizar o estado de um pedido existente. No Redmi, abrir o link a frio com o aparelho já desvinculado orientou a abrir pelo ícone. Voltar pelo ícone, no mesmo processo, exibiu o botão para um novo vínculo e preservou o estado desvinculado. A página de retorno pode ser visitada por uma prévia de link sem consumir uma operação.
- A expiração real do acesso ocorreu durante o ensaio. O laboratório descartou uma resposta de renovação que o servidor já tinha confirmado. Ao voltar, o app recuperou a mesma operação e mostrou o vínculo ativo sem o motorista tocar em atualizar. A API registrou uma única rotação efetiva.
- Na desvinculação, o teste interrompeu somente o caminho USB entre o APK de laboratório e a API. A tela mostrou **Desvinculação em andamento**. Após restabelecer o caminho, a tela mostrou **Aparelho desvinculado** e a consulta ao banco confirmou a revogação dos tokens. O estado sobreviveu à reab

## Revisões e reprodução históricas

- `coordination:7069fbdc1e69bafa9382c110dc4d72c7b94ed668`
- `coordination:a739a346bee30a8a717cc71e9c823280129db0e4`
- `backend:8411f9ac47fb59517e57aa7165d97d7c8e55c66e`
- `app:ec86dc60be165898dc0493ed9b8bd1bc41044261`
- `app:5e13c629e6b7c618a11027cb6e35bc3032089c7d`
- `app:ad40766c651e18e3a382f274c9266f72c49dce4e`

- Comando registrado na origem: `./scripts/qa_gate_card.sh --no-e2e`.
- Comando registrado na origem: `flutter test --concurrency=1`.
- Comando registrado na origem: `python3 android/scripts/qa_driver_session_host.py`.
- Comando registrado na origem: `bash ios/RunnerTests/run_driver_session_host.sh`.
- Comando registrado na origem: `./scripts/ci_quality_gate.sh`.

## Limites e recuperação

Este recibo sanitizado preserva o vínculo do card e do estado histórico sem transportar logs, endpoints privados, GPS ou dados de dispositivo. Não é evidência do SHA do monorepo. Reprodução atual exige os gates dos produtores/consumidores e nova evidência na mesma revisão. Piloto e produção continuam HOLD; iOS físico está pausado. O rollback da migração e os limites atuais estão no card ativo e na documentação de desenvolvimento.
