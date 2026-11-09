# Como contribuir

Comece por uma frente e uma tarefa pequena. Os testes simulados não exigem
número do bot, acesso à VPS ou chave OpenAI.

## 1. Obter o projeto

Com Git instalado, no PowerShell, Terminal do macOS ou Linux:

```text
git clone https://github.com/mathwidu/Vanrides-Projeto-Inovacao.git
cd Vanrides-Projeto-Inovacao
```

Sem permissão de escrita, faça um fork pelo GitHub e abra o pull request a partir
dele. Não compartilhe conta, token ou arquivo de ambiente.

## 2. Conferir o ambiente da sua frente

Confira o que já está instalado antes de instalar ou atualizar ferramentas.

| Frente | Requisitos | Comandos e limites |
|---|---|---|
| Documentos/backlog | Git, editor, Python 3.11+ para conferir dados | [Backlog](docs/07-como-ler-o-backlog.md) |
| Bot/mensagens | Node.js 22, Python; Docker para ensaios de contêiner | [WhatsApp](docs/desenvolvimento/whatsapp.md) |
| API | Java 17, Maven Wrapper e Docker para Testcontainers | [Backend](docs/desenvolvimento/backend.md) |
| Aplicativo | Flutter 3.38.5; JDK 21/Android SDK para Android | [Aplicativo](docs/desenvolvimento/aplicativo.md) |
| iOS | Ambiente do app mais macOS/Xcode/CocoaPods | Mesmo guia; build não substitui prova física |

Os testes de documentos podem rodar no Windows nativo. Para o gate completo do
bot e scripts `.sh`, use WSL2 conforme o guia da frente; alguns testes Node
isolados são portáveis, mas não substituem esse gate. Testes com Docker exigem
um mecanismo de contêiner acessível ao ambiente escolhido.
Não presuma que o WSL tem o mesmo acesso USB do Windows. Cada integrante ainda
precisa executar seu primeiro teste local; CI não valida seu computador pessoal.

Primeira conferência, sem instalar a stack inteira:

```text
python scripts/backlog.py --check
python scripts/check_repository.py
```

No macOS/Linux, use `python3` se necessário.

## 3. Combinar uma tarefa

Leia a issue e sua [especificação](docs/08-especificacoes-mvp.md): problema,
resultado, escopo, critérios e como testar. Confira o objetivo ativo do
[programa](docs/desenvolvimento/programa.md). Mantemos um objetivo vertical ativo;
a equipe pode trabalhar em partes independentes dele.

Registre na issue quem implementa, quem revisa e quais arquivos pretende alterar.
Nomes existentes na planilha não geram atribuição automática. Propostas de escopo
precisam de decisão antes de código. Um critério incompleto pode ser o primeiro PR.

Se o programa ainda não indicar uma entrega executável, combine a próxima
com a equipe e complete seu escopo, dependências e critérios antes de iniciar
código de produto. Reproduzir o ambiente e revisar as instruções são os
primeiros passos; isso não inicia uma funcionalidade nem aprova o piloto.

```text
git switch main
git pull --ff-only
git switch -c feat/tarefa-descricao-curta
```

## 4. Implementar e testar

Use os comandos da sua frente. Preserve contratos ou ajuste produtores e
consumidores juntos. Execute os testes relacionados ao comportamento alterado;
a CI automática confirma a construção, não executa a suíte de testes. Registre comando,
revisão, ambiente, resultado e testes que não conseguiu executar.

A [revisão da CI e dos testes](docs/desenvolvimento/revisao-ci-e-testes.md)
registra os gargalos, mudanças implementadas, medições e etapas ainda pendentes.
Use os comandos atuais do guia da sua frente; o gate local completo permanece
disponível para validações direcionadas, fora do caminho de publicação.

Não comite `.env`, chaves, assinatura Apple/Android, conversas, telefones reais
ou coordenadas pessoais. Use dados sintéticos. Revise os logs antes de anexá-los.

## 5. Enviar para revisão

```text
git add caminho/do/arquivo
git commit -m "feat: descreve a mudança"
git push -u origin feat/tarefa-descricao-curta
```

Abra PR para `main`, relacione a issue e use o template. Explique o problema,
a mudança observável, os critérios atendidos, as provas e as limitações.
Um colega deve revisar e compreender a alteração antes de integrar. Espere os
checks aplicáveis e resolva as observações. Não use `Closes #...` quando ainda
faltar parte do objetivo, teste físico ou aceite externo.

## 6. Manter o backlog coerente

Issues guardam colaboração e provas. Os três JSONs em `docs/backlog/` mantêm
campos acadêmicos, acompanhamento e critérios; o programa controla cards e
dependências. Os IDs Txx ligam essas informações.

Depois de alterar os JSONs:

```text
python scripts/backlog.py
python scripts/backlog.py --check
python scripts/check_repository.py
```

Atualize a issue e os textos afetados no mesmo trabalho. A planilha mantém
somente BACKLOG/INSTRUÇÕES e oito colunas, com a legenda original. Use números
reais de issue; confira as células contra `tarefas.json` e revise o arquivo
no Excel/LibreOffice quando editá-lo.

O relatório de acompanhamento mantém o texto editável em
`docs/apresentacoes/retorno-2026-11-03.md`. Para gerar seu PDF, use Python com
ReportLab 4.4.9 em ambiente virtual (`python -m pip install reportlab==4.4.9`)
e execute `python scripts/build_academic_report.py`
na raiz. Confira visualmente todas as páginas depois de gerar; o PDF não deve
ficar desatualizado em relação ao texto. Esse gerador é opcional para desenvolver o produto.

**Concluído** exige escopo atendido, critérios verificados e revisão registrada.
“Implementado” é inventário de código; laboratório não comprova WhatsApp real.
Percurso, bateria e utilidade exigem seus próprios ensaios.

## Primeiro PR sugerido

Reproduza o primeiro comando de uma frente e registre o resultado na issue.
Envie uma correção pequena de instrução ou um teste de cenário compreendido.
Isso confirma acesso e revisão. A escolha da área depende do grupo, não do
sistema operacional de cada pessoa.

Deploy e testes com usuários são etapas separadas. O bot está conectado à
Evolution e o perfil básico foi validado no WhatsApp real. O uso pelo colega
requer admissão do contato; credenciais e convites são individuais. Testes
físicos do aplicativo iPhone continuam pausados. O novo fluxo publica a API
automaticamente após a construção da aplicação e da imagem em um push na `main`, assim que o provisionamento
inicial for concluído. PRs não publicam. Consulte o
[estado de ativação](docs/desenvolvimento/area-da-equipe.md) e o
[procedimento de deploy](deploy/homologacao/AUTOMATICO.md).

O [guia de homologação da equipe](docs/desenvolvimento/homologacao-da-equipe.md)
reúne URLs, versões, fluxos, roteiro de teste e limites do que está publicado.
Antes de diagnosticar uma diferença de resposta, confira se a mudança está
somente na sua branch, na `main` ou também na versão instalada na VPS.
