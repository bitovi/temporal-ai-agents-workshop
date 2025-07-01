package com.bitovi.mcp.mcp_server_demo.service;

import java.time.LocalDateTime;
import java.util.function.Function;

public class CurrentTimeService implements Function<CurrentTimeService.Request, CurrentTimeService.Response> {

    public record Request() {
    }

    public record Response(String time) {
    }

    @Override
    public Response apply(Request request) {
        return new Response(LocalDateTime.now().toString());
    }
}
