# DECISIONS — registro inicial
## ADR-001: microsserviços desde a fundação
**Motivo:** objetivo educacional de praticar arquitetura distribuída Senior/Lead/Architect.
**Alternativa:** monólito modular seria mais proporcional ao desafio original.
**Riscos:** maior custo operacional, integração, consistência eventual e observabilidade.
**Mitigação:** implementação incremental, serviços independentes e documentação.

## ADR-002: monorepo
Simplifica ensino e CI; não implica compartilhar entidades ou bancos.

## ADR-003: Outbox na versão Enterprise
Implementada em `services/ads-service`: anúncio e evento Outbox são gravados na mesma transação PostgreSQL e o relay publica no Kafka. A solução pequena não usa Outbox, por não precisar de mensageria.

## ADR-004: laboratório evolutivo, entrega backend sem frontend
Compose preserva dependências locais e o backend contém a solução oficial pequena e a evolução Enterprise. O frontend demonstrativo existente não integra o desafio nem as duas APIs.

## Ambiguidades do desafio
Documentação de moderação usa `reason`; implementação do mock pode retornar `rejection_reason`. Os clientes aceitam ambas as propriedades.

## Tempo e decisões futuras
Preencher com tempo real, alternativas testadas e mudanças feitas ao longo do curso.

## ADR-005: AWS Emulator isolado
Docker local no Windows, porta ligada a 127.0.0.1, credenciais efêmeras e sem acesso a recursos AWS reais. Integração com SDK Java será implementada em aula posterior, respeitando truststore HTTPS.

## Aula 3 — dimensionamento inicial antes da implementação

**Status:** análise inicial para orientar decisões. Os cenários e valores abaixo são hipóteses didáticas, não requisitos nem medições da OLX. Esta seção não altera o desafio oficial, as aulas anteriores ou as classes existentes.

### Evidências e limites

O desafio pede criar, listar com paginação e filtro por categoria, moderar antes de publicar e consultar anúncios. Não define RPS, p95/p99, tamanho da base, disponibilidade ou uso de Redis, OpenSearch, Kafka ou microsserviços. O mock define até 5 tentativas de moderação por segundo e tem atraso configurável; no padrão do código local há atraso de 100–1999 ms e erro configurável de 20%. Esses limites pertencem ao mock, não à API OLX.

Há um microexperimento isolado da construção de `AdDraft`, executado em OpenJDK 21.0.13, com heap fixo de 256 MiB e G1GC. Em cinco repetições de 100 mil construções por configuração, a mediana observada foi cerca de 0,88 milhão de operações/s com uma thread de plataforma e 3,69 milhões com oito; para threads virtuais, cerca de 0,86 milhão e 3,99 milhões, respectivamente. A alocação observada para threads de plataforma foi 2.208 bytes/op. O contador de alocação usado não forneceu dados para threads virtuais. As contagens/tempos agregados de GC foram 7/6 ms, 27/15 ms e 55/34 ms para 1/4/8 threads de plataforma; para threads virtuais, 6/3 ms, 27/11 ms e 55/23 ms. São resultados deste microbenchmark, não latência HTTP, p95/p99, capacidade do serviço, heap necessário ou prova de vantagem produtiva das threads virtuais. O teste não inclui Spring, rede, banco, Kafka, moderação nem carga concorrente externa; não usamos JMH.

### Cenários quantitativos hipotéticos

**Cenário A — entrega simples do desafio.** Hipótese de planejamento: 1 cadastro/s em média, pico de 5 cadastros/s e 50 consultas/s. Não é uma previsão da OLX. Com moderação síncrona, 5 chamadas/s já consomem todo o limite documentado do mock; o atraso médio teórico se o atraso for uniforme entre 100 e 1.999 ms é aproximadamente 1,05 s. Pela Lei de Little, `concorrência média ≈ vazão × tempo médio`: a 5 chamadas/s, cerca de `5 × 1,05 = 5,25` chamadas externas em voo. Com erro padrão hipotético de 20%, o modelo simples de decisões concluídas é `5 × (1 − 0,20) = 4/s`, antes de timeouts, 429 e retries. Uma API síncrona pode, portanto, manter várias requisições abertas e ultrapassar qualquer objetivo de resposta rápida; a solução simples precisa limitar concorrência/taxa e deixar claro o comportamento de espera/erro. A alternativa assíncrona reduz a dependência da chamada no tempo de resposta do cadastro, mas exige estado pendente e processamento durável.

