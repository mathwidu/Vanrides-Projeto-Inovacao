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

Execute da raiz Git:

```bash
bash scripts/check_backend.sh --light
bash scripts/check_backend.sh
```

`--light` executa contratos dos gates, isolamento/proveniência/locks e ratchet,
sem Maven, containers ou rede. O comando completo exige Docker local: roda
uma vez o gate canônico com toda a matriz PostgreSQL, empacota o mesmo código
e executa a jornada HTTP sintética com Spring/PostgreSQL/worker Node reais e
um provedor Evolution simulado em loopback. Não requer número, WhatsApp, IA
paga nem credencial externa. Os recursos temporários pertencem à execução.

CI: [backend.yml](../../.github/workflows/backend.yml), Ubuntu padrão, Java 17,
Node fixado e permissão `contents: read`. Ela também constrói a imagem local;
não publica imagens, não implanta e não envia logs brutos como artefato.
PASS remoto deve ser conferido no SHA efetivamente avaliado. Um `mvnw test`
isolado que tenha testes pulados não substitui o gate.

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
