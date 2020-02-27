FROM ubuntu:xenial-20200212
RUN apt-get update -y \
    && apt-get install curl httping -y 
ADD create-indices.sh /create-indices.sh

CMD while ! httping -qc1 $ELASTICSEARCH_URL ; do sleep 1 ; echo "waiting for $ELASTICSEARCH_URL to come up"; done \
    && bash /create-indices.sh \
    && echo "finished, exiting"