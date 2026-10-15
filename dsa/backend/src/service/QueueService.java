package service;

import dsa.QueueScheduler;
import dto.ApiResponse;
import dto.CitizenResponse;
import dto.QueueStatsResponse;
import dto.RegisterRequest;
import model.Citizen;
import model.Citizen.Category;
import model.Citizen.ServiceType;
import model.Citizen.Status;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * QueueService – single source of truth for all business logic.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Validate and parse incoming {@link RegisterRequest} data.</li>
 *   <li>Generate unique tokens (Q101, Q102, …).</li>
 *   <li>Delegate to {@link QueueScheduler} for all priority / scheduling decisions.</li>
 *   <li>Maintain a master map of every citizen (past and present) keyed by token
 *       so that history, stats, and "current citizen" state survive across calls.</li>
 *   <li>Compute live statistics (waiting count, average wait, served/skipped totals).</li>
 *   <li>Update wait-time estimates for waiting citizens periodically.</li>
 * </ol>
 *
 * <p>This class is designed as a singleton (no Spring yet) – the controller creates
 * one instance.  If Spring Boot is added later, annotate with {@code @Service} and
 * {@code @Singleton}.
 */
public class QueueService {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------
    /** Baseline processing time per citizen in minutes. */
    private static final int BASE_SERVICE_MINUTES = 5;

    /** Token prefix. */
    private static final String TOKEN_PREFIX = "Q";

    /** Starting counter for tokens. */
    private static final int TOKEN_START = 101;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private final QueueScheduler            scheduler    = new QueueScheduler();
    private final Map<String, Citizen>      masterMap    = new LinkedHashMap<>();
    private final AtomicInteger             tokenCounter = new AtomicInteger(TOKEN_START);
    private final AtomicInteger             arrivalCounter = new AtomicInteger(1);

    /** Currently active (Called) citizen – only one at a time. */
    private volatile Citizen currentCitizen;

    /** Physical counter the current citizen was assigned to. */
    private volatile int     currentCounter = 1;

    /** Served count from before the current in-memory session (e.g. seeded demo data). */
    private          int     servedBeforeSession = 0;

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    /**
     * Registers a new citizen in the queue.
     *
     * @param request incoming form data from the frontend
     * @return ApiResponse containing the new Citizen's details on success
     * @throws IllegalArgumentException if request data is invalid
     */
    public ApiResponse<CitizenResponse> register(RegisterRequest request) {
        // --- Validation ---
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > 60)
            return ApiResponse.error("Citizen name must be between 1 and 60 characters.");

        ServiceType serviceType = parseService(request.getService());
        if (serviceType == null)
            return ApiResponse.error("Unknown service type: " + request.getService());

        Category category = parseCategory(request.getCategory());
        if (category == null)
            return ApiResponse.error("Unknown category: " + request.getCategory());

        // --- Build citizen ---
        String token = TOKEN_PREFIX + tokenCounter.getAndIncrement();
        int    arrival = arrivalCounter.getAndIncrement();

        Citizen citizen = new Citizen(token, name, serviceType, category, arrival);

        // Estimate wait immediately based on current queue depth
        citizen.setWaitMinutes(estimateWait(citizen));

        // --- Persist & schedule ---
        masterMap.put(token, citizen);
        scheduler.enqueue(citizen);

