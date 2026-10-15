package controller;

import dto.ApiResponse;
import dto.CitizenResponse;
import dto.QueueStatsResponse;
import dto.RegisterRequest;
import service.QueueService;

/**
 * QueueController – the HTTP API boundary for the QueueCare system.
 *
 * <p>This class is written as a plain Java class (no Spring Boot annotations)
 * so it can be read, tested, and understood purely as a DSA project artifact.
 * If you later add Spring Boot, replace the handler method signatures with
 * {@code @RestController}, {@code @PostMapping}, etc.
 *
 * <p><strong>API surface (all paths relative to {@code /api/queue}):</strong>
 *
 * <pre>
 * POST   /register          → register a new citizen, returns CitizenResponse
 * POST   /call-next         → call the next highest-priority citizen
 * POST   /call/{token}      → call a specific citizen by token
 * POST   /mark-served       → mark the current citizen as served
 * POST   /skip/{token}      → skip a citizen by token
 * GET    /stats             → full queue snapshot + statistics
 * POST   /reset             → reset to demo seed data
 * </pre>
 */
public class QueueController {

    // ---------------------------------------------------------------
    // Dependency – created once; would be @Autowired in Spring Boot
    // ---------------------------------------------------------------
    private final QueueService queueService;

    /** Default constructor (initialises its own service instance). */
    public QueueController() {
        this.queueService = new QueueService();
        // Seed demo data so the frontend has a working queue on first load
        queueService.resetDemo();
    }

    /** Constructor for dependency injection / testing. */
    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    // ---------------------------------------------------------------
    // POST /api/queue/register
    // ---------------------------------------------------------------

    /**
     * Registers a new citizen in the queue.
     *
     * <p>Expected request body:
     * <pre>
     * {
     *   "name"     : "Lakshmi Nair",
     *   "service"  : "Document Services",
     *   "category" : "Priority Assistance"
     * }
     * </pre>
     *
     * @param request parsed from JSON body
     * @return 201 Created with CitizenResponse on success, 400 on validation failure
     */
    public ApiResponse<CitizenResponse> register(RegisterRequest request) {
        if (request == null)
            return ApiResponse.error("Request body must not be empty.");

        return queueService.register(request);
    }

    // ---------------------------------------------------------------
    // POST /api/queue/call-next
    // ---------------------------------------------------------------

    /**
     * Selects and calls the next citizen according to the scheduling policy
     * (Priority Assistance → longest wait → arrival order).
     *
     * @return called citizen details, or error message if queue is empty
     */
    public ApiResponse<CitizenResponse> callNext() {
        return queueService.callNext();
    }

    // ---------------------------------------------------------------
    // POST /api/queue/call/{token}
    // ---------------------------------------------------------------

    /**
     * Directly calls a specific citizen by token (staff overrides the scheduler).
     *
     * @param token the citizen's token, e.g. "Q103"
     * @return called citizen details, or 404 if token not found
     */
    public ApiResponse<CitizenResponse> callByToken(String token) {
        if (token == null || token.isBlank())
            return ApiResponse.error("Token must not be blank.");

        return queueService.callByToken(token.trim().toUpperCase());
    }

    // ---------------------------------------------------------------
    // POST /api/queue/mark-served
    // ---------------------------------------------------------------

    /**
     * Marks the currently called citizen as served and frees the counter.
     *
     * @return served citizen details, or error if no citizen is active
     */
    public ApiResponse<CitizenResponse> markServed() {
        return queueService.markServed();
    }

    // ---------------------------------------------------------------
    // POST /api/queue/skip/{token}
    // ---------------------------------------------------------------

    /**
     * Skips a citizen by token (removes them from the queue, marks SKIPPED).
     *
     * @param token the citizen's token
     * @return skipped citizen details, or error if not found / already served
     */
    public ApiResponse<CitizenResponse> skip(String token) {
        if (token == null || token.isBlank())
            return ApiResponse.error("Token must not be blank.");

        return queueService.skip(token.trim().toUpperCase());
    }

    // ---------------------------------------------------------------
    // GET /api/queue/stats
    // ---------------------------------------------------------------

    /**
     * Returns a full snapshot of the current queue state plus computed statistics.
     * This is the primary payload consumed by the frontend's Overview section.
     *
     * @return {@link QueueStatsResponse} with live counts, wait times, and citizen list
     */
    public ApiResponse<QueueStatsResponse> getStats() {
        return ApiResponse.ok("Queue statistics retrieved.", queueService.getStats());
    }

    // ---------------------------------------------------------------
    // POST /api/queue/reset
    // ---------------------------------------------------------------

    /**
     * Resets the in-memory queue to the default demo seed state.
     * Mirrors the frontend's "Reset Demo" button behaviour.
     *
     * @return full stats response after reset so the frontend can re-render
     */
    public ApiResponse<QueueStatsResponse> reset() {
        return queueService.resetDemo();
    }
}
