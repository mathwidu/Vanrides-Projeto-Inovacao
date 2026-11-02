# ADR-001 — Namespace global e atômico de e-mail de login

- status: `accepted`
- data: 2026-07-17
- responsáveis: `SEC`, `SES`, `BE`, `QA`
- cards relacionados: `VR-SEC-004-EMAIL-NAMESPACE-001`,
  `VR-SEC-004-BOOTSTRAP-TRUST-001`, `VR-A11Y-003`, `VR-PRIV-003`

## Contexto

O backend possui quatro tabelas que podem representar uma conta de login:
`company_admin`, `passageiro`, `guardian` e `motorista`. As unicidades atuais
são isoladas por tabela; em `guardian`, a chave é apenas `(company_id, email)`.
O login e o reenvio consultam os repositórios em sequência, portanto uma
colisão pode escolher uma conta pela ordem do código. Os preflights existentes
também são sujeitos a TOCTOU: duas transações podem observar disponibilidade e
inserir o mesmo e-mail em tabelas diferentes.

O deploy DEV usa atualização `start-first`, e testes, fixtures e manutenção
também escrevem por JPA ou JDBC sem atravessar uma única service. Uma reserva
mantida apenas pela aplicação não protegeria writers antigos durante rolling
deploy nem imporia a invariável diretamente no PostgreSQL.

## Decisão

### 1. Proprietários que entram no namespace

Cada linha de `company_admin`, `passageiro`, `guardian` e `motorista` ocupa uma
identidade global, independentemente de verificação de e-mail ou de o
`guardian` já possuir senha.

Um `guardian` sem senha ainda é a conta que poderá receber acesso. Excluí-lo do
registry permitiria que outro papel ocupasse o endereço e criaria uma colisão
latente no momento da ativação. Contato que possa legitimamente se repetir em
várias empresas deverá ser modelado no futuro como contato separado da conta,
em vez de compartilhar o campo de login.

As flags de verificação e acesso não transferem ownership. Um endereço possui
no máximo um `(user_role, user_id)`, e esse owner possui no máximo um endereço.

### 2. Normalização e validação

A regra canônica é pequena, explícita e idêntica em Java e PostgreSQL:

1. remover somente espaços ASCII `U+0020` das extremidades;
2. exigir entre 3 e 254 bytes, exatamente um `@`, com conteúdo antes e depois;
3. aceitar somente ASCII imprimível entre `0x21` e `0x7e`;
4. converter todo o valor para minúsculas com `Locale.ROOT` no Java e collation
   `C` na função SQL;
5. não remover `+tag`, pontos nem aplicar regras específicas de provedor.

Tabs, controles, espaços internos e endereços EAI/Unicode falham com `400`
antes da persistência. A migration também aborta se encontrar legado fora
dessa regra. Suporte internacionalizado exige contrato de entrega, app e banco
próprio e fica rastreado em `VR-A11Y-003`; não haverá conversão silenciosa.

### 3. Autoridade do PostgreSQL

A migration `V25__global_login_email_namespace.sql` criará:

- função imutável de normalização/validação;
- tabela `login_identity` com `normalized_email` como primary key,
  `user_role`, `user_id`, timestamps e `UNIQUE (user_role, user_id)`;
- checks canônicos nas quatro tabelas de conta;
- triggers nas quatro tabelas.

Os triggers normalizam e validam antes da escrita e mantêm o registry depois
de `INSERT`, mudança de e-mail e `DELETE`. Uma colisão na primary key global
aborta a mesma transação que tentou criar ou alterar a conta. Uma troca move o
owner antigo para o novo endereço sem janela livre; se a nova reserva falhar,
conta e endereço antigo permanecem inalterados.

Em troca de e-mail, o trigger move primeiro a identity exata e só então revoga
refresh tokens, consome verificações pendentes e invalida device tokens. No
hard delete, ele remove primeiro a identity exata, verifica que uma única linha
foi afetada e depois invalida os mesmos artefatos. Essa ordem cobre writers
JPA antigos e JDBC durante rolling deploy e faz conta, registry e artefatos
confirmarem ou reverterem juntos.

