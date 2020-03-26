#!/bin/bash

sudo yum update -y
sudo yum install docker -y
sudo usermod -aG docker ${USER}
sudo service docker start
sudo systemctl enable docker
sudo curl -L "https://github.com/docker/compose/releases/download/1.25.4/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# for EC: increase vm.max_map_count is set to at least 262144 (see https://opendistro.github.io/for-elasticsearch-docs/docs/install/docker/)
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

# Install Amazon Corretto
sudo yum install -y java-11-amazon-corretto

# Install Gradle
gradle_version=6.2.1
wget -c http://services.gradle.org/distributions/gradle-${gradle_version}-all.zip
sudo unzip  gradle-${gradle_version}-all.zip -d /opt
sudo ln -s /opt/gradle-${gradle_version} /opt/gradle
printf "export GRADLE_HOME=/opt/gradle\nexport PATH=\$PATH:\$GRADLE_HOME/bin\n" | sudo tee /etc/profile.d/gradle.sh
source /etc/profile.d/gradle.sh