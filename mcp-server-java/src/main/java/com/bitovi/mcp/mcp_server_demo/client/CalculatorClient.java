package com.bitovi.mcp.mcp_server_demo.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A simple client to demonstrate using the MCP server calculator tool.
 */
public class CalculatorClient {
    private static final String BASE_URL = "http://localhost:8080";
    private static final String MCP_ENDPOINT = BASE_URL + "/mcp/message";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws IOException, InterruptedException {
        // Create an HttpClient that will handle cookies
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        
        // First, make a request to get the CSRF token
        HttpRequest initialRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .GET()
                .build();
        
        HttpResponse<String> initialResponse = client.send(initialRequest, 
                HttpResponse.BodyHandlers.ofString());
        
        // Extract CSRF token from cookies
        Optional<String> csrfToken = initialResponse.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("_csrf="))
                .findFirst()
                .map(cookie -> cookie.split(";")[0].substring("_csrf=".length()));
        
        if (csrfToken.isEmpty()) {
            System.out.println("Failed to get CSRF token");
            return;
        }
        
        String token = csrfToken.get();
        System.out.println("Got CSRF token: " + token);
        
        // Create the request body for the calculator
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("jsonrpc", "2.0");
        requestBody.put("id", 1);
        requestBody.put("method", "tools/call");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "calculator");
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("operator", "ADD");
        arguments.put("a", 2);
        arguments.put("b", 2);
        
        params.put("arguments", arguments);
        requestBody.put("params", params);
        
        // Convert request to JSON
        String jsonRequestBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("Sending request: " + jsonRequestBody);
        
        // Create and send the HTTP request with CSRF token
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MCP_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token)
                .header("Cookie", "_csrf=" + token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody))
                .build();
        
        HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        // Print the result
        System.out.println("Response status code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
        
        // Parse and display just the calculation result if successful
        if (response.statusCode() == 200) {
            try {
                Map<String, Object> jsonResponse = objectMapper.readValue(response.body(), 
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                
                if (jsonResponse.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultObj = (Map<String, Object>) jsonResponse.get("result");
                    if (resultObj.containsKey("result")) {
                        System.out.println("\nCalculation result (2 + 2): " + resultObj.get("result"));
                    }
                } else if (jsonResponse.containsKey("error")) {
                    System.out.println("Error: " + jsonResponse.get("error"));
                }
            } catch (Exception e) {
                System.out.println("Failed to parse response: " + e.getMessage());
            }
        }
    }
}
