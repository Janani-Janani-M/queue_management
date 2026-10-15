package dto;

/**
 * ApiResponse&lt;T&gt; – a generic envelope used for all non-queue-list responses
 * so the frontend always receives a consistent shape:
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Token Q105 generated",
 *   "data"   : { ... }         // null on error
 * }
 * </pre>
 */
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;
    private final T       data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }

    // ---------------------------------------------------------------
    // Static factories
    // ---------------------------------------------------------------

    /** Successful response with a data payload. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /** Successful response with no data payload. */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /** Error response with a descriptive message. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------
    public boolean isSuccess() { return success; }
    public String  getMessage() { return message; }
    public T       getData()    { return data; }
}
