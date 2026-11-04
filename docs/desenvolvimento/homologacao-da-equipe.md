# Ambiente de testes do VanRides

O endereço da equipe é **https://vanrides-inovacao-api.matheusduarte.dev.br**. Os integrantes usam o mesmo ambiente em paralelo. O motorista entra pelo app e o passageiro pelo WhatsApp. Credenciais de serviço ficam com o operador; o grupo não precisa dividir usuário de SSH nem token de administrador.

| Componente | O que temos | O que falta para usar online |
| --- | --- | --- |
| API e PostgreSQL | Cadastro, rotinas, presença, vínculo do aparelho, início de viagem, GPS e consulta da van implementados. A aproximação é controlada por configuração. | Publicado. API e PostgreSQL estão na VPS com TLS válido. O grupo pode abrir a saúde pelo link abaixo. As rotas de produto exigem credenciais individuais ou de serviço. |
| Android do motorista | O aplicativo integrado fala com a API e transmite GPS. O APK do ensaio integrado anterior foi compilado para localhost por USB. | [Baixar o APK da equipe](https://vanrides-inovacao-api.matheusduarte.dev.br/motorista/VanRides.apk). APK, associação Android e links de vínculo e início publicados. Falta o vínculo pelo canal e a primeira saída com um integrante como motorista. |
| Worker do VanRides | Publicado em rede privada, com credenciais próprias da homologação. Comunicação com a API conferida. | Primeira conversa real validada: ajuda e início de cadastro respondidos. Esta ativação usa comandos determinísticos, sem consumo pago de IA. |
| n8n | Oito fluxos VanRides importados, com credencial de controle exclusiva. Quatro agendamentos e tratamento de erros publicados. Execuções de conversa e envio observadas com sucesso. | Duas respostas entregues e recibos processados no teste real. Os demais fluxos existentes foram preservados. |
| Evolution e número do bot | Evolution dedicada ao VanRides, banco e cache próprios, webhook configurado e TLS interno conferido. Número ativo no WhatsApp Business do Redmi. | Pareamento OPEN; envio e recebimento reais comprovados. Admitir individualmente os contatos dos próximos participantes. |
| Funções ainda abertas | Parada técnica do GPS e controle de acesso já existem. | Concluir o encerramento automático e a recuperação pelo suporte. Calibrar os avisos em percurso antes de ativá-los para passageiros. |

## Verificação em 4 de novembro de 2026

A [saúde da API](https://vanrides-inovacao-api.matheusduarte.dev.br/actuator/health) respondeu HTTP 200 com `{"status":"UP"}` e o ambiente `vanrides-mvp-hml`. O endpoint do aparelho e a entrada de eventos do WhatsApp responderam 401 sem credencial. O Swarm confirmou uma réplica da API e uma do banco com as imagens esperadas. A imagem usa o backend `57a2ffd`. A receita publicada é `e9fe510`. Na primeira tentativa o Tomcat não iniciou porque a montagem abreviada de /tmp foi descartada. A segunda usou um volume temporário de 64 MiB e manteve o banco exclusivo da equipe.

O download de 68.903.599 bytes foi conferido por HTTPS. É o VanRides Motorista 0.3.0-hml+13. O hash SHA-256 é `c438bb3566f87d3ca836096c2d962963ae5de12edbd6d2c2c67104ff9e0210a9`. O primeiro teste de conversa usou o proprietário como passageiro. A saída com GPS depende de um segundo participante com o papel de motorista.

O canal possui seis serviços ativos. A entrada de eventos e o worker iniciaram corretamente. O controle do worker recusou uma chamada sem credencial (401) e respondeu `IDLE` com a credencial própria (200, nenhuma mensagem processada). Um contato de passageiro foi admitido com autorização. Depois dos ajustes do receptor, o teste no WhatsApp do Mac confirmou duas mensagens processadas, duas respostas entregues e quatro callbacks de status concluídos. O bot respondeu à ajuda e iniciou o cadastro, ficando na pergunta do nome. Cadastro completo, presença e percurso pelo canal real ainda precisam ser verificados. O [registro da publicação do canal](../runbooks/whatsapp-mvp/homologacao-canal.md) guarda os detalhes operacionais.

## Ordem de publicação

1. **API primeiro.** Publicar a imagem do backend e um PostgreSQL da equipe. O perfil online atende as rotas implementadas em `/driver-app/v1/` e `/integrations/whatsapp/v1/` com a autenticação do produto. Conferir HTTPS e a identificação do ambiente. O serviço antigo continua no endereço próprio.
2. **Atendimento.** Publicar o worker, configurar o n8n e conectar a Evolution com o número do Redmi. Preparar as identidades de teste dos integrantes e conferir o recebimento de uma resposta no aparelho. A conferência pode ser feita por um integrante; o acesso pode ser liberado para o grupo inteiro.
3. **App e demonstração.** Distribuir o APK integrado assinado para o mesmo domínio. Vincular o aparelho, iniciar uma viagem da coorte e acompanhar a chegada das posições à API. Encerrar o compartilhamento pelo aparelho ao concluir a demonstração. Registrar horário/estado ou contagem, sem expor localização pessoal.
4. **Piloto.** As verificações de percurso, bateria, utilidade e encerramento automático têm critérios próprios. A validação física do iPhone permanece pausada.

## Identificar o APK

O APK 0.1.0+8 mostra “Teste de localização · etapa de preparação” e usa o receptor publicado em `gps-spike.vanrides.matheusduarte.dev.br`. Ele permite demonstrar transmissão do aparelho ao receptor com uma credencial temporária por celular. O APK de laboratório 0.2.0+12 usa `localhost:18443` e precisa da ponte de laboratório. Para o MVP publicado, o build precisa trazer a origem HTTPS da API e o host de links no Android e no Dart. Os três casos não são intercambiáveis.

A implantação da API está nas issues [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) e [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90). O canal do WhatsApp está na [#91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91) e a distribuição Android na [#87](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/87). O cenário técnico por credencial individual continua disponível em `scripts/homologacao/testar_api.py` para o perfil restrito de cadastro.

O [ensaio de passageiro sem motorista](../runbooks/whatsapp-mvp/cadastro-passageiro-sem-motorista.md) concluiu o perfil básico no WhatsApp real e conferiu as recusas sem vínculo. Ponto, rotina e presença efetiva ainda dependem do convite e da aprovação de um motorista.
