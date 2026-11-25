# Contrato HTTP da solução pequena

Base local: `http://localhost:8087`. A categoria usa os códigos textuais documentados pelo desafio, por exemplo `eletronicos`.

## Criar anúncio

`POST /ads` recebe JSON:

```json
{
  "title": "Notebook usado em bom estado",
  "description": "Notebook com carregador original e sinais leves de uso.",
  "price": 1800.00,
  "category": "eletronicos",
  "images": ["https://example.com/notebook.jpg"]
}
```

A aplicação valida a entrada, chama `POST /v1/moderation/check` no mock com `title`, `description`, `price` e `category`, e persiste a decisão final e as URLs. A URL da imagem não é enviada ao mock, que não realiza análise visual.

Resposta de sucesso: `201 Created`, com `Location: /ads/{id}` e corpo semelhante a:

```json
{
  "id": "3d79761a-95d6-4a09-8ba0-c7d4263845a9",
  "title": "Notebook usado em bom estado",
  "description": "Notebook com carregador original e sinais leves de uso.",
  "price": 1800.00,
  "category": "eletronicos",
  "images": ["https://example.com/notebook.jpg"],
  "status": "APPROVED",
  "rejectionReason": null,
  "createdAt": "2026-11-22T12:00:00Z"
}
```

O status também pode ser `REJECTED`; nesse caso `rejectionReason` contém o motivo fornecido pelo mock, quando presente. Entrada inválida retorna `400`; falha ou timeout da moderação retorna `502`, sem persistir resultado inconclusivo.

## Listar anúncios aprovados

`GET /ads?page=0&size=20&category=eletronicos`

`page` começa em zero, `size` usa padrão 20 e é limitado a 100. `category` é opcional. A resposta é a estrutura `Page` do Spring Data e inclui apenas anúncios aprovados.

## Consultar anúncio por ID

`GET /ads/{uuid}` retorna o anúncio com status final. Anúncios rejeitados permanecem consultáveis aqui para que o motivo seja visível. ID inexistente retorna `404`.

## Códigos de resposta previstos

- `201`: anúncio persistido com decisão de moderação.
- `400`: JSON, validação, categoria, URL ou preço inválidos.
- `404`: anúncio inexistente.
- `502`: dependência de moderação indisponível, timeout ou resposta inválida.

O enunciado não define os formatos exatos de todos os erros; os códigos acima são a decisão local desta implementação.