Uma guarda adicional em `device_token` protege `INSERT` e reativação por
writers novos ou antigos. Para `active=true`, ela canonicaliza aliases de
papel, exige o owner global exato e segura sua linha com `FOR KEY SHARE` até o
commit. O service atual adquire esse lock antes de carregar a linha do device,
mantendo a mesma ordem usada pela troca/delete e evitando deadlock no caminho
novo. Em uma disputa, ou o device confirma primeiro e é visto pela invalidação,
ou espera a mutação e falha fechado; nunca confirma ativo para owner obsoleto.

O registry não será reservado em duplicidade pela aplicação. O
`LoginIdentityService` será a interface profunda para normalizar, consultar um
owner inequívoco, confirmar ownership e traduzir somente as constraints
conhecidas de e-mail. Writers continuarão transacionais e usarão
`saveAndFlush` para transformar a colisão concorrente em
`409 EMAIL_ALREADY_IN_USE`. Qualquer outra integridade continua falhando.

As constraints traduzíveis são somente `pk_login_identity_email`,
`uk_company_admin_email`, `uk_passageiro_email`,
`uk_guardian_company_email` e `uk_motorista_email`. Em especial,
`uk_login_identity_owner` sinaliza drift estrutural e nunca é convertida em
conflito público. A tradução usa apenas o nome estruturado da constraint; não
inspeciona texto livre de mensagens, que poderia conter o endereço no `DETAIL`.

Login, Basic Auth e o worker de reenvio consultarão primeiro `login_identity` e
carregarão exatamente o papel e ID apontados. Ausência ou inconsistência falha
fechada; não existe fallback para o próximo repositório. Login executa uma
única verificação BCrypt de custo 12 também no caminho inexistente, usando um
hash fictício. O endpoint de reenvio sempre enfileira o mesmo trabalho local
limitado antes de retornar, e apenas o worker resolve a identidade e alcança o
provedor. Confirmação e refresh exigem que `(role, userId, email)` ainda
correspondam ao owner global.

### 4. Preflight, backfill e reconciliação

A V25 executará em uma única transação PostgreSQL:

1. lock de escrita nas quatro tabelas durante a seção crítica;
2. preflight de inválidos, valores armazenados fora da forma canônica e
   colisões normalizadas intra-role e cross-role;
3. aborto com somente contagens/categorias sanitizadas quando houver problema;
4. criação e backfill 1:1 do registry, sem reescrever os e-mails das contas;
5. reconciliação fail-closed de device tokens ativos históricos, normalizando
   apenas aliases com owner exato e invalidando o restante;
6. instalação de checks e triggers das contas e de `device_token`;
7. validação de contagens, ausência de owners órfãos e ausência de device ativo
   sem owner exato.

Mesmo um endereço válido que dependa de lowercase ou remoção de espaço externo
para ficar canônico interrompe a V25. Essa escolha evita alterar dado pessoal
legado silenciosamente: a correção ocorre pelo runbook, com autorização e nova
execução limpa. A função de normalização ainda é usada para detectar colisões,
validar igualdade canônica e proteger todos os writes posteriores.

O preflight nunca escolhe vencedor, faz merge, apaga ou renomeia uma conta. Se
falhar, a transação inteira volta ao estado V24. O runbook humano agrupa as
linhas por identificador e IDs internos sem colocar e-mail bruto em logs ou
evidências. Qualquer alteração de dado pessoal exige autorização humana e
mantém a promoção em `HOLD` até nova execução limpa.

A leitura prévia de um banco real não é requisito para o card ficar `ready` ou
para validar a implementação local. Ela é obrigatória antes de promover a V25
em cada ambiente persistente. Colisão real move a promoção para
`waiting_human`/`HOLD`; não existe waiver para ignorá-la.

### 5. Deleção, reuso e artefatos antigos

Hard delete confirmado revoga refresh tokens, consome verificações pendentes e
libera a reserva no `AFTER DELETE` da mesma transação. Falha no delete reverte
também as revogações e mantém o owner. Soft delete ou desativação futura retém
a reserva enquanto a conta existir.

Uma troca de e-mail também zera a verificação persistida e invalida os
artefatos antigos no trigger. O writer mantém esse estado no contexto JPA,
executa `saveAndFlush` e somente depois cria a nova verificação; assim nenhum
token novo é consumido pela própria transação de troca.

Um endereço liberado pode criar uma identidade nova, sempre com novo
`role + userId` e sem herdar vínculos. Tokens antigos continuam vinculados ao
owner anterior e não podem verificar ou renovar a conta nova. Tombstone para
abuso ou auditoria, se necessário, deverá ser hashado e ter finalidade e
retenção explícitas em `VR-PRIV-003`; não ficará no namespace ativo.

