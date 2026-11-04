# VR-HML-010-TEAM-API — Homologação técnica da API para o grupo

## Resultado esperado

Todos os integrantes podem usar a API implementada do MVP no mesmo endereço HTTPS, inclusive rotas de atendimento e do app do motorista. Issues existentes: [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) e [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90).

## Estado

`in_progress`. O controlador mantém um único card ativo. A decisão RD47 registra a finalidade desse ambiente.

## Ajuste de prioridade (RD48)

Publicar a API completa do MVP primeiro. O cenário de cadastro restrito abaixo é uma prova opcional da etapa anterior. A publicação usará o modo online da API com autenticação normal dos atores e acesso para toda a equipe. Worker, n8n/Evolution e APK do domínio entram após a API. A pedido do proprietário, limitar as verificações operacionais a artefato, destino, startup/HTTPS e fronteira de acesso.

## Atualização de 04/11/2026

API, PostgreSQL, APK e canal publicados em homologação. Saúde e download
reconferidos; perfil básico e quatro interações após a correção validados no
WhatsApp real. [Guia atual da equipe](../../docs/desenvolvimento/homologacao-da-equipe.md).
A prova real do canal está na issue #91; não altera retroativamente o escopo
restrito descrito neste card. Faltam motorista participante, jornada completa
e revisão/aceite do colega. O card permanece em andamento. A integração local
com main/PR #105 não substitui a API acc572f ainda instalada na VPS.
O pedido de 04/11 inclui integrar as melhorias do colega e atualizar README,
CONTRIBUTING, arquitetura, resultados e plano de entrega para orientar o time.
Esses caminhos foram acrescentados ao escopo documental deste mesmo card;
nenhuma nova funcionalidade ou promoção do piloto foi autorizada por isso.
A conferência da CI também inclui alinhar o texto esperado no ensaio
`070-worker-scenarios.mjs` ao comportamento publicado, mantendo os checks.

## Impact Analysis da preparação inicial

A base local f85ec3e contém o mesmo runtime de dd12cf0, aprovado nas seis jornadas. A primeira fatia muda o perfil público do backend, o comando de teste para o colega e a receita de implantação. O domínio vanrides-inovacao-api.matheusduarte.dev.br resolve para a VPS. O serviço publicado ainda precisa de configuração, certificado válido e teste funcional. PostgreSQL e credenciais serão específicos da nova stack.

## Dependências

040 (autenticação) e 070 (contratos de conversa) concluídos. A homologação usa dados fictícios e tem aceite próprio. Os aceites de canal, percurso, iPhone, encerramento e piloto permanecem no plano do MVP.

## Escopo

Backend: perfil homolog restrito, bloqueio de e-mail, rotas permitidas e testes de isolamento. Coordenação: stack da API e banco, imagem que preserva o JAR aprovado, preparo/retorno, runner do colega e instruções. Os diretórios permitidos constam de program.json.

## Fora de escopo

Dados de passageiros reais, disparos de WhatsApp, GPS de aparelhos, serviços pagos, acesso do grupo por SSH administrativo e alterações na API antiga. A integração real da Evolution tem prova separada na issue #91.

## Contratos

Usar o perfil Spring homolog e um modo explícito da API para o time. Preservar os controles públicos e negar configuração ambígua, e-mail, cadastros legados e rotas fora da lista. No perfil restrito, permitir saúde e quatro operações de conversa. No perfil online, permitir as rotas disponíveis de WaRoutes com a autenticação original e bloquear administração/legado. Credencial nominal por integrante e instância sintética distinta. Restringir os comandos a operações sintéticas do cenário, tanto no contexto quanto na execução. Não persistir conteúdo de conversas ou tokens em evidência.

## Riscos

Uma rota legado exposta pelo proxy, uso de provedor real, vazamento entre integrantes ou troca do binário após o teste invalidam a publicação. Aplicar restrições na API e no ingresso, revisar casos negativos, conferir o digest instalado e manter o volume anterior na recuperação.

## Definition of Ready

- [x] Base e dependências conferidas. Checkout limpo antes desta abertura.
- [x] Contrato, owners por frente, testes, domínio, limites e rollback definidos.
- [x] Preparação local autorizada; publicação exige plano do SHA, artefatos e ambiente concretos.

## Critérios de aceite

- [ ] Perfil restrito passa nos testes de configuração pública, isolamento, autorização, replay e bloqueio de e-mail.
- [ ] Receita reproduzível preserva o JAR testado e testa a stack e o retorno em dados sintéticos.
- [ ] A revisão e as imagens aprovadas respondem em HTTPS na nova stack. Credencial inválida, expirada ou de outro integrante é negada.
- [ ] Um colega reproduz o cenário e a persistência do resultado e registra sua revisão.

## Validação

Testes de configuração/HTTP e de integração com PostgreSQL, gates de contratos, CI, verificação da imagem e restore em banco descartável. Depois da publicação autorizada, confirmar TLS, digest e a operação real do colega. Ver o [roteiro do ambiente](../../docs/runbooks/whatsapp-mvp/homologacao-api.md).

## Rollout

Preparar em branch própria, revisar o PR pelo grupo e testar. A promoção exige fonte e imagens fixadas, escopo de rede/secrets da nova stack, smoke e retorno aprovados. Primeira rodada com um colega e credencial temporária.

## Rollback

Revogar as credenciais da rodada e retirar a nova rota. No primeiro deploy, parar os novos serviços preservando o volume próprio. Em atualização, voltar ao digest anterior apenas se o schema for compatível; quando necessário, restaurar o banco isolado previamente ensaiado.
