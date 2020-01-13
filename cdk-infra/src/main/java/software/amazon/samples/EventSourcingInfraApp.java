package software.amazon.samples;

import software.amazon.awscdk.core.App;

public class EventSourcingInfraApp {
    public static void main(final String[] args) {
        App app = new App();

        new EventSourcingInfraStack(app, "EventSourcingInfraStack");

        app.synth();
    }
}
