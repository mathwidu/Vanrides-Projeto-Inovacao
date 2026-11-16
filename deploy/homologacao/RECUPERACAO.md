# Ensaio isolado de recuperação

`rehearse_recovery.py` prepara um ensaio **sintético**, executável futuramente
na VPS pelo mantenedor. Seu preparo/teste unitário não comprova que esse ensaio
foi executado. Não publica, reinicia ou consulta serviços compartilhados, não
usa Evolution, não lê credenciais live e não envia WhatsApp ou OpenAI.

O conjunto próprio reúne API com JAR revisado, PostgreSQL, worker WhatsApp
real, n8n **main e worker em queue mode**, Redis com AOF e um canal Node
simulado. Os oito exports originais permanecem inativos e intactos. Apenas
cópias recebem IDs exclusivos, referências correspondentes, URL interna e
credencial sintética. Agendas, nós, conexões e políticas de retenção são
preservados. Quatro cópias de controle são publicadas no n8n próprio.
Os IDs das cópias usam `rec` + os primeiros 24 caracteres do nonce + sufixo:
30 caracteres para workflow e 34 para credencial, dentro do `varchar(36)`
do schema PostgreSQL do n8n 2.45.4. Referências e `callerIds` usam o mesmo
remapeamento; o nonce completo continua na identidade dos recursos Docker.

## Contrato de entrada

Biblioteca padrão do Python; executar do checkout revisado. Os arquivos
`rehearse_recovery.py`, `recovery_fixture.py` e `rehearse_migration.py` precisam
estar juntos. O último é reutilizado somente para IO privado, lock, identidade
do host e executor Docker limitado; o ensaio de migração não é invocado.
`070-n8n-smoke.py` fornece somente a validação pura dos exports: seu smoke
SQLite/CLI não é executado nem contado como prova de queue.

O manifesto não aceita shell, URLs, serviços, tokens, flags, comandos ou
variáveis de ambiente fornecidos pelo operador. Seus campos são fechados:

```json
{
  "schemaVersion": 1,
  "environment": "wa070-local",
  "candidate": {
    "jar": "/home/mathwidu/candidato-revisado/backend.jar",
    "jarSha256": "<SHA-256 do JAR revisado>",
    "revision": "<SHA Git completo declarado para esse artefato>",
    "tree": "<árvore Git completa declarada para esse artefato>",
    "flywayVersion": 44
  },
  "images": {
    "apiRuntime": "ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api@sha256:<digest revisado do runtime JRE>",
    "node": "node:22.23.2-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9",
    "postgres": "postgres@sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28",
    "redis": "redis@sha256:976ab0f0939b78c06d802bcd3bea44c5142ea9a756a57b861e08819817f768c8",
    "n8n": "docker.n8n.io/n8nio/n8n@sha256:80e23ced901f0e22597d4f3464e3aaf9d835372262ea6f08ef760d8ca35df25c"
  },
  "sourceFiles": { "<cada caminho emitido pelo inventário>": "<SHA-256>" },
  "workflows": { "<manifest.json e oito arquivos emitidos pelo inventário>": "<SHA-256>" },
  "privacy": { "state": "PENDING_V44" }
}
```

Placeholders são inválidos. `--inventory` é uma leitura local, sem Docker:

```sh
python3 -B deploy/homologacao/rehearse_recovery.py --inventory
```

Copiar seus mapas completos `sourceFiles`/`workflows`, sem editar hashes. O
inventário cobre worker/agent/transport, incluindo testes `.mjs`, validador dos
exports e os três helpers Python consumidos. O worker roda num snapshot
privado desses bytes usando a imagem Node fixada. Não exige imagem worker
publicada. Fonte e exports são revalidados ao fim.

O JAR precisa conter a versão Flyway declarada (43 ou posterior); seu hash é
validado e o mesmo JAR é montado na API. Revisão/árvore são âncoras declaradas
pelo produtor, não uma reconstrução do JAR pelo helper. O digest `apiRuntime`
fornece só o JRE: `--entrypoint java` evita o entrypoint/checksum da imagem
anterior e inicia o JAR montado. O recibo identifica ambos separadamente.
Não se infere que um teste desse JAR aprove outra compilação ou publicação.

Somente `mathwidu` em `srv1397065`, daemon Unix local, imagens já presentes,
e lock `~/.local/share/vanrides/cd/deploy.lock`. Não há pull automático. O
diretório de saída precisa ser novo, canônico, fora do Git, sem vírgula ou
quebra de linha. Exemplo de execução futura, pelo principal:

```sh
python3 -B deploy/homologacao/rehearse_recovery.py \
  --manifest /home/mathwidu/ensaio-revisado/manifest.json \
  --sha256 <SHA-256 dos bytes exatos desse manifesto> \
  --output /home/mathwidu/ensaio-revisado/resultado-novo
```

## O que o ensaio produz e mede

