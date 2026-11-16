# Ensaio privado de uma migração

`rehearse_migration.py` prepara uma prova de restauração e retorno para **uma
nova migração revisada**. Não publica imagem, não atualiza/escala serviços, não
escreve aprovação para o atualizador e não concede aceite de release.

Este é um helper novo. O `backup_restore.py` antigo continua restrito ao seu
plano e banco sintéticos; ele não foi generalizado nem executado por este fluxo.
O arquivo novo usa somente a biblioteca padrão do Python e pode ser copiado
sozinho; os guards seguem os padrões dos helpers existentes, sem importá-los.

## Entradas revisáveis

O manifesto JSON não contém credenciais. Seu SHA-256 deve ser conferido fora
do helper e passado explicitamente. As chaves são fechadas:

```json
{
  "schemaVersion": 1,
  "environment": "vanrides-mvp-hml",
  "previous": {
    "apiImage": "ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api@sha256:<digest>",
    "postgresImage": "postgres@sha256:<digest>",
    "revision": "<SHA Git completo da API anterior>",
    "tree": "<árvore Git completa da API anterior>",
    "jarSha256": "<SHA-256 do JAR anterior>"
  },
  "candidate": {
    "jar": "/home/mathwidu/candidato-revisado/backend.jar",
    "jarSha256": "<SHA-256 do JAR candidato>",
    "revision": "<SHA Git completo do candidato>",
    "tree": "<árvore Git completa do candidato>"
  },
  "migration": {
    "path": "BOOT-INF/classes/db/migration/V44__nome_revisado.sql",
    "sha256": "<SHA-256 dos bytes SQL revisados>"
  }
}
```

Os placeholders são intencionalmente inválidos. O inventário exige que a nova
versão seja a seguinte à maior versão anterior; o nome V44 é apenas exemplo.
No v1, as migrações anteriores, **todas** as bibliotecas em `BOOT-INF/lib/` e as
configurações `.properties/.yaml/.yml/.xml` em `BOOT-INF/classes/` precisam ser
idênticas byte a byte. Código Java pode mudar. Mudança em outra migração,
biblioteca ou configuração exige outro plano; a exceção limitada de configuração
do v2 está descrita a seguir.

## Delta opcional de configuração: manifesto v2

O v1 acima continua estrito: não aceita `reviewedConfigurations` nem mudança
em qualquer configuração inventariada. Para a entrega V43, use
`schemaVersion: 2`, mantendo as mesmas chaves de origem, candidato e migração,
e acrescente **exatamente uma** entrada:

```json
"reviewedConfigurations": [
  {
    "path": "BOOT-INF/classes/application.properties",
    "oldSha256": "<SHA-256 dos bytes desse arquivo no JAR anterior>",
    "newSha256": "<SHA-256 dos bytes desse arquivo no JAR candidato>"
  }
]
```

Essa entrada não é permissão genérica para trocar o arquivo. A política aceita
somente a inserção exata, uma vez por linha, de ambas as propriedades:

```properties
vanrides.wa.auto-finish-enabled=${WA_AUTO_FINISH_ENABLED:false}
vanrides.wa.mvp-rehearsal-routines=${WA_MVP_REHEARSAL_ROUTINES:false}
```

Depois de retirar essas duas linhas do candidato, todos os bytes restantes
devem ser idênticos aos do arquivo anterior. Assim, as propriedades Flyway/JPA,
seus valores, comentários, ordem e o restante da configuração permanecem
intocados. Não se aceita mudar defaults para `true`, outra variável, remover,
reordenar ou editar conteúdo existente. As duas chaves também não podem estar
definidas nem referenciadas no arquivo anterior. O v2 suporta o arquivo UTF-8
plano deste incremento e recusa backslashes/continuações/escapes no anterior,
evitando interpretar uma adição como parte de outro valor. As novas linhas
são literais com terminador LF; outras codificações ou deltas exigem revisão.

Os hashes devem vir dos arquivos **dentro dos dois JARs revisados**, não apenas
dos fontes. Um arquivo diferente, caminho repetido, checksum divergente,
biblioteca/SQL antigo removido ou alterado e outra configuração não declarada
bloqueiam a operação antes do backup. Continua sendo permitida exatamente uma
nova migração SQL revisada, com a versão seguinte. Nada aceita comandos shell.

A saída guarda `manifest.json` com os bytes exatos cujo hash foi aprovado.
No v2, o recibo mantém `manifestSha256`, acrescenta `manifestSchemaVersion` e registra
`reviewedConfigurations`, os hashes anterior/novo, `unchangedBaseSha256` e as
duas linhas efetivamente aceitas. A permissão não se propaga para uma migração
futura: cada nova combinação exige manifesto e hash próprios. O ensaio de
restauração e as verificações de isolamento/cleanup permanecem iguais ao v1.

## Execução futura na VPS

Somente `mathwidu` em `srv1397065`, no daemon Unix local, com as imagens já
presentes. O script não conecta SSH nem faz pull. A execução requer o opt-in
`--execute-on-vps`, caminhos absolutos canônicos e diretório de saída novo,
fora de Git. O caminho de saída aceita apenas letras/números, `_`, `-`, `.`, `/`,
para que não possa alterar o significado do bind mount.

