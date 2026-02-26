package bitovi.activities;

import java.util.List;

import bitovi.activities.types.PersistMessage;
import io.temporal.failure.ApplicationFailure;

public class Persist {
    public static void execute(List<PersistMessage> messages) {
        try {
            System.out.println("persistActivity called with " + messages.size() + " messages:");

            for (PersistMessage msg : messages) {
                if ("user".equals(msg.role())) {
                    System.out.println(String.format("  %s (%s): %s",
                            msg.name(), msg.date(), msg.message()));
                } else if ("assistant".equals(msg.role())) {
                    System.out.println(String.format("  assistant: %s", msg.message()));
                }
            }

        } catch (Exception e) {
            System.err.println("Error in persistActivity: " + e.getMessage());
            throw ApplicationFailure.newFailure("persistActivity failed: " + e.getMessage(),
                    "PersistActivityError");
        }
    }
}
