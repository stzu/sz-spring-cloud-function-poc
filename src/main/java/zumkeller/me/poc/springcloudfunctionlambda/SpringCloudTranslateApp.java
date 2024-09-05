package zumkeller.me.poc.springcloudfunctionlambda;

import java.net.URI;
import java.util.Date;
import java.util.function.Consumer;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SpringBootApplication
public class SpringCloudTranslateApp {

    public static void main(final String[] args) {
        SpringApplication.run(SpringCloudTranslateApp.class, args);
    }

    @Value("${target-queue-url:}")
    String queueUrl;

    @Bean
    public SqsClient sqsClient(@Value("${sqs.endpoint:}") final String sqsEndpoint) {
        final var builder = SqsClient.builder().region(Region.EU_CENTRAL_1);
        if (!sqsEndpoint.isBlank()) { // only for @SpringBootTest
            builder.endpointOverride(URI.create(sqsEndpoint));
        }
        return builder.build();
    }

    @Bean
    public Consumer<SQSEvent> processSqsEvent(final SqsClient sqsClient) {
        return (sqsEvent) -> sqsEvent.getRecords().forEach(record -> sendTranslation(record.getBody(), sqsClient));
    }

    private void sendTranslation(final String input, final SqsClient sqsClient) {
        System.out.printf("Translating %s%n", input);
        final var translated = "Hallo Welt".equals(input) ? "Hello world" : "input is not in this dictionary";
        sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl)
                .messageBody("%s: %s".formatted(new Date().toString(), translated)).messageGroupId("sz-poc").build());
    }
}
