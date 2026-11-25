# Alinhamento ao desafio OLX

## Contrato oficial confirmado

Fontes conferidas: [README oficial da trilha Backend](https://github.com/olxbr/code-challenges/blob/main/backend/README.md), [desafio e critérios de avaliação](https://github.com/olxbr/code-challenges/blob/main/README.md) e [contrato oficial do mock](https://github.com/olxbr/code-challenges/blob/main/backend/api-mock/docs-candidato/API.md). O repositório fornecido pelo candidato contém uma cópia local da documentação do mock.

- Criar anúncio com título, descrição, preço, categoria e no mínimo uma imagem (URL aceita).
- Validar preço maior que zero e título entre 10 e 100 caracteres.
- Antes de publicar, chamar a API externa de moderação; persistir o status e expor o motivo quando rejeitado.
- Listar com paginação e filtro por categoria.
- Consultar os dados e status de um anúncio individual.
- Documentar a API, decisões, ambiguidades, uso de IA e testes críticos.

O mock aceita `title`, `description`, `price` e `category` como texto. Responde `approved` ou `rejected`; no contrato, o motivo é `reason`, enquanto a implementação local do mock também pode usar `rejection_reason`. A integração aceita os dois nomes. O mock limita a 5 requisições por segundo.

## Duas implementações

### Solução pequena — entrega oficial

Está em `solutions/simple`. Atende os endpoints e validações acima usando uma aplicação Spring Boot, PostgreSQL/Flyway e chamada síncrona ao mock. Não exige Kafka, Redis, OpenSearch, frontend ou infraestrutura de microsserviços.

### Solução Enterprise — evolução educacional

Está em `services/` e `platform/`. Adiciona Outbox/Kafka, serviço de moderação, Redis, OpenSearch e gateway. Esses elementos são extensões de arquitetura, não requisitos do avaliador. O estado `PENDING_MODERATION` permite aceitar a escrita sem esperar o mock; só anúncios aprovados são consultáveis pelo read service.

## Ambiguidades que permanecem visíveis

1. **Imagens impróprias:** o mock não recebe URL nem bytes de imagem. A solução não pode afirmar que avaliou conteúdo visual. Na Enterprise, o evento de moderação não carrega imagens; a consulta de detalhe hidrata as URLs do serviço de cadastro. Uma futura análise visual exigiria contrato e componente próprios.
2. **Nome do campo do motivo:** documentado como `reason`, implementação local observada com `rejection_reason`; cliente tolera ambos.
3. **Publicação e persistência:** o desafio diz moderar antes de publicar, mas não define status de criação nem HTTP quando o mock demora. A solução simples aguarda a decisão. A Enterprise responde `202` e grava `PENDING_MODERATION`, exibindo somente aprovados no read service.
4. **Listagem de rejeitados:** o desafio exige que motivo fique visível, sem dizer se rejeitados aparecem em busca pública. Interpretação: ficam persistidos e disponíveis pelo detalhe da API de cadastro, mas fora das listagens públicas.
5. **Limites de descrição e imagens:** não definidos no enunciado. Não inventamos máximos de negócio; limites de payload/abuso precisam de decisão explícita.
6. **Idempotência de moderação:** o mock não documenta chave de idempotência. Não existe garantia de exatamente uma chamada externa diante de queda depois da resposta do mock e antes da persistência local.
7. **RPS da API:** não definido pelo desafio. As contas Enterprise em `DECISIONS.md` são cenários hipotéticos, não volume real ou requisito oficial.
8. **Escala do preço:** o enunciado exige valor positivo, sem definir precisão. O schema escolhe `NUMERIC(19,2)`; valores que não cabem nessa escala são rejeitados antes da chamada externa.

## Fora do desafio

Favoritos, frontend Next.js, gateway, Kafka, Outbox, Redis, OpenSearch, Kubernetes, AWS Emulator e Terraform não são necessários para a entrega pequena. A pasta Enterprise os usa apenas quando a responsabilidade de cada um está explicada.
