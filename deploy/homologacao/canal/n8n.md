# Importar e publicar somente os oito fluxos HML

Importação/publicação ainda não executadas por este preparador. A conferência
somente leitura de04/11 confirmou n8n2.45.4 no digest abaixo, queue ativo,
100 workflows/36 ativos e o projeto pessoal do proprietário, sem consultar
email, tokens, cookies ou dados de credenciais. Não cria outra
instância, reinicia serviços ou altera fluxos/credenciais preexistentes.
Os exports canônicos são de n8n **2.45.4**, fonte
`e0c02a617f5e6ede3d6c448aff41f632a782aaca`. Conferir a versão instalada antes
da execução autorizada. Não atualizar a imagem compartilhada para este trabalho.

## Caminho automatizado confirmado no runtime atual

`n8n_importar.py` é executado pelo operador no host da VPS, com Docker já
autorizado. Sem `--apply`, realiza somente leitura de metadados; não abre o
arquivo control. O helper fixa o digest já observado, confirma queue, valida
projeto pessoal do global owner, hashes e IDs do bundle e recusa importação
inicial sobre IDs ou credencial existentes. Não seleciona email, senhas,
credential.data ou cookies no banco. Usa a CLI do editor existente, com
`--projectId` explícito e o mesmo mecanismo de criptografia do serviço.

```bash
# Reutilizar o bundle e o namespace já revisados, sem gerar outro conjunto.
N8N_HML_ROOT=/home/mathwidu/.local/share/vanrides/homologacao/canal-c7cbe3d
N8N_HML_ARGS=(
  --bundle "$N8N_HML_ROOT/n8n"
  --manifest-sha256 HASH_DO_MANIFEST_REVISADO
  --project-id ID_DO_PROJETO_PESSOAL_CONFERIDO
  --receipt "$N8N_HML_ROOT/n8n-receipt.json"
)
python3 n8n_importar.py status "${N8N_HML_ARGS[@]}"
python3 n8n_importar.py importar "${N8N_HML_ARGS[@]}" \
  --control-file "$N8N_HML_ROOT/segredos/control" --apply
python3 n8n_importar.py publicar-w07 "${N8N_HML_ARGS[@]}" --apply
# Somente após API/worker prontos, contatos admitidos e instância OPEN:
python3 n8n_importar.py publicar-agendamentos "${N8N_HML_ARGS[@]}" --apply
```

Somente a etapa `importar --apply` lê o **novo** control privado0600, escreve
um Header Auth temporário0600 dentro do contêiner, importa a credencial dedicada
e remove o arquivo temporário. Nenhum segredo vai a argv, stdout ou recibo.
A credencial é associada ao projeto pelo importador; não é necessário digitá-la
no editor. Não exporta/reutiliza credenciais de outros fluxos.

As etapas de publicação usam `import:workflow --activeState=fromJson`, caminho
suportado em queue e confirmado no código instalado: chama
`WorkflowService.activateWorkflow`. Isso difere de `publish:workflow`, que nesta
versão altera o banco e pede restart. `publicar-w07` importa/publica somente W07;
`publicar-agendamentos` importa/publica somente W01/W04/W06/W08. W02/W03/W05
permanecem inativos. Não reinicia editor, worker, webhook ou outros workflows.

O runtime aplica callerPolicy ao handler de erro. O preparador original só
permitia W01/W08 em W07, bloqueando erros dos outros pais. O payload derivado
ajusta **somente W07** para allowlist dos outros sete IDs desta mesma entrega;
não usa sameOwner nem altera política global. O recibo registra o hash do
manifest de entrada, o hash do bundle inativo efetivamente aplicado e a mudança.
Para c7cbe3d/namespace `vrHml6dffaf010805`, os hashes revisados e o diff sem
segredos ficam no arquivo privado `n8n-canal/derivacao.json`.

