# Event Sourcing Demo

This is a demo project that explores an event sourcing architecture on
AWS. The use case is a personal banking website that uses event sourcing
with eventual consistency to process its data. In particular it has the
following features:
* It uses Amazon Managed Streaming for Apache Kafka to store events
* It uses a Java based open source library for event sourcing from
Simple Machines called Simple Sourcing [Simple Sourcing](https://simplesource.io/)
* It uses Amazon Elasticsearch Service to hold a read side projection or
materialized view of our events.
* It uses AWS Fargate to run the Java based docker containers for our
read, write and projection services.

## Architecture

TODO

## Running locally
TODO

## Running on an EC2 instance
Provision an Amazon Linux 2 EC2 instance (m5.large or larger is recommended), ssh in ir an run:

```bash
sudo yum update -y
sudo yum install -y git docker
sudo groupadd docker
sudo usermod -aG docker ${USER}
sudo service docker start
sudo systemctl enable docker
sudo curl -L "https://github.com/docker/compose/releases/download/1.25.4/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
newgrp docker
sudo chmod +x /usr/local/bin/docker-compose

# for EC: increase vm.max_map_count is set to at least 262144 (see https://opendistro.github.io/for-elasticsearch-docs/docs/install/docker/)
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# Install Amazon Corretto
#sudo yum install -y java-1.8.0-openjdk
#sudo amazon-linux-extras enable corretto8
#sudo yum install -y java-1.8.0-amazon-corretto-devel
sudo yum install -y java-11-amazon-corretto

# Install Gradle
gradle_version=6.2.1
wget -c http://services.gradle.org/distributions/gradle-${gradle_version}-all.zip
sudo unzip  gradle-${gradle_version}-all.zip -d /opt
sudo ln -s /opt/gradle-${gradle_version} /opt/gradle
printf "export GRADLE_HOME=/opt/gradle\nexport PATH=\$PATH:\$GRADLE_HOME/bin\n" | sudo tee /etc/profile.d/gradle.sh
source /etc/profile.d/gradle.sh

# Clone repo
cd ~ && git clone https://github.com/jousby/msk-event-sourcing-demo && cd ./msk-event-sourcing-demo

# Build read-api, write-api, projections
gradle init
gradle build

docker-compose up # --detach
```
TODO: check/fix versions above and in ./docker-compose.yml

## Bootstrapping the project in AWS

### Required tools

The following tools need to be accessible in your development
environment (laptop, container or cloud9 etc).

1. Java 11. Why not try [Amazon Corretto.](https://docs.aws.amazon.com/corretto/latest/).
2. Gradle. [How to install Gradle](https://gradle.org/install/).
3. Node + NPM (Required for CDK CLI Installation). [ How to install Node + NPM.](https://nodejs.org/en/download/)
4. CDK CLI. Make sure you install the correct version with ```npm i -g aws-cdk@1.20.0```
5. Git. [Install git.](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
6. AWS CLI (Only required for configuring access keys). [Install AWS CLI.](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html)

Verify you have the required tools by typing the following at a
command line:

```java --version``` (should be 11+)

and

```cdk --version```  (should be 1.20.0)


### Github setup

You will need a github account if you don't have one already.

1. Make a personal fork of this project in your github account. For the
devops pipelines we will be creating you will need the ability to add a
commit hook to the target repository. This step will fail if you are
using the master repository.

2. Clone a local copy of your forked repository to your development
environment. i.e ```git clone <clone url from your forked github project>```

3. Create a [personal github token](https://help.github.com/en/articles/creating-a-personal-access-token-for-the-command-line)
and ensure it has permissions to create commit hooks. The specific
perimssions you need to ensure your token has are documented [here](https://docs.aws.amazon.com/codebuild/latest/userguide/sample-access-tokens.html).
Please make a note of the personal token and store it somewhere you can
retrieve later on down below.

### Setup your AWS environment

You will need access to an AWS account and a set of IAM access keys to
drive programmatic access to the AWS environment from the command line.
Specifically you need the access keys to allow the CDK CLI to provision
resources.

#### In the AWS Console

1. Log into the AWS Console and switch over to your target region using
the region selector in the top right hand corner.

2. Open up the Secrets Manager Service and create a new secret for the
github personal access token you created in the previous section.
Choose to create a new secret for 'Other type of secret (API Key)' and
put in an arbitrary key name and the github token as the value. Make
a note of the secret name and the key. i.e for myself I have a secret
called 'jousby/github' and the key is 'oauthToken'. This token is
retrieved by your pipeline to enable access to your github project.

3. Open the S3 Service and create an s3 bucket in this region to be used
by AWS CodeBuild for caching project dependencies during the gradle
build. i.e something along the lines of
```<accountnumber>-<region>-codebuild-cache```. Make a note of the
bucket name.

4. If you don't have a set of access keys for accessing this aws
environment from the command line then open up th IAM service and
following these [instructions](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey)
to create your access keys.

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