1. Cria rede bridge `internal`, sem portas publicadas, com PostgreSQL e Redis
   próprios. Usa bancos novos `vanrides_wa070` e `n8n_recovery`; nenhuma cópia
   de sessão Evolution ou banco live entra no conjunto. API, worker e canal
   recebem chaves novas. A fixture cria uma coorte sintética, um destinatário
   com quinze zeros e quatro credenciais distintas válidas por uma hora.
   A API usa perfil `local`, `WA_TEAM_API_ONLY=false`, `WA_MVP_ONLINE=false`
   e `VANRIDES_EMAIL_PROVIDER=log`: esse é o contrato do sandbox com o guard
   atual, que recusa `disabled` fora dos modos MVP publicados. O adapter log
   apenas registra entrega simulada sem destinatário/conteúdo; não envia
   e-mail e o ensaio não chama rotas legadas de e-mail. Não há chave Resend.
   A homologação publicada tem outro contrato e continua exigindo `disabled`
   no modo MVP explícito. Nenhum guard de produto foi alterado.
2. Importa as oito cópias e a credencial sintética. Publica individualmente
   W01/W04/W06/W08, para os endpoints de controle do worker. Para o worker
   n8n próprio, persiste um evento na API e espera uma agenda criar pendência
   real no Redis. Exige inbox ainda `PENDING`, zero comandos e zero efeitos.
   Reinicia esse mesmo worker e exige processamento, submissão e tentativa
   duráveis exatamente uma vez. Os quatro controles precisam receber agenda
   real. Não usa `n8n execute --id` como substituto do scheduler.
3. Para produtores, reinicia os mesmos PostgreSQL/Redis com volumes exclusivos
   e retoma API/workers/n8n. Reenvia o evento original, exige `DUPLICATE`, uma
   nova execução de agenda e ausência de novos comandos/submissões/efeitos.
4. Para agendas/worker, exige filas `wait`/`active` vazias, persiste um segundo evento pendente na API e para a
   API. Cria outro PostgreSQL e outro volume, produz dois `pg_dump -Fc` reais
   (até 64 MiB cada), verifica fingerprint antes/depois, restaura com
   `pg_restore --single-transaction` no destino e compara todas as tabelas e
   sequências `public`/`wa`. Exige que a única coorte seja sintética/local.
5. Abre a porta de privacidade **apenas no banco restaurado**, ainda pendente.
   Retoma o conjunto com novo Redis e as mesmas configurações/chaves
   sintéticas, verifica os oito workflows restaurados, espera a agenda
   completar o evento pendente e repete ambos os eventos sem duplicar efeito.

O canal aceita apenas texto para o endereço sintético e os endpoints fixos de
conexão simulada; rejeita conteúdo inesperado e efeito de texto repetido.
Sua contagem persiste entre as duas pilhas. O worker compartilha o namespace
de rede do canal para respeitar a política HTTP **loopback** do adapter
Evolution; n8n alcança somente o proxy privado `worker:7081`. O proxy encaminha
controles para `127.0.0.1:7080`, sem transportar texto de conversa para n8n.

## Recibo, falha e recuperação do próprio ensaio

A saída nasce com modo 0700; manifesto exato, dumps, JAR, segredos sintéticos,
configurações, estado do canal, logs e recibo usam arquivos privados 0600.
O executor não herda o ambiente do operador, usa configuração Docker vazia,
timeouts, limites de saída e logs locais rotativos. Os contêineres têm limites
de memória/CPU/PIDs, não montam socket Docker, não recebem privilégios nem
portas e usam somente a rede própria. Os limites somados cabem no ensaio
homologado; ainda é necessário conferir capacidade disponível antes de rodar.

Main e worker n8n mantêm a raiz somente leitura. Cada um tem tmpfs privado
em `/home/node/.cache` (32 MiB, `mode=700,nosuid,nodev`, UID/GID do operador
que executa o helper na VPS), além de `/home/node/.n8n` (32 MiB) e `/tmp`
(64 MiB). O teto dos tmpfs passa de 96 para 128 MiB por contêiner, dentro
do limite de memória que continua 512 MiB; o par main/worker acrescenta
64 MiB de teto de cache, consumido sob demanda e disputando esse mesmo limite
com o processo. Não há cache no host, volume compartilhado nem troca de HOME.
O teste usa o UID/GID do processo local; na execução VPS esses valores são
obtidos novamente do operador remoto, não transportados do Mac.

No [InstanceSettings do n8n 2.45.4](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/packages/core/src/instance-settings/instance-settings.ts),
o cache está em `userHome/.cache/n8n/public`, e o
[FrontendService](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/packages/cli/src/services/frontend.service.ts)
cria o diretório `types` ao iniciar o main. A alternativa
[`N8N_USER_FOLDER`](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/packages/%40n8n/config/src/utils/utils.ts)
também deslocaria `.n8n` e seu diretório-pai, conforme
[InstanceSettingsConfig](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/packages/%40n8n/config/src/configs/instance-settings-config.ts).
O tmpfs restrito preserva esses caminhos; não depende de uma flag XDG não
comprovada. Tamanho e startup precisam ser confirmados no próximo ensaio real.

