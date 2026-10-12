datacenter = "dc1"
node_name  = "consul-example"
data_dir   = "/var/lib/consul"
server     = false
retry_join = ["consul-1.internal"]

acl {
  enabled                  = true
  default_policy           = "deny"
  enable_token_persistence = true
}

connect {
  enabled = true
}

ports {
  http  = 8500
  https = 8501
}

tls {
  defaults {
    ca_file         = "/etc/consul/ca.pem"
    cert_file       = "/etc/consul/agent.pem"
    key_file        = "/etc/consul/agent-key.pem"
    verify_incoming = true
    verify_outgoing = true
  }
}
