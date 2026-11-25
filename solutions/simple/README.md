# OLX Marketplace — solução pequena

Implementação independente do contrato oficial do desafio, em Java 21, Spring Boot e PostgreSQL. Não depende de Kafka, Redis, OpenSearch, Kubernetes ou do frontend. Esta é a entrega proporcional ao escopo oficial; a pasta `services/` da raiz contém a evolução Enterprise.

## Requisitos cobertos

- `POST /ads`: valida os campos, consulta a moderação externa e persiste o resultado aprovado ou reprovado.
- `GET /ads`: lista apenas anúncios aprovados, com filtro opcional por categoria e paginação.
- `GET /ads/{id}`: retorna os dados, status de moderação e motivo de reprovação.
- PostgreSQL é a persistência. Flyway cria o esquema; Hibernate valida o esquema e não o altera.
- O cliente de moderação tem timeout configurável e traduz indisponibilidade para HTTP 502. Respostas HTTP 429 também são tratadas como falha da dependência, sem retry automático que possa consumir quota repetidamente.
- Preço usa `NUMERIC(19,2)` e é validado para caber nessa precisão. O enunciado não define casas decimais; duas é a escolha deste modelo de moeda.

## Executar

Com Docker Compose da raiz do projeto, inicie o banco e o mock (o banco `olx_simple` é criado pelo script inicial):

```bash
docker compose up -d postgres moderation
```

Se a aplicação retornar `password authentication failed for user "olx"`, o volume antigo provavelmente guarda a senha que foi definida na primeira inicialização. `POSTGRES_PASSWORD` só configura um banco novo; ele não altera a senha já gravada no volume. Corrija a senha existente sem remover dados:

```bash
docker compose exec -u postgres postgres psql -d postgres -c "ALTER ROLE olx WITH PASSWORD 'olx_local_only';"
```

Se o banco `olx_simple` também não existir, crie-o uma vez. Pule este comando se o banco já estiver presente:

```bash
docker compose exec -u postgres postgres psql -d postgres -c "CREATE DATABASE olx_simple;"
```

Depois, em `solutions/simple`:

```bash
mvn spring-boot:run
```

Configuração padrão: API em `http://localhost:8087`, PostgreSQL em `localhost:5433`, mock em `http://localhost:8787`. Ajuste `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `MODERATION_URL`, `MODERATION_CONNECT_TIMEOUT_MS` e `MODERATION_READ_TIMEOUT_MS` por ambiente.

## Exemplo

```http
POST http://localhost:8087/ads
Content-Type: application/json

{
  "title": "Notebook usado em bom estado",
  "description": "Notebook com carregador original e sinais leves de uso.",
  "price": 1800.00,
  "category": "eletronicos",
  "images": ["https://example.com/notebook.jpg"]
}
```

O mock recebe somente `title`, `description`, `price` e `category`, conforme o contrato fornecido. Ele **não recebe nem analisa as imagens**. A API devolve `201 Created` com o status final da moderação e grava também anúncios rejeitados para que o motivo possa ser consultado.

Paginação: `GET /ads?page=0&size=20&category=eletronicos`. A página começa em zero; tamanho padrão 20 e máximo 100. A resposta segue o formato de página do Spring Data.

## Fluxo e responsabilidades

1. `AdController` converte HTTP em chamadas de aplicação; não acessa o banco nem a API externa diretamente.
2. `CreateAdRequest` usa Bean Validation para rejeitar corpo ausente ou inválido antes do serviço.
3. `AdService` converte o código de categoria, chama `ModerationClient` e só depois persiste o anúncio e as URLs.
4. `ModerationClient` é o adaptador HTTP para o serviço externo. A integração acontece antes da gravação; a transação curta do `JpaRepository.save` não fica aberta durante a chamada de rede.
5. `AdRepository` delega consultas paginadas ao Spring Data JPA/PostgreSQL.
6. `ApiExceptionHandler` converte erros esperados em respostas HTTP estáveis, sem devolver stack trace ao cliente.

O contrato detalhado de endpoints e exemplos está em [`docs/API.md`](docs/API.md); as decisões e ambiguidades estão em [`docs/DECISIONS.md`](docs/DECISIONS.md). Na cópia dentro do repositório completo, [`../../docs/CHALLENGE_ALIGNMENT.md`](../../docs/CHALLENGE_ALIGNMENT.md) registra o alinhamento com o enunciado oficial.

## Decisões e limites

- A sequência síncrona foi escolhida para corresponder literalmente ao requisito “moderar antes de publicar” e manter a solução pequena. O cliente espera pela latência variável do mock; o timeout limita a espera, mas não cria processamento assíncrono.
- A chamada externa e o commit do PostgreSQL não participam da mesma transação distribuída. Se a moderação responder e o banco falhar, a chamada pode ser repetida. O contrato do desafio não fornece chave de idempotência para o mock; essa limitação está registrada em [`docs/DECISIONS.md`](docs/DECISIONS.md).
- Não há retry automático. Retry cego após timeout/429 pode repetir uma chamada já recebida e pressionar a quota compartilhada de 5 RPS. A API responde 502 para falha da dependência; o cliente pode repetir conscientemente.
- Limitar tamanho de página reduz custo de resposta, mas o desafio não define um máximo; 100 é uma escolha local documentada, não um requisito oficial.
- Listagens retornam apenas `APPROVED`. A consulta por ID também permite ver `REJECTED`, incluindo motivo. O enunciado não especifica se anúncios reprovados devem aparecer em listagem; registramos essa interpretação.
- Imagens são URLs guardadas no PostgreSQL. O mock não as avalia; conteúdo impróprio não será detectado pelo contrato existente. Isso exige esclarecimento de produto se a moderação visual for necessária.
- Não incluímos autenticação, edição, exclusão, favoritos, upload de mídia, cache ou mecanismo de busca: não são necessários para demonstrar os requisitos do desafio.

## Complexidade e recursos

Validação e normalização das `n` URLs custam O(n + B), onde B é o total de caracteres processados nas URLs. O resultado mantém uma lista independente. A listagem usa `LIMIT/OFFSET` por meio de `Pageable`; o custo depende do índice e da profundidade da página. O índice `(status, category, created_at DESC)` serve o filtro e a ordenação mais usados. Paginação por cursor seria uma evolução caso medições revelem custo alto em offsets profundos.

Cada POST mantém uma conexão PostgreSQL somente durante a gravação, não durante o request HTTP externo. A capacidade de ponta a ponta continua limitada pela latência/quota do mock. Nenhum RPS, latência ou tamanho de instância é afirmado sem ensaio.

## Verificações

Execute `mvn test` dentro desta pasta para rodar os testes incluídos. O resultado precisa ser obtido no ambiente de desenvolvimento antes de declarar a build validada; este arquivo não presume que eles já foram executados.
