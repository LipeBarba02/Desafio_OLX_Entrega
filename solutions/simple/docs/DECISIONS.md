# Decisões e ambiguidades — solução pequena

Este documento descreve decisões locais para atender ao desafio com uma aplicação Spring Boot e PostgreSQL. Uma decisão local não deve ser apresentada como regra oficial quando o enunciado não a define.

## Escopo e arquitetura

- Uma única aplicação HTTP contém controller, caso de uso, adaptador REST para moderação e persistência PostgreSQL.
- A chamada de moderação é síncrona: o POST só persiste depois de receber `approved` ou `rejected`. Essa forma corresponde diretamente ao requisito de moderar antes de publicar e é adequada à entrega pequena.
- O custo dessa simplicidade é que a latência do POST inclui a chamada externa e a indisponibilidade do mock impede novas criações. Não há RPS ou SLO da API definidos pelo desafio.
- Não há Kafka, Outbox, Redis, OpenSearch, favoritos, autenticação, edição, exclusão ou upload de imagens nesta solução.

## Contrato e ambiguidades registradas

- Título: 10–100 caracteres; preço positivo; uma ou mais URLs absolutas HTTP/HTTPS. As validações de título/preço e existência da imagem vêm do desafio; protocolo HTTP/HTTPS é a interpretação adotada para URL.
- Imagens impróprias: o mock recebe apenas título, descrição, preço e categoria. Não recebe URL ou bytes e não pode avaliar conteúdo visual. A aplicação guarda as URLs, mas não afirma que as imagens foram moderadas.
- Campo do motivo: o contrato documenta `reason`; a implementação local do mock pode retornar `rejection_reason`. O cliente reconhece ambos.
- Listagem de rejeitados: o desafio exige expor o motivo, mas não define se anúncios rejeitados aparecem na listagem. Decisão: listagem pública só retorna `APPROVED`; consulta por ID retorna estado e motivo também para `REJECTED`.
- Precisão monetária: o enunciado exige valor maior que zero, sem fixar escala. O banco escolhe `NUMERIC(19,2)`; a aplicação rejeita valores que não cabem antes de chamar o mock.
- Idempotência: o mock não documenta chave idempotente. Se ele processar uma chamada e a resposta se perder, não há como garantir exatamente uma avaliação externa.
- Limites de descrição, quantidade de imagens e tamanho de URL não estão definidos. A versão não inventa limites de negócio; limites de transporte e abuso devem ser definidos antes de exposição pública.

## Falhas e consistência

- O cliente HTTP tem timeout de conexão e resposta configurável.
- Não existe retry automático. Repetir um timeout ou 429 pode consumir novamente a quota compartilhada de 5 RPS se o mock já recebeu a chamada.
- Moderação HTTP e commit PostgreSQL não pertencem a uma transação distribuída. Se o mock responder e a gravação falhar, uma repetição do cliente pode chamar moderação de novo.
- A aplicação mapeia falhas da dependência para `502`; não persiste decisão inconclusiva.
- A transação de escrita fica limitada à operação do repositório; não segura conexão do banco enquanto aguarda a rede externa.

## Performance e custo

- Normalizar e validar `n` URLs custa O(n + B), onde B é o total de caracteres processados; a lista de resultados requer espaço O(n + B).
- A listagem usa paginação `LIMIT/OFFSET` através de `Pageable`, com tamanho máximo local de 100. Offsets profundos podem ficar caros; cursor é evolução somente se testes mostrarem necessidade.
- Índices locais cobrem status/categoria/data. A escolha deve ser conferida com `EXPLAIN (ANALYZE, BUFFERS)` em dados representativos.
- Não há benchmark ou dimensionamento medido no pacote. RPS, latência p50/p95/p99, heap, CPU, pool e throughput devem ser medidos em execução antes de qualquer alegação de capacidade.

## Fontes do contrato

- [Desafio Backend oficial](https://github.com/olxbr/code-challenges/blob/main/backend/README.md)
- [Repositório e critérios oficiais](https://github.com/olxbr/code-challenges/blob/main/README.md)
- [Documentação oficial do mock](https://github.com/olxbr/code-challenges/blob/main/backend/api-mock/docs-candidato/API.md)
