# Ferramentas históricas do laboratório 110

Código de recuperação, isolamento, limites e teardown preservado com seus
contratos/hashes originais. IP, hostname, usuário, alias e bundle IDs são
identificadores públicos de compatibilidade histórica; não há chaves privadas,
credenciais de conta, sessões ou GPS real neste diretório.

A CI executa unitários com fixtures. `physical/prepare.py package` e sua
reconstrução exata v6 dependem do histórico original e de entradas externas
explicitamente fixadas; não são o caminho de bootstrap deste repositório.
Nenhum teste offline autoriza usar estes controladores contra VPS ou aparelhos.
Uso externo exige preparar outro manifest, conferir o ambiente e obter a
respectiva autorização. Não reatribua recibos antigos ao SHA acadêmico.

O smoke atual sem dependência desse histórico é
`node src/whatsapp/scripts/academic-integration-smoke.mjs`, iniciado pelo gate
do backend em checkout limpo. Ele cria apenas recursos próprios locais.
