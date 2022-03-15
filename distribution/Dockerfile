FROM 998131032990.dkr.ecr.us-east-1.amazonaws.com/openjdk8-ubuntu18.04:latest

WORKDIR /opt/druid

ARG ARTIFACT_PATH=target/apache-druid-*-bin.tar.gz
ADD $ARTIFACT_PATH /opt/druid/

RUN find -name '*.tgz' -type f -exec tar xzf {} \; && mv apache-druid-*/* . && rm -r apache-druid-*/

RUN addgroup --gid 1001 druid \
 && adduser --home /opt/druid --shell /bin/sh --no-create-home --uid 1001 --gecos '' --gid 1001 --disabled-password druid \
 && mkdir -p /var/log/druid/gc \
 && chown -R druid:druid /var/log/druid \
 && chmod -R 775 /var/log/druid \
 && chown -R druid:druid /opt/druid \
 && chmod 775 /opt/druid/ \
 && mkdir /var/druid \
 && chown -R druid:druid /var/druid

USER root
RUN apt-get update
RUN apt-get install --yes inotify-tools
RUN apt-get install --yes jq
RUN apt-get install --yes netcat
RUN apt-get install --yes vim
RUN apt-get install --yes less
USER druid

CMD ["bin/start_docker.sh"]