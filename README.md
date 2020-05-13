# MSK Event Sourcing Demo

This is a demo project that explores an event sourcing architecture on
AWS. The use case is a personal banking website that uses event sourcing
with eventual consistency to process its data. In particular it has the
following features:
* It uses Amazon Managed Streaming for Apache Kafka to store events
* It uses a Java based open source library for event sourcing from
Simple Machines called [Simple Sourcing](https://simplesource.io/)
* It uses Amazon Elasticsearch Service to hold a read side projection or
materialized view of our events.
* It uses AWS Fargate to run the Java based docker containers for our
read, write and projection services.
* It use the AWS CDK for doing both pipelines and infrastructure as code.


### Required tooling

The following tools need to be accessible in your development
environment (laptop, container or cloud9 etc).

1. Java 11. Why not try [Amazon Corretto.](https://docs.aws.amazon.com/corretto/latest/).
2. Gradle. [How to install Gradle](https://gradle.org/install/).
3. Node + NPM (Required for CDK CLI Installation). [ How to install Node + NPM.](https://nodejs.org/en/download/)
4. CDK CLI. Make sure you install the correct version with ```npm i -g aws-cdk@1.31.0```
5. Git. [Install git.](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
6. AWS CLI (Only required for configuring access keys). [Install AWS CLI.](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html)

Verify you have the required tools by typing the following at a
command line:

```java --version``` (should be 11+)

and

```gradle -version```  (should be 6+)

and

```cdk --version```  (should be 1.31.0)

You will need access to an AWS account and a set of IAM access keys to
drive programmatic access to the AWS environment from the command line.
Specifically you need the access keys to allow the CDK CLI to provision
resources using AWS Cloudformation.

## AWS deployment

### Github setup

You will need a github account if you don't have one already.

1. Clone a local copy of your forked repository to your development
environment. i.e ```git clone https://github.com/jousby/msk-event-sourcing-demo.git```

### In the AWS Console

2. Log into the AWS Console and switch over to your target region using
the region selector in the top right hand corner.

3. If you don't have a set of access keys for accessing this aws
environment from the command line then open up th IAM service and
following these [instructions](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey)
to create your access keys.

4. At the moment (13/5/2020) there is one part of the demo infrastructure
that can't be created via CDK/Cloudformation. In order to provision the
Kafka cluster you will first need to manually create a Kafka Cluster
Configuration object in the console.

    Navigate to the Amazon MSK Service Homepage. Click on the navigation bar
on the left of the screen if it is collapsed, then click on ```Cluster Configurations```
. Click on the button called ```Create cluster configuration```.

    On the create cluster configuration screen enter:

    Configuration Name: ESDemoConfig
    Apache Kafka version: 2.3.1

    Configuration details:
    (Update the first three rows in the block to look like the following)
    ```
    auto.create.topics.enable=true
    default.replication.factor=3
    min.insync.replicas=1
    ```

    Hit the ```Create``` button to create the configuration.

    Back on the Cluster Configuration page click into our newly created
    cluster configuration. Under the configuration name there will be a
    Configuration ARN field. Copy this ARN value somewhere you can
    retrieve it later on (thanks for sticking with me :)


#### On the Command Line

5. Configure your command line to use the access keys created in the
previous step by running ```aws configure```. Make sure the region
you enter during the configuration wizard is the same as the one you
were using in the console.

6. If you have completed the aws configuration step successfully then
when you run the cdk cli it should pick up your chosen region and access
keys. Run ```cdk bootstrap``` to setup the cdk cli for deploying
stacks in your chosen region. This boostrap process creates a
cloudformation stack and some s3 buckets.

7. Change directory to the base of the project folder. Build the project
   with:

    ```gradle build```

    Check for any errors in your gradle build.

8. Within the project change directory to the ```cdk-infra``` folder.

9. Update the ```cdk.json``` file with the Kafka Cluster Configuration
ARN your recorded from step 4.

10. Once step 9 has been completed run:

    ```cdk deploy```

    This command will connect to AWS using the credentials (and implied
    account) you setup in step 5 and initiate the creation of a Cloudformation
    Stack that will provision both the infrastructure and application
    code required for this demo. This will take a while to complete
    (up to ~60 mins). Please see the next step for checking on how the
    creation of the Cloudformation stack is going back in the AWS Console.

#### Back in the AWS Console

9. Open up the AWS Cloudformation Service homepage with in the AWS
Console. You should see a Cloudformation Stack for your the event sourcing
demo in the progress of being created.

10. Once the stack has been created click on the stack in the nav bar on
the left and then click on the Outputs tab. Make a copy of both the
readApiUrl and the writeApiUrl values for use in a later step

#### Back on the Command Line

11. Change directory to the ```frontend``` folder in the base of the project

12. Update the ```src/service/Endpoints.js``` file with the read and write
url values you recorded in step 10.

13. Build the frontend project with
    ```npm install && npm start```

14. At this point you should have a local copy of the frontend running that
is connected to your AWS infrastructure. Navigate to ```localhost:3000```
to take the demo application for a test drive.