Cada espera usa um prazo monotônico absoluto, incluindo o tempo gasto em
sondagens, inspeções de ownership, HTTP, SQL e subprocessos. Não multiplica
tentativas pelo timeout de cada chamada. O tempo restante limita o executor,
o abort HTTP, o timeout SQL e a próxima pausa; sucesso depois do prazo é
recusado. Ao esgotar uma espera, o cleanup usa um orçamento separado de até
120 segundos e mantém a conferência de identidade. Se esse prazo também
esgotar, o recibo permanece bloqueado e preserva os recursos para intervenção;
o limite está sujeito somente ao pequeno custo de escalonamento/encerramento
dos subprocessos pelo sistema operacional. Isso é controle local testado com
relógio/executor simulados, não uma medição de recuperação real na VPS.

O `receipt.json` registra manifesto, imagens, JAR, revisão/árvore declaradas,
hashes dos fontes/exports, hash das cópias efetivas, fases, checks, hashes e
fingerprints dos backups, IDs próprios e volumes retidos. Não registra tokens,
texto, dump ou `Env` bruto. Logs privados não devem ser anexados sem revisão.

Falha, SIGTERM ou interrupção não viram PASS. O cleanup confere nome exato,
imagem, ID, nonce, ambiente, rede sem exposição e labels antes de tocar qualquer
contêiner, inclusive se a resposta de criação tiver sido perdida. Confere
todos antes de remover; identidade divergente bloqueia. Remove contêineres
próprios e redes próprias; **não remove volumes** nem usa prune. Dumps, logs,
segredos sintéticos e volumes exclusivos permanecem para diagnóstico/retomada
pelo mantenedor, identificado pelo recibo. Não há limpeza automática de uma
pilha compartilhada nem recuperação por nome parcial.

O estado final possível deste incremento é `CORE_VERIFIED_PRIVACY_PENDING`,
com `releaseEligible: false`. Ele só existe depois da execução real completa
das etapas acima. Uma execução unitária mockada não produz essa evidência.

## Porta V44 e limites dos critérios

`privacy: {state: PENDING_V44}` é o único contrato aceito agora. A função
`privacy_after_restore` recebe `RestoredDatabase`, cuja consulta está presa ao
papel `restored-pg` e banco de negócio restaurado; não aceita um recibo externo
nem um seletor de banco. Ela retorna `reapplied: false` e
`absenceVerified: false`. Não implementa nem simula exclusões V44.

A integração futura precisa pinçar o journal/adapter do mesmo candidato,
reaplicar exclusões no **destino restaurado**, demonstrar ausência dos alvos
e preservação de controles retidos antes de retomar seus consumidores. Deve
registrar versão/hashes e resultado desse trabalho, sem copiar IDs humanos.
Isso exige um incremento revisado do manifesto/adapter e seus testes. Trocar
o campo para PASS ou fornecer um recibo pronto é recusado pelo helper atual.

O núcleo oferece evidência direcionada para #82/#85/#92 quando realmente
executado. Não fecha #84, nem comprova bateria, aparelho ou percurso. A cópia
de backup é feita com produtores quiescentes e a pendência a recuperar está
no inbox da API. O Redis original prova retomada AOF no mesmo volume; o Redis
do destino começa vazio. Não há prova aqui de recuperação de qualquer job em
voo após perda arbitrária da AOF, nem de efeito externo com ACK perdido ou
estado `UNKNOWN`. Os testes de recuperação/idempotência de
`120-delivery-recovery.mjs` continuam necessários e preservados.
Também não mede RPO/RTO de desastre live nem aceita conteúdo humano em n8n;
`save*none` sozinho não significa retenção zero.

## Fontes oficiais e validação local

Documentação oficial conferida em 16/11/2026: [CLI de importação/publicação](https://docs.n8n.io/hosting/cli-commands/),
[queue mode com PostgreSQL/Redis/main/worker](https://docs.n8n.io/hosting/scaling/queue-mode/)
e [Schedule Trigger](https://docs.n8n.io/integrations/builtin/core-nodes/n8n-nodes-base.scheduletrigger/).
O limite de IDs foi conferido no schema da versão fixada para
[workflow_entity](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/docs/generated/postgres-schema/public.workflow_entity.md)
e [credentials_entity](https://github.com/n8n-io/n8n/blob/n8n%402.45.4/docs/generated/postgres-schema/public.credentials_entity.md).
O n8n 2.x publica por `publish:workflow --id=...`; importar não comprova
ativação, e Schedule Trigger precisa estar publicado. A versão fixada é
2.45.4; o ensaio exige disparo observado para detectar incompatibilidade real.

```sh
python3 -B deploy/homologacao/rehearse_recovery_test.py
python3 scripts/check_repository.py
git diff --check
```

Os testes usam ZIPs, IO temporário e executor Docker mockado. Nenhum Docker,
JVM, SSH, mensagem real ou restauração real é iniciado por essa suíte.
O teste do perfil local confere a fixture e a cláusula do guard no fonte;
não executa o bootstrap Java. A inicialização real precisa ser repetida pelo
mantenedor com novo inventário/manifesto após integrar esta correção.
