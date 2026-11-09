# Desenvolver o backend

A raiz Maven é `src/backend/`; a raiz Git e o programa estão na raiz deste
repositório. Java 17, Spring Boot 3.4.5 e Maven Wrapper 3.9.9 foram preservados.
PostgreSQL de teste usa a imagem `postgres:16-alpine` existente no projeto.
O módulo próprio de mapas continua adiado.

## Primeiro teste

Linux e macOS precisam de Git, Python 3.10+, Node 22.23.2, Java 17 e Docker local.
Confira `git --version`, `python3 --version`, `node --version`, `java -version`
e `docker info` antes de instalar ou configurar ferramentas. No Windows, use
WSL2 com Git, Python, Node e Java dentro do Linux e integração Docker Desktop
habilitada para a distribuição. O gate usa Bash e locks POSIX; PowerShell
nativo e Git Bash não foram validados como substitutos.

Para conferir a construção usada pelo CI/CD, execute:

```bash
cd src/backend
./mvnw -B -ntp -Dmaven.test.skip=true package
```

Esse comando compila e empacota a aplicação, sem compilar ou executar testes.
Um build aprovado não comprova o funcionamento das regras de negócio.

O [workflow automático](../../.github/workflows/backend.yml) tem dois jobs:
**Construir API** e **Atualizar API da homologação da equipe**. PRs e pushes em
`dev` constroem a aplicação/imagem; somente push na `main` publica a imagem no
GHCR e atualiza `vanrides-hml-api_api`. A referência usa digest e o checksum do
JAR dessa execução. O deploy confere saúde HTTPS e tenta retorno à imagem
anterior se falhar. Veja o [procedimento](../../deploy/homologacao/AUTOMATICO.md).

Não há matriz PostgreSQL, jornadas HTTP, testes dos auxiliares, ratchet ou
homologação encadeada no CI/CD automático. Os workflows de verificação ficam
separados, sem gatilhos de push/PR. O cache Maven armazena dependências;
nenhum relatório de teste é necessário para construir/publicar.

Durante o desenvolvimento, escolha os testes relacionados à mudança. Para uma
validação completa deliberada, continuam disponíveis na raiz Git:

```bash
bash scripts/check_backend.sh --light
bash scripts/check_backend.sh
```

`--light` executa ratchet e testes dos auxiliares sem Maven/containers. O comando
completo exige Docker, executa a suíte canônica e as jornadas sintéticas com
Spring/PostgreSQL/worker. Não precisa de WhatsApp real nem credencial externa.
Não é um requisito automático de todo commit ou deploy. Mantenha essas execuções
sequenciais no computador; não abra oito processos Maven simultâneos.

O histórico da divisão em oito grupos e dos experimentos de compartilhamento do
JAR está na [revisão da CI](revisao-ci-e-testes.md). Esses mecanismos não fazem
parte da esteira automática atual. Para testes de produto, registre cenário,
versão, resultado e limitações na issue; para um build, registre apenas o build.

A jornada 130 já executa integralmente a 110. O smoke agora a executa uma vez,
com cinco execuções e seis conjuntos de verificações. O recibo V2 identifica
os checks 110 como executados dentro de 130; os recibos históricos V1 continuam
validados no formato original. Quotas, espera de frescor do GPS e viradas de
minuto de presença não foram encurtadas artificialmente.

`bash scripts/check_backend.sh --integration-only` é essa checagem parcial de
package e jornada; não equivale ao gate completo. O comando local sem flags
continua sequencial: primeiro a suíte canônica, depois a integração.

## Executar uma API de desenvolvimento

Use um banco descartável separado do banco de testes e de qualquer serviço real.
Exemplo local (credencial exclusivamente sintética):

```bash
docker run --name vanrides-dev-local --rm -d -p 127.0.0.1:5432:5432 \
  -e POSTGRES_DB=vanrides_db -e POSTGRES_USER=vanrides_user \
  -e POSTGRES_PASSWORD=synthetic-local-only postgres:16-alpine
cd src/backend
cp .env.example .env
```

Edite a senha em `.env` para corresponder ao banco e mantenha o arquivo fora do
Git. `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` conserva o schema gerido pelo
Flyway. Depois:

```bash
./mvnw spring-boot:run
# Em outro terminal:
curl --fail http://127.0.0.1:8080/actuator/health
```

Ao terminar a API, pare apenas esse banco com `docker stop vanrides-dev-local`.
A receita Windows usa os mesmos comandos dentro do WSL2. São receitas
reproduzíveis, não uma declaração de teste físico nos computadores da equipe.

## Contratos e limites

Contratos WhatsApp canônicos: `src/backend/contracts/whatsapp-mvp/`; o bot lê
esses mesmos arquivos, sem cópia divergente. Pacotes Java, API, status,
autenticação, CORS, WebSocket e migrations foram preservados na migração.
Os scripts de fixtures e operação em `src/backend/deploy/ops/` foram mantidos
porque os testes verificam seus guardrails; a CI não os instala nem os executa
contra uma VPS. Helpers HTTP agora usam loopback por padrão.

A proveniência do gate usa SHA, tree e fingerprint de alterações de todo o
repositório, enquanto Maven/relatórios continuam no módulo. `--require-clean`
falha se qualquer fonte do checkout mudar. O lock e a finalização após liberação
do lock continuam obrigatórios. Evidência sanitizada fica local ao runner.
Nenhum resultado deste gate equivale a piloto, produção ou aceite de aparelho.

O remetente padrão em `application.properties` foi conservado como
compatibilidade; o provider local é `log`, sem entrega externa. O exemplo de
ambiente usa `example.invalid`. A biblioteca histórica de operação exige URL
DEV explicitamente informada e conserva o allowlist de segurança; ela não é
uma receita de bootstrap nem é acionada pelo gate acadêmico.
