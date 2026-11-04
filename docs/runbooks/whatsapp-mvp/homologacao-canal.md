# Canal WhatsApp da homologação da equipe

Registro de 04/11/2026, relacionado à issue #91. A autorização do proprietário
abrangeu publicação do canal, importação dos fluxos, admissão do contato de teste
e preparação do QR no Redmi. Nenhum serviço pago ou modelo de IA foi ativado.

## O que foi aplicado

- Repositório `mathwidu/Vanrides-Projeto-Inovacao`, branch
  `codex/homologacao-api-equipe`, fonte de execução `c7cbe3d`.
- VPS `187.77.47.96`, stack `vanrides-hml-canal`: Evolution 2.3.7, PostgreSQL,
  Redis, proxy TLS interno, receptor de eventos e worker. Seis serviços 1/1.
  Imagens fixadas por digest em `deploy/homologacao/canal/stack.yml`.
- Rede privada própria e integração do worker à rede existente do n8n. Nenhuma
  porta adicional publicada na internet. Dois volumes exclusivos para banco e
  sessão do WhatsApp. Credenciais próprias em Docker Secrets.
- Instância `vanrides-hml`, sem sincronização de histórico/agenda/grupos.
  Webhook de mensagens, estados de entrega e conexão apontado ao receptor privado.
- Quatro credenciais da API com funções separadas: canal, conversa, envio e
  agendamento. Expiram em **01/12/2026 00:00 UTC**. Renovar antes dessa data.
- Lote técnico separado dos participantes reais. Um passageiro admitido com
  confirmação do proprietário, sem preencher automaticamente perfil ou papel.
- Oito fluxos novos no projeto pessoal autorizado do n8n. Uma Header Auth
  exclusiva controla o worker. W07 publicado. W01/W04/W06/W08 aguardam conexão
  OPEN do bot para ativação. W02/W03/W05 permanecem subfluxos inativos.

A API publicada continua no backend `57a2ffd`; o aplicativo continua
`0.3.0-hml+13`. O JAR administrativo é executado em serviços temporários
não web, sem reiniciar a API nem executar migração. Todos os sete pedidos
administrativos retornaram COMMITTED e os serviços temporários foram removidos.

## Evidências e limites

| Conferência | Resultado |
| --- | --- |
| API HTTPS depois da preparação | 200 / UP |
| Réplicas do canal | 6 serviços com 1/1 |
| Inicialização do receptor e worker | READY |
| Saúde privada do worker | 200 |
| HTTPS Evolution com CA própria e hostname validado | 200 |
| Controle sem credencial | 401 / UNAUTHORIZED |
| Controle autenticado, inbox vazio | 200 / IDLE, processed=0 |
| n8n: importação | 8 fluxos próprios, inicialmente inativos |
| n8n: publicação de W07 | Publicado, confirmado também no editor |
| Fluxos preexistentes do n8n | Estados e versões preservados |
| Pareamento WhatsApp | QR preparado privadamente; aguardando leitura no Redmi |
| Conversa real, envio e recibo | Ainda não verificados |
| GPS pelo canal publicado | Ainda não verificado; requer participante motorista |

Os testes físicos do iPhone continuam pausados. Os avisos de aproximação
continuam desabilitados até a calibração e validação previstas. Não confundir
instalação dos serviços com aceite de todas as funcionalidades do MVP.

## Decisões da operação

1. A Evolution é exclusiva deste ambiente para separar sessão, armazenamento e
   configuração dos outros projetos da VPS. O n8n existente foi reutilizado.
2. A importação usa IDs exclusivos e confere colisões antes de escrever.
   A CLI instalada do n8n 2.45.4, em queue, suporta a publicação por
   `import:workflow --activeState=fromJson` sem reiniciar o serviço compartilhado.
   Publicar apenas os IDs dessa entrega. Ver `deploy/homologacao/canal/n8n.md`.
3. W07 precisa de versão publicada. Sua lista de chamadores foi ajustada na
   cópia implantada para os outros sete fluxos desta entrega; não foi aberto
   acesso a fluxos de outros projetos. O helper registra o hash derivado.
4. A primeira tentativa administrativa falhou na consulta de serviço: o Docker
   não aceita `service ls --no-trunc`. Nenhuma solicitação havia sido enviada.
   O serviço daquela tentativa foi removido após verificar seu identificador
   de propriedade. A consulta passou a usar nome e inspeção do ID completo.
5. Arquivos públicos montados no operador recebem modo 0444 explícito,
   independentemente do umask. Solicitações, recibos, tokens e QR permanecem
   privados e fora do Git. O operador também trata a precisão do timestamp Java.

## Proveniência

- Fontes do canal: SHA-256
  `2d48f3ecf4ef66cb6667c0cc9d869e03956678697e4d207d7cc5478008c6941c`.
- JAR administrativo: SHA-256
  `29dd480124e7e515cd47aac30b4eeea46a56bfdf134e91f62b343aa4b91f9773`.
- Bundle n8n inativo aplicado, com ajuste restrito do W07: SHA-256
  `ecd5aad4cbd9584696e0f1f0ba9decbc2c21294f88410a69a59858fd3830dfcc`.
- Namespace dos fluxos: `vrHml6dffaf010805` (W01 a W08).
- Recibos administrativos e de importação ficam no diretório privado do operador
  na VPS, dentro de `homologacao/canal-c7cbe3d`. Não copiar seu conteúdo para issues.

## Continuação e retorno

Após a leitura do QR, confirmar OPEN, publicar W01/W04/W06/W08 e pedir que o
passageiro autorizado envie uma mensagem ao número do bot. Conferir ingestão,
processamento, resposta recebida e recibo correlacionado sem guardar a conversa
na evidência pública. A inclusão dos colegas exige confirmação dos contatos e
admissão na mesma homologação, sem compartilhar credenciais administrativas.

Se necessário suspender o canal, despublicar somente os quatro agendamentos
deste namespace, desligar o webhook da instância dedicada e remover apenas os
serviços da stack do canal. Revogar as credenciais técnicas da rodada pelo
operador. Preservar volumes e recibos para diagnóstico. Não reiniciar n8n,
excluir dados de outros projetos ou alterar as stacks da API e do APK.
