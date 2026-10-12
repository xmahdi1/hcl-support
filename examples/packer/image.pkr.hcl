packer {
  required_plugins {
    amazon = {
      source  = "github.com/hashicorp/amazon"
      version = ">= 1.3.0"
    }
  }
}

variable "region" {
  type        = string
  default     = "eu-central-1"
  description = "Region for the build instance."
}

locals {
  image_name = "example-${formatdate("YYYYMMDDhhmmss", timestamp())}"
}

source "amazon-ebs" "ubuntu" {
  region        = var.region
  ami_name      = local.image_name
  instance_type = "t3.small"
  ssh_username  = "ubuntu"

  source_ami_filter {
    filters = {
      name                = "ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    owners      = ["099720109477"]
    most_recent = true
  }
}

build {
  sources = ["source.amazon-ebs.ubuntu"]

  provisioner "shell" {
    inline = ["echo Building an image"]
  }

  post-processor "manifest" {
    output = "manifest.json"
  }
}
