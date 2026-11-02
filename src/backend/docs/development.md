# Ambiente de desenvolvimento

A receita atual está no [guia do backend](../../../docs/desenvolvimento/backend.md).
A raiz Maven é `src/backend`, com Java 17, Maven Wrapper 3.9.9 e PostgreSQL isolado.
Use `.env.example` apenas como modelo local; `.env` fica fora do Git.
Flyway mantém o schema; o modo local usa validação JPA, rotas estáticas e mapas
próprios desativados. O gate obrigatório exige Docker e matriz PostgreSQL sem skips.
