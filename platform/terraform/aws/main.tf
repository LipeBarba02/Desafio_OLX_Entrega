# Fundação de IaC: recursos EKS, ECR, RDS e S3 serão adicionados
# nas aulas de AWS após estimativa de custos e configuração de IAM.
# Não executar terraform apply esperando uma plataforma pronta.
locals {
  tags = {
    Project = var.project_name
    Environment = "learning"
    ManagedBy = "terraform"
  }
}
