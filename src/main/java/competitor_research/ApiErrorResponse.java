package competitor_research;

import java.time.Instant;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String timestamp
) {
    public ApiErrorResponse(int status, String error, String message) {
        this(status, error, message, Instant.now().toString());
    }
}