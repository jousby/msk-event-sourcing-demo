package software.amazon.samples;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateServiceProps;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticsearch.CfnDomain;
import software.amazon.awscdk.services.elasticsearch.CfnDomainProps;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.msk.CfnClusterProps;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EventSourcingInfraStack extends Stack {
    public EventSourcingInfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EventSourcingInfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Build a configuration object from context injected from values stored in 'cdk.json' or via cdk cli
        EventSourcingInfraStackConfig conf = new EventSourcingInfraStackConfig(scope);

        // Vpc setup
        Vpc vpc = new Vpc(this, "EventSourcingVPC", VpcProps.builder().build());

        // Kafka
        ISecurityGroup kafkaSecurityGroup = new SecurityGroup(this,
            "kafkaSecurityGroup", SecurityGroupProps.builder()
            .securityGroupName("eventSourcingKafkaSG")
            .vpc(vpc)
            .build());
        CfnCluster kafkaCluster = eventSourcingKafka(vpc, kafkaSecurityGroup, conf);
        String kafkaClusterArn = Token.asString(Fn.ref(kafkaCluster.getClusterName()));

        // Elasticsearch
        SecurityGroup esSecurityGroup = new SecurityGroup(this,
            "esSecurityGroup", SecurityGroupProps.builder()
            .securityGroupName("eventSourcingElasticsearchSG")
            .vpc(vpc)
            .build());
        CfnDomain esDomain = eventSourcingElasticsearch(vpc, esSecurityGroup);
        String esDomainEndpoint = esDomain.getAttrDomainEndpoint();

        // S3 webserver

        // 3 x ECS Fargate
        ApplicationLoadBalancedFargateService readApi = createReadApi(vpc, esDomainEndpoint);
        ApplicationLoadBalancedFargateService writeApi = createWriteApi(vpc, kafkaClusterArn);
        ApplicationLoadBalancedFargateService projections = createProjections(vpc, kafkaClusterArn, esDomainEndpoint);

        IRole readApiRole = readApi.getTaskDefinition().getTaskRole();
        IRole writeApiRole = writeApi.getTaskDefinition().getTaskRole();
        IRole projectionsRole = projections.getTaskDefinition().getTaskRole();

        ISecurityGroup readApiTaskSG = readApi.getService().getConnections().getSecurityGroups().get(0);
        ISecurityGroup projectionsTaskSG = projections.getService().getConnections().getSecurityGroups().get(0);
        ISecurityGroup writeApiTaskSG = writeApi.getService().getConnections().getSecurityGroups().get(0);

        // Allow read api and projections access the elasticsearch cluster
        esDomain.setAccessPolicies(createAccessPolicies(esDomain, readApiRole, projectionsRole));
        readApi.getService().getConnections().allowTo(esSecurityGroup, Port.tcp(443));
        projections.getService().getConnections().allowTo(esSecurityGroup, Port.tcp(443));
        esSecurityGroup.addIngressRule(readApiTaskSG, Port.tcp(443));
        esSecurityGroup.addIngressRule(projectionsTaskSG, Port.tcp(443));

        // Allow write api and projections access to the kafka cluster
        PolicyStatement kafkaPolicy = createKafkaPolicy(kafkaClusterArn);
        writeApiRole.addToPolicy(kafkaPolicy);
        projectionsRole.addToPolicy(kafkaPolicy);
        writeApi.getService().getConnections().allowTo(kafkaSecurityGroup, Port.tcp(9092));
        projections.getService().getConnections().allowTo(kafkaSecurityGroup, Port.tcp(9092));
        kafkaSecurityGroup.addIngressRule(writeApiTaskSG, Port.tcp(9092));
        kafkaSecurityGroup.addIngressRule(projectionsTaskSG, Port.tcp(9092));

        // Output
        CfnOutput output1 = new CfnOutput(this, "ElasticsearchDomainEndpoint", CfnOutputProps.builder()
            .value(esDomainEndpoint)
            .build());
        CfnOutput output2 = new CfnOutput(this, "KafkaClusterArn", CfnOutputProps.builder()
            .value(kafkaCluster.getClusterName())
            .value(Token.asString(Fn.ref(kafkaCluster.getClusterName())))
            .build());
        CfnOutput output3 = new CfnOutput(this, "ReadApiEndpoint", CfnOutputProps.builder()
            .value(readApi.getLoadBalancer().getLoadBalancerDnsName())
            .build());
    }

    private ApplicationLoadBalancedFargateService createReadApi(Vpc vpc, String esDomainEndpoint) {
        IRepository repo = Repository.fromRepositoryName(
            this,
            "readRepo",
            "event-sourcing-read-api"
        );
        return new ApplicationLoadBalancedFargateService(
            this,
            "readApi",
            ApplicationLoadBalancedFargateServiceProps.builder()
                .vpc(vpc)
                .desiredCount(1)
                .memoryLimitMiB(1024)
                .cpu(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                    .containerPort(4568)
                    .environment(Map.of(
                        "ELASTICSEARCH_URL", esDomainEndpoint
                    ))
                    .image(ContainerImage.fromEcrRepository(repo, "latest"))
                    .enableLogging(true)
                    .build())
                .listenerPort(80)
                .publicLoadBalancer(true)
                .build()
        );
    }
    private ApplicationLoadBalancedFargateService createWriteApi(Vpc vpc, String kafkaClustreArn) {
        IRepository repo = Repository.fromRepositoryName(
            this,
            "writeRepo",
            "event-sourcing-write-api"
        );
        return new ApplicationLoadBalancedFargateService(
            this,
            "writeApi",
            ApplicationLoadBalancedFargateServiceProps.builder()
                .vpc(vpc)
                .desiredCount(1)
                .memoryLimitMiB(1024)
                .cpu(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                    .containerPort(4567)
                    .environment(Map.of(
                        "KAFKA_CLUSTER_ARN", kafkaClustreArn
                    ))
                    .image(ContainerImage.fromEcrRepository(repo, "latest"))
                    .enableLogging(true)
                    .build())
                .listenerPort(80)
                .publicLoadBalancer(true)
                .build()
        );
    }

    private ApplicationLoadBalancedFargateService createProjections(Vpc vpc, String kafkaClustreArn, String esDomainEndpoint) {
        IRepository repo = Repository.fromRepositoryName(
            this,
            "projectionsRepo",
            "event-sourcing-projections"
        );
        return new ApplicationLoadBalancedFargateService(
            this,
            "projections",
            ApplicationLoadBalancedFargateServiceProps.builder()
                .vpc(vpc)
                .desiredCount(1)
                .memoryLimitMiB(1024)
                .cpu(512)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                    .containerPort(4567)
                    .environment(Map.of(
                        "KAFKA_CLUSTER_ARN", kafkaClustreArn,
                        "ELASTICSEARCH_URL", esDomainEndpoint
                    ))
                    .image(ContainerImage.fromEcrRepository(repo, "latest"))
                    .enableLogging(true)
                    .build())
                .listenerPort(80)
                .publicLoadBalancer(true)
                .build()
        );
    }

    private PolicyDocument createAccessPolicies(CfnDomain esDomain, IRole readApiRole, IRole projectionsRole) {

        PolicyStatement statement = new PolicyStatement(PolicyStatementProps.builder()
            .effect(Effect.ALLOW)
            .principals(List.of(readApiRole, projectionsRole))
            //.actions(List.of("es:ESHttp*"))
            .actions(List.of("es:*"))
            .resources(List.of(
                Token.asString(Fn.sub("arn:aws:es:${AWS::Region}:${AWS::AccountId}:domain/" + esDomain.getDomainName() + "/*"))
            ))
            .build());

        return new PolicyDocument(PolicyDocumentProps.builder()
            .statements(List.of(statement))
            .build());
    }

    private PolicyStatement createKafkaPolicy(String kafkaClusterArn) {
        return new PolicyStatement(PolicyStatementProps.builder()
            .effect(Effect.ALLOW)
            .actions(List.of(
                "kafka:Describe*",
                "kafka:Get*",
                "kafka:List*",
                "kafka:Update*"
            ))
            .resources(List.of(kafkaClusterArn))
            .build());
    }

    private CfnCluster eventSourcingKafka(Vpc vpc, ISecurityGroup kafkaSecurityGroup, EventSourcingInfraStackConfig conf) {
        return  new CfnCluster(this, "EventSourcingKafkaCluster",
            CfnClusterProps.builder()
                .clusterName("EventSourcingKafkaCluster")
                .configurationInfo(CfnCluster.ConfigurationInfoProperty.builder()
                    .arn(conf.kafkaClusterConfigARN)
                    .build())
                .kafkaVersion("2.3.1")
                .numberOfBrokerNodes(3)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                    .instanceType("kafka.m5.large")
                    .securityGroups(List.of(kafkaSecurityGroup.getSecurityGroupId()))
                    .storageInfo(CfnCluster.StorageInfoProperty.builder()
                        .ebsStorageInfo(CfnCluster.EBSStorageInfoProperty.builder()
                            .volumeSize(40)
                            .build())
                        .build())
                    .clientSubnets(vpc.getPrivateSubnets().stream().map(s -> s.getSubnetId()).collect(Collectors.toList()))
                    .build())
                .encryptionInfo(CfnCluster.EncryptionInfoProperty.builder()
                    .encryptionInTransit(CfnCluster.EncryptionInTransitProperty.builder()
                        .clientBroker("PLAINTEXT")
                        .build())
                    .build())
                .build());
    }

    private CfnDomain eventSourcingElasticsearch(Vpc vpc, ISecurityGroup esSecurityGroup) {
        String domainName = "eventsourcing";

        return new CfnDomain(this, "ElasticsearchCluster",
            CfnDomainProps.builder()
                .domainName(domainName)
                .elasticsearchVersion("7.1")
                .vpcOptions(CfnDomain.VPCOptionsProperty.builder()
                    .subnetIds(vpc.getPrivateSubnets().stream().map(s -> s.getSubnetId()).collect(Collectors.toList()))
                    .securityGroupIds(List.of(esSecurityGroup.getSecurityGroupId()))
                    .build())
                .elasticsearchClusterConfig(CfnDomain.ElasticsearchClusterConfigProperty.builder()
                    .dedicatedMasterEnabled(true)
                    .instanceCount(3)
                    .zoneAwarenessEnabled(true)
                    .zoneAwarenessConfig(CfnDomain.ZoneAwarenessConfigProperty.builder()
                        .availabilityZoneCount(3)
                        .build())
                    .instanceType("r5.large.elasticsearch")
                    .dedicatedMasterType("r5.large.elasticsearch")
                    .dedicatedMasterCount(3)
                    .build())
                .ebsOptions(CfnDomain.EBSOptionsProperty.builder()
                    .ebsEnabled(true)
                    .iops(0)
                    .volumeSize(20)
                    .volumeType("gp2")
                    .build())
                .snapshotOptions(CfnDomain.SnapshotOptionsProperty.builder()
                    .automatedSnapshotStartHour(0)
                    .build())
                .advancedOptions(Map.of(
                    "indices.fielddata.cache.size", "",
                    "rest.action.multi.allow_explicit_index", "true"
                ))
                .build());
    }

    private class EventSourcingInfraStackConfig {
        public final String kafkaClusterConfigARN;

        public EventSourcingInfraStackConfig(Construct scope) {
            kafkaClusterConfigARN = getUnsafeValue(scope, "kafkaClusterConfigARN");
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
