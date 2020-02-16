package software.amazon.samples;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.msk.CfnClusterProps;

import java.util.stream.Collectors;

public class EventSourcingInfraStack extends Stack {
    public EventSourcingInfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public EventSourcingInfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // Vpc setup
        Vpc vpc = new Vpc(this, "EventSourcingVPC", VpcProps.builder().build());

        // Kafka
        CfnCluster kafkaCluster = new CfnCluster(this, "EventSourcingKafka",
            CfnClusterProps.builder()
                .clusterName("EventSourcingKafkaCluster")
                .kafkaVersion("2.3.1")
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                    .instanceType("kafka.m5.large")
                    .storageInfo(CfnCluster.StorageInfoProperty.builder()
                        .ebsStorageInfo(CfnCluster.EBSStorageInfoProperty.builder()
                            .volumeSize(20)
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

        // Elasticsearch


        // S3 webserver

        // 3 x ECS Fargate

    }
}