```text
python3 deploy/homologacao/rehearse_migration.py --execute-on-vps \
  --manifest /home/mathwidu/ensaio-revisado/manifest.json \
  --expected-manifest-sha256 <hash-exato> \
  --output /home/mathwidu/ensaio-revisado/resultado-novo
```

O lock exclusivo é o mesmo do atualizador:
`~/.local/share/vanrides/cd/deploy.lock`, resolvido pelo usuário do sistema,
sem depender de um `HOME` sobrescrito. O diretório deve existir; o helper não
provisiona acesso, grupos ou configuração do daemon.

O fluxo confere `vanrides-hml-api_api` e `vanrides-hml-api_db`: uma task em
execução, namespace e labels do ambiente esperado, imagens imutáveis,
origem/revisão/árvore/checksum da imagem da API, checksum declarado no serviço e checksum
do JAR que está em execução. A label histórica de ambiente da imagem não
é usada para determinar escopo: valem serviço/task e `WA_ENVIRONMENT`. Não projeta `Env`, argumentos ou comandos inteiros.
O JAR anterior é extraído de um contêiner próprio **sem iniciá-lo**, não de
volume ou secret do serviço live.

## Provas e isolamento

1. Lê versão/estado Flyway e fingerprints do banco dedicado. Aceita coortes
   reais e sintéticas dentro de `vanrides-mvp-hml`, registrando suas contagens;
   outra distribuição de ambientes é recusada, sem limpeza de dados.
2. Faz `pg_dump` real, privado 0600, com teto de **64 MiB** imposto também ao
   arquivo do subprocesso. Depois compara novamente o estado dos serviços e
   o conteúdo da origem. Escrita concorrente detectada bloqueia o ensaio.
   Cada fingerprint usa uma transação de leitura com snapshot repetível,
   UTC, formato ISO e representação completa de floats. Assim, configurações
   diferentes de sessão entre a origem e a cópia não alteram os hashes.
3. Restaura em PostgreSQL próprio com nonce, `network=none`, sem portas nem
   volume de origem; compara dados de tabelas e sequências de `wa/public`.
   Aguarda uma consulta TCP ao banco exato; o servidor temporário por socket
   usado no primeiro boot da imagem não é tratado como prontidão.
   Outros schemas de usuário ou large objects são recusados porque não estão
   cobertos por esse fingerprint. O dump usa `--no-owner --no-privileges`.
4. Executa sequencialmente API anterior no schema anterior, JAR candidato no
   novo e API anterior no novo. Cada cenário exige `/actuator/health` UP e a
   versão Flyway esperada, sem migração falhada. O runtime é a mesma imagem
   imutável anterior, com o JAR conferido montado somente para leitura.
5. Remove somente os contêineres cuja identidade, nome, imagem, nonce, operação
   e ambiente sejam comprovados. Resposta de criação perdida é reconciliada
   pelo nonce; colisão ou proprietário divergente nunca autoriza remoção.

PostgreSQL: 512 MiB, 1 CPU, 128 PIDs e tmpfs de dados de 512 MiB.
API: 640 MiB, 1 CPU, 192 PIDs, raiz somente leitura e tmpfs `/tmp` de 32 MiB.
Apenas uma API do ensaio executa por vez, no namespace de rede do PostgreSQL
isolado. O probe usa `/bin/busybox wget` da imagem PostgreSQL; não instala
ferramentas. Falta desse utilitário bloqueia a prova.

O ambiente da API é gerado com chaves e senha **sintéticas**. Team Web, avisos,
autoend, navegação externa, push e e-mail ficam desabilitados. Não são lidas ou
copiadas credenciais dos serviços live; registros existentes no dump permanecem
opacos no backup privado. O CLI Docker usa configuração própria vazia e não
herda tokens nem seleção de daemon do processo chamador.

## Resultado e limites

O diretório 0700 preserva dump, JARs, manifesto exato, ambiente sintético, logs
0600 e `receipt.json`, inclusive quando há erro. Não anexe esses arquivos brutos ao Git
ou ao chat. O recibo contém hashes, contagens, cenários, fase e resultado de
cleanup; `releaseEligible` permanece **false**, também no sucesso.

Saída 0 significa somente `RESTORE_AND_MIGRATION_RETURN_VERIFIED`. Saída 2
indica bloqueio/cancelamento; o CLI distingue preflight sem Docker de uma
execução com recibo privado. Cleanup sem comprovação deixa o recurso intacto e
o recibo bloqueado. Backups e logs não são apagados automaticamente.

Esta prova não valida grants/owners restaurados, toda a imagem candidata,
jornadas de negócio, exclusões/tombstones, journal V44, n8n/worker, canal real ou
retorno do banco live. **Não conclui #84 integralmente**. A janela real de dados
estáveis e a publicação continuam sob coordenação do principal.

## Verificação local desta implementação

```text
python3 deploy/homologacao/rehearse_migration_test.py
```

Os testes usam subprocessos simulados, ZIPs e diretórios temporários: v1/v2,
delta de configuração, Flyway/JPA preservados, hashes e inventário, três cenários,
isolamento, alteração da origem, restore divergente,
dump inválido, API falhada, perda de resposta, proprietário forjado, colisão,
permissões, limites, ambiente e lock. Não executam Docker, SSH ou banco real.
