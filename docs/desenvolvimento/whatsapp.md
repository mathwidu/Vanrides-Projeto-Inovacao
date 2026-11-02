# Desenvolver o atendimento WhatsApp

O módulo fica em `src/whatsapp/`: `agent/`, `worker/`, `transport/`, `links/`,
`workflows/`, receptor GPS sintético e ferramentas de testes. Os caminhos HTTP
continuam `/integrations/whatsapp/v1`; somente os caminhos de arquivos mudaram.
Os contratos são lidos de `src/backend/contracts/whatsapp-mvp/`.

## Testes sem credenciais

Com Git, Node 22.23.2 e Python 3.10+, execute da raiz:

```bash
python3 scripts/check_whatsapp.py
```

O comando cobre os testes Node, o contrato dos oito exports n8n inativos,
salvaguardas do smoke n8n sem iniciá-lo, o receptor GPS em SQLite temporário e
os testes dos utilitários de laboratório com fixtures sintéticas. Não instala
pacotes Node, não chama OpenAI, não inicia WhatsApp e não envia mensagens.

Linux e macOS usam os mesmos comandos. No Windows, use WSL2: testes de
permissões, UID, sinais e arquivos privados dependem de POSIX; não há garantia
de equivalência no PowerShell nativo. O CI público usa Ubuntu padrão.
Os computadores da equipe e aparelhos físicos ainda precisam de conferência.

## Integração sintética com a API real

Com Java 17 e Docker local, o gate do backend inclui a jornada de integração:

```bash
bash scripts/check_backend.sh
```

Ela empacota o JAR do checkout, cria PostgreSQL descartável, inicia Spring e
um processo real do worker, reaproveita os cenários determinísticos de conversa,
exercita autenticação e idempotência e verifica envio/recibo usando somente
um receptor Evolution simulado em loopback. Tokens são aleatórios e temporários;
nenhuma credencial real é procurada. O recibo publica apenas checks e hashes.
Isso valida integração de software; rastreamento de fundo, rede móvel e envio
real de WhatsApp continuam sendo verificações separadas.

## Imagens e n8n

```bash
docker build -t vanrides-whatsapp:local -f src/whatsapp/worker/Dockerfile src/whatsapp
docker run --rm --network none --entrypoint node vanrides-whatsapp:local \
  -e "Promise.all(['./dispatch.mjs','./process-inbox.mjs'].map(p=>import(p)))"
python3 src/whatsapp/scripts/070-n8n-smoke.py --preflight-only
```

O Dockerfile usa o contexto `src/whatsapp` e inclui os dois adapters de
transporte necessários. O smoke n8n completo é executado na CI com imagem
fixada por digest; importa somente exports inativos em um runtime descartável
e aponta para controle sintético. Nenhum workflow é ativado num serviço real.

Os pins existentes de Node, n8n, Python/Gunicorn e imagens foram preservados;
consulte Dockerfiles, `requirements-production.txt` e `workflows/manifest.json`.
A CI [whatsapp.yml](../../.github/workflows/whatsapp.yml) usa `contents: read`,
constrói imagens somente no runner e não publica logs, imagens ou deploys.

## Ferramentas preservadas e limites

`lab/live-ingress`, `lab/live-runtime` e `scripts/vps110` preservam código,
contratos e testes de preparação/recuperação. Os manifests históricos continuam
identificando as fontes originais para não reatribuir evidência a este checkout.
Testar essas ferramentas offline não autoriza usá-las contra VPS, celulares,
contatos ou sessões antigas. O caminho suportado para integração acadêmica é o
smoke sintético acima; o uso externo exige preparar novo manifest de ambiente
e autorização específica. Instruções pessoais de instalação/hosts e notas
operacionais antigas não foram transferidas.

O receptor `gps-spike` usa fixtures equatoriais sintéticas. Probes Android e
fontes de laboratório iOS foram preservados como suporte; nenhuma execução
física é inferida dos testes offline. Não registre GPS real, números, tokens,
bancos ou capturas de aparelhos no Git ou nos artefatos públicos.

Hosts do receptor GPS, bundle IDs e pins dos laboratórios foram conservados
porque fazem parte dos contratos e de verificações de origem. Eles não são
chaves nem acionam tráfego nos testes offline. Os manifests de `vps110` incluem
identificadores públicos do ambiente histórico; a entrada atual da equipe
continua sendo loopback e não usa esses destinos automaticamente.
