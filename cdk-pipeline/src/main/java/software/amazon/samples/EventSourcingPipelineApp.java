package software.amazon.samples;

import software.amazon.awscdk.core.App;

public class EventSourcingPipelineApp {
    public static void main(final String[] args) {
        App app = new App();

        new EventSourcingPipelineStack(app, "EventSourcingPipelineStack");

        app.synth();
    }
}