### 6. Autorização de vínculo administrativo

O registry prova qual conta ocupa um endereço; ele não prova que um
administrador pode adotar essa conta. Portanto, endpoints administrativos de
criação exigem e-mail globalmente livre. Qualquer owner existente, ainda que
tenha o mesmo papel, esteja pendente ou não possua tenant, recebe o conflito
genérico. Nenhum desses endpoints altera tenant, perfil ou senha de conta
existente pelo simples conhecimento do e-mail.

Motoristas e passageiros públicos vinculam-se por convite/request autenticado
e aceite já existentes. Guardian já existente é alterado somente pelo endpoint
tenant-scoped que recebe seu ID. Essa separação preserva identidade e
autorização como decisões distintas.

### 7. Contrato público

Colisão sequencial ou concorrente retorna `409`, código
`EMAIL_ALREADY_IN_USE` e mensagem genérica, sem revelar papel, tenant, estado
de verificação ou existência de senha. O reenvio continua sempre `202` e não
enumerável: vazio, inválido, desconhecido, verificado, cooldown e elegível
retornam antes do lookup e do provedor pelo mesmo boundary de fila. Rotas e
payloads de sucesso não mudam.

`CompanyAdminController` deixará a exceção tipada atravessar para o handler
global, preservando os demais erros legados do bootstrap. Os consumidores
Flutter já aceitam `message`, `error` e texto; os testes focados deverão provar
essa compatibilidade no SHA congelado.

### 8. Rollout e rollback

A V25 é expand-compatible com o código anterior porque triggers protegem as
mesmas tabelas existentes. Durante um rolling deploy, writers antigos passam a
manter o registry sem conhecer a nova entidade. Depois que a V25 existir em um
ambiente compartilhado, ela nunca será editada ou removida: rollback de código
mantém tabela, checks e triggers; correção de schema é somente forward-fix.

## Alternativas consideradas

### Preflight nos serviços atuais

Rejeitada. Continua TOCTOU, duplica regra e não cobre JDBC ou código antigo.

### Registry mantido apenas por `LoginIdentityService`

Rejeitada. Não garante a invariável no banco e quebra compatibilidade no
rolling deploy.

### Adoção administrativa pelo mesmo e-mail

Rejeitada. Resolver um owner exato impede ambiguidade, mas conhecer o endereço
não autoriza transferir tenant nem sobrescrever perfil ou senha. Vínculo exige
convite/request autenticado ou update tenant-scoped por ID.

### Reservar somente `guardian` com senha

Rejeitada. Permite colisão latente e tomada do endereço antes da ativação. A
separação entre contato e conta deve ser explícita no modelo futuro.

### Escolher automaticamente a conta verificada ou mais antiga

Rejeitada. Estado de verificação ou antiguidade não prova ownership e pode
produzir takeover ou perda de dados.

### Manter tombstone eterno após hard delete

Rejeitada. Retém dado pessoal sem finalidade definida e impede recuperação
legítima. Auditoria futura deve ser minimizada, hashada e ter prazo.

## Consequências

- A invariável passa a ser estrutural e compatível com writers antigos.
- Login cai de até quatro consultas por e-mail para registry por PK mais uma
  consulta por ID.
- Escritas concorrentes no mesmo endereço serializam no índice global; outras
  identidades não compartilham lock lógico.
- Ambientes com legado ambíguo não iniciam a nova versão até reconciliação
  humana.
- Endereços não ASCII deixam de ser aceitos nesta versão; a limitação fica
  explícita e testada, sem transformação silenciosa.
- A matriz PostgreSQL obrigatória acrescenta 18 testes ratcheteados: seis de
  migration/paridade, quatro de triggers, quatro de concorrência de contas e
  quatro de concorrência de device. O perfil estrito sobe de 13 para 31
  testes.
- `R-017` só pode ser encerrado após a V25 estar promovida e verificada em todos
  os bancos; prova local deixa o risco apenas `mitigando`.

## Validade e revisão

Revisar quando `VR-A11Y-003` definir e-mail internacionalizado,
`VR-PRIV-003` definir deleção/retention completa ou o modelo separar contato de
conta. Mudança da regra canônica exige nova migration forward-only, matriz de
compatibilidade e revalidação de todos os consumidores.
