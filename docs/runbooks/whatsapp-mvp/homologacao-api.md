# Primeira API compartilhada da equipe

A preparação e a publicação são acompanhadas nas issues [#89](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/89) e [#90](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/90). O objetivo desta primeira etapa é permitir que cada integrante exercite um comando do MVP em dados sintéticos isolados, pela API em HTTPS. O [guia da equipe](../../desenvolvimento/homologacao-da-equipe.md) explica como essa etapa se encaixa na entrega.

## Antes da primeira implantação

- **Ambiente.** Implementar e testar um modo exclusivo da API de homologação. Usar o perfil `homolog`, com os controles públicos ativos, JWT forte, limitação de requisições, Basic desabilitado, autoverificação de e-mail desabilitada e coortes de teste obrigatórias. O modo deve bloquear o envio de e-mail e negar as rotas legadas também na própria API, inclusive por acesso direto na rede privada. Digitação incorreta do provedor ou combinação de ambiente inválida deve impedir a inicialização. A revisão atual ainda exige essa mudança.
- **Artefato.** O Dockerfile padrão do backend recompila o projeto. Para publicar a aplicação testada, preparar imagem de execução que copie o JAR conferido na CI. Verificar os hashes antes e depois do empacotamento, registrar a revisão no rótulo da imagem e publicar por digest imutável. O artefato do laboratório tem finalidade própria e não autoriza a publicação da stack.
- **Destino.** Preparar stack `vanrides-hml-api` com serviços `api` e `db`, rede privada e volume de dados próprios. Apenas a API também precisa alcançar a rede do Traefik. Usar `network_swarm_public`, entrada `websecure`, resolvedor `letsencryptresolver` e porta interna 8080, observados na VPS. Conferir colisões, espaço, memória, imagens linux/amd64 e resolução do domínio antes de aplicar a definição.
- **Acesso.** Expor a saúde e somente os métodos necessários ao cenário abaixo. Restringir as operações ao acesso de teste aprovado e ao Bearer próprio do integrante. O PostgreSQL e o operador de provisionamento permanecem privados. Segredos são exclusivos da stack e devem ser fornecidos por arquivos montados. Chaves de serviço e de administração não entram no Git.
- **Limites do primeiro ambiente.** Usar cálculo de rota estático, coortes sintéticas e início de novas viagens e avisos automáticos desligados. A conexão do número real pertence à [issue #91](https://github.com/mathwidu/Vanrides-Projeto-Inovacao/issues/91). Imagens, rede, e-mail e rotas do ambiente permanente ainda exigem implementação e provas.

## Cenário de aceite com um integrante

1. O operador cria um lote sintético, uma instância identificada para o integrante e sua credencial temporária de conversa. Prepara uma mensagem fictícia com reserva de curta duração. Cada colega usa instância e credencial próprias.
2. O colega confere certificado e saúde em `https://vanrides-inovacao-api.matheusduarte.dev.br/actuator/health`. Após expirar a reserva inicial da fixture, usa a rota de claim com seu Bearer e recebe o evento preparado. A mesma chamada sem credencial deve ser negada.
3. Com a reserva válida, consulta o contexto, executa o comando permitido de início de cadastro, repete o mesmo comando com a mesma chave de idempotência e confirma o processamento. O recibo repetido deve referir o mesmo resultado. Uma segunda credencial não pode consultar nem confirmar o evento da primeira.
4. O operador confirma a persistência do resultado e da auditoria, reinicia apenas a API da nova stack e repete a leitura. Revoga a credencial e confere que ela deixou de funcionar. Registra versão, digests, horário e resultado, sem conteúdos de conversa, credenciais ou localização.
5. Exercitar backup e restauração em banco isolado, confirmar o estado recuperado e as exclusões aplicáveis. Para atualizações futuras, comparar versões de migração antes de escolher um rollback de imagem ou restauração.

## Retorno e liberação

Se falhar TLS, autenticação, isolamento, migração, persistência ou o cenário do colega, bloquear a entrada e recolher apenas o diagnóstico necessário. Na primeira publicação, retirar a nova rota ou interromper o serviço mantendo o volume próprio para diagnóstico. Em atualização posterior, voltar ao digest anterior somente se a versão continuar compatível com o schema. Provar o retorno com uma operação autenticada.

O plano a executar deve conter revisão e imagens exatas, serviços e redes afetados, migração, testes, responsável e procedimento de retorno. O DNS já aponta à VPS e a consulta inicial apresentou o certificado padrão do Traefik. A conclusão da issue #90 depende da versão do MVP responder com certificado válido e do cenário do integrante passar. O aceite do piloto tem outros critérios de percurso, dispositivos e WhatsApp.

Referências operacionais: [Docker Swarm secrets](https://docs.docker.com/engine/swarm/secrets/) e [Traefik v2.11 com Docker](https://doc.traefik.io/traefik/v2.11/providers/docker/).
