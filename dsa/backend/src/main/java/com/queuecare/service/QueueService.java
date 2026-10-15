package com.queuecare.service;

import com.queuecare.dsa.QueueScheduler;
import com.queuecare.dto.ApiResponse;
import com.queuecare.dto.CitizenResponse;
import com.queuecare.dto.DSAStateResponse;
import com.queuecare.dto.QueueStatsResponse;
import com.queuecare.dto.RegisterRequest;
import com.queuecare.model.Citizen;
import com.queuecare.model.Citizen.Category;
import com.queuecare.model.Citizen.ServiceType;
import com.queuecare.model.Citizen.Status;
import com.queuecare.repository.CitizenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * QueueService – core business logic.
 *
 * Dual-layer architecture:
 *  • In-memory: DSA structures (CitizenPriorityQueue + CustomQueue via QueueScheduler)
 *               for O(log n) scheduling decisions at runtime.
 *  • Persistent: Spring Data JPA → MySQL for durability, history, and analytics.
 *
 * On startup (triggered by DataSeeder) the scheduler is re-hydrated from
 * today's WAITING rows in MySQL so state survives server restarts.
 */
@Service
public class QueueService {

    // ── Constants ─────────────────────────────────────────────────────
    private static final int    BASE_SERVICE_MIN = 5;
    private static final String TOKEN_PREFIX     = "Q";
    private static final int    TOKEN_START      = 101;

    // ── Dependencies ──────────────────────────────────────────────────
    private final CitizenRepository citizenRepo;
    private final QueueScheduler    scheduler;

    // ── In-memory registry (token → Citizen) ─────────────────────────
    private final Map<String, Citizen> registry = Collections.synchronizedMap(new LinkedHashMap<>());

    // ── Atomic counters ───────────────────────────────────────────────
    private final AtomicInteger tokenCounter   = new AtomicInteger(TOKEN_START);
    private final AtomicInteger arrivalCounter = new AtomicInteger(1);

    // ── Active citizen state ──────────────────────────────────────────
    private volatile Citizen currentCitizen;
    private volatile int     currentCounter = 1;

    public QueueService(CitizenRepository citizenRepo) {
        this.citizenRepo = citizenRepo;
        this.scheduler   = new QueueScheduler();
    }

    // ── Hydrate from DB on startup ────────────────────────────────────
    /**
     * Called by DataSeeder at startup.
     * Loads today's WAITING citizens from MySQL back into the in-memory scheduler.
     */
    @Transactional(readOnly = true)
    public void hydrateFromDatabase() {
        LocalDate today = LocalDate.now();
        List<Citizen> todayWaiting = citizenRepo.findByStatusAndSessionDate(Status.WAITING, today);

        for (Citizen c : todayWaiting) {
            registry.put(c.getToken(), c);
            scheduler.enqueue(c);

            // Keep counters ahead of existing tokens
            String tokenNum = c.getToken().replaceAll("[^0-9]", "");
            if (!tokenNum.isEmpty()) {
                int num = Integer.parseInt(tokenNum);
                tokenCounter.accumulateAndGet(num + 1, Math::max);
            }
            arrivalCounter.accumulateAndGet(c.getArrivalOrder() + 1, Math::max);
        }
    }

    // ── Register ──────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<CitizenResponse> register(RegisterRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > 60)
            return ApiResponse.error("Name must be 2–60 characters.");

        ServiceType serviceType = parseService(request.getService());
        if (serviceType == null)
            return ApiResponse.error("Unknown service: " + request.getService());

        Category category = parseCategory(request.getCategory());
        if (category == null)
            return ApiResponse.error("Unknown category: " + request.getCategory());

        String token   = TOKEN_PREFIX + tokenCounter.getAndIncrement();
        int    arrival = arrivalCounter.getAndIncrement();

        Citizen citizen = new Citizen(token, name, serviceType, category, arrival);
        citizen.setWaitMinutes(estimateWait(citizen));

        // Persist to MySQL
        citizenRepo.save(citizen);

        // Add to in-memory structures
        registry.put(token, citizen);
        scheduler.enqueue(citizen);

