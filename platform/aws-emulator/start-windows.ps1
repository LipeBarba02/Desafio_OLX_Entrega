$ErrorActionPreference = "Stop"
$directory = Join-Path $env:USERPROFILE ".aws\emulator"
New-Item -ItemType Directory -Force $directory | Out-Null
docker build -t aws-emulator -f platform/aws-emulator/Dockerfile platform/aws-emulator
if ($LASTEXITCODE -ne 0) { throw "Falha ao construir imagem AWS Emulator." }
Write-Host "Iniciando AWS Emulator em https://127.0.0.1:9297 (Ctrl+C para parar)"
docker run --rm --memory=2g --memory-swap=2g `
  -p "127.0.0.1:9297:9297" `
  -v "${directory}:/home/emulator/.aws/emulator" `
  aws-emulator -p 9297
