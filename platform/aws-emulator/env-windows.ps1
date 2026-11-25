$ErrorActionPreference = "Stop"
$folder = Join-Path $env:USERPROFILE ".aws\emulator\9297"
if (-not (Test-Path (Join-Path $folder "ca.pem"))) {
  throw "Certificado ausente. Inicie o AWS Emulator primeiro."
}
Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_SESSION_TOKEN, Env:AWS_PROFILE -ErrorAction SilentlyContinue
$env:AWS_ENDPOINT_URL = "https://127.0.0.1:9297"
$env:AWS_SHARED_CREDENTIALS_FILE = Join-Path $folder "emulator_credentials"
$env:AWS_CONFIG_FILE = Join-Path $folder "config"
$env:AWS_CA_BUNDLE = Join-Path $folder "ca.pem"
$env:AWS_EC2_METADATA_DISABLED = "true"
$env:AWS_DISABLE_HOST_PREFIX_INJECTION = "true"
$env:AWS_REGION = "us-east-1"
$env:AWS_DEFAULT_REGION = "us-east-1"
Write-Host "AWS CLI configurado para o emulador nesta sessao PowerShell."
Write-Host "Cheque a conta: aws sts get-caller-identity (deve retornar 000000000000)."
