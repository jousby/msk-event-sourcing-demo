package software.amazon.samples;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class EventSourcingInfraApp {

    private static Environment getEnv() {
        // are we running in CodeBuild?
        // If so passing in the account and region to cdk helps contextualise VPC generation (this is clunky in
        // my opinion).
        String codeBuildARN = System.getenv("CODEBUILD_BUILD_ARN");
        if (codeBuildARN != null) {
            // expected format: arn:aws:codebuild:region-ID:account-ID:build/codebuild-demo-project:b1e6661e-e4f2-4156-9ab9-82a19EXAMPLE
            String[] tokens = codeBuildARN.split(":");
            String regionId = tokens[3];
            String accountId = tokens[4];

            return Environment.builder()
                .account(accountId)
                .region(regionId)
                .build();
        }

        return Environment.builder().build();
    }

    public static void main(final String[] args) {
        App app = new App();

        new EventSourcingInfraStack(app, "EventSourcingInfraStack",
            StackProps.builder()
                .env(getEnv())
                .build());

        app.synth();
    }
}
