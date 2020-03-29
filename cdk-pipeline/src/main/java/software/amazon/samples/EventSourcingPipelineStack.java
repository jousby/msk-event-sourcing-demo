package software.amazon.samples;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.codebuild.*;
import software.amazon.awscdk.services.codepipeline.*;
import software.amazon.awscdk.services.codepipeline.actions.*;
import 	software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecr.RepositoryProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;

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

        // Create the source action
        Artifact sourceArtifact = Artifact.artifact("SourceArtifact");
        GitHubSourceAction sourceAction = createSourceAction(conf, sourceArtifact);

        // Create the docker build actions
        CodeBuildAction readApiDockerBuildAction = createDockerBuildAction("read-api", conf, sourceArtifact);
        CodeBuildAction writeApiDockerBuildAction = createDockerBuildAction("write-api", conf, sourceArtifact);
        CodeBuildAction projectionsDockerBuildAction = createDockerBuildAction("projections", conf, sourceArtifact);

        // Create the cdk synth action
        Artifact cdkArtifact = Artifact.artifact("CdkArtifact");
        CodeBuildAction cdkBuildAction = createCDKBuildAction(conf, sourceArtifact, cdkArtifact);


        CloudFormationCreateReplaceChangeSetAction prepareChangesAction = new CloudFormationCreateReplaceChangeSetAction(
            CloudFormationCreateReplaceChangeSetActionProps.builder()
                .actionName("PrepareChanges")
                .stackName("EventSourcingInfraStack")
                .changeSetName("EventSourcingInfraStackChangeSet")
                .adminPermissions(true)
                .templatePath(cdkArtifact.atPath("cdk-infra/cdk.out/EventSourcingInfraStack.template.json"))
                .runOrder(1)
                .build()
        );

        CloudFormationExecuteChangeSetAction executeChangesAction = new CloudFormationExecuteChangeSetAction(
            CloudFormationExecuteChangeSetActionProps.builder()
                .actionName("ExecuteChanges")
                .stackName("EventSourcingInfraStack")
                .changeSetName("EventSourcingInfraStackChangeSet")
                .runOrder(2)
                .build()
        );

        // Assemble the pipeline
        Pipeline pipeline = new Pipeline(this, "EventSourcingPipeline", PipelineProps.builder()
            .stages(List.of(
                StageProps.builder()
                    .stageName("SourceStage")
                    .actions(List.of(sourceAction))
                    .build(),
                StageProps.builder()
                    .stageName("DockerBuildStage")
                    .actions(List.of(
                        readApiDockerBuildAction,
                        writeApiDockerBuildAction,
                        projectionsDockerBuildAction
                    ))
                    .build(),
                StageProps.builder()
                    .stageName("CdkSynthStage")
                    .actions(List.of(cdkBuildAction))
                    .build(),
                StageProps.builder()
                    .stageName("CfnDeployStage")
                    .actions(List.of(prepareChangesAction, executeChangesAction))
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
        String imageName,
        EventSourcingPipelineStackConfig conf,
        Artifact sourceArtifact) {

        // Create the ECR registry
        Repository repository = new Repository(this, "event-sourcing-" + imageName, RepositoryProps.builder()
            .repositoryName("event-sourcing-" + imageName)
            .build());

        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
            .buildImage(LinuxBuildImage.AMAZON_LINUX_2)
            .environmentVariables(Map.of(
                "REPOSITORY_URI", BuildEnvironmentVariable.builder().value(repository.getRepositoryUri()).build(),
                "IMAGE_TAG", BuildEnvironmentVariable.builder().value("latest").build()
            ))
            .privileged(true)
            .build();

        PipelineProject buildProject = new PipelineProject(this, imageName + "-PipelineProject",
            PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(BuildSpec.fromSourceFilename(imageName + "/buildspec.yml"))
                .cache(Cache.local(
                    LocalCacheMode.DOCKER_LAYER,
                    LocalCacheMode.SOURCE,
                    LocalCacheMode.CUSTOM
                ))
                .build());

        buildProject.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        repository.grantPullPush(buildProject);

        return new CodeBuildAction(CodeBuildActionProps.builder()
            .actionName(imageName + "dockerBuildAction")
            .input(sourceArtifact)
            .project(buildProject)
            .build());
    }

    private CodeBuildAction createCDKBuildAction(
        EventSourcingPipelineStackConfig conf,
        Artifact sourceArtifact,
        Artifact cdkArtifact) {

        // Leverage a custom docker image that has a specific build toolchain
        BuildEnvironment buildEnvironment = BuildEnvironment.builder()
            .buildImage(LinuxBuildImage.AMAZON_LINUX_2)
            .build();

        PipelineProject buildProject = new PipelineProject(this, "PipelineProject",
            PipelineProjectProps.builder()
                .environment(buildEnvironment)
                .buildSpec(BuildSpec.fromSourceFilename("cdk-infra/buildspec.yml"))
                .cache(Cache.local(
                    LocalCacheMode.DOCKER_LAYER,
                    LocalCacheMode.SOURCE,
                    LocalCacheMode.CUSTOM
                ))
                .build());

        // Adding AdministratorAccess allows the build agent to provision any type of resources but if looking to use
        // in a production context you would want to tighten this up with a custom policy that only allows the build
        // agent to provision those resources that you use in your environment stack.
        buildProject.getRole()
            .addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("AdministratorAccess"));

        return new CodeBuildAction(CodeBuildActionProps.builder()
            .actionName("CdkBuildAction")
            .input(sourceArtifact)
            .project(buildProject)
            .outputs(List.of(cdkArtifact))
            .build());
    }


    private class EventSourcingPipelineStackConfig {
        public final String githubOwner;
        public final String githubRepo;
        public final String githubBranch;
        public final String githubSecretId;
        public final String githubSecretJsonField;

        public EventSourcingPipelineStackConfig(Construct scope) {
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
