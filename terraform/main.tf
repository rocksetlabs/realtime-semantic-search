# PROVIDERS
# --------------------
terraform {
    required_providers {
        confluent = {
            source = "confluentinc/confluent"
            version = "1.51.0"
        }
    }
}
# RANDOM IDS
# --------------------
resource "random_id" "confluent" {
    byte_length = 4
}
# VARS
# --------------------
variable "example_name" {
    type = string
}
variable "aws_region" {
    type = string
    default = "us-east-2"
}
# ENV
# --------------------
resource "confluent_environment" "main" {
    display_name = var.example_name
}
# TF MANAGER
# --------------------
resource "confluent_service_account" "app_manager" {
    display_name = "app-manager-${random_id.confluent.hex}"
    description = "app-manager for '${var.example_name}'"
}
resource "confluent_role_binding" "app_manager_env_admin" {
    principal = "User:${confluent_service_account.app_manager.id}"
    role_name = "EnvironmentAdmin"
    crn_pattern = confluent_environment.main.resource_name
}
resource "confluent_api_key" "app_manager_kafka" {
    display_name = "app-manager-kafka-${random_id.confluent.hex}"
    description = "app-manager-kafka-${random_id.confluent.hex}"
    owner {
        id = confluent_service_account.app_manager.id
        api_version = confluent_service_account.app_manager.api_version
        kind = confluent_service_account.app_manager.kind
    }
    managed_resource {
        id = confluent_kafka_cluster.main.id
        api_version = confluent_kafka_cluster.main.api_version
        kind = confluent_kafka_cluster.main.kind
        environment {
            id = confluent_environment.main.id
        }
    }
    depends_on = [
        confluent_service_account.app_manager,
        confluent_role_binding.app_manager_env_admin
    ]
}
# KAFKA
# --------------------
resource "confluent_kafka_cluster" "main" {
    display_name = "kafka-${var.example_name}"
    availability = "SINGLE_ZONE"
    cloud = "AWS"
    region = var.aws_region
    basic {}
    environment {
        id = confluent_environment.main.id 
    }
}
# KAFKA CLIENTS
# --------------------
resource "confluent_service_account" "clients" {
    display_name = "clients-${random_id.confluent.hex}"
    description = "Service account for clients"
}
resource "confluent_role_binding" "clients_kafka" {
    principal = "User:${confluent_service_account.clients.id}"
    role_name = "CloudClusterAdmin"
    crn_pattern = confluent_kafka_cluster.main.rbac_crn
}
resource "confluent_api_key" "clients_kafka" {
    display_name = "clients-kafka-${random_id.confluent.hex}"
    description = "clients-kafka-${random_id.confluent.hex}"
    owner {
        id = confluent_service_account.clients.id
        api_version = confluent_service_account.clients.api_version
        kind = confluent_service_account.clients.kind
    }
    managed_resource {
        id = confluent_kafka_cluster.main.id
        api_version = confluent_kafka_cluster.main.api_version
        kind = confluent_kafka_cluster.main.kind
        environment {
            id = confluent_environment.main.id 
        }
    }
    depends_on = [
        confluent_role_binding.clients_kafka
    ]
}
# TOPICS
# --------------------
resource "confluent_kafka_topic" "products_metadata" {
    topic_name = "products.metadata"
    rest_endpoint = confluent_kafka_cluster.main.rest_endpoint
    kafka_cluster {
        id = confluent_kafka_cluster.main.id
    }
    credentials {
        key = confluent_api_key.app_manager_kafka.id
        secret = confluent_api_key.app_manager_kafka.secret
    }
}
resource "confluent_kafka_topic" "products_embeddings" {
    topic_name = "products.embeddings"
    rest_endpoint = confluent_kafka_cluster.main.rest_endpoint
    kafka_cluster {
        id = confluent_kafka_cluster.main.id
    }
    credentials {
        key = confluent_api_key.app_manager_kafka.id
        secret = confluent_api_key.app_manager_kafka.secret
    }
}
# PROPS FILE OUTPUT
# --------------------
resource "local_file" "client_props" {
    filename = "../client.properties"
    content = <<-EOT
    bootstrap.servers=${substr(confluent_kafka_cluster.main.bootstrap_endpoint,11,-1)}
    security.protocol=SASL_SSL
    sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule   required username="${confluent_api_key.clients_kafka.id}"   password="${confluent_api_key.clients_kafka.secret}";
    sasl.mechanism=PLAIN
    client.dns.lookup=use_all_dns_ips
    EOT
}

resource "local_file" "rockset_props" {
    filename = "../rockset.properties"
    content = <<-EOT
    bootstrap.servers=${substr(confluent_kafka_cluster.main.bootstrap_endpoint,11,-1)}
    security.protocol=SASL_SSL
    sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule   required username="${confluent_api_key.clients_kafka.id}"   password="${confluent_api_key.clients_kafka.secret}";
    sasl.mechanism=PLAIN
    client.dns.lookup=use_all_dns_ips
    EOT
}