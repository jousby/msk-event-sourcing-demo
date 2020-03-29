package software.amazon.samples.read;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.ContainerCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import com.amazonaws.internal.CredentialsEndpointProvider;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.samples.domain.AccountSummary;
import software.amazon.samples.domain.AccountTransaction;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;


/**
 * Use Elasticsearch as our read datasource
 */
public class ElasticsearchReadApi implements ReadApi {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchReadApi.class);

    private static final String SUMMARY_INDEX = "simplesourcedemo_account_summary";
    private static final String TRANSACTION_INDEX = "simplesourcedemo_account_transaction";

    private final RestHighLevelClient esClient;

    public ElasticsearchReadApi() {
        String elasticsearchUrl = System.getenv("ELASTICSEARCH_URL");
        //int elasticsearchPort = Integer.parseInt(System.getenv("ELASTICSEARCH_PORT"));

        esClient = esClient(elasticsearchUrl);
        log.info("Elasticsearch client created");

        createIndiciesIfNotPresent();
        log.info("Back from indicies check");
    }

    // Adds the interceptor to the ES REST client
    public RestHighLevelClient esClient(String elasticsearchUrl) {
        AWSCredentialsProvider credentialsProvider = new ContainerCredentialsProvider();
        log.info("creds: "+ credentialsProvider.getCredentials().getAWSAccessKeyId());
        log.info("creds: "+ credentialsProvider.getCredentials().getAWSSecretKey());
        String serviceName = "es";

        AWS4Signer signer = new AWS4Signer();
        signer.setServiceName(serviceName);
        signer.setRegionName("ap-southeast-2"); // TODO how to inject

        String realEndpoint = "https://vpc-eventsourcing-sqmle6z6em5tkb2hz42tgv6yoe.ap-southeast-2.es.amazonaws.com";
        String thisEndpoint = "https://" + elasticsearchUrl;
        log.info(realEndpoint);
        log.info(thisEndpoint);

        HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider);

        return new RestHighLevelClient(RestClient.builder(HttpHost.create("https://" + elasticsearchUrl))
            .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
    }

    private void createIndiciesIfNotPresent() {
        try {
            boolean summaryIndexExists = esClient.indices().exists(
                new GetIndexRequest(SUMMARY_INDEX), RequestOptions.DEFAULT);

            log.info("summary index is present? -> " + summaryIndexExists);
            if (!summaryIndexExists) createSummaryIndex();

            boolean transactionIndexExists = esClient.indices().exists(
                new GetIndexRequest(TRANSACTION_INDEX), RequestOptions.DEFAULT);

            log.info("transaction index is present? -> " + transactionIndexExists);
            if (!transactionIndexExists) createTransactionIndex();
        } catch (IOException e) {
            log.error("Unable to create indicies if not present, might be a race condition so swallowing", e);
        }
    }

    private void createSummaryIndex() throws IOException {
        log.info("Creating summary index: " + SUMMARY_INDEX);
        CreateIndexRequest request = new CreateIndexRequest(SUMMARY_INDEX);
        request.mapping(
            "{\n" +
                "  \"properties\": {\n" +
                "    \"accountName\": {\n" +
                "      \"type\": \"keyword\"\n" +
                "    },\n" +
                "    \"balance\": {\n" +
                "      \"type\": \"double\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
            XContentType.JSON);

        esClient.indices().create(request, RequestOptions.DEFAULT);
    }

    private void createTransactionIndex() throws IOException {
        log.info("Creating transaction index: " + TRANSACTION_INDEX);
        CreateIndexRequest request = new CreateIndexRequest(TRANSACTION_INDEX);
        request.mapping(
            "{\n" +
                "  \"properties\": {\n" +
                "    \"account\": {\n" +
                "      \"type\": \"keyword\"\n" +
                "    },\n" +
                "    \"amount\": {\n" +
                "      \"type\": \"double\"\n" +
                "    }\n" +
                "  }\n" +
                "}",
            XContentType.JSON);

        esClient.indices().create(request, RequestOptions.DEFAULT);
    }

    @Override
    public Optional<AccountSummary> accountSummary(String accountName) {
        GetRequest getRequest = new GetRequest(SUMMARY_INDEX, accountName);

        try{
            GetResponse getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
            if(getResponse.isExists()) {
                String account = (String) getResponse.getSourceAsMap().get("accountName");
                double balance = (double) getResponse.getSourceAsMap().get("balance");
                long version = Long.valueOf(getResponse.getSourceAsMap().get("sequence").toString());
                return Optional.of(new AccountSummary(account, balance, version));
            } else {
                return Optional.empty();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public List<AccountSummary> list() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(SUMMARY_INDEX);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchRequest.source(searchSourceBuilder);

        try {
            log.info("before search");
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            log.info("after search");
            ArrayList<AccountSummary> result = new ArrayList<>();


            Iterator<SearchHit> searchHits = searchResponse.getHits().iterator();
            SearchHit searchHit;
            while (searchHits.hasNext()) {
                searchHit = searchHits.next();
                String account = (String) searchHit.getSourceAsMap().get("accountName");
                double balance = (double) searchHit.getSourceAsMap().get("balance");
                long version = Long.valueOf(searchHit.getSourceAsMap().get("sequence").toString());
                result.add(new AccountSummary(account, balance, version));
            }

            return result;

        } catch (IOException e) {
            log.error("list accounts failed", e);
            log.error(e.getMessage());
            throw new RuntimeException("ElasticSearch query failure", e);
        }
    }

    @Override
    public List<AccountTransaction> getTransactions(String accountName) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(TRANSACTION_INDEX);

        QueryBuilder qb = QueryBuilders.termQuery("account", accountName);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(qb);

        searchRequest.source(searchSourceBuilder);

        try {
            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

            ArrayList<AccountTransaction> result = new ArrayList<>();

            Iterator<SearchHit> searchHits = searchResponse.getHits().iterator();
            SearchHit searchHit;

            while (searchHits.hasNext()) {
                searchHit = searchHits.next();
                double amount = (double) searchHit.getSourceAsMap().get("amount");
                Instant ts = Instant.parse((String) searchHit.getSourceAsMap().get("time"));
                result.add(new AccountTransaction(amount, ts));
            }

            return result;

        } catch (IOException e) {
            throw new RuntimeException("ElasticSearch query failure", e);
        }

    }

}
