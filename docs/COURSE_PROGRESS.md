# Estado do repositório e da formação

## Backend preparado

- `solutions/simple`: API independente do desafio oficial, Spring Boot, PostgreSQL e moderação síncrona.
- `services/ads-service`: API de cadastro/estado, PostgreSQL/Flyway, Outbox e relay Kafka.
- `services/moderation-worker`: consumidor, quota compartilhada Redis, integração com mock, persistência de decisões e retry/DLT.
- `services/ads-query-service`: projeção OpenSearch, busca com cursor e detalhe cache-aside em Redis.
- API Gateway e infraestrutura local atualizados para os serviços Ads/Query.
- Contrato e decisões em `docs/API.md`, `docs/CHALLENGE_ALIGNMENT.md`, `docs/SOLUTION_ARCHITECTURE.md` e `DECISIONS.md`.

## Evidência pendente

O código foi escrito nesta sessão, mas ainda não foi compilado ou executado. `mvn test`, imagens Docker, fluxo ponta a ponta, métricas observadas e teste de carga continuam pendentes. Não declarar a solução funcional até rodar os comandos e registrar saídas reais. Os testes presentes não equivalem a testes executados.

## Curso

A sequência e a ementa de seis dias não são alteradas por este arquivo. O progresso pedagógico do estudante deve ser anotado somente depois de cada aula feita, sem assumir que classes geradas nesta sessão foram digitadas ou compreendidas.

## Registro por aula / validação real

Preencher após realização:

- Data e objetivo:
- Conceitos compreendidos:
- Arquivos revistos/alterados pelo estudante:
- Testes executados e saída:
- Decisões/trade-offs defendidos:
- Dúvidas e ambiguidades novas:
- Commit real:
- Próxima etapa:
