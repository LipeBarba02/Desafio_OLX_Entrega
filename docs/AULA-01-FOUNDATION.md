# Aula 1 — Foundation, microsserviços independentes e AWS Emulator

**Formação:** Senior / Tech Lead / Architect  
**Objetivo:** configurar e verificar a plataforma local sem implementar classes Java ou regras de negócio.  
**Pré-requisitos:** Windows + Docker Desktop, PowerShell, Git Bash, Java 21, Maven 3.9+, Node.js 22 e npm; AWS CLI v2 para testes do emulador.  
**Tempo estimado:** 3–5 horas, sujeito a downloads e recursos da máquina.

## 1. Conceito: monorepo não é monólito

Monorepo = organização de código em um Git. Microsserviços = unidades de execução/deploy autônomas, com contratos e dados sob responsabilidade própria. O laboratório adota **monorepo com microsserviços independentes**.

O repositório inclui:
- `services/ads-service`: projeto Maven Spring Boot Java 21, sem classes;
- `services/favorites-service`: projeto Maven Spring Boot Java 21, sem classes;
- `services/api-gateway`: projeto Maven Spring Cloud Gateway, sem classes;
- `apps/web`: Next.js + TypeScript com tela estática;
- `platform/`: Docker Compose, PostgreSQL, Kafka, mock OLX, Prometheus, Grafana, kind, Helm, Terraform e AWS Emulator;
- `.github/workflows`: pipelines iniciais;
- `docs/`: decisões e roteiro.

**Ainda não há APIs Java executáveis.** Cada serviço terá futuramente seu próprio build, banco, container, health checks e deploy.

## 2. Diagnóstico de ferramentas

No Git Bash:
```bash
java -version
mvn -version
docker --version
docker compose version
git --version
node --version
npm --version
```

Confirme Java 21 também no `mvn -version`.

## 3. Infraestrutura local

Na raiz do repositório:
```bash
docker compose config --quiet
docker compose up -d --build postgres kafka kafka-ui moderation prometheus grafana
docker compose ps
```

Não execute `docker compose --profile apps up` nesta aula: os serviços Java ainda não têm classes `@SpringBootApplication`.

### PostgreSQL
```bash
docker compose exec postgres psql -U olx -d postgres -c '\l'
```
Confira os bancos `ads` e `favorites`.

### Kafka
Abra http://localhost:8085 e localize o cluster `local`.

### Mock OLX
```bash
curl http://localhost:8787/health
curl -X POST http://localhost:8787/v1/moderation/check \
  -H 'Content-Type: application/json' \
  -d '{"title":"Apartamento amplo no centro","description":"Apartamento com dois quartos","price":350000,"category":"imoveis"}'
```
O mock simula latência e falhas; resposta aprovada não é garantida em todas as chamadas.

### Observabilidade
- Prometheus: http://localhost:9090/targets
- Grafana: http://localhost:3000 (admin / admin_local_only)

Os targets dos serviços Java estarão DOWN enquanto não houver aplicações executando. Isso é esperado.

## 4. Validar projetos

```bash
mvn -f services/pom.xml validate
cd apps/web
npm install
npm run build
npm run dev -- -p 3001
```
Abra http://localhost:3001. Não há páginas de anúncios ainda.

## 5. AWS Emulator — prática guiada

Documentação oficial:
https://docs.aws.amazon.com/emulator/latest/userguide/getting-started.html

A AWS recomenda Docker no Windows. Não é necessário criar conta AWS para usar o emulador. A ferramenta não simula necessariamente todos os recursos da nuvem.

### Passo A — iniciar no PowerShell

Abra PowerShell na raiz do repositório e execute:
```powershell
powershell -ExecutionPolicy Bypass -File platform/aws-emulator/start-windows.ps1
```
O script cria a imagem oficial localmente, monta a pasta de credenciais e certificados e expõe somente `127.0.0.1:9297`. Mantenha essa janela aberta.

### Passo B — apontar AWS CLI para o emulador

Abra **outro PowerShell** na raiz do projeto:
```powershell
. .\platform\aws-emulator\env-windows.ps1
curl.exe --cacert "$env:USERPROFILE\.aws\emulator\9297\ca.pem" https://127.0.0.1:9297/__admin/health
aws sts get-caller-identity
```

Resultados esperados:
- health: `ok`;
- conta AWS: `000000000000`;
- ARN: `arn:aws:iam::000000000000:user/emulator`.

**Segurança:** se a conta retornada não for `000000000000`, interrompa os testes. Não faça chamadas sem confirmar que a CLI está apontando para o emulador. Não use credenciais AWS reais.

### Passo C — entender a integração futura com Java

O emulador gera `ca.pem` e `truststore.p12` para HTTPS. O AWS SDK for Java 2.x não usa automaticamente `AWS_CA_BUNDLE`; a JVM exige truststore. A configuração de demonstração está em `platform/aws-emulator/README.md`.

**Não configure `JAVA_TOOL_OPTIONS` globalmente agora**: trocar o truststore padrão pode impedir conexões HTTPS legítimas. Na aula de AWS SDK construiremos uma solução segura para confiar no emulador e nas CAs normais.

O estado emulado é volátil e não deve ser tratado como banco persistente.

## 6. CI/CD, Kubernetes e AWS

- `ci.yml`: validação Compose/Maven e build Next.js; não executa testes Java nesta fase.
- `cd.yml`: fluxo manual de imagens preparado; somente será utilizado após criar aplicações Java.
- `platform/kubernetes/kind-config.yaml`: cluster local futuro.
- `platform/helm/olx`: chart inicial; ainda não representa deploy funcional.
- `platform/terraform/aws`: apenas configuração inicial do provider, **não cria recursos AWS**.
- AWS Emulator é **laboratório local opcional**, não substitui IAM real, EKS, RDS, redes e custos.

## 7. Documentar decisões e uso real da IA

Leia `DECISIONS.md` e `AI_USAGE.md`. Registre decisões, prompts, correções, comandos executados e resultados reais. Não invente evidências nem histórico.

## 8. Primeiro commit

```bash
git init
git branch -M main
git add .
git commit -m "chore: bootstrap microservices platform and aws emulator"
```

Associe um repositório GitHub privado de sua propriedade e faça push quando desejar.

## 9. Checklist

- [ ] Java 21 e Maven configurados.
- [ ] Docker Compose validado.
- [ ] PostgreSQL, Kafka, mock, Prometheus e Grafana iniciados.
- [ ] Bancos `ads` e `favorites` visíveis.
- [ ] Kafka UI acessível.
- [ ] Mock OLX respondendo.
- [ ] `mvn validate` sem erros.
- [ ] Frontend estático executado.
- [ ] AWS Emulator iniciado com Docker (opcional, recomendado).
- [ ] Health do AWS Emulator retorna `ok`.
- [ ] `aws sts get-caller-identity` retorna conta `000000000000`.
- [ ] `DECISIONS.md` e `AI_USAGE.md` revisados.
- [ ] Commit inicial criado.

## 10. Questões para defesa técnica

1. Por que monorepo não significa monólito?
2. Qual é a diferença entre infraestrutura configurada e serviço funcional?
3. Por que não habilitar CD para produção antes dos testes?
4. Por que validar o account ID ao usar o AWS Emulator?
5. Por que `truststore.p12` pode afetar chamadas HTTPS fora do emulador?
6. Quais custos operacionais os microsserviços introduzem em relação a um monólito modular?

**Próxima aula:** DDD, domínio de anúncios, regras oficiais do desafio, testes e primeiras classes Java — construídas incrementalmente com IA e revisão crítica.
