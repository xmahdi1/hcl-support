variable "image" {
  type    = string
  default = "nginx:stable"
}

job "web" {
  datacenters = ["dc1"]
  type        = "service"

  group "web" {
    count = 2

    network {
      port "http" {
        to = 80
      }
    }

    task "nginx" {
      driver = "docker"

      config {
        image = var.image
        ports = ["http"]
      }

      resources {
        cpu    = 100
        memory = 128
      }

      service {
        name     = "web"
        port     = "http"
        provider = "nomad"

        check {
          type     = "http"
          path     = "/"
          interval = "10s"
          timeout  = "2s"
        }
      }
    }
  }
}
