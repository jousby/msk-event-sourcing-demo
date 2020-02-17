package software.amazon.samples;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class EventSourcingPipelineApp {
    public static void main(final String[] args) {
        App app = new App();

        new EventSourcingPipelineStack(app, "EventSourcingPipelineStack",
            StackProps.builder()
                .env(Environment.builder()
                    .region("ap-southeast-2")
                    .build())
                .build());

        app.synth();
    }
}
