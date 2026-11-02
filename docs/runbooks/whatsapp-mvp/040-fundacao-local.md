> Registro técnico anterior à centralização. Caminhos pessoais e endereços operacionais foram omitidos. Para executar a versão atual, use os guias em `docs/desenvolvimento/`; este histórico não autoriza deploy nem comprova a nova revisão.

# VR-WA-040 — validação local da fundação

Este procedimento valida o contexto de identidade/autorização do backend com
dados sintéticos. Não envia WhatsApp, não coleta GPS e não substitui os ensaios
Android/iPhone, a integração do canal050 ou o aplicativo060.

## Artefatos e preparação

O contrato executável está no backend em `contracts/whatsapp-mvp/openapi.yaml`.
Configuração/protocolo e CLI estão em `docs/whatsapp-runtime.md` e
`docs/whatsapp-operator.md` desse repositório. O backend e a coordenação têm
commits separados; nenhum push faz parte deste procedimento.

1. Conferir `program_control.py check --workspace core`, card040 ativo,
   dirty paths e revisão. Manter iPhone físico pausado e GPS Android parado.
2. Fixar commit local do backend com checkout limpo. A fonte deve permanecer
   nesse SHA durante build, gate e smoke; alteração de código exige nova prova.
3. Preparar diretório privado0700 em
   `~/.local/share/vanrides/local/wa040-backend-<8 primeiros caracteres do SHA>`.
   Seus arquivos de configuração, estado, pedidos e resultados têm modo0600.
   Não copiar esses arquivos para Git ou publicar logs brutos.
4. Reservar API `127.0.0.1:18082` e PostgreSQL `127.0.0.1:51221`, com container
   e volume exclusivos identificados por `vanrides.card=VR-WA-040-DOMAIN-AUTH`
   e pelo SHA. Conferir portas livres e labels antes de reutilizar um recurso.
   Não reutilizar o banco do baseline010, da VPS ou de outro projeto.
5. PerfilSpring `local`, DDL `validate`, Flyway sem baseline implícito, SQL log
   desligado, routing `static`, maps desabilitado, push `none`, e-mail `log`.
   O processo importa somente a configuração privada; `.env` preexistente não
   entra. Configurar WA em `wa040-local`, URL pública igual à API loopback,
   flags de laboratório e coorte de teste. Gerar segredos novos com CSPRNG,
   incluindo as duas chaves separadas e credencial OPERATOR com prazo curto.

O auxiliar privado `backend-local.py`, preservado no diretório da execução,
implementa build/serve/gate/status/stop-api/start-db/stop-db. Ele deriva do
controle já usado no010, mas verifica SHA, artefato, PID, nomes e label040
próprios. O preparador desta execução permanece privado em
`~/.local/share/vanrides/wa040/prepare_runtime.py`; não contém credenciais fixas.
Esses auxiliares são controles deste Mac, não uma distribuição para produção.

## Gate e smoke

1. Build Java17 do SHA limpo e cópia do JAR fora de `target`, com SHA-256.
   O gate executa `clean test` e não pode apagar o artefato a testar.
2. Iniciar banco exclusivo e API pelo JAR copiado, limitado em memória e
   vinculado a loopback. Esperar health200 e verificar PID/listener.
3. Executar `qa_gate_card.sh` com `VANRIDES_BACKEND_BASE_URL` explícito apontando
   para18082. Esse comando inclui o gate canônico inteiro e smoke legado; não
   trocar por `mvn test` parcial. A matriz versionada exige todos os testes
   PostgreSQL, sem skips, e a evidência deve apontar o mesmo SHA limpo.
4. Só após o gate, executar o smoke integrado desta coordenação:

   ```bash
   node integrations/whatsapp/scripts/040-foundation-smoke.mjs \
     /caminho/privado/wa040-backend-<sha8> <sha-completo-do-backend>
   ```

   Node22 e Java17 já instalados são suficientes, sem dependências npm.
   O script confere SHA/checkout/JAR/PID/listener e limita-se a loopback e ao
   ambiente `wa040-local`. Não aceita um destino remoto por parâmetro.
5. O smoke usa o mesmo JAR em modo `none` para a CLI, cria coorte/credencial/
   mensagens sintéticas, testa cadastro, replay, bootstrap vinculado, notice,
   nonce/DPoP/PKCE, isolamento de chave, status, rotação e revogação. O helper
   de completar inbox exige recibo durável e não finge ACK da Evolution.
6. No sucesso, resetar somente o lote sintético criado e revogar sua credencial
   técnica. O script conserva prova global de nonce e auditoria OPERATOR;
   não executa DROP/TRUNCATE nem remove dados externos ao lote.
7. Guardar recibo sanitizado (SHA do código, JAR, script, horários e checks),
   evidência canônica e limites no QA Log/card. O diretório privado conserva
   falhas e dados para diagnóstico; uma falha não recebe PASS nem é apagada.

O monitor de memória envolve cada comando pesado. Pressão elevada interrompe
o processo próprio e invalida a rodada. O checkout limita heap/metaspace do
Maven/Surefire; o servidor e a CLI locais também têm limites. Não executar
compilação nativa, simulador ou emulador em paralelo ao gate.
Após duas interrupções por pressão sustentada, o Docker Desktop local recebeu
limite de2GiB (antes4GiB), mantendo2CPUs. Arquivo e memória efetiva da VM foram
conferidos; backup e restauração estão no projeto de configuração Mac-M5 em
`docs/docker-memoria-vanrides-2026-10-31.md`. Nenhuma redução da matriz de testes
ou mudança na VPS acompanha esse ajuste. A cópia externa do backup não está
comprovada.

### Uso do Mac durante os testes

O Mac é compartilhado com o trabalho do proprietário. Executar um teste pesado
por vez. Se houver pressão de memória, interromper apenas o grupo de processos
criado pelo teste e seus contêineres de propriedade confirmada. Aguardar a
normalização antes de retomar. Aplicativos, abas, ferramentas de outros projetos
e o Docker Desktop compartilhado ficam sob controle do proprietário.

Ao medir a VM Linux do Docker, registrar `boot_id`, `pswpout`, swap livre e
memória disponível imediatamente antes do teste. `pswpout` acumula páginas
desde a inicialização. No ensaio de presença, a guarda exige a mesma VM,
nenhum aumento nesse contador, swap inteiramente livre e pelo menos 128 MiB
disponíveis em cada amostra. Aplicar também a guarda de pressão do macOS.

## Encerramento e retomada

Parar somente o PID confirmado deste JAR e o container com label040, preservando
volume e arquivos privados para reconciliação. Não usar `prune` ou remover
recursos por prefixo. Não eliminar um resultado `PREPARED`: a operação pode ter
sido confirmada no banco antes de falhar sua marcação final; consultar a
reconciliação documentada na CLI.

O reciboPASS local habilita fechar040 conforme DoD e seguir o programa. Não
habilita publicar a API, operar coortes reais, ativar n8n/Evolution ou declarar
o MVP pronto. Publicação exige manifest, impacto, rollback e autorização
correspondentes; a autorização antiga do laboratório GPS não cobre essa release.