**Cenário B — leitura intensiva Enterprise.** Hipótese de teste, não requisito: 20 cadastros/s, 1.000 consultas/s, com 95% das consultas repetindo dados quentes. Se a consulta média no estágio considerado durar 50 ms, há `1.000 × 0,050 = 50` operações em voo em média. Isso não define número de threads nem conexões. Sem cache, se cada consulta mantiver uma conexão por 10 ms, a ocupação média estimada seria `1.000 × 0,010 = 10` conexões; com 95% de acerto de cache e as mesmas hipóteses, seriam `50 × 0,010 = 0,5` conexões médias para esse tráfego de leitura. São médias de modelo; picos, transações de escrita, distribuição de latência, locks e pool precisam ser medidos. Como metas de um ensaio hipotético podemos propor p95 ≤ 100 ms e p99 ≤ 300 ms para leitura, mas esses números não vêm do desafio nem da OLX.

**Backlog de moderação no cenário B.** Usando o modelo de 4 decisões/s acima: `(20 − 4) × 60 = 960` anúncios pendentes após um minuto de chegadas, se não houver outra restrição/admissão. Parando as chegadas, a drenagem ideal seria `960 / 4 = 240 s`. Se a taxa de entrada permanecer acima da capacidade efetiva, o backlog continua crescendo. Partições e réplicas Kafka não aumentam o teto compartilhado de 5 tentativas/s do mock; a quota precisa ser coordenada globalmente e retries também consomem chamadas.

**Retenção Kafka — exemplo de ordem de grandeza.** Admitindo, apenas para estimativa, evento serializado médio de 1 KiB, 20 eventos/s, retenção de 24 h: `1 KiB × 20 × 86.400 = 1.769.472.000 bytes`, aproximadamente 1,65 GiB de payload. Com fator de replicação 3, aproximadamente 4,95 GiB de payload replicado, antes de overhead, índices, segmentos, metadados e variação de tamanho; compressão pode alterar o resultado. Precisamos medir o tamanho do evento real e escolher retenção/replicação conforme disponibilidade e custo.

**Redis — memória mínima do payload quente.** Admitindo 100 mil anúncios quentes com payload de 2 KiB: `100.000 × 2.048 = 204.800.000 bytes`, aproximadamente 195,3 MiB. É apenas o payload; chaves, estruturas Redis, allocator, fragmentação e cópias elevam o consumo real. TTL, invalidação, stampede e taxa de acerto devem ser medidos. Redis só se justifica se leituras repetidas reduzirem pressão/latência o suficiente para compensar custo, memória e risco de dado desatualizado.

**Heap/JVM e threads.** Não se deriva `-Xmx` de RPS nem do microbenchmark de `AdDraft`. O orçamento precisa incluir live set e alocações temporárias, buffers, metaspace, code cache, memória direta e nativa, stacks de threads de plataforma e margem dentro do limite do container. A coleta deve observar heap após GC, taxa de alocação, pausas e RSS/cgroup com carga representativa. Threads virtuais podem reduzir o custo de muitas esperas bloqueantes, mas não aumentam CPU, conexões disponíveis no pool, quota de moderação ou capacidade do banco. Não fixamos agora quantidade de threads, tamanho de heap ou réplicas.

**PostgreSQL e conexões.** O PostgreSQL permanece fonte de verdade. Para uma consulta relacional por categoria, comparar primeiro a consulta e índices adequados no PostgreSQL. Uma aproximação da ocupação média de conexões é `taxa de operações que usam o banco × tempo médio segurando a conexão`; somar pools de todas as réplicas e reservar conexões para operação/administração. Não manter conexão aberta durante chamada HTTP à moderação. Dimensionamento final exige medir planos, latência, locks, CPU/IOPS, transações e uso do pool.

**OpenSearch e paginação.** Não há volumetria, requisito de relevância textual ou necessidade documentada de busca complexa no desafio. PostgreSQL é baseline para filtro por categoria e paginação. OpenSearch passa a ser justificável se busca textual/ranking/filtros de leitura ou pressão demonstrada no baseline exigirem índice de busca separado. Paginação profunda por offset deve ser avaliada; `search_after` com PIT pode atender navegação sequencial, mas não oferece acesso aleatório simples a uma página arbitrária. Tamanho de documentos, shards, réplicas e throughput de indexação dependem de benchmark; não fixados nesta análise.

### Conclusões arquiteturais provisórias

**Solução simples:** um Spring Boot modular, PostgreSQL como persistência e integração controlada com o mock são suficientes para demonstrar o contrato oficial. Paginação, filtro, detalhe, persistência, timeout, tratamento de 429/erro e uma política explícita para a moderação atendem ao escopo sem Kafka, Redis ou OpenSearch obrigatórios. O desafio não exige esses produtos, nem fornece carga para afirmar que um monólito falharia. A integração síncrona tem latência condicionada pelo mock; caso o contrato de resposta não aceite essa espera, a decisão de aceitar cadastro pendente precisa ser registrada e compatibilizada com o requisito “moderação antes de publicar”.

