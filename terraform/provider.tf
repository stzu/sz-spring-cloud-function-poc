terraform {
  required_version = "~> 1.5.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">=5.0.0, <6.0.0"
    }
  }
  backend "local" {
  }
}

provider "aws" {
  profile = "appd-dev-emea"
  region  = "eu-central-1"
}