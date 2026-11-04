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

`--light` executa primeiro o ratchet e depois os testes dos gates,
isolamento/proveniência/locks, sem Maven, containers ou rede. Usa a mesma
definição de checks de `src/backend/scripts/ci_quality_gate.sh --quick`.
É uma validação parcial, não aprovação do backend. O comando completo exige Docker local: roda
uma vez o gate canônico com toda a matriz PostgreSQL, empacota o mesmo código
e executa a jornada HTTP sintética com Spring/PostgreSQL/worker Node reais e
um provedor Evolution simulado em loopback. Não requer número, WhatsApp, IA
paga nem credencial externa. Os recursos temporários pertencem à execução.

CI: [backend.yml](../../.github/workflows/backend.yml), Ubuntu padrão e
permissão `contents: read`:

- **Verificações rápidas do backend**: ratchet e testes dos auxiliares. Uma
  falha impede a execução dos dois jobs pesados seguintes.
- **Suíte canônica PostgreSQL**: após a etapa rápida, executa toda a matriz
  com `qa_integration_profile.sh --all-tests`, sem reduzir testes nem aceitar
  skips. As duas etapas compõem o mesmo gate canônico do comando local.
- **Integração HTTP e imagem**: empacota o mesmo checkout com testes pulados
  somente nesse empacotamento, executa as seis jornadas reais sintéticas e
  constrói a imagem local. A aprovação da suíte canônica continua obrigatória.
- **Resultado do backend**: apresenta a revisão e os resultados de cada etapa;
  só aprova se rápida, PostgreSQL e integração tiverem sucesso. Falha,
  cancelamento ou etapa não executada não viram aprovação.

PostgreSQL e integração continuam em paralelo depois da etapa rápida.
Os três checkouts usam explicitamente o mesmo `github.sha` (merge de teste em
PR), com proveniência e árvore limpa; o smoke rejeita um SHA diferente do esperado.
O workflow só fica aprovado quando todas as etapas obrigatórias passam. Não há cache Maven,
publicação de imagens, implantação ou upload de logs brutos. Um `mvnw test`
isolado que tenha testes pulados não substitui o gate.

As invocações Maven dos gates usam `-ntp` para omitir progresso de transferência
de dependências, mantendo resultados, erros e saída dos testes. O workflow de
homologação também espera os testes do auxiliar em Windows/Linux e confere
regras estáticas e receitas antes de empacotar a API. Não omitir a etapa rápida
ao reproduzir manualmente a validação completa.

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
