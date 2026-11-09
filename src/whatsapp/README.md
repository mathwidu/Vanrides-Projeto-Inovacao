# Atendimento WhatsApp e receptor GPS

Comece pelo [guia da equipe](../../docs/desenvolvimento/whatsapp.md).
Testes offline: `python3 scripts/check_whatsapp.py`, na raiz Git.
Contratos canônicos: `../backend/contracts/whatsapp-mvp/`.

Fontes transferidos da coordenação `c1e0546f0c84`, com caminhos adaptados para
o repositório acadêmico. Nenhuma credencial, sessão, banco, GPS real ou imagem
construída acompanha o código. Os exports n8n continuam inativos.

O smoke acadêmico executa as jornadas 070, 080, 100, 110, 120 e 130 com
provider Evolution em loopback. A consulta 130 usa pin estático efêmero com
horário, liberado pela API somente na autorização de envio; o bot não passa
coordenadas ao modelo. Detalhes e fontes fixadas em [transport/README.md](transport/README.md).

Para emitir a evidência do smoke, a CI pode definir
`VANRIDES_ACADEMIC_RECEIPT_PATH` com um caminho absoluto novo fora do checkout
(por exemplo, sob `RUNNER_TEMP`). O JSON sanitizado é publicado atomicamente com
modo0600 somente após sucesso, limpeza e conferência final dos fontes/JAR.
Inclui hashes/tamanho, checks das seis jornadas, contadores e limites; não
inclui logs, credenciais, conversa ou coordenadas. Falha não gera recibo PASS.
