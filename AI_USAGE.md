# AI_USAGE — diário transparente de colaboração com IA

## Aula 1: fundação
**Ferramenta:** ChatGPT.
**Solicitação:** criar estrutura inicial de monorepo com Java 21/Spring Boot, Next.js, Docker, Kafka, PostgreSQL, observabilidade, CI/CD, Kubernetes e arquivos iniciais AWS, sem classes de negócio.
**Gerado:** configurações, projetos Maven vazios, página estática Next.js, mock oficial copiado e documentação.
**Correção humana solicitada:** retirar classes Java e páginas de negócio geradas prematuramente; manter somente a fundação.
**Validação pendente:** executar Compose, Maven validate, build do frontend e inspecionar serviços no computador do estudante.

## Como manter o diário
Em cada aula, registrar: prompts realmente usados, trechos aceitos, trechos rejeitados, bugs identificados, correções próprias e comandos executados com seus resultados.
**Não simular nem inventar histórico de autoria ou de validação.** O desafio permite IA, mas exige entendimento e avaliação crítica do resultado.

## Revisão da fundação — AWS Emulator
**Solicitação do estudante:** confirmar monorepo com microsserviços independentes, incorporar AWS Emulator conforme documentação oficial e atualizar Aula 1 sem gerar classes Java.
**Mudanças geradas:** Dockerfile do emulador, scripts PowerShell, documentação e aula atualizada.
**Validação local:** pendente no computador do estudante; registrar os resultados efetivos após execução.


## Preparação do pacote de infraestrutura e frontend (assistência de IA)
- A IA preparou o scaffold da infraestrutura e uma interface demonstrativa Next.js.
- Foram ajustadas as portas externas para evitar 8080 no Windows e 5432 no host PostgreSQL.
- Nenhuma classe Java de negócio foi gerada.
- A validação do frontend e dos containers deve ser registrada somente após execução real.
- O frontend utiliza dados locais simulados e não deve ser confundido com uma integração backend concluída.

## Implementação das soluções simples e Enterprise — 2026-11-22

**Ferramenta:** ChatGPT/Codex.

**Solicitação resumida:** inspecionar o repositório disponibilizado, completar o backend Enterprise que já estava em scaffold e criar uma implementação pequena e separada, documentando requisitos, arquitetura, ambiguidades, falhas, dimensionamento e trade-offs.

**Código/documentação gerados nesta sessão:** solução Spring Boot síncrona em `solutions/simple`; classes Ads Command, Transactional Outbox, worker de moderação com quota Redis, consumidor de projeção OpenSearch/cache Redis, configuração local e documentos em `docs/`, `DECISIONS.md` e `README.md`. O evento enviado à moderação foi mantido sem imagens, conforme decisão de produto registrada; o mock só recebe os campos de texto documentados.

**Revisão humana pendente:** conferir cada classe, os contratos HTTP/eventos, compatibilidade de dependências e a configuração do ambiente local. O usuário ainda deve entender e validar o código antes de apresentar como próprio.

**Validação desta sessão:** não executamos `mvn test`, compilação Maven, Compose, testes ponta a ponta nem benchmark. Os testes escritos são código entregue, não evidência de execução. Nenhum resultado de build, latência, throughput, heap ou quota foi inventado.

**Decisões corrigíveis após revisão:** registrar neste arquivo qualquer trecho rejeitado, correção feita pelo usuário, saída real de testes e tempo efetivamente gasto. Não atribuir ao usuário revisão que ainda não aconteceu.