        return ApiResponse.ok("Token " + token + " generated.", CitizenResponse.from(citizen));
    }

    // ── Call Next ─────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<CitizenResponse> callNext() {
        // Abandon current un-served citizen
        if (currentCitizen != null && currentCitizen.isCalled()) {
            currentCitizen.setStatus(Status.SKIPPED);
            citizenRepo.save(currentCitizen);
        }

        Optional<Citizen> next = scheduler.callNext();
        if (next.isEmpty()) {
            currentCitizen = null;
            return ApiResponse.error("No citizens are waiting.");
        }

        currentCitizen = next.get();
        currentCounter = 1;
        citizenRepo.save(currentCitizen);

        return ApiResponse.ok(
                currentCitizen.getName() + " — proceed to Counter " + currentCounter,
                CitizenResponse.from(currentCitizen));
    }

    // ── Call by Token ─────────────────────────────────────────────────
    @Transactional
    public ApiResponse<CitizenResponse> callByToken(String token) {
        Citizen citizen = registry.get(token);
        if (citizen == null) citizen = citizenRepo.findByToken(token).orElse(null);
        if (citizen == null)         return ApiResponse.error("Token " + token + " not found.");
        if (!citizen.isWaiting())    return ApiResponse.error(token + " is not WAITING (status: " + citizen.getStatus() + ").");

        if (currentCitizen != null && currentCitizen.isCalled()) {
            currentCitizen.setStatus(Status.SKIPPED);
            citizenRepo.save(currentCitizen);
        }

        scheduler.skip(token); // remove from heap
        citizen.setStatus(Status.CALLED);
        currentCitizen = citizen;
        currentCounter = 1;
        citizenRepo.save(citizen);

        return ApiResponse.ok(citizen.getName() + " — proceed to Counter " + currentCounter, CitizenResponse.from(citizen));
    }

    // ── Mark Served ───────────────────────────────────────────────────
    @Transactional
    public ApiResponse<CitizenResponse> markServed() {
        if (currentCitizen == null || !currentCitizen.isCalled())
            return ApiResponse.error("No citizen is currently being served.");

        currentCitizen.setStatus(Status.SERVED);
        citizenRepo.save(currentCitizen);
        Citizen served = currentCitizen;
        currentCitizen = null;
        recalculateWaitTimes();
        return ApiResponse.ok(served.getName() + " served successfully.", CitizenResponse.from(served));
    }

    // ── Skip ──────────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<CitizenResponse> skip(String token) {
        Citizen citizen = registry.get(token);
        if (citizen == null) citizen = citizenRepo.findByToken(token).orElse(null);
        if (citizen == null) return ApiResponse.error("Token " + token + " not found.");
        if (citizen.getStatus() != Status.WAITING && citizen.getStatus() != Status.CALLED)
            return ApiResponse.error(token + " cannot be skipped (status: " + citizen.getStatus() + ").");

        scheduler.skip(token);
        citizen.setStatus(Status.SKIPPED);
        citizenRepo.save(citizen);

        if (currentCitizen != null && currentCitizen.getToken().equals(token)) currentCitizen = null;
        return ApiResponse.ok("Citizen " + token + " skipped.", CitizenResponse.from(citizen));
    }

    // ── Get Ticket by Token ───────────────────────────────────────────
    @Transactional(readOnly = true)
    public ApiResponse<CitizenResponse> getTicketByToken(String token) {
        if (token == null || token.isBlank()) {
            return ApiResponse.error("Token must not be blank.");
        }
        String cleanToken = token.trim().toUpperCase();
        Citizen citizen = registry.get(cleanToken);
        if (citizen == null) {
            citizen = citizenRepo.findByToken(cleanToken).orElse(null);
        }
        if (citizen == null) {
            return ApiResponse.error("Ticket with token " + cleanToken + " was not found.");
        }
        return ApiResponse.ok("Ticket details retrieved.", CitizenResponse.from(citizen));
    }

    // ── Stats ─────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public QueueStatsResponse getStats() {
        LocalDate today = LocalDate.now();

        // Pull today's full list from DB for accurate counts
        List<Citizen> all = citizenRepo.findBySessionDateOrderByArrivalOrderAsc(today);

        // Merge in-memory unsaved state (newly registered in this session)
        registry.values().forEach(c -> {
            if (all.stream().noneMatch(r -> r.getToken().equals(c.getToken()))) all.add(c);
        });

        List<Citizen> waiting  = all.stream().filter(Citizen::isWaiting).toList();
        List<Citizen> priority = waiting.stream().filter(c -> c.getCategory() == Category.PRIORITY_ASSISTANCE).toList();
        List<Citizen> served   = all.stream().filter(c -> c.getStatus() == Status.SERVED).toList();
        List<Citizen> skipped  = all.stream().filter(c -> c.getStatus() == Status.SKIPPED).toList();

        double avgWait = waiting.isEmpty() ? 0 :
                waiting.stream().mapToInt(Citizen::getWaitMinutes).average().orElse(0);

        Optional<Citizen> nextOpt = scheduler.peekNext();

        List<CitizenResponse> queueDto = all.stream()
                .map(CitizenResponse::from)
                .collect(Collectors.toList());

        return QueueStatsResponse.builder()
                .waitingCount(waiting.size())
                .priorityCount(priority.size())
                .servedToday(served.size())
                .skippedToday(skipped.size())
                .averageWaitMin(Math.round(avgWait * 10.0) / 10.0)
                .currentToken(currentCitizen != null ? currentCitizen.getToken() : null)
                .nextToken(nextOpt.map(Citizen::getToken).orElse(null))
                .currentCounter(currentCounter)
                .queue(queueDto)
                .build();
    }

    // ── Reset Demo ────────────────────────────────────────────────────
    @Transactional
    public ApiResponse<QueueStatsResponse> resetDemo() {
        // Wipe today's data from MySQL
        LocalDate today = LocalDate.now();
        List<Citizen> todayAll = citizenRepo.findBySessionDateOrderByArrivalOrderAsc(today);
        citizenRepo.deleteAll(todayAll);

        // Reset in-memory state
        scheduler.reset();
        registry.clear();
        tokenCounter.set(TOKEN_START);
        arrivalCounter.set(1);
        currentCitizen = null;
        currentCounter = 1;

        // Seed demo citizens
        seedDemoCitizen("Ravi Kumar",   ServiceType.DOCUMENT_SERVICES,    Category.PRIORITY_ASSISTANCE, 12);
        seedDemoCitizen("Meena Devi",   ServiceType.GENERAL_SERVICES,     Category.STANDARD,            8);
        seedDemoCitizen("Arun Kumar",   ServiceType.ACCESSIBILITY_SUPPORT, Category.PRIORITY_ASSISTANCE, 17);
        seedDemoCitizen("Priya S",      ServiceType.GENERAL_SERVICES,     Category.STANDARD,            4);

        return ApiResponse.ok("Demo data restored.", getStats());
    }

    // ── Wait-time recalculation ───────────────────────────────────────
    public void recalculateWaitTimes() {
        List<Citizen> priority = registry.values().stream()
                .filter(c -> c.isWaiting() && c.getCategory() == Category.PRIORITY_ASSISTANCE)
                .sorted(Comparator.comparingInt(Citizen::getArrivalOrder)).toList();
        List<Citizen> standard = registry.values().stream()
                .filter(c -> c.isWaiting() && c.getCategory() == Category.STANDARD)
                .sorted(Comparator.comparingInt(Citizen::getArrivalOrder)).toList();

        for (int i = 0; i < priority.size(); i++) priority.get(i).setWaitMinutes((i + 1) * BASE_SERVICE_MIN);
        for (int i = 0; i < standard.size(); i++) standard.get(i).setWaitMinutes((priority.size() + i + 1) * BASE_SERVICE_MIN);

        scheduler.rebuildHeap();
    }

    // ── Private helpers ───────────────────────────────────────────────
    private int estimateWait(Citizen citizen) {
        long ahead = registry.values().stream()
                .filter(Citizen::isWaiting)
                .filter(c -> {
                    if (c.getCategory() == Category.PRIORITY_ASSISTANCE && citizen.getCategory() == Category.STANDARD) return true;
                    if (c.getCategory() == citizen.getCategory()) return c.getArrivalOrder() < citizen.getArrivalOrder();
                    return false;
                }).count();
        return (int) ahead * BASE_SERVICE_MIN;
    }

    @Transactional
    protected void seedDemoCitizen(String name, ServiceType service, Category category, int wait) {
        String token   = TOKEN_PREFIX + tokenCounter.getAndIncrement();
        int    arrival = arrivalCounter.getAndIncrement();
        Citizen c = new Citizen(token, name, service, category, arrival);
        c.setWaitMinutes(wait);
        citizenRepo.save(c);
        registry.put(token, c);
        scheduler.enqueue(c);
    }

    private ServiceType parseService(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "general services", "general"           -> ServiceType.GENERAL_SERVICES;
            case "document services", "document"         -> ServiceType.DOCUMENT_SERVICES;
            case "accessibility support","accessibility" -> ServiceType.ACCESSIBILITY_SUPPORT;
            default -> null;
        };
    }

    private Category parseCategory(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "priority assistance", "priority" -> Category.PRIORITY_ASSISTANCE;
            case "standard", "normal"              -> Category.STANDARD;
            default -> null;
        };
    }

    // ── DSA State (for visualizer) ────────────────────────────────────
    private volatile String lastDecision = "Queue initialized. Awaiting first call.";

    /**
     * Returns a snapshot of the internal DSA data structures for the
     * real-time visualizer in the frontend.
     *
     * <p>Heap nodes are returned in their actual heap-array order (index 0 = root).
     * The frontend uses the 0-based {@code heapIndex} to compute parent/child
     * relationships: parent(i) = (i-1)/2, left(i) = 2i+1, right(i) = 2i+2.
     */
    public DSAStateResponse getDSAState() {
        // Snapshot heap in array order
        java.util.List<Citizen> heapList = scheduler.getHeapSnapshot();
        java.util.List<DSAStateResponse.HeapNode> heapNodes = new java.util.ArrayList<>();
        for (int i = 0; i < heapList.size(); i++) {
            Citizen c = heapList.get(i);
            int depth = (int)(Math.log(i + 1) / Math.log(2)); // floor(log2(i+1))
            heapNodes.add(new DSAStateResponse.HeapNode(
                    i, depth,
                    c.getToken(), c.getName(),
                    c.getCategoryLabel(),
                    c.getWaitMinutes(), c.getArrivalOrder(),
                    capitaliseStatus(c.getStatus().name()),
                    i == 0
            ));
        }

        // FIFO lane snapshot (Standard citizens only)
        java.util.List<Citizen> fifo = scheduler.getFifoSnapshot();
        java.util.List<DSAStateResponse.FifoNode> fifoNodes = new java.util.ArrayList<>();
        for (int i = 0; i < fifo.size(); i++) {
            Citizen c = fifo.get(i);
            fifoNodes.add(new DSAStateResponse.FifoNode(i, c.getToken(), c.getName(), c.getWaitMinutes()));
        }

        // Build last decision string
        String nextToken = scheduler.peekNext().map(Citizen::getToken).orElse("—");
        String decision  = scheduler.peekNext()
                .map(c -> String.format("Next → %s (%s) | Wait: %d min | Arrival: #%d",
                        c.getToken(), c.getCategoryLabel(),
                        c.getWaitMinutes(), c.getArrivalOrder()))
                .orElse("Queue empty — no pending citizens.");

        return DSAStateResponse.builder()
                .heapNodes(heapNodes)
                .heapSize(heapList.size())
                .fifoLane(fifoNodes)
                .fifoSize(fifo.size())
                .schedulingPolicy("Priority Assistance → Longest Wait → Earliest Arrival")
                .lastDecision(decision)
                .algorithmComplexity("Insert: O(log n) · ExtractMin: O(log n) · PeekMin: O(1) · Remove: O(n+log n)")
                .build();
    }

    private static String capitaliseStatus(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}

