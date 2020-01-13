package software.amazon.samples;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildAction;
import software.amazon.awscdk.services.codepipeline.actions.CodeBuildActionProps;
import software.amazon.awscdk.services.codepipeline.actions.GitHubSourceAction;
import software.amazon.awscdk.services.codepipeline.actions.GitHubSourceActionProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;

import java.util.List;
import java.util.Optional;

public class EventSourcingPipelineStack extends Stack {

    public EventSourcingPipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EventSourcingPipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Build a configuration object from context injected from values stored in 'cdk.json' or via cdk cli
        EventSourcingPipelineStackConfig conf = new EventSourcingPipelineStackConfig(scope);

        IBucket regionalArtifactCache = Bucket.fromBucketName(this, "artifactCache", conf.regionalArtifactCacheBucket);

        SecretValue githubToken = SecretValue.secretsManager(conf.githubSecretId, SecretsManagerSecretOptions.builder()
            .jsonField(conf.githubSecretJsonField)
            .build());

        // Create a pipeline
        Pipeline pipeline = new Pipeline(this, "EventSourcingPipeline", PipelineProps.builder().build());

        pipeline.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        Artifact sourceArtifact = Artifact.artifact("SourceArtifact");

        // Add a source stage that retrieves our source from github on each commit to a specific branch
        GitHubSourceAction sourceAction = new GitHubSourceAction(GitHubSourceActionProps.builder()
            .actionName("GithubSourceAction")
            .owner(conf.githubOwner)
            .repo(conf.githubRepo)
            .oauthToken(githubToken)
            .branch(conf.githubBranch)
            .output(sourceArtifact)
            .build());

        pipeline.addStage(StageOptions.builder()
            .stageName("SourceStage")
            .actions(List.of(sourceAction))
            .build());

        // Add a build stage that takes the source from the previous stage and runs the build commands in our
        // buildspec.yml file (located in the base of this project).

        // Leverage a custom docker image that has a specific build toolchain
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
            .buildImage(LinuxBuildImage.fromDockerRegistry(conf.dockerBuildEnvImage))
            .build();

        PipelineProject buildProject = new PipelineProject(this, "PipelineProject",
            PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(BuildSpec.fromSourceFilename("buildspec.yml"))
                .cache(Cache.bucket(regionalArtifactCache))
                .build());

        // Adding AdministratorAccess allows the build agent to provision any type of resources but if looking to use
        // in a production context you would want to tighten this up with a custom policy that only allows the build
        // agent to provision those resources that you use in your environment stack.
        buildProject.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        CodeBuildAction buildAction = new CodeBuildAction(CodeBuildActionProps.builder()
            .actionName("PipelineBuildAction")
            .input(sourceArtifact)
            .project(buildProject)
            .build());

        pipeline.addStage(StageOptions.builder()
            .stageName("BuildStage")
            .actions(List.of(buildAction))
            .build());
    }


    private class EventSourcingPipelineStackConfig {
        public final String regionalArtifactCacheBucket;
        public final String dockerBuildEnvImage;
        public final String githubOwner;
        public final String githubRepo;
        public final String githubBranch;
        public final String githubSecretId;
        public final String githubSecretJsonField;

        public EventSourcingPipelineStackConfig(Construct scope) {
            regionalArtifactCacheBucket = getUnsafeValue(scope, "regionalArtifactCacheBucketName");
            dockerBuildEnvImage = getUnsafeValue(scope, "dockerBuildEnvImage");
            githubOwner = getUnsafeValue(scope, "githubOwner");
            githubRepo = getUnsafeValue(scope, "githubRepo");
            githubBranch = getUnsafeValue(scope, "githubBranch");
            githubSecretId = getUnsafeValue(scope, "githubSecretId");
            githubSecretJsonField = getUnsafeValue(scope, "githubSecretJsonField");
        }

        private String getUnsafeValue(Construct scope, String key) {
            Optional<Object> optValue = Optional.ofNullable(
                scope.getNode().tryGetContext(key)
            );

            return optValue
                .map(v -> v.toString())
                .orElseThrow();
        }
    }
}
