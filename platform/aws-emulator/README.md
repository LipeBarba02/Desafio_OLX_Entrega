# AWS Emulator — laboratório opcional, Windows + Docker

Referências oficiais:
- https://docs.aws.amazon.com/emulator/latest/userguide/getting-started.html
- https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html
- https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html

## Iniciar (PowerShell na raiz do repositório)

```powershell
powershell -ExecutionPolicy Bypass -File platform/aws-emulator/start-windows.ps1
```

O script usa o Dockerfile do tutorial oficial (arquitetura x86_64). Para computadores ARM, substituir a URL do RPM por `aws-emulator-aarch64.rpm`.

O emulador é exposto **somente em 127.0.0.1:9297**. O diretório `%USERPROFILE%\.aws\emulator` recebe credenciais e certificados temporários. Não o inclua no Git.

## Verificar (OUTRA janela PowerShell, na raiz do projeto)

```powershell
. .\platform\aws-emulator\env-windows.ps1
curl.exe --cacert "$env:USERPROFILE\.aws\emulator\9297\ca.pem" https://127.0.0.1:9297/__admin/health
aws sts get-caller-identity
```

Esperado: health `ok`; conta `000000000000`. **Se a conta não for 000000000000, pare: a CLI está apontando para a AWS real.**

As variáveis são locais à sessão. Evite rodar testes do emulador com credenciais reais configuradas.

## Java / Spring Boot (aula posterior)

O SDK Java 2.x precisa confiar no certificado local:
```powershell
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStore=$env:USERPROFILE\.aws\emulator\9297\truststore.p12 -Djavax.net.ssl.trustStoreType=PKCS12"
```

**Cuidado:** esse truststore substitui os certificados padrão da JVM e pode quebrar HTTPS para outros destinos. Na aula de integração criaremos uma configuração de confiança combinada e isolada. Não ativar globalmente nesta fundação.

O emulador não substitui EKS, RDS ou AWS real. Conferir suporte a cada API antes de criar exercícios. Estado emulado é volátil e se perde ao parar o processo.
