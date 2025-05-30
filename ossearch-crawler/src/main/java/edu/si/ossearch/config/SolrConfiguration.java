package edu.si.ossearch.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author jbirkhimer
 */
@Configuration
public class SolrConfiguration {

    @Value("${spring.data.solr.host}")
    private String hostMaster;

    @Value("${spring.data.solr.slave}")
    private String hostSlave;

    @Value("${spring.data.solr.connection.timeout:10000}") // default 10 seconds
    private int solrConnectionTimeout;

    @Value("${spring.data.solr.socket.timeout:60000}") // default 60 seconds
    private int solrSocketTimeout;

    /*@Value("${spring.data.solr.zk-host}")
    private List<String> zkHost;

    @Bean
    public SolrClient solrCloudClient() {
        return new CloudSolrClient.Builder(zkHost, Optional.of("/solr-6.6.1")).build();
    }*/

    @Primary
    @Bean("master")
    public SolrClient solrMasterClient() {
        return new HttpSolrClient.Builder(hostMaster)
                .withConnectionTimeout(solrConnectionTimeout)
                .withSocketTimeout(solrSocketTimeout)
                .build();
    }

    @Bean("slave")
    public SolrClient solrSlaveClient() {
        return new HttpSolrClient.Builder(hostSlave)
                .withConnectionTimeout(solrConnectionTimeout)
                .withSocketTimeout(solrSocketTimeout)
                .build();
    }

}
