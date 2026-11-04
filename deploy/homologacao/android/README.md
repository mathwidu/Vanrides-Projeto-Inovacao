# Links e download do Android de homologação

O app e a API usam o mesmo host. O serviço de arquivos atende apenas cinco caminhos exatos com um router Traefik de prioridade superior a 10000. Os outros caminhos seguem para a API. O APK é distribuível publicamente e só inicia o compartilhamento depois que o participante vincula o aparelho e começa uma saída.

Na versão a publicar, montar em /srv/vanrides o APK assinado como VanRides.apk, os três arquivos de src/whatsapp/links (driver-return.html, driver-start.html e driver-start.js) e assetlinks.json gerado pelo script association.mjs com o SHA-256 do certificado assinado. A impressão digital do certificado é diferente do hash do arquivo APK. Usar o nginx com links.conf, volume de arquivos somente leitura e /tmp gravável. O log de acesso fica desligado para que links com códigos não sejam registrados aqui.

Após a publicação, conferir HTTPS, tipo de conteúdo do JSON, pacote e certificado no arquivo de associação, hash do download e abertura do vínculo pelo Android. O professor pode acompanhar a chegada da posição pela idade da última amostra e pelo estado do compartilhamento. O teste com aparelho depende de vínculo válido, saída aberta e permissão de localização.
