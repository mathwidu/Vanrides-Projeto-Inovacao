# Ambiente de testes do VanRides

O endereço da equipe é **https://vanrides-inovacao-api.matheusduarte.dev.br**. Os integrantes usam o mesmo ambiente em paralelo. O motorista entra pelo app e o passageiro pelo WhatsApp. Credenciais de serviço ficam com o operador; o grupo não precisa dividir usuário de SSH nem token de administrador.

| Componente | O que temos | O que falta para usar online |
| --- | --- | --- |
| API e PostgreSQL | Cadastro, rotinas, presença, vínculo do aparelho, início de viagem, GPS e consulta da van implementados. A aproximação é controlada por configuração. | Publicar a API do MVP com banco isolado, certificado válido, rotas do aplicativo e das integrações. O DNS já aponta para a VPS. |
| Android do motorista | O aplicativo integrado fala com a API e transmite GPS. O APK do ensaio integrado anterior foi compilado para localhost por USB. | Distribuir o build do motorista compilado para o domínio da equipe, servir os links de vínculo/início e o arquivo de associação da assinatura Android. |
| Worker do VanRides | Interpreta as solicitações, chama a API e envia respostas. | Publicar o worker em rede privada com credenciais da homologação e destino correto da API/Evolution. |
| n8n | O serviço já existe na VPS. Os fluxos do projeto estão versionados. | Importar as cópias do VanRides, configurar a credencial de controle e habilitar os fluxos necessários no executor real. |
| Evolution e número do bot | O serviço compartilhado existe e o número dedicado está ativo no WhatsApp Business do Redmi. | Usar Evolution com configurações isoladas para o VanRides, parear o bot e conectar os eventos e recibos à API. |
| Funções ainda abertas | Parada técnica do GPS e controle de acesso já existem. | Concluir o encerramento automático e a recuperação pelo suporte. Calibrar os avisos em percurso antes de ativá-los para passageiros. |

## Ordem de publicação

1. **API primeiro.** Publicar a imagem do backend e um PostgreSQL da equipe. O perfil online atende as rotas implementadas em `/driver-app/v1/` e `/integrations/whatsapp/v1/` com a autenticação do produto. Conferir HTTPS e a identificação do ambiente. O serviço antigo continua no endereço próprio.
2. **Atendimento.** Publicar o worker, configurar o n8n e conectar a Evolution com o número do Redmi. Preparar as identidades de teste dos integrantes e conferir o recebimento de uma resposta no aparelho. A conferência pode ser feita por um integrante; o acesso pode ser liberado para o grupo inteiro.
3. **App e demonstração.** Distribuir o APK integrado assinado para o mesmo domínio. Vincular o aparelho, iniciar uma viagem da coorte e acompanhar a chegada das posições à API. Encerrar o compartilhamento pelo aparelho ao concluir a demonstração. Registrar horário/estado ou contagem, sem expor localização pessoal.
4. **Piloto.** As verificações de percurso, bateria, utilidade e encerramento automático têm critérios próprios. A validação física do iPhone permanece pausada.

## Identificar o APK

O APK 0.1.0+8 mostra “Teste de localização · etapa de preparação” e usa o receptor publicado em `gps-spike.vanrides.matheusduarte.dev.br`. Ele permite demonstrar transmissão do aparelho ao receptor com uma credencial temporária por celular. O APK de laboratório 0.2.0+12 usa `localhost:18443` e precisa da ponte de laboratório. Para o MVP publicado, o build precisa trazer a origem HTTPS da API e o host de links no Android e no Dart. Os três casos não são intercambiáveis.

A implantação da API está nas issues [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) e [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90). O canal do WhatsApp está na [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) e a distribuição Android na [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87). O cenário técnico por credencial individual continua disponível em `scripts/homologacao/testar_api.py` para o perfil restrito de cadastro.
