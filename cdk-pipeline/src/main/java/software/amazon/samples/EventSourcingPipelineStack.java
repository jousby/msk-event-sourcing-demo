package software.amazon.samples;

import org.jetbrains.annotations.NotNull;
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
import 	software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventSourcingPipelineStack extends Stack {

    public EventSourcingPipelineStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EventSourcingPipelineStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Build a configuration object from context injected from values stored in 'cdk.json' or via cdk cli
        EventSourcingPipelineStackConfig conf = new EventSourcingPipelineStackConfig(scope);

        // Import the pre existing regionalArtifactCache bucket
        // (used by code build to cache build dependencies/libraries between builds)
        IBucket regionalArtifactCache = Bucket.fromBucketName(this, "artifactCache", conf.regionalArtifactCacheBucket);

        // Create the source action
        Artifact sourceArtifact = Artifact.artifact("SourceArtifact");
        GitHubSourceAction sourceAction = createSourceAction(conf, sourceArtifact);

        // Create the docker build actions
        // CodeBuildAction dockerBuildAction = createDockerBuildAction(conf, regionalArtifactCache, sourceArtifact);

        // Create the cdk build and deploy action
        CodeBuildAction cdkBuildAction = createCDKBuildAction(conf, regionalArtifactCache, sourceArtifact);

        // Assemble the pipeline
        Pipeline pipeline = new Pipeline(this, "EventSourcingPipeline", PipelineProps.builder()
            .stages(List.of(
                StageProps.builder()
                    .stageName("SourceStage")
                    .actions(List.of(sourceAction))
                    .build(),
//                StageProps.builder()
//                    .stageName("DockerBuildStage")
//                    .actions(List.of(dockerBuildAction))
//                    .build(),
                StageProps.builder()
                    .stageName("CdkBuildStage")
                    .actions(List.of(cdkBuildAction))
                    .build()
            ))
            .build());

        pipeline.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

    }

    private GitHubSourceAction createSourceAction(EventSourcingPipelineStackConfig conf, Artifact sourceArtifact) {
        SecretValue githubToken = SecretValue.secretsManager(conf.githubSecretId, SecretsManagerSecretOptions.builder()
            .jsonField(conf.githubSecretJsonField)
            .build());

        // Add a source stage that retrieves our source from github on each commit to a specific branch
        return new GitHubSourceAction(GitHubSourceActionProps.builder()
            .actionName("GithubSourceAction")
            .owner(conf.githubOwner)
            .repo(conf.githubRepo)
            .oauthToken(githubToken)
            .branch(conf.githubBranch)
            .output(sourceArtifact)
            .build());
    }

    private CodeBuildAction createDockerBuildAction(
        EventSourcingPipelineStackConfig conf,
        IBucket regionalArtifactCache,
        Artifact sourceArtifact) {

        // Create the ECR registry
        Repository repository = new Repository(this, "event-sourcing-read-api", RepositoryProps.builder()
            .build());

        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
            .buildImage(LinuxBuildImage.AMAZON_LINUX_2)
            .environmentVariables(Map.of(
                "IMAGE_REPO_NAME", BuildEnvironmentVariable.builder().value(repository.getRepositoryName()).build(),
                "IMAGE_TAG", BuildEnvironmentVariable.builder().value("latest").build()
            ))
            .privileged(true)
            .build();

        PipelineProject buildProject = new PipelineProject(this, "ReadApiPipelineProject",
            PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(BuildSpec.fromSourceFilename("read-api/buildspec.yml"))
                .cache(Cache.bucket(regionalArtifactCache))
                .build());

        buildProject.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        return new CodeBuildAction(CodeBuildActionProps.builder()
            .actionName("dockerBuildAction")
            .input(sourceArtifact)
            .project(buildProject)
            .build());
    }

    private CodeBuildAction createCDKBuildAction(
        EventSourcingPipelineStackConfig conf,
        IBucket regionalArtifactCache,
        Artifact sourceArtifact) {

        // Leverage a custom docker image that has a specific build toolchain
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
            .buildImage(LinuxBuildImage.AMAZON_LINUX_2)
            .build();

        PipelineProject buildProject = new PipelineProject(this, "PipelineProject",
            PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(BuildSpec.fromSourceFilename("cdk-infra/buildspec.yml"))
                .cache(Cache.bucket(regionalArtifactCache))
                .build());

        // Adding AdministratorAccess allows the build agent to provision any type of resources but if looking to use
        // in a production context you would want to tighten this up with a custom policy that only allows the build
//        // agent to provision those resources that you use in your environment stack.
//        buildProject.getRole()
//            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        return new CodeBuildAction(CodeBuildActionProps.builder()
            .actionName("CdkBuildAction")
            .input(sourceArtifact)
            .project(buildProject)
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
