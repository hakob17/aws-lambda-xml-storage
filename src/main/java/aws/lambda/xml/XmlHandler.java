package aws.lambda.xml;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.SneakyThrows;

import java.util.Optional;
import java.util.UUID;

public class XmlHandler implements RequestHandler<XmlBody, String> {
    static AmazonDynamoDB client;
    static DynamoDB dynamoDB;
    static AWSCredentials credentials;
    static AmazonS3 s3client;
    static {
         client = AmazonDynamoDBClientBuilder.standard().build();
         dynamoDB = new DynamoDB(client);

         credentials = new BasicAWSCredentials(
                 getEnvVariable("ACCESS_KEY"),
                 getEnvVariable("SECRET_KEY")
        );
         s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.US_EAST_2)
                .build();
    }

    XmlMapper xmlMapper = new XmlMapper();
    ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.WRAP_ROOT_VALUE, false)
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);

    @Override
    public String handleRequest(XmlBody xmlBody, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log(String.valueOf(xmlBody));

        String body = xmlBody.getBody();
        return Optional.of(body)
                .map(this::parse)
                .flatMap(this::writeToDb)
                .map(item -> writeToS3(body, item.asMap().get("id")))
                .map(String::valueOf)
                .orElse("");
    }

    private Optional<Item> writeToDb(JsonNode object) {
        return Optional.of(getEnvVariable("DB_NAME"))
                .map(dynamoDB::getTable)
                .map(table -> {
                    Item item = new Item()
                            .withPrimaryKey("id", UUID.randomUUID().toString())
                            .withString("body", object.toString());

                     table.putItem(item);
                    return item;
                });
    }

    private PutObjectResult writeToS3(String object, Object id) {
        String bucketName = getEnvVariable("BUCKET_NAME");

        if(!s3client.doesBucketExistV2(bucketName)) {
            s3client.createBucket(bucketName);
        }

        return s3client.putObject(
                bucketName,
                String.valueOf(id) + ".xml",
                object
        );
    }

    private static String getEnvVariable(String bucket_name) {
        return System.getenv(bucket_name);
    }

    @SneakyThrows
    private JsonNode parse(String body) {
        return xmlMapper.readValue(body, JsonNode.class);
    }
}
