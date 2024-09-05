// Lambda function
resource "aws_lambda_function" "poc_lambda_function" {
  function_name                  = "stephanz-poc-translator"
  filename                       = "../target/sz-spring-cloud-function-poc-0.0.1-SNAPSHOT-aws.jar"
  source_code_hash = filebase64sha256("../target/sz-spring-cloud-function-poc-0.0.1-SNAPSHOT-aws.jar")
  runtime                        = "java17"
  handler                        = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  role                           = aws_iam_role.poc_lambda_role.arn
  description                    = "PoC for Lambda that subscribes on SQS queue"
  reserved_concurrent_executions = 1
  timeout                        = 15

  environment {
    variables = {
      TARGET_QUEUE_URL = aws_sqs_queue.output_queue.url
    }
  }
}

// SQS queue on which the lambda writes the translations
resource "aws_sqs_queue" "output_queue" {
  name                        = "stephanz-poc-output-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
}

// SQS queue that triggers lambda
resource "aws_sqs_queue" "input_queue" {
  name                        = "stephanz-poc-input-queue.fifo"
  fifo_queue                  = true
  content_based_deduplication = true
}

// Event source mapping to trigger Lambda on SQS messages
resource "aws_lambda_event_source_mapping" "sqs_to_lambda" {
  event_source_arn = aws_sqs_queue.input_queue.arn
  function_name    = aws_lambda_function.poc_lambda_function.arn
}

// IAM Role for the Lambda function to grant access to publish on SQS
data "aws_iam_policy" "amazon_sqs_full_access" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}
data "aws_iam_policy" "aws_lambda_basic_execution" {
  arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
resource "aws_iam_role_policy_attachment" "amazon_sqs_full_access_attachment" {
  role       = aws_iam_role.poc_lambda_role.name
  policy_arn = data.aws_iam_policy.amazon_sqs_full_access.arn
}
resource "aws_iam_role_policy_attachment" "amazon_basic_exec_role_attachment" {
  role       = aws_iam_role.poc_lambda_role.name
  policy_arn = data.aws_iam_policy.aws_lambda_basic_execution.arn
}
data "aws_iam_policy_document" "poc_lambda_assume_role_policy" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}
resource "aws_iam_role" "poc_lambda_role" {
  name               = "stephanz-poc-role"
  assume_role_policy = data.aws_iam_policy_document.poc_lambda_assume_role_policy.json
}