Pós-condições verificam propriedade, credencial, quantidade e versões publicadas,
aguardando até45s quando a aplicação da publicação for assíncrona. O recibo
mantém o resultado de cada etapa e compara o estado dos workflows fora do lote.
Falha parcial não autoriza repetir o importador sobre dados existentes: consultar
`status` e reconciliar os IDs próprios antes de continuar. Não há rollback global.
O editor autenticado continua sendo alternativa para publicar/despublicar esses
mesmos IDs, mas não há uma etapa humana obrigatória no caminho automatizado.

## Credencial e inventário — editor/operador

No editor autenticado, selecionar o projeto já autorizado para VanRides. Se a
instância não disponibilizar projetos de equipe, usar o projeto pessoal do
proprietário autorizado e manter o namespace abaixo; isso não cria segregação
adicional de acesso. Não contratar plano nem trocar a chave de criptografia.

Na alternativa pelo editor, criar uma **nova Header Auth** chamada `VanRides HML Controle` ou com sufixo da
entrega. Nome do header: `Authorization`. Valor: `Bearer ` seguido do token
de controle exclusivo do worker HML, fornecido privadamente pelo proprietário.
Não é a chave da Evolution, OPERATOR, CHANNEL, DELIVERY ou de API do n8n.
Compartilhar somente no projeto autorizado. Copiar apenas ID e nome da nova
credencial para a preparação local; este procedimento/helper não lê seu valor.
Essa criação/entrada do segredo é um passo explícito no editor.

O operador pode inventariar IDs usando a CLI instalada do mesmo n8n:

```bash
n8n --version
n8n list:workflow --onlyId
```

Executar como o usuário do serviço, no contexto da instância já existente.
A CLI observada emite avisos de inicialização antes dos IDs. Não usar essa saída
bruta como inventário do preparador: preservar apenas as linhas de IDs ou usar
o inventário de metadados READ ONLY já conferido pelo operador.
Não exportar credenciais, execuções ou conteúdos dos fluxos para obter IDs.
Registrar o projeto/usuário de destino. `--projectId` e `--userId` são opções
alternativas da importação; não usar as duas nem deixar o destino implícito.

## Preparar localmente — sem conexão ao n8n

```bash
python3 deploy/homologacao/canal/n8n_preparar.py \
  --ids-existentes /diretorio/privado/ids-antes.txt \
  --credencial-id ID_CRIADO_NO_EDITOR \
  --credencial-nome 'VanRides HML Controle' \
  --saida /diretorio/privado/n8n-hml-entrega-nova
```

O helper usa o gerador canônico, atribui namespace aleatório exclusivo aos oito
IDs/nomes, reescreve `errorWorkflow` e `callerIds`, associa somente a referência
da credencial e preserva todos inativos. Recusa destino existente/colisão de ID.
`manifest.json` registra IDs, hashes, pin, inventário e ordem; `importar.json`
contém os oito exports. Não usar os IDs fixos `vrHmlFlow...` diretamente em uma
segunda importação: a CLI preserva IDs fornecidos e pode atualizar registros.
Não repetir importação de um bundle já aplicado nem alterar fluxos pelo nome.

Antes de importar, repetir o inventário e conferir ausência dos oito IDs. Se
algum existir, interromper e reconciliar aquela entrega; gerar outro namespace
somente quando uma nova entrega for intencional. Essa checagem evita sobrescrita
por IDs; a CLI não oferece uma garantia transacional de create-only contra uma
alteração concorrente. Manter apenas um operador importando esta entrega.

## Importar inativos — CLI existente

Colocar somente `importar.json` em arquivo temporário legível pelo usuário do
n8n, com caminho exclusivo da entrega; não copiar credenciais para o arquivo.
Na instância/contêiner existente, executar **uma** das formas:

```bash
n8n import:workflow --input=/privado/entrega/importar.json \
  --projectId=ID_DO_PROJETO_AUTORIZADO --activeState=false
# Alternativa quando o destino é o projeto pessoal autorizado:
n8n import:workflow --input=/privado/entrega/importar.json \
  --userId=ID_DO_USUARIO_AUTORIZADO --activeState=false
```

