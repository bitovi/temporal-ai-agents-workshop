package com.bitovi.mcp.mcp_server_demo.service;

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CalculatorService implements Function<CalculatorService.Request, CalculatorService.Response> {

    public enum Operator {
        ADD, SUB, MUL, DIV
    }

    public record Request(@JsonProperty(required = true, value = "operator") Operator operator,
            @JsonProperty(required = true, value = "a") double a,
            @JsonProperty(required = true, value = "b") double b) {
    }

    public record Response(double result) {
    }

    @Override
    public Response apply(Request request) {
        double result = switch (request.operator()) {
            case ADD -> request.a() + request.b();
            case SUB -> request.a() - request.b();
            case MUL -> request.a() * request.b();
            case DIV -> request.a() / request.b();
        };
        return new Response(result);
    }
}
