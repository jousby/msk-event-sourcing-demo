FROM amazon/opendistro-for-elasticsearch-kibana:1.4.0
RUN /usr/share/kibana/bin/kibana-plugin remove opendistro_security
COPY --chown=kibana:kibana kibana.yml /usr/share/kibana/config/