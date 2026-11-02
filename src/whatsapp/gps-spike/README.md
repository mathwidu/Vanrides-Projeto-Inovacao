# Receptor GPS sintético

Código do receptor, contrato e fixtures de laboratório preservados para estudo
e testes. Não contém trajetos de pessoas nem banco de operação.

```bash
# A partir da raiz Git, sem rede:
(cd src/whatsapp/gps-spike && python3 -m unittest discover -s tests -v)
```

O fixture `fixtures/route_v1.json` descreve uma linha equatorial sintética.
`receiver/`, `Dockerfile` e `requirements-production.txt` conservam o protocolo
existente. Hosts e identificadores de pacote fixados são compatibilidade com o
app/probes; testes não precisam acessar esses hosts. Probes Android e fontes
iOS não representam prova física. Veja [o guia da equipe](../../../docs/desenvolvimento/whatsapp.md).
