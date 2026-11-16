# Links e download do Android de homologação

O app e a API usam o mesmo host. O serviço de arquivos atende apenas cinco caminhos exatos com um router Traefik de prioridade superior a 10000. Os outros caminhos seguem para a API. O APK é distribuível publicamente e só inicia o compartilhamento depois que o participante vincula o aparelho e começa uma saída.

Em 16/11/2026, o download contém o app normal **0.4.0+15**, compilado do código
do aplicativo em `dae7ab8d93b0371e52779bcc24cf167a52b58b9e`.
SHA-256 do APK: `7f22ebd465f0c2f9947ee8c9639fb0ee63405bc360e618fa97a1955bf6cba946`.
O download HTTPS foi comparado byte a byte. No Redmi Note 8, esse artefato
enviou GPS, produziu o pin recebido pelo passageiro e confirmou a parada após
`FINALIZAR` no WhatsApp. Isso não aprova percurso, bateria ou recuperação de rede.

A publicação alterou somente a pasta de arquivos do serviço
`vanrides-hml-android_links`; imagem Nginx, certificado de assinatura do APK e
os cinco arquivos web foram preservados. O diretório da versão +14 permanece
disponível ao operador para restaurar a montagem anterior. O deploy automático
da API não publica esses arquivos. Consulte os
[resultados e limites da entrega](../../../docs/desenvolvimento/entrega-mvp.md).

Na versão a publicar, montar em /srv/vanrides o APK assinado como VanRides.apk, os três arquivos de src/whatsapp/links (driver-return.html, driver-start.html e driver-start.js) e assetlinks.json gerado pelo script association.mjs com o SHA-256 do certificado assinado. A impressão digital do certificado é diferente do hash do arquivo APK. Usar o nginx com links.conf, volume de arquivos somente leitura e /tmp gravável. O log de acesso fica desligado para que links com códigos não sejam registrados aqui.

Após a publicação, conferir HTTPS, tipo de conteúdo do JSON, pacote e certificado no arquivo de associação, hash do download e abertura do vínculo pelo Android. O professor pode acompanhar a chegada da posição pela idade da última amostra e pelo estado do compartilhamento. O teste com aparelho depende de vínculo válido, saída aberta e permissão de localização.
