package demo;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;

public class Config {
    public static ActivityOptions getDefaultActivityOptions() {
        ActivityOptions defaulActivityOptions = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(120))
                .build();

        return defaulActivityOptions;
    }
}
