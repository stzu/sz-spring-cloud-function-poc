package zumkeller.me.poc.springcloudfunctionlambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.STS;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.test.FunctionalSpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@FunctionalSpringBootTest
@Testcontainers
class SpringCloudTranslateAppTests {

    private static final String QUEUE_NAME = "ccts-response-queue.fifo";
    private static SqsClient testSqsClient;
    @Autowired
    Consumer<SQSEvent> handler;

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3")).withServices(SQS, STS);

    @DynamicPropertySource
    static void awsProperties(final DynamicPropertyRegistry registry) {
        registry.add("sqs.endpoint", () -> LOCALSTACK.getEndpointOverride(SQS));
        registry.add("target-queue-url",
                () -> "%s/000000000000/%s".formatted(LOCALSTACK.getEndpointOverride(SQS), QUEUE_NAME));
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    }

    @BeforeAll
    static void beforeEach() {
        testSqsClient = SqsClient.builder().credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.builder().accessKeyId(LOCALSTACK.getAccessKey())
                                .secretAccessKey(LOCALSTACK.getSecretKey()).build()))
                .endpointOverride(LOCALSTACK.getEndpointOverride(SQS)).region(Region.EU_CENTRAL_1).build();
        testSqsClient.createQueue(CreateQueueRequest.builder().queueName(QUEUE_NAME).attributes(
                        Map.of(QueueAttributeName.FIFO_QUEUE, "true", QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"))
                .build());
    }

    @AfterAll
    static void afterAll() throws IOException, InterruptedException {
        LOCALSTACK.execInContainer("awslocal", "sqs", "delete-queue", "--region", "eu-central-1", "--queue-name",
                QUEUE_NAME);
    }

    @AfterEach
    void afterEach() {
        testSqsClient.purgeQueue(PurgeQueueRequest.builder()
                .queueUrl("%s/000000000000/%s".formatted(LOCALSTACK.getEndpointOverride(SQS), QUEUE_NAME)).build());
    }

    @Test
    void handleSqsEvent() {
        handler.accept(newSqsEvent());
        final var messageResponse = Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> testSqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl("%s/000000000000/%s".formatted(LOCALSTACK.getEndpointOverride(SQS), QUEUE_NAME))
                        .build()), ReceiveMessageResponse::hasMessages);
        assertThat(messageResponse.messages()).hasSize(1);
        assertThat(messageResponse.messages().get(0).body()).endsWith("Hello world");
    }

    private SQSEvent newSqsEvent() {

        final SQSEvent.SQSMessage sqsMessage = new SQSEvent.SQSMessage();
        sqsMessage.setBody("Hallo Welt");

        final var event = new SQSEvent();
        event.setRecords(List.of(sqsMessage));

        return event;
    }
}
