# Pareamento iOS — validação host do 060

Na raiz do app, compilar uma vez e executar o binário host sem Xcode, simulador
ou aparelho. A fixture usa uma chave Security efêmera sem Keychain; o código de
produção exige Secure Enclave e não faz fallback para ela.

```sh
bash ios/RunnerTests/run_driver_session_host.sh
```

O runner descobre o SDK macOS e adiciona explicitamente os caminhos do framework
XCTest e do overlay Swift em `MacOSX.platform/Developer/usr/lib`. Compila apenas
os dois fontes nativos e as fixtures; remove o binário temporário ao terminar.

O teste usa HTTP sintético injetado, sem servidor nem segredo real. Verifica
assinatura ES256/JOSE real, PKCE, nonce limitado, digest dos bytes, `ath`, journal
antes do POST, retomada do begin/redeem/refresh/revoke, rotação serializada,
estados fechados e ausência de identidade/token no snapshot. Não comprova TLS
real, persistência Keychain/Secure Enclave no iOS nem comportamento visual.
Timeout/offline devolve `NETWORK_ERROR`, e HTTP429 devolve `RATE_LIMITED`, sem
mensagem remota nem alteração dos bytes/chave de idempotência no journal.
Um access vencido conserva `SETUP_REQUIRED` quando o refresh ainda pode ser
usado: sincronizar rotaciona com a mesma chave e só informa `READY` após status.
Cada processo novo também exige esse status antes de atestar `READY` persistido.
O teste cobre cold restart depois dos cinco minutos e refresh realmente vencido.
Intenção de revogação persiste separada do journal: se o access vencer, a
recuperação rotaciona o refresh com idempotência e cria novo revoke sem voltar
`READY`. Rede/429 conservam essa operação; refresh negado/vencido ou recuperação
410 terminam `EXPIRED`, sem alegar revogação. Reinício entre o commit da rotação
e o revoke também é coberto. REFRESH/REDEEM410 permitem novo vínculo explícito.

Novas chaves Secure Enclave e o journal usam
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: após o primeiro desbloqueio
desde o boot, podem atender a execução nativa com tela bloqueada; continuam
locais ao aparelho e não sincronizam. A chave mantém `.privateKeyUsage`, sem
exportação ou fallback de produção. Essa escolha não inicia coleta nem renova
lease por conta própria. Ver [documentação da Apple](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly).

O registro local v1 identifica o provisionamento anterior, `WhenUnlocked`.
Leitura, pedido pendente, PKCE, status, refresh/replays e revogação conservam
essa versão e a mesma chave/JWK; não há mudança silenciosa de identidade nem
promessa de alterar a ACL da chave antiga. O journal recebe a nova classe em
seu update atômico. Somente no próximo `beginPairing` explícito já elegível
após encerramento/expiração é gravado um registro v2 com tag nova antes de
criar a chave sob a nova política. Crash/retry reutiliza a tag e o journal v2;
novos vínculos v2 não repetem essa migração. Binário antigo que só lê v1 não
deve ser tratado como downgrade compatível para um registro v2.

As fixtures host v1 comprovam preservação do vínculo vivo, dos corpos/chaves
idempotentes e da chave pública; também cobrem revogação seguida de migração
única, restart com begin perdido e interrupção antes de criar a chave nova.
Elas não comprovam a política de proteção do Secure Enclave no aparelho. Uma
sessão v1 já existente conserva a restrição antiga até novo vínculo explícito.
Nos cards [030](../../../../program/cards/VR-WA-030-GPS-PROOF.md),
[110](../../../../program/cards/VR-WA-110-RUN-TRACKING.md) e
[180](../../../../program/cards/VR-WA-180-E2E-DEVICES.md), permanece pendente provar
DPoP, refresh/journal, renovação/expiração de lease e stop com tela bloqueada,
incluindo reinício e primeiro desbloqueio. Build e testes host não liberam
essa aceitação física.

`Runner` inclui os três fontes `DriverSession*.swift` e o teste no target
`RunnerTests`. O bridge existe somente com `VANRIDES_DRIVER_MVP`; não modifica o
canal `tracking.v1`. A configuração lê `VANRIDES_PAIRING_API_ORIGIN` do Info.plist
compilado. Ela exige origem HTTPS sem path, slash final, query ou usuário. A
origem precisa coincidir exatamente com a base canônica do backend. O exemplo
em `ios/Flutter/Driver-Pairing.xcconfig.example` é deliberadamente inválido para
execução real; nenhum host público ou credencial é provisionado por estes fontes.

Chamadas do canal não aceitam argumentos. Apenas `beginPairing`, vindo da ação
explícita da tela, cria a chave e o PKCE. Retorno por URI só reconcilia um pedido
existente; o bridge não lê URI nem altera origem/chave com dados do link.
`revokeSession` pressupõe confirmação humana no Flutter e conserva `REVOKING`
e as credenciais privadas enquanto não receber ACK válido. O nativo oculta as
janelas ao desativar o app/scene para reduzir exposição no seletor de apps;
o comportamento real do snapshot exige a prova física que continua pendente.
`dev.matheusduarte.vanrides.driver/launch.v1` aceita apenas `isLauncherVisit`
sem argumentos. No iOS devolve `false` conservadoramente: visibilidade/lifecycle
de scene não comprovam ícone em retorno warm. A UI mantém a orientação de fechar
e abrir pelo ícone em uma visita cold; a consulta nunca inicia pareamento.

Build genérico iOS, teste de canal Flutter, TLS local e prova física ficam a
cargo da integração coordenada. Não executar estes checks host como evidência
de iPhone, de coleta ativa ou de prontidão de piloto/produção.
