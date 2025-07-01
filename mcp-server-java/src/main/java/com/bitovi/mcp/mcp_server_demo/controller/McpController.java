package com.bitovi.mcp.mcp_server_demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bitovi.mcp.mcp_server_demo.service.CalculatorService;
import com.bitovi.mcp.mcp_server_demo.service.CurrentTimeService;
import com.bitovi.mcp.mcp_server_demo.service.WeatherService;

/**
 * A simplified MCP server controller that implements the basic MCP protocol
 * for tool invocation.
 */
@RestController
@CrossOrigin
@RequestMapping("/mcp")
public class McpController {

    private final CalculatorService calculatorService;
    private final CurrentTimeService currentTimeService;
    private final WeatherService weatherService;

    public McpController() {
        this.calculatorService = new CalculatorService();
        this.currentTimeService = new CurrentTimeService();
        this.weatherService = new WeatherService();
    }

    @PostMapping(value = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleMcpMessage(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        String jsonrpc = (String) request.get("jsonrpc");
        Object id = request.get("id");

        response.put("jsonrpc", jsonrpc);
        response.put("id", id);

        String method = (String) request.get("method");

        if ("initialize".equals(method)) {
            // Return server info and capabilities
            Map<String, Object> result = new HashMap<>();
            result.put("protocolVersion", "2024-11-05");

            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("name", "bitovi-mcp-demo");
            serverInfo.put("version", "1.0.0");
            result.put("serverInfo", serverInfo);

            Map<String, Object> capabilities = new HashMap<>();
            Map<String, Object> toolCapabilities = new HashMap<>();
            toolCapabilities.put("supported", true);
            capabilities.put("tools", toolCapabilities);
            result.put("capabilities", capabilities);

            response.put("result", result);
        } else if ("tools/list".equals(method)) {
            // Return list of available tools
            Map<String, Object> result = new HashMap<>();

            Map<String, Object> calculatorTool = createToolDescription(
                    "calculator",
                    "A basic calculator that performs operations like ADD, SUB, MUL, and DIV",
                    createCalculatorSchema());

            Map<String, Object> currentTimeTool = createToolDescription(
                    "currentTime",
                    "Get the current time",
                    "{}");

            Map<String, Object> weatherTool = createToolDescription(
                    "weather",
                    "Get weather forecast by zip code",
                    createWeatherSchema());

            result.put("tools", new Object[] { calculatorTool, currentTimeTool, weatherTool });
            response.put("result", result);
        } else if ("tools/call".equals(method)) {
            // Handle tool call
            try {
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String toolName = (String) params.get("name");
                Map<String, Object> toolArgs = (Map<String, Object>) params.get("arguments");

                Map<String, Object> result = new HashMap<>();

                if ("calculator".equals(toolName)) {
                    result.put("result", handleCalculator(toolArgs));
                } else if ("currentTime".equals(toolName)) {
                    result.put("result", handleCurrentTime());
                } else if ("weather".equals(toolName)) {
                    result.put("result", handleWeather(toolArgs));
                } else {
                    Map<String, Object> error = new HashMap<>();
                    error.put("code", -32601);
                    error.put("message", "Tool not found: " + toolName);
                    response.put("error", error);
                    return response;
                }

                result.put("partial", false);
                response.put("result", result);
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", -32603);
                error.put("message", "Internal error: " + e.getMessage());
                response.put("error", error);
            }
        } else {
            // Method not supported
            Map<String, Object> error = new HashMap<>();
            error.put("code", -32601);
            error.put("message", "Method not found: " + method);
            response.put("error", error);
        }

        return response;
    }

    private Map<String, Object> createToolDescription(String name, String description, String schema) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("schema", schema);
        return tool;
    }

    private String createCalculatorSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "operator": {
                      "type": "string",
                      "enum": ["ADD", "SUB", "MUL", "DIV"],
                      "description": "Operation to perform"
                    },
                    "a": {
                      "type": "number",
                      "description": "First operand"
                    },
                    "b": {
                      "type": "number",
                      "description": "Second operand"
                    }
                  },
                  "required": ["operator", "a", "b"]
                }
                """;
    }

    private String createWeatherSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "zipCode": {
                      "type": "string",
                      "description": "Zip code to get weather forecast for"
                    }
                  },
                  "required": ["zipCode"]
                }
                """;
    }

    private String handleCalculator(Map<String, Object> args) {
        CalculatorService.Request request = new CalculatorService.Request(
                CalculatorService.Operator.valueOf((String) args.get("operator")),
                ((Number) args.get("a")).doubleValue(),
                ((Number) args.get("b")).doubleValue());
        CalculatorService.Response response = calculatorService.apply(request);
        return String.valueOf(response.result());
    }

    private String handleCurrentTime() {
        CurrentTimeService.Request request = new CurrentTimeService.Request();
        CurrentTimeService.Response response = currentTimeService.apply(request);
        return response.time();
    }

    private String handleWeather(Map<String, Object> args) {
        WeatherService.Request request = new WeatherService.Request((String) args.get("zipCode"));
        WeatherService.Response response = weatherService.apply(request);

        // Format the response as a human-readable string
        StringBuilder result = new StringBuilder();
        result.append(String.format("Current weather for %s: %s, %d°F\n\n",
                response.location(), response.currentCondition(), response.currentTemp()));

        result.append("5-Day Forecast:\n");
        for (WeatherService.DailyForecast forecast : response.forecast()) {
            result.append(String.format("- %s: %s, High: %d°F, Low: %d°F\n",
                    forecast.date(), forecast.condition(), forecast.highTemp(), forecast.lowTemp()));
        }

        return result.toString();
    }
}
