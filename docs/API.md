# Contratos HTTP e de eventos

## Corpo de criação

```json
{
  "title": "Notebook usado em bom estado",
  "description": "Notebook com carregador original e sinais leves de uso.",
  "price": 1800.00,
  "category": "eletronicos",
  "images": ["https://example.com/notebook.jpg"]
}
```

`title` é obrigatório e tem de 10 a 100 caracteres; `description` é obrigatória; `price` é maior que zero e precisa caber no armazenamento local `NUMERIC(19,2)`; `category` aceita `eletronicos`, `moveis`, `veiculos`, `imoveis`, `esportes` ou `colecionaveis`; `images` deve conter pelo menos uma URL absoluta HTTP/HTTPS. O desafio não especifica a escala monetária, então esta implementação escolhe duas casas decimais. Nenhum limite máximo para descrição, número de URLs ou tamanho de URL consta no desafio.

## Solução pequena (`solutions/simple`)

`POST /ads` chama o mock antes de persistir. `201 Created` retorna o anúncio com `APPROVED` ou `REJECTED`; anúncios rejeitados incluem `rejectionReason`. Uma falha/timeout/429 da dependência retorna `502 Bad Gateway`, sem persistir uma decisão incompleta.

`GET /ads?page=0&size=20&category=eletronicos` retorna página Spring Data somente de aprovados. `page` começa em zero, `size` padrão 20 e máximo 100. Sem `category`, todas as categorias aprovadas são listadas.

`GET /ads/{uuid}` consulta o anúncio específico; se não existir, responde 404. Esse endpoint também mostra rejeitados e o motivo. `400` identifica entrada/categoria/URL inválida.

## Enterprise (`services/`)

`POST /ads` em Ads Command valida e grava anúncio + Outbox em uma transação; retorna `202 Accepted`, ID e `PENDING_MODERATION`. Não espera o mock.

`GET /ads` e `GET /ads/{uuid}` em Ads Command leem PostgreSQL e podem mostrar status pendente/rejeitado. A listagem pública filtra aprovados.

`GET /ads/search?q=notebook&category=eletronicos&size=20&cursor=<token>` em Ads Query executa busca textual/filtro no OpenSearch. O cursor é opaco para o consumidor; retorne `nextCursor` à próxima chamada. A resposta informa `totalRelation` (`eq` ou `gte`) porque o OpenSearch pode informar somente um limite inferior. O resultado de busca não inclui URLs de imagem.

`GET /ads/{uuid}` em Ads Query retorna somente anúncio aprovado. O detalhe é cache-aside; cache miss busca confirmação/projeção no OpenSearch e hidrata imagens no serviço Ads Command.

Via Gateway: `POST /api/ads` cria no Ads Command; `GET /api/ads` lista pelo Ads Query usando cursor; `GET /api/ads/{uuid}` busca detalhe no Ads Query; `GET /api/ads/search?q=...&category=...` e o alias `GET /api/search?q=...&category=...` executam busca. Portas locais diretas, sem gateway: 8082 para Ads Command e 8086 para Ads Query. A rota interna de hidratação do Ads Command não é exposta pelo gateway.

## Payload de moderação

```json
{
  "title": "Notebook usado em bom estado",
  "description": "Notebook com carregador original e sinais leves de uso.",
  "price": 1800.00,
  "category": "eletronicos"
}
```

Contrato `POST /v1/moderation/check`. A categoria é o código textual. URLs e conteúdo de imagem ficam de fora; o mock não faz moderação visual.

## Eventos Kafka

### `ads.submitted.v1` — `AdSubmittedV1`

```json
{
  "eventId": "UUID estável desta publicação",
  "schemaVersion": 1,
  "occurredAt": "2026-01-01T00:00:00Z",
  "adId": "UUID do anúncio",
  "title": "Notebook usado em bom estado",
  "description": "Descrição detalhada",
  "price": 1800.00,
  "category": "eletronicos"
}
```

A chave Kafka é `adId`. `eventId` permite reconhecer replay. O contrato da moderação não carrega imagens.

### `ads.moderation-decided.v1` — `AdModerationDecidedV1`

Inclui `eventId`, `schemaVersion`, instante da decisão, `adId`, `status`, `rejectionReason`, instante de criação, título, descrição, categoria e preço. A projeção atualiza por `adId`; índices do OpenSearch só recebem `approved`. Este evento também omite imagens.

## Importante sobre códigos e estado

A API usa categoria textual documentada; o domínio converte o texto para enum e volta a emitir `Category.code()`. O nome Java da constante (por exemplo, `ELECTRONICS`) não é o valor do contrato (`eletronicos`). A API simples responde decisão final; Enterprise começa pendente. Não misture os códigos HTTP e estados entre essas duas versões.
