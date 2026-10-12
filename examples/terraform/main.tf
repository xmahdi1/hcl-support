terraform {
  required_version = ">= 1.5.0"
}

variable "environment" {
  type        = string
  default     = "dev"
  description = "Deployment environment."

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Choose dev, staging, or prod."
  }
}

locals {
  name = "example-${var.environment}"
}

output "name" {
  value       = local.name
  description = "Environment-specific name."
}
