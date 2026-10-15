package com.queuecare.dsa;

import com.queuecare.model.Citizen;
import com.queuecare.model.Citizen.Category;
import com.queuecare.model.Citizen.Status;

import java.util.Optional;

/**
 * QueueScheduler – orchestrates {@link CitizenPriorityQueue} and {@link CustomQueue}
 * to implement the QueueCare scheduling policy.
 *
 * Policy (evaluated in order):
 *   1. PRIORITY_ASSISTANCE always before STANDARD
 *   2. Longer wait time served first (within same category)
 *   3. Arrival order breaks ties (FIFO within same priority + wait)
 *
 * The priority heap handles the full policy via its comparator.
 * The FIFO standard lane demonstrates CustomQueue usage alongside the heap.
 */
public class QueueScheduler {

    private final CitizenPriorityQueue priorityHeap     = new CitizenPriorityQueue();
    private final CustomQueue<Citizen> standardFifoLane = new CustomQueue<>();

    // ── Enqueue ──────────────────────────────────────────────────────
    public void enqueue(Citizen citizen) {
        if (citizen == null)                                   throw new IllegalArgumentException("Null citizen");
        if (citizen.getStatus() != Status.WAITING)            throw new IllegalStateException("Citizen must be WAITING");
        priorityHeap.insert(citizen);
        if (citizen.getCategory() == Category.STANDARD)       standardFifoLane.enqueue(citizen);
    }

    // ── Peek next (non-destructive) ──────────────────────────────────
    public Optional<Citizen> peekNext() {
        return findNextWaiting();
    }

    // ── Call next ────────────────────────────────────────────────────
    public Optional<Citizen> callNext() {
        Optional<Citizen> next = findNextWaiting();
        next.ifPresent(c -> {
            c.setStatus(Status.CALLED);
            priorityHeap.remove(c.getToken());
            if (c.getCategory() == Category.STANDARD)
                standardFifoLane.removeIf(s -> s.getToken().equals(c.getToken()));
        });
        return next;
    }

    // ── Skip ─────────────────────────────────────────────────────────
    public boolean skip(String token) {
        Optional<Citizen> found = priorityHeap.remove(token);
        found.ifPresent(c -> {
            c.setStatus(Status.SKIPPED);
            if (c.getCategory() == Category.STANDARD)
                standardFifoLane.removeIf(s -> s.getToken().equals(token));
        });
        return found.isPresent();
    }

    // ── Stats ─────────────────────────────────────────────────────────
    public long countWaiting() {
        return priorityHeap.toList().stream().filter(Citizen::isWaiting).count();
    }

    public long countPriorityWaiting() {
        return priorityHeap.toList().stream()
                .filter(c -> c.isWaiting() && c.getCategory() == Category.PRIORITY_ASSISTANCE)
                .count();
    }

    public void rebuildHeap() { priorityHeap.rebuild(); }
    public boolean isEmpty()  { return priorityHeap.isEmpty(); }

    public void reset() {
        priorityHeap.clear();
        standardFifoLane.clear();
    }

    // ── Private ──────────────────────────────────────────────────────
    private Optional<Citizen> findNextWaiting() {
        while (!priorityHeap.isEmpty()) {
            Citizen top = priorityHeap.peekMin();
            if (top.isWaiting()) return Optional.of(top);
            priorityHeap.extractMin(); // evict stale entry
        }
        return Optional.empty();
    }

    // ── DSA Visualizer snapshots ──────────────────────────────────────

    /**
     * Returns the heap array in its raw internal order (0-based).
     * Index 0 = root (highest priority), parent(i) = (i-1)/2.
     * Used by the frontend heap-tree visualizer.
     */
    public java.util.List<Citizen> getHeapSnapshot() {
        return priorityHeap.toList();
    }

    /**
     * Returns the FIFO standard lane in enqueue order (head = index 0).
     * Used by the frontend FIFO lane strip visualizer.
     */
    public java.util.List<Citizen> getFifoSnapshot() {
        return standardFifoLane.toList();
    }
}
