package software.amazon.samples;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

public class EventSourcingInfraStack extends Stack {
    public EventSourcingInfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EventSourcingInfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here
    }
}
