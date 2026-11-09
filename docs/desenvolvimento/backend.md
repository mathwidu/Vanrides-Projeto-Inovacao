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

- **Verificações rápidas**: ratchet e testes dos auxiliares, antes dos jobs pesados.
- **PostgreSQL em oito grupos**: a mesma matriz obrigatória, distribuída por
  duração histórica entre runners isolados. Cada grupo executa sequencialmente,
  com banco próprio; nenhum caso ou skip é dispensado.
- **Demais testes Java**: descoberta padrão do Surefire, excluindo apenas as
  classes da matriz PostgreSQL. Novos testes continuam sendo descobertos;
  não há uma segunda lista manual de testes unitários.
- **Suíte canônica PostgreSQL**: agrega os oito grupos e os demais testes Java,
  confere cobertura, revisão e origem dos resultados e reprova ausências,
  duplicações ou falhas. Roda em paralelo ao empacotamento do candidato.
- **Empacotar candidato uma vez**: gera um JAR e seu checksum, guarda somente
  esse arquivo como artefato imutável por um dia. Ainda não é aprovação do produto.
- **Integração HTTP e imagem**: recebe o candidato por ID, verifica revisão e
  checksum, executa cinco jornadas com os seis conjuntos de validação, e monta
  a imagem com o mesmo JAR. O target `tested-jar` confere o checksum novamente.
- **Homologação do candidato compartilhado**: chama
  [homologacao-api.yml](../../.github/workflows/homologacao-api.yml), que testa
  o auxiliar em Windows/Linux e exercita a API com duas credenciais sintéticas.
  Usa o mesmo JAR, sem empacotar outro.
- **Resultado do backend**: exige sucesso de rápida, PostgreSQL, candidato,
  integração e homologação. Etapas puladas ou canceladas não viram aprovação.

Todos os checkouts usam `github.sha` (merge de teste no PR). A ação local
`receive-backend` exige ID único desta execução, SHA compatível com o checkout,
checksum calculado pelo produtor e somente `backend.jar` no artefato. Não há
busca pela última compilação de outra branch. O cache Maven guarda dependências,
não resultados de testes ou aprovação. O `user.home` isolado e os locks da
matriz PostgreSQL continuam preservados.

A homologação deixou de disparar outro workflow independente para cada mudança.
Seu disparo manual agora é pelo workflow **Backend e integração sintética**,
que produz o candidato e executa todos os consumidores. Os paths anteriores
foram incorporados ao caller. Antes da integração no GitHub, conferir os checks
exigidos pela proteção da branch, pois os nomes da homologação passam a ser aninhados.

Cada grupo compila os testes no próprio runner; o candidato continua sendo o
único JAR empacotado. Essa escolha reduz o tempo de espera, mas pode aumentar o
trabalho somado dos runners. PostgreSQL e as integrações permanecem obrigatórios.
Em PRs, o fluxo termina na validação. Em push na `main`, os gates liberam a
imagem do mesmo JAR e o deploy da API pelo digest. O provisionamento inicial
ainda está pendente; consulte o [guia](../../deploy/homologacao/AUTOMATICO.md).
Os testes continuam sem WhatsApp real ou upload de logs brutos.
Maven usa `-ntp` sem ocultar saída de falhas. O build Docker padrão por fontes
continua disponível para os consumidores existentes.

O comando local completo mantém `qa_integration_profile.sh --all-tests` e
continua sequencial. A divisão em oito grupos é da CI; não iniciar oito Maven
simultâneos em um computador de desenvolvimento. Medições locais e remotas,
incluindo o estado de publicação desta mudança, ficam na
[revisão da CI](revisao-ci-e-testes.md).

Nesta primeira versão, os recibos precisam pertencer à mesma tentativa da
execução. No GitHub, uma retentativa deve usar **Re-run all jobs**: reexecutar
somente um grupo ou o agregador não encontrará os nove recibos dessa tentativa
e será rejeitado. Reaproveitar grupos de tentativas anteriores fica para uma
melhoria separada; nenhum resultado incompleto é aprovado por essa restrição.

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
