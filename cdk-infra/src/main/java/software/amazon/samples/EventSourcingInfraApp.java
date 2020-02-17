package software.amazon.samples;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class EventSourcingInfraApp {

    public static void main(final String[] args) {
        App app = new App();

        new EventSourcingInfraStack(app, "EventSourcingInfraStack",
            StackProps.builder()
                .env(Environment.builder()
                    .region("ap-southeast-2") // triggers vpc to create a 3 az instead of 2 az setup
                    .build())
                .build());

        app.synth();
    }
}