**Solução Enterprise:** separar cadastro/comandos e consulta pode ser justificado quando há necessidade observada de escalar ou proteger leitura independentemente da escrita, quando o PostgreSQL otimizado não atinge metas acordadas, ou quando texto/relevância exige mecanismo especializado. O serviço de cadastro grava anúncio e evento Outbox na mesma transação PostgreSQL; relay publica no Kafka; consumidor executa moderação respeitando quota global; decisão aprovada/rejeitada é persistida e gera evento de projeção; serviço de consulta atualiza OpenSearch e pode usar Redis para detalhes quentes. OpenSearch e Redis são projeções/cache, não fonte transacional. O fluxo admite atraso, duplicatas, reprocessamento e reconstrução de índice; exige idempotência, versionamento de eventos, métricas e operação adicional. O contrato de moderação documenta título, descrição, preço e categoria; não recebe nem inspeciona imagens. URLs de imagens não serão descritas como conteúdo moderado.

Redis não deve ser incluído apenas para marcar a arquitetura como Enterprise; OpenSearch não deve substituir PostgreSQL para consultas que o banco já atende; Kafka não aumenta a quota externa. A separação física de serviços acrescenta deploys, rede, observabilidade, contratos e consistência eventual. O ganho precisa aparecer em testes com carga e metas definidas.

### Plano de evidência e itens pendentes

1. **Estimativa prévia (agora):** usar os cenários e fórmulas acima para listar gargalos e orientar limites. Valores são hipóteses.
2. **Testes de carga (depois da implementação):** registrar versão do código, hardware/container, dataset, taxa oferecida, duração, concorrência, warm-up e erros; medir separadamente escrita, listagem, detalhe e moderação; reportar p50/p95/p99, throughput concluído, CPU, RSS/heap/GC, pool PostgreSQL, lag Kafka e, se existirem, acerto/memória Redis e latência/lag OpenSearch.
3. **Métricas observadas:** substituir hipóteses somente quando houver resultado reproduzível. Não chamar estimativa de medição nem extrapolar o microbenchmark de domínio para a aplicação inteira.

Permanecem sem evidência: RPS real da OLX ou do avaliador; perfil de pico; dataset representativo; SLO/SLA; limite de memória/CPU do ambiente-alvo; latência p95/p99 da API; capacidade PostgreSQL; throughput Kafka e índice; taxa de repetição de leitura; cardinalidade do cache; tamanho real dos eventos. As decisões de réplicas, heap, pool, partições, shards, TTL e limites de container ficam abertas até teste representativo.

**Sequência da Aula 3:** requisitos → hipóteses de carga → estimativas → decisões arquiteturais → implementação conforme o plano original → testes → evidências → trade-offs. Esta análise não implementa classes nem muda a ementa.

### Fontes