Nesta importação **inicial inativa**, não usar `--activeState=fromJson`, `--all`, importação de entidades ou importação
de credenciais em massa. Conferir **8 importados, nenhum ignorado**. No editor,
conferir os oito IDs efetivos contra o manifest, nomes com o namespace novo,
projeto, estado não publicado, referência da Header Auth e URLs
`http://vanrides-hml-worker:7080/v1/...`. Os executores existentes precisam
alcançar esse nome pela rede privada prevista na stack do canal; erro de DNS
não se resolve reiniciando o n8n.

Se o editor for a alternativa de importação, criar oito workflows **novos**,
um arquivo `Wxx.json` por vez, nunca importar sobre um canvas existente. O n8n
pode atribuir outros IDs: registrar o mapa lógico→real e ajustar antes da
publicação o Error Workflow para W07 e a lista de chamadores para W01/W08
reais. Não presumir que os IDs do JSON foram preservados nesse caminho.

## Alternativa: publicar sem reiniciar o n8n pelo editor

Na fonte fixada, `publish:workflow` solicita restart para aplicar mudanças.
Por isso **não usar publish:workflow nem update:workflow** neste procedimento.
O caminho hot de importação em queue está documentado acima.
Publicar individualmente no editor autenticado, que aplica a alteração ao
runtime em execução. Não alterar flags globais/publication service/queue.

1. Conferir W07 (erro) e W02/W03/W05 (subfluxos), salvar/publicar as versões
   desse namespace conforme o editor. Eles não possuem agendamento periódico.
   Conferir W07 como Error Workflow de todos os demais e chamadores W01/W08.
2. Com API e worker saudáveis, instância dedicada OPEN, credenciais técnicas
   válidas e um participante admitido, publicar W06 e W01 desse namespace.
   W06 agenda envio a cada2s; W01 processa inbox a cada5s. Não publicar versões
   de outra entrega nem ativar um segundo conjunto com o mesmo worker.
3. Publicar W04 e W08 somente com SYSTEM_JOB no worker: presença e reconciliação
   a cada60s. W08 não é o watchdog GPS; esse controle continua na API.
4. Conferir na execução os contadores sanitizados e o destinatário/recibo pela
   evidência própria do produto, sem copiar conversa, PN, GPS, tokens ou dados
   da credencial. Rodar W01/W06 manualmente pode processar/enviar de verdade;
   não usar isso como simples teste de conexão antes das condições acima.

A alternativa de editor consiste em criar/associar a Header Auth
e clicar Publicar nos IDs desta entrega, inclusive por automação da sessão já
autenticada sem extrair cookies. Uma API pública autenticada já
autorizada pode substituir esses cliques, após conferir o schema da versão
fixada; usa `X-N8N-API-KEY`, nunca cookies copiados do editor. Este pacote não
solicita, lê ou grava essa chave nem inclui cliente HTTP de ativação.

Para suspender, despublicar no editor somente W01/W06/W04/W08 do manifest.
Não apagar volumes, execuções, credenciais compartilhadas ou outros workflows.
Se a importação parar no meio, manter o subconjunto novo inativo e reconciliar
os IDs; não repetir o bundle cegamente. Importar/publicar não comprova, sozinho,
pareamento do bot, recebimento no WhatsApp ou GPS do APK.

Fontes: [importação CLI no commit fixado](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/commands/import/workflow.ts),
[publicação CLI no mesmo commit](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/commands/publish/workflow.ts),
[importação de credenciais](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/commands/import/credentials.ts),
[ativação pela importação queue](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/services/import.service.ts),
[política de Error Workflow](https://github.com/n8n-io/n8n/blob/e0c02a617f5e6ede3d6c448aff41f632a782aaca/packages/cli/src/workflows/error-workflow-validation.service.ts),
[importar pelo editor](https://docs.n8n.io/workflows/export-import/),
[autenticação da API](https://docs.n8n.io/api/authentication/) e
[guia canônico HML](../../../src/whatsapp/workflows/HOMOLOGACAO.md).
A documentação atual pode descrever melhorias posteriores do CLI; a fonte
fixada acima governa a restrição de restart desta entrega.