        return ApiResponse.ok(
                "Token " + token + " generated successfully.",
                CitizenResponse.from(citizen));
    }

    // ------------------------------------------------------------------
    // Call Next
    // ------------------------------------------------------------------

    /**
     * Calls the next highest-priority waiting citizen to a service counter.
     *
     * @return ApiResponse with the called citizen, or an error if queue is empty
     */
    public ApiResponse<CitizenResponse> callNext() {
        // If there is already a called citizen who hasn't been served, mark them
        // skipped first (staff moved on without confirming service).
        if (currentCitizen != null && currentCitizen.isCalled()) {
            currentCitizen.setStatus(Status.SKIPPED);
        }

        Optional<Citizen> next = scheduler.callNext();

        if (next.isEmpty()) {
            currentCitizen = null;
            return ApiResponse.error("No citizens are currently waiting.");
        }

        currentCitizen = next.get();
        currentCounter = 1; // single-counter setup; extend for multi-counter

        return ApiResponse.ok(
                currentCitizen.getName() + " — please proceed to Counter " + currentCounter + ".",
                CitizenResponse.from(currentCitizen));
    }

    // ------------------------------------------------------------------
    // Call Specific Citizen
    // ------------------------------------------------------------------

    /**
     * Calls a specific citizen by token (staff clicked "Call" in the table).
     *
     * @param token the citizen's token string
     * @return ApiResponse with the called citizen, or error if not found / not waiting
     */
    public ApiResponse<CitizenResponse> callByToken(String token) {
        Citizen citizen = masterMap.get(token);
        if (citizen == null)
            return ApiResponse.error("Citizen with token " + token + " not found.");

        if (!citizen.isWaiting())
            return ApiResponse.error("Citizen " + token + " is not in WAITING status (current: " + citizen.getStatus() + ").");

        // Abandon current if any
        if (currentCitizen != null && currentCitizen.isCalled()) {
            currentCitizen.setStatus(Status.SKIPPED);
        }

        scheduler.skip(token); // remove from scheduler heap
        citizen.setStatus(Status.CALLED);
        currentCitizen  = citizen;
        currentCounter  = 1;

        return ApiResponse.ok(
                citizen.getName() + " — please proceed to Counter " + currentCounter + ".",
                CitizenResponse.from(citizen));
    }

    // ------------------------------------------------------------------
    // Mark Served
    // ------------------------------------------------------------------

    /**
     * Marks the currently called citizen as SERVED.
     *
     * @return ApiResponse on success or error if no active citizen
     */
    public ApiResponse<CitizenResponse> markServed() {
        if (currentCitizen == null || !currentCitizen.isCalled())
            return ApiResponse.error("No citizen is currently being served.");

        currentCitizen.setStatus(Status.SERVED);
        Citizen served = currentCitizen;
        currentCitizen = null;

        recalculateWaitTimes(); // refresh estimates for remaining queue

        return ApiResponse.ok(
                served.getName() + " has been served successfully.",
                CitizenResponse.from(served));
    }

    // ------------------------------------------------------------------
    // Skip
    // ------------------------------------------------------------------

    /**
     * Skips a specific citizen by token and removes them from the queue.
     *
     * @param token the citizen's token
     * @return ApiResponse on success or error if not found
     */
    public ApiResponse<CitizenResponse> skip(String token) {
        Citizen citizen = masterMap.get(token);
        if (citizen == null)
            return ApiResponse.error("Citizen with token " + token + " not found.");

        if (citizen.getStatus() != Status.WAITING && citizen.getStatus() != Status.CALLED)
            return ApiResponse.error("Citizen " + token + " cannot be skipped (status: " + citizen.getStatus() + ").");

        boolean removed = scheduler.skip(token);
        if (!removed) {
            // May already have been called; force status update
            citizen.setStatus(Status.SKIPPED);
        }

        if (currentCitizen != null && currentCitizen.getToken().equals(token)) {
            currentCitizen = null;
        }

        return ApiResponse.ok(
                "Citizen " + token + " has been skipped.",
                CitizenResponse.from(citizen));
    }

    // ------------------------------------------------------------------
    // Stats / snapshot
    // ------------------------------------------------------------------

    /**
     * Computes the full queue snapshot + statistics that the frontend Overview
     * section consumes.
     *
     * @return {@link QueueStatsResponse} populated with live data
     */
    public QueueStatsResponse getStats() {
        List<Citizen> all = new ArrayList<>(masterMap.values());

        List<Citizen> waiting  = all.stream().filter(Citizen::isWaiting).toList();
        List<Citizen> priority = waiting.stream()
                .filter(c -> c.getCategory() == Category.PRIORITY_ASSISTANCE).toList();
        List<Citizen> served   = all.stream().filter(c -> c.getStatus() == Status.SERVED).toList();
        List<Citizen> skipped  = all.stream().filter(c -> c.getStatus() == Status.SKIPPED).toList();

        double avgWait = waiting.isEmpty() ? 0.0 :
                waiting.stream().mapToInt(Citizen::getWaitMinutes).average().orElse(0.0);

        // Peek at next without consuming
        Optional<Citizen> nextOpt = scheduler.peekNext();

        List<CitizenResponse> queueDto = all.stream()
                .map(CitizenResponse::from)
                .collect(Collectors.toList());

        return QueueStatsResponse.builder()
                .waitingCount((int) waiting.size())
                .priorityCount((int) priority.size())
                .servedToday(servedBeforeSession + served.size())
                .skippedToday(skipped.size())
                .averageWaitMin(Math.round(avgWait * 10.0) / 10.0)
                .currentToken(currentCitizen != null ? currentCitizen.getToken() : null)
                .nextToken(nextOpt.map(Citizen::getToken).orElse(null))
                .currentCounter(currentCounter)
                .queue(queueDto)
                .build();
    }

    // ------------------------------------------------------------------
    // Reset (Demo / End-of-day)
    // ------------------------------------------------------------------

    /**
     * Resets the entire queue to the demo seed data that the frontend expects on
     * a fresh load (mirrors the JS {@code resetData()} function).
     *
     * @return success ApiResponse
     */
    public ApiResponse<QueueStatsResponse> resetDemo() {
        // Clear live state
        scheduler.reset();
        masterMap.clear();
        tokenCounter.set(TOKEN_START);
        arrivalCounter.set(1);
        currentCitizen       = null;
        currentCounter       = 1;
        servedBeforeSession  = 3; // mirrors the JS initialiser

        // Seed with demo citizens (same as the JS initialQueue)
        seedDemoCitizen("Ravi Kumar",    ServiceType.DOCUMENT_SERVICES,    Category.PRIORITY_ASSISTANCE, 12);
        seedDemoCitizen("Meena Devi",    ServiceType.GENERAL_SERVICES,     Category.STANDARD,            8);
        seedDemoCitizen("Arun Kumar",    ServiceType.ACCESSIBILITY_SUPPORT, Category.PRIORITY_ASSISTANCE, 17);
        seedDemoCitizen("Priya S",       ServiceType.GENERAL_SERVICES,     Category.STANDARD,            4);

        return ApiResponse.ok("Demo data restored successfully.", getStats());
    }

    // ------------------------------------------------------------------
    // Wait-time helpers
    // ------------------------------------------------------------------

    /**
     * Refreshes wait-time estimates for all currently WAITING citizens.
     * Called after each callNext / markServed so the frontend displays
     * updated wait values in real time.
     */
    public void recalculateWaitTimes() {
        List<Citizen> waiting = masterMap.values().stream()
                .filter(Citizen::isWaiting)
                .sorted(Comparator.comparingInt(Citizen::getArrivalOrder))
                .toList();

        // Each citizen's estimated wait = their position × base service time
        for (int i = 0; i < waiting.size(); i++) {
            waiting.get(i).setWaitMinutes((i + 1) * BASE_SERVICE_MINUTES);
        }

        // Priority citizens bump non-priority; re-sort after update
        List<Citizen> priority = waiting.stream()
                .filter(c -> c.getCategory() == Category.PRIORITY_ASSISTANCE)
                .toList();
        List<Citizen> standard = waiting.stream()
                .filter(c -> c.getCategory() == Category.STANDARD)
                .toList();

        for (int i = 0; i < priority.size(); i++) {
            priority.get(i).setWaitMinutes((i + 1) * BASE_SERVICE_MINUTES);
        }
        for (int i = 0; i < standard.size(); i++) {
            standard.get(i).setWaitMinutes((priority.size() + i + 1) * BASE_SERVICE_MINUTES);
        }

        // Rebuild the heap so the updated waits are reflected in ordering
        // (CitizenPriorityQueue.rebuild() is O(n))
        scheduler.rebuildHeap();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Estimates wait for a newly registered citizen based on queue depth. */
    private int estimateWait(Citizen citizen) {
        long aheadCount = masterMap.values().stream()
                .filter(Citizen::isWaiting)
                .filter(c -> {
                    if (c.getCategory() == Category.PRIORITY_ASSISTANCE
                            && citizen.getCategory() == Category.STANDARD) return true;
                    if (c.getCategory() == citizen.getCategory())
                        return c.getArrivalOrder() < citizen.getArrivalOrder();
                    return false;
                })
                .count();
        return (int) aheadCount * BASE_SERVICE_MINUTES;
    }

    /** Seeds a citizen with a preset wait time for demo purposes. */
    private void seedDemoCitizen(String name, ServiceType service, Category category, int wait) {
        String  token   = TOKEN_PREFIX + tokenCounter.getAndIncrement();
        int     arrival = arrivalCounter.getAndIncrement();
        Citizen citizen = new Citizen(token, name, service, category, arrival);
        citizen.setWaitMinutes(wait);
        masterMap.put(token, citizen);
        scheduler.enqueue(citizen);
    }

    /** Parse service string from frontend (case-insensitive). */
    private ServiceType parseService(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "general services",     "general"       -> ServiceType.GENERAL_SERVICES;
            case "document services",    "document"      -> ServiceType.DOCUMENT_SERVICES;
            case "accessibility support","accessibility" -> ServiceType.ACCESSIBILITY_SUPPORT;
            default -> null;
        };
    }

    /** Parse category string from frontend (case-insensitive). */
    private Category parseCategory(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "priority assistance", "priority" -> Category.PRIORITY_ASSISTANCE;
            case "standard",            "normal"   -> Category.STANDARD;
            default -> null;
        };
    }
}