- [Desafio backend e requisitos oficiais](https://github.com/olxbr/code-challenges/blob/main/backend/README.md)
- [Contrato da API de moderação](https://github.com/olxbr/code-challenges/blob/main/backend/api-mock/docs-candidato/API.md)
- [`incremento-01/docs/CAPACITY.md`](../../incremento-01/docs/CAPACITY.md), análise local do mock e modelo de backlog.
- [Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Kafka — Design](https://kafka.apache.org/39/design/design/)
- [PostgreSQL — Using EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html)
- [OpenSearch — Paginate results](https://opensearch.org/docs/latest/search-plugins/searching-data/paginate/)
- [Redis — Key eviction](https://redis.io/docs/latest/develop/reference/eviction/)

## Complemento da Aula 3 — dimensionamento ponta a ponta por serviço

**Objetivo:** transformar o perfil Enterprise do AEF em uma estimativa por estágio da aplicação. Os valores desta seção são hipóteses de um cenário de estresse; não representam tráfego real da OLX, capacidade comprovada, metas oficiais ou medições da aplicação. Não altera a ementa nem implementa infraestrutura.

### 1. Perfil usado e ressalvas do modelo

O `Main.java` do AEF define o perfil `enterprise` com 8.300.000 usuários ativos **por dia**, 30 requisições por usuário/dia, fator de pico 8, 92% leituras, latência média global de 250 ms, dois eventos por escrita, evento médio de 8 KiB, retenção de 14 dias e cache hit de 80%. O perfil também usa capacidade genérica de consumidor de 1.000 eventos/s e disponibilidade-alvo de 99,99%. Todos esses números são dados de entrada hipotéticos do framework. Em especial, 8,3 milhões de DAU não devem ser confundidos com usuários mensais nem com uma métrica oficial de tráfego do serviço.

Não foi possível executar o AEF nesta sessão porque o ambiente não disponibilizou Maven/JDK de compilação. As contas abaixo reproduzem as fórmulas visíveis em `Sizing.java` e `Main.java`; portanto, são cálculos reproduzidos, não uma execução do motor. O protótipo documenta que sua retenção considera o pico constante durante 14 dias e não modela CPU, heap, IOPS, limites de banco, custo, p99, compressão ou overhead. A conta de backlog é de taxa sustentada.

### 2. Da audiência hipotética aos RPS

Primeiro calculamos a taxa média sobre o dia inteiro:

```text
RPS médio = 8.300.000 usuários × 30 requisições/dia ÷ 86.400 s/dia
          = 2.881,94 requisições/s

RPS de pico = 2.881,94 × fator de pico 8
            = 23.055,56 requisições/s
```

Pelo mix AEF de 92% leitura e 8% escrita:

```text
Leituras médias = 2.651,39/s       Escritas médias = 230,56/s
Leituras de pico = 21.211,11/s     Escritas de pico = 1.844,44/s
```

O fator 8 não diz quanto tempo o pico dura. Para tornar o backlog calculável sem fingir que conhecemos a curva diária, adotamos adicionalmente **15 minutos de pico** como hipótese de teste. A duração precisa ser variada nos testes de sensibilidade (por exemplo, 5, 15 e 60 minutos). O AEF, por outro lado, aplica o pico continuamente em algumas contas; essas contas são limites conservadores, não previsão diária.

Para dividir a leitura entre serviços, o perfil AEF não fornece tipos de endpoint. Adotamos explicitamente uma distribuição didática: 70% busca/listagem, 25% detalhe e 5% leitura de favoritos. Isso resulta, no pico, em aproximadamente 14.848 buscas/listagens/s, 5.303 detalhes/s e 1.061 leituras de favoritos/s. Alterar essa distribuição muda Redis, OpenSearch, PostgreSQL e rede.

### 3. Gateway e rede

O gateway recebe até 23.056 requisições/s neste cenário. A Lei de Little estima operações em voo como taxa multiplicada pela **latência média do mesmo estágio**. Usando os 250 ms médios do perfil apenas como aproximação do caminho completo:

```text
requisições em voo no gateway ≈ 23.055,56/s × 0,250 s
                               ≈ 5.764
```

Isso não significa 5.764 threads. É a quantidade média de requisições simultaneamente em processamento/espera sob essas hipóteses. Em Spring MVC com I/O bloqueante, pools e filas precisam ser limitados; virtual threads podem baratear espera bloqueante, mas não criam capacidade no banco, no broker ou no mock. O número de réplicas deve vir da vazão medida por instância na meta de latência, com margem e testes de falha; RPS sozinho não fornece esse número.

Para enxergar a ordem de grandeza de rede, supomos 4 KiB de corpo médio por requisição. A entrada seria `23.055,56 × 4 KiB`, cerca de 90 MiB/s ou 0,76 Gbit/s, antes de TLS, cabeçalhos, retransmissões e tráfego interno. Para saída, supomos resposta média de 20 KiB para busca/listagem, 4 KiB para detalhe e 1 KiB para favoritos. O resultado aproximado é 327 MB/s, ou 2,62 Gbit/s, de egress no pico. São hipóteses de payload; uma página com 20 anúncios, campos grandes ou URLs extensas altera muito essa conta. O payload das imagens não está sendo transferido: o desafio fornece URLs, não exige upload da mídia.

Esses números justificam que um teste observe banda de entrada e saída no gateway e na rede do cluster. Não justificam, isoladamente, escolher instância AWS ou número de pods.

### 4. Serviço de cadastro e PostgreSQL transacional

O serviço de cadastro/comandos recebe 1.844 novas submissões/s no pico hipotético. A requisição deve validar os campos e persistir anúncio e evento Outbox juntos na transação do PostgreSQL. A chamada ao mock não deve manter essa transação aberta: o worker de moderação processa depois. O evento leva os campos documentados pelo contrato do mock; URLs/imagens não entram no evento de moderação, pois o mock não as recebe nem as inspeciona.

Para expor a sensibilidade da escrita, supomos cinco imagens por anúncio e persistência relacional das referências. Cada submissão criaria pelo menos uma linha de anúncio, uma linha Outbox e cinco linhas de imagem: sete inserções lógicas. Isso representa cerca de `1.844 × 7 = 12.911` linhas/s no pico, antes de índices, WAL, atualização de estado, auditoria e operações internas do banco. Cinco imagens e esse modelo de tabelas são hipóteses, não regras oficiais. Se imagens forem armazenadas como array/JSON, cai a contagem de linhas, mas mudam atualização, indexação e consulta.

Para estimar conexões, supomos que a transação segura uma conexão por 20 ms:

```text
conexões médias ocupadas ≈ 1.844,44 transações/s × 0,020 s
                           ≈ 36,9 conexões
```

Se planejarmos que a ocupação média desse pool fique em até 70%, a conta preliminar seria `36,9 ÷ 0,70 ≈ 53` conexões agregadas para o serviço de cadastro. É um **ponto de partida para ensaio**, não um tamanho aprovado para Hikari ou PostgreSQL: caudas de latência, locks, queries, conexões de outros serviços, failover e CPU/IOPS podem exigir menos ou mais. O total de pools de todas as réplicas deve caber no orçamento do banco. Medimos tempo de transação e conexão segurada; não dimensionamos pool pelo número de usuários HTTP.

Para uma indisponibilidade de publicação de um minuto no pico, a Outbox acumularia aproximadamente 110.667 eventos se forem dois eventos por escrita, ou cerca de 1,69 GiB de payload bruto a 8 KiB/evento. Overhead de linhas, índices, páginas, WAL e retenção aumenta o armazenamento. A Outbox precisa de alerta de idade do evento mais antigo, política de retenção/limpeza e limite operacional; não se deve deixar crescimento ilimitado no banco.

### 5. Moderação: o gargalo que limita o fluxo

O serviço de moderação não pode ser dimensionado pela capacidade genérica de 1.000 eventos/s do AEF: o contrato do mock limita tentativas a 5/s compartilhadas. Para deixar margem a variação de janela e coordenação, usamos 4 tentativas/s como capacidade de planejamento. Com o erro padrão hipotético de 20%, o valor esperado é cerca de 3,2 respostas de negócio/s, antes de 429, timeout e retries. Essa expectativa não é garantia de aprovação: respostas de negócio incluem aprovados e rejeitados.

Com 1.844 novas submissões/s e capacidade efetiva de 4 decisões/s, o backlog aumenta aproximadamente `1.844,44 − 4 = 1.840,44` por segundo. Em 15 minutos, isso representa cerca de 1.656.400 anúncios pendentes. Mesmo se as chegadas pararem completamente, a drenagem ideal a 4/s levaria aproximadamente 414.100 segundos, ou 4,79 dias. Com 3,2 decisões/s esperadas, a drenagem passa de 5,99 dias. Retentativas consomem a mesma quota e podem piorar a vazão.

Conclusão operacional: este perfil hipotético **não é compatível com aceitar esse pico sem política de admissão e sem um SLA de moderação muito longo**. Kafka conserva trabalho, mas não o processa mais rápido. Réplicas, partições e pods não multiplicam uma quota externa global. Para o desafio pequeno, a solução deve respeitar o limite, tratar 429/Retry-After e explicitar estados pendentes. Para um cenário real de escala, seria preciso negociar capacidade/quota do fornecedor, controlar admissão ou rever a experiência/SLA; não contornar o limite com mais consumidores.

Com atraso médio inferido do mock de cerca de 1,05 s, 4 tentativas/s representam aproximadamente `4 × 1,05 = 4,2` chamadas externas em voo em média. Esse cálculo ajuda a dimensionar o bulkhead do cliente, não determina o tamanho do pool de threads. O rate limiter precisa ser coordenado entre réplicas; um limite local de 4 por pod seria incorreto.

### 6. Kafka e Transactional Outbox

Com duas mensagens por submissão, o perfil gera `1.844,44 × 2 = 3.688,89 eventos/s` no pico. A 8 KiB por evento, o tráfego bruto é aproximadamente 30,2 MB/s (28,8 MiB/s), sem cabeçalhos, protocolo, compressão ou replicação. A retenção usando pico constante por 14 dias daria 33,25 TiB em uma cópia, ou 99,74 TiB com fator de replicação 3. Essa é a conta conservadora do AEF, não uma previsão de armazenamento.

O AEF, aplicando sua capacidade genérica de 1.000 eventos/s, calcula backlog de `(3.688,89 − 1.000) × 3.600 ≈ 9,68 milhões de eventos/h`. Isso é a saída do modelo genérico. Para a fila de moderação, a taxa de entrada relevante é uma submissão por escrita (1.844,44/s), e o gargalo real conhecido é a quota externa de 5 tentativas/s; por isso calculamos separadamente cerca de 6,63 milhões de submissões pendentes/h usando 4 decisões/s. Um evento de indexação não deve ser contado como se também fosse uma chamada de moderação.

Para uma conta mais compatível com o perfil médio, a taxa média de eventos é `230,56 × 2 = 461,11/s`. Mantida por 14 dias, com 8 KiB/evento, resulta em aproximadamente 4,16 TiB de payload em uma cópia ou 12,47 TiB com fator 3. Ainda é grande porque as hipóteses assumem 8 KiB para todo evento e dois eventos para cada escrita. Devemos serializar os eventos reais e usar seu tamanho médio/p95 medido para revisar a estimativa. O volume físico inclui overhead e pode diminuir com compressão; replicas aumentam o armazenamento.

O total de partições Kafka não pode ser fechado sem throughput medido por partição, tamanho de mensagem, chave, retenção, número de grupos e objetivo de recuperação do backlog. A chave `adId` preserva ordem relativa por anúncio, mas pode criar partições quentes se a distribuição for desigual. A fórmula de partida é: `partições necessárias ≥ ceil(vazão desejada ÷ vazão sustentada medida por partição)`, validada também por paralelismo útil e tempo máximo de drenagem. A configuração local atual tem um único broker/controller e fator de replicação 1; serve ao desenvolvimento, não oferece tolerância de falha nem valida uma topologia produtiva.

No desenho do fluxo, o anúncio e o registro Outbox são gravados na mesma transação PostgreSQL. Um relay publica o evento e marca sua entrega após confirmação do broker; uma queda entre confirmação e atualização pode duplicar a publicação. O consumidor deduplica por `eventId`. PostgreSQL, Kafka e OpenSearch não formam uma única transação atômica. O consumidor de moderação também precisa respeitar a quota; o consumidor de projeção atualiza OpenSearch de modo idempotente/versionado. Falha e duplicata são parte do modelo, não exceção eliminada por “exactly once” entre sistemas.

### 7. Serviço de consulta, Redis e OpenSearch

Com a distribuição didática de leituras, o serviço de consulta recebe cerca de 14.848 buscas/listagens/s e 5.303 detalhes/s. Aplicando o hit ratio hipotético de 80% somente aos detalhes:

```text
Redis hits ≈ 5.303 × 0,80 = 4.242/s
Redis misses ≈ 5.303 × 0,20 = 1.061/s
```

Se aplicarmos literalmente a abstração simplificada do AEF a todas as leituras, ela calcula `21.211 × 0,20 ≈ 4.242` cache misses/s. A decomposição por endpoint é mais útil para arquitetura: busca textual/listagem não necessariamente compartilha chave, TTL ou valor de cache com detalhe. Por isso a estimativa abaixo mantém busca/listagem no OpenSearch e aplica o hit ratio somente aos detalhes. É uma hipótese de modelagem que o teste deve validar.

Se o detalhe em cache leva 2 ms em média, a ocupação média de operações Redis é `4.242 × 0,002 ≈ 8,5` em voo. Se o OpenSearch atende busca/listagem e detalhes que não estão no cache, recebe aproximadamente `14.848 + 1.061 = 15.909` consultas/s. Com latência média hipotética de 100 ms, são cerca de 1.591 consultas OpenSearch simultâneas. Isso é uma exigência muito alta para validar por benchmark; não autoriza inferir número de nós ou shards sem documento real, mapping, filtros, concorrência e hardware.

Um cache com 100 mil detalhes quentes a 2 KiB por item teria 195,3 MiB somente de payload. Chaves, objetos, estruturas de dados, allocator, fragmentação e política de replicação elevam o consumo. O hit ratio de 80% é hipótese do AEF; precisa ser medido com distribuição de acessos realista. Cache-aside reduz leituras repetidas, mas introduz invalidação, TTL, stampede e risco de resposta obsoleta. Redis indisponível deve levar a fallback controlado e protegido por limite de concorrência; caso contrário, todas as requisições migram para OpenSearch/PostgreSQL ao mesmo tempo.

PostgreSQL continua dono dos dados transacionais. OpenSearch é uma projeção de busca atualizada por eventos e admite consistência eventual; Redis é cache, não fonte da verdade. A consulta deve aceitar/explicar atraso de indexação e permitir reindexação a partir de uma fonte durável. Busca textual, ordenação e paginação profunda exigem medir latência p50/p95/p99, tamanho do índice, taxa de atualização e custo de `search_after`/PIT. O desafio oficial não obriga Redis ou OpenSearch; aqui eles são componentes da arquitetura Enterprise a justificar por metas e evidências.

### 8. JVM, heap, GC, threads e CPU

O AEF não calcula heap, GC, CPU ou quantidade de threads. Não existe conversão válida de 23 mil RPS para `-Xmx` sem conhecer bytes retidos por requisição, alocação, baseline, buffers, stacks, carga do Spring, cliente HTTP, drivers e live set. Para visualizar a sensibilidade, usando 5.764 requisições em voo:

```text
se cada requisição retiver 100 KiB temporários: ≈ 563 MiB
se cada requisição retiver 512 KiB temporários: ≈ 2,82 GiB
```

Esses resultados são apenas multiplicações hipotéticas; não incluem heap de base nem memória nativa e não são recomendação de heap. Um dump de heap serve para investigar retenção depois de observar pressão; não é método de sizing e pode exigir espaço em disco e causar pausa. G1GC é o coletor padrão usual do Java 21 em heaps servidor, mas a escolha e parâmetros devem ser validados com pausas, throughput, alocação e limite do container. Primeiro medimos live set pós-GC, taxa de alocação, pausas, RSS/cgroup e OOM; depois definimos heap com margem nativa.

O microbenchmark de `AdDraft` anteriormente registrado mediu cerca de 2.208 bytes/op em sua configuração de plataforma. Multiplicado pelas 1.844 escritas/s hipotéticas, isso daria aproximadamente 3,88 MiB/s apenas para aquela construção isolada. Não inclui serialização HTTP, entidades JPA, eventos, logs ou aplicação Spring; não serve como estimativa total de alocação nem prova de que GC terá essa taxa.

CPU também depende do trabalho por requisição. Após teste, a fórmula operacional é `vCPU necessárias ≈ RPS-alvo ÷ throughput medido por vCPU`, ajustada para o limite de utilização e a latência objetivo. Deve-se observar throttling de CPU no container. Virtual threads ajudam em tarefas bloqueantes, mas não aumentam o throughput de CPU-bound nem superam pools de PostgreSQL, conexões HTTP, quota de moderação e capacidade do broker.

### 9. Capacidade por componente e decisão para as duas soluções

Gateway: 23.056 RPS de pico; cerca de 5.764 requisições em voo pela latência média global hipotética. Medir vazão por instância, banda, saturação de CPU, filas e p95/p99.

Cadastro: 1.844 submissões/s de pico; cerca de 12.911 inserções lógicas/s na hipótese de cinco imagens em tabela separada; média de 37 conexões ocupadas sob transação de 20 ms. Medir throughput de commit, WAL, índices, locks, pool e tempo de conexão.

Moderação: teto externo de 5 tentativas/s; planejamento em 4 tentativas/s e aproximadamente 3,2 respostas de negócio/s sob erro hipotético de 20%. Este componente torna o perfil de 8,3M DAU impraticável sem admissão/quota/SLA compatíveis.

Consulta: 14.848 buscas/listagens/s e até 15.909 consultas/s no OpenSearch sob os pressupostos acima; 4.242 hits/s e 1.061 misses/s no Redis. Medir throughput sustentável, p95/p99, lag de indexação, cardinalidade, hit ratio, memória e comportamento de falha.

Banco de favoritos: o perfil não informa proporção de escritas de favoritos, tamanho da tabela ou consultas por usuário. Portanto, só estimamos 1.061 leituras/s sob a divisão hipotética; não dimensionamos escritas nem pool. Precisamos de distribuição por endpoint e teste próprio.

PostgreSQL de cadastro, Kafka, Redis, OpenSearch e serviços não têm hardware suficiente especificado para converter essas taxas em tamanhos de máquinas. A conta responsável agora é delimitar as cargas e gargalos; o próximo estágio escolhe recursos usando benchmark e limites do ambiente.

Para a solução simples do desafio, o cenário de submissões é pequeno, sem RPS oficial, e a arquitetura menor Spring Boot + PostgreSQL + integração respeitosa com o mock minimiza operação. Sua adequação deve ser defendida pelo contrato, pelo limite de 5 RPS e por um ensaio que represente o avaliador; não por métricas de usuários da OLX. Para a solução Enterprise, separação de comando e consulta passa a fazer sentido se tráfego de leitura, busca textual ou metas de latência forem comprovados; Kafka/Outbox protegem integração assíncrona; Redis e OpenSearch absorvem padrões específicos de leitura. Mesmo assim, nenhum desses componentes resolve o limite de moderação. O dimensionamento acima expõe esse gargalo antes de implementação.

### 10. O que medir no teste integrado

Executar degraus de carga (por exemplo, 25%, 50%, 75%, 100% do perfil hipotético e um teste de estresse controlado), com dataset e duração registrados. Variar mix de endpoint, duração do pico, tamanho de eventos, taxa de erro do mock e indisponibilidade de cada dependência. Registrar por serviço: RPS oferecido/concluído, p50/p95/p99, erros/timeouts, CPU e throttling, RSS, heap antes/depois do GC, alocação e pausas G1, threads/pools, conexões e espera do PostgreSQL, WAL/IOPS, lag/idade do Kafka, idade da Outbox, Redis hit ratio/memória/latência, OpenSearch latência/CPU/shards e tráfego de rede.

Dimensionar réplicas pelo throughput **concluído** por instância na meta de latência, não por RPS oferecido. Dimensionar consumidores pelo tempo de drenagem tolerável e pela quota global externa. Guardar as configurações, versão do código, dataset e resultados para que a decisão possa ser repetida. Até esse teste, todos os valores desta seção continuam marcados como hipóteses e contas analíticas.

### Fontes e rastreabilidade

- O perfil e as fórmulas foram lidos no arquivo enviado `AEF-Java-AWS-v0.1(1).zip`, em `Main.java`, `Sizing.java` e `README.md`; o AEF não foi executado nesta sessão.
- O código local do mock, em `platform/moderation-mock/server.js`, define padrão de 5 RPS, 20% de erro e atraso aleatório entre 100 e 1.999 ms.
- [Desafio backend e requisitos oficiais](https://github.com/olxbr/code-challenges/blob/main/backend/README.md)
- [Contrato da API de moderação](https://github.com/olxbr/code-challenges/blob/main/backend/api-mock/docs-candidato/API.md)
- [Java 21 — Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [Java 21 — G1 Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html)
- [Kafka 3.9 — Design](https://kafka.apache.org/39/design/design/)
- [PostgreSQL — EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html)
- [OpenSearch — Paginação](https://opensearch.org/docs/latest/search-plugins/searching-data/paginate/)
- [Redis — Políticas de eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- [Kubernetes — Recursos de containers](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/)

## Decisões da implementação das duas versões (2026-11-22)

### ADR-006 — solução oficial pequena separada

**Decisão:** manter `solutions/simple` como aplicação independente, síncrona, Spring Boot + PostgreSQL + Flyway. Ela atende ao contrato oficial, sem Kafka, Redis, OpenSearch, Gateway ou Kubernetes.

**Motivo:** o desafio não define escala/SLO nem pede componentes distribuídos. Menos deploys e estados facilitam revisão dentro do esforço esperado de 4 a 6 horas. A limitação é o request aguardar o mock.

### ADR-007 — Enterprise usa escrita assíncrona e projeção de leitura

**Decisão:** `ads-service` grava anúncio e `AdSubmittedV1` na Outbox na mesma transação. `OutboxRelay` publica ao Kafka; `moderation-worker` respeita quota via token bucket Redis e publica decisão; `ads-query-service` atualiza OpenSearch e usa Redis para detalhe.

**Alternativas:** uma API monolítica sincronizada (proporcional ao desafio), uma única tabela consultada por todas as leituras (baseline) e chamada direta ao mock dentro da transação (rejeitada por manter conexão durante rede e acoplar commit à dependência).

**Trade-offs:** consistência eventual, deploys e observabilidade adicionais, custo de broker/index/cache e falhas entre sistemas. Kafka aumenta durabilidade/desacoplamento, não a quota externa. Redis/OpenSearch só se justificam se teste demonstrar ganho para acesso quente/busca textual.

### ADR-008 — evento de moderação sem imagens

**Decisão:** `AdSubmittedV1` e `AdModerationDecidedV1` não carregam URLs ou bytes de imagem. O mock recebe apenas título, descrição, preço e categoria em texto. Detalhe Enterprise hidrata URLs no Ads Command; resultados de busca do OpenSearch são resumos sem imagem.

**Ambiguidade:** se imagem for parte do critério de moderação, o mock atual não oferece esse recurso. Uma projeção de busca com imagem exige contrato/fluxo próprio; não declarar conteúdo visual moderado.

### ADR-009 — duplicidade assumida e idempotência delimitada

**Decisão:** `eventId` estável por evento; Outbox reenvia quando não consegue marcar publicação. O worker armazena decisão com chave `eventId` antes de publicar o resultado, evitando repetir a chamada externa quando o offset retorna após persistência. Ads aplica estado final e Query faz upsert/delete usando `adId`.

**Limite:** se o mock respondeu e o worker caiu antes de persistir, a API externa pode ser chamada novamente; não há chave de idempotência no contrato. Publicar a decisão e cair antes do commit do offset também pode duplicar o evento Kafka. A arquitetura é at-least-once com consumidores idempotentes; não é exactly-once entre serviços.

### ADR-010 — quota compartilhada do mock

**Decisão:** token bucket centralizado no Redis com capacidade e reposição de 4 chamadas/s, e 4 tentativas por evento entre tentativas Kafka antes da DLT. O valor 4 deixa margem em relação ao teto de 5 tentativas/s documentado.

**Trade-off:** dependência de Redis para executar moderação; Redis indisponível causa fail-closed e acúmulo de Kafka. O bucket é compartilhado entre pods que usam a mesma chave Redis. O Compose de nó único é laboratório, não tolerante a falhas.

### ADR-011 — search-after e detalhe no command source

**Decisão:** OpenSearch recebe apenas anúncios aprovados e a navegação usa `search_after`; detalhe passa por cache Redis e hidrata campos/imagens do Ads Command em cache miss.

**Trade-off:** busca e detalhe têm consistência eventual. Sem PIT, atualização do índice entre páginas pode mudar o conjunto percorrido. A busca não inclui imagem. Redis indisponível aciona fallback com bulkhead de 32 chamadas simultâneas por instância; excedente responde 503.

### ADR-012 — separar quota e cache em instâncias Redis distintas

**Decisão:** `redis-quota` usa `noeviction` para que pressão de memória não remova tokens de rate limit; `redis-cache` usa LRU e TTL porque seus dados podem ser reconstruídos.

**Trade-off:** duas instâncias aumentam memória/operacional local, mas evitam que a política de eviction do cache altere a coordenação do limite externo. Em produção, a quota ainda exige persistência/HA e análise de comportamento durante failover; Redis de nó único no Compose não garante cota entre falhas.

### Status da evidência

Estas decisões e valores configurados são escolhas de implementação para laboratório. Os testes incluídos ainda precisam ser executados; não registramos resultado de `mvn test`, build de imagem, Compose ponta a ponta ou teste de carga. Os números de escala descritos nas seções anteriores permanecem hipóteses/contas do AEF, não medições da implementação desta data. O tempo real de trabalho também deve ser preenchido após a execução.
