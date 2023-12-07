package com.aws.pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;

import java.util.HashMap;
import java.util.Map;

public class LogsQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Declare the logger
    LambdaLogger logger = null;
    // Initialize the mapper
    ObjectMapper requestMapper = new ObjectMapper();
    // Knowledgebase ID
    private final static String KNOWLEDGE_BASE_ID = "KQ003NEFHS";
    // Initialize the knowledgebase configuration
    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    KnowledgeBaseVectorSearchConfiguration knowledgeBaseVectorSearchConfiguration = KnowledgeBaseVectorSearchConfiguration.builder()
            .numberOfResults(10)
            .build();
    KnowledgeBaseRetrievalConfiguration knowledgeBaseRetrievalConfiguration = KnowledgeBaseRetrievalConfiguration.builder()
            .vectorSearchConfiguration(knowledgeBaseVectorSearchConfiguration)
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        // Initialize the logger
        logger = context.getLogger();

        // Parse the request
        logger.log("INPUT REQUEST: %s".formatted(input.toString()));

        // Initializing the bedrock
        try (BedrockAgentRuntimeClient bedrockAgentRuntimeClient = BedrockAgentRuntimeClient.builder()
                .region(Region.US_WEST_2)
                .build()) {

            // Form the request for bedrock knowledgebase
            KnowledgeBaseQuery knowledgeBaseQuery = KnowledgeBaseQuery.builder()
                    .text(input.getBody())
                    .build();
            RetrieveRequest retrieveRequest = RetrieveRequest.builder()
                    .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                    .retrievalQuery(knowledgeBaseQuery)
                    .retrievalConfiguration(knowledgeBaseRetrievalConfiguration)
                    .build();

            // Invoke the bedrock knowledgebase
            RetrieveResponse retrieveResponse = bedrockAgentRuntimeClient.retrieve(retrieveRequest);

            // Extract the bedrock results and return the results
            try {
                if(retrieveResponse.hasRetrievalResults()) {
                    ArrayNode responseNode = requestMapper.createArrayNode();
                    for(KnowledgeBaseRetrievalResult result: retrieveResponse.retrievalResults()) {
                        responseNode.add(result.content().text());
                    }
                    response.setHeaders(createHeaders());
                    response.setStatusCode(HttpStatusCode.OK);
                    response.setBody(requestMapper.writeValueAsString(responseNode));
                    logger.log("OUTPUT RESPONSE: %s".formatted(responseNode.toString()));
                }
            } catch (JsonProcessingException e) {
                logger.log(e.getMessage());
                response.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR);
                response.setBody("Error processing JSON: %s".formatted(e.getOriginalMessage()));
            }
        }
        return response;
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
