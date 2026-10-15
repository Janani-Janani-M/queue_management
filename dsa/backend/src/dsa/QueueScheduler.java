package dsa;

import model.Citizen;
import model.Citizen.Category;
import model.Citizen.Status;

import java.util.Optional;

/**
 * QueueScheduler – the core scheduling brain of QueueCare.
 *
 * <p>It owns <em>two</em> queues internally:
 * <ol>
 *   <li>A {@link CitizenPriorityQueue} – all waiting citizens ordered by priority.</li>
 *   <li>A {@link CustomQueue} – a FIFO shadow queue used only for Standard-category
 *       citizens when the priority heap is exhausted (demonstrates both DSA structures
 *       working together in one system).</li>
 * </ol>
 *
 * <p><strong>Scheduling policy (mirrors the frontend's getNextCitizen logic):</strong>
 * <ol>
 *   <li>If any PRIORITY_ASSISTANCE citizens are waiting → serve the one with the
 *       longest wait time; ties broken by arrival order.</li>
 *   <li>Otherwise serve the Standard citizen with the longest wait; ties by arrival.</li>
 *   <li>If the queue is empty, return {@link Optional#empty()}.</li>
 * </ol>
 *
 * <p>The priority heap handles both categories together through its comparator,
 * so a single {@code peekMin()} / {@code extractMin()} call always returns the
 * correct next citizen.
 */
public class QueueScheduler {

    // The heap satisfies the full scheduling policy on its own.
    private final CitizenPriorityQueue priorityHeap = new CitizenPriorityQueue();

    // FIFO lane: receives Standard citizens when enqueued – kept in parallel so
    // that if a caller ever wants "pure FIFO for Standard" it is available.
    // In normal flow the heap is the authoritative selector.
    private final CustomQueue<Citizen> standardFifoLane = new CustomQueue<>();

    // ---------------------------------------------------------------
    // Enqueue
    // ---------------------------------------------------------------

    /**
     * Registers a citizen into the scheduling system.
     *
     * <ul>
     *   <li>All citizens go into the priority heap.</li>
     *   <li>Standard citizens also enter the FIFO lane for demonstration.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if citizen is null or not in WAITING status
     */
    public void enqueue(Citizen citizen) {
        if (citizen == null)
            throw new IllegalArgumentException("Cannot schedule a null citizen");
        if (citizen.getStatus() != Status.WAITING)
            throw new IllegalStateException("Only WAITING citizens may be enqueued; got " + citizen.getStatus());

        priorityHeap.insert(citizen);

        if (citizen.getCategory() == Category.STANDARD) {
            standardFifoLane.enqueue(citizen);
        }
    }

    // ---------------------------------------------------------------
    // "Peek" – who is next without committing
    // ---------------------------------------------------------------

    /**
     * Returns the citizen who would be served next, without removing them.
     * O(1) from the heap root.
     *
     * @return Optional containing the next citizen, or empty if queue is empty
     */
    public Optional<Citizen> peekNext() {
        if (priorityHeap.isEmpty()) return Optional.empty();

        // Filter to only WAITING citizens at the top
        // (Called/Skipped ones may still be in the heap until explicitly removed)
        Citizen top = priorityHeap.peekMin();
        return top.isWaiting() ? Optional.of(top) : findNextWaiting();
    }

    // ---------------------------------------------------------------
    // "Call Next" – the primary scheduling action
    // ---------------------------------------------------------------

    /**
     * Selects and returns the next citizen to be served according to the
     * scheduling policy, changes their status to {@link Status#CALLED}, and
     * removes them from the heap in O(log n).
     *
     * @return Optional of the called Citizen, or empty if no one is waiting
     */
    public Optional<Citizen> callNext() {
        Optional<Citizen> next = findNextWaiting();
        next.ifPresent(c -> {
            c.setStatus(Status.CALLED);
            priorityHeap.remove(c.getToken());
            if (c.getCategory() == Category.STANDARD) {
                // drain FIFO lane past any non-waiting entries
                syncStandardLane(c.getToken());
            }
        });
        return next;
    }

    // ---------------------------------------------------------------
    // Skip
    // ---------------------------------------------------------------

    /**
     * Marks a citizen as SKIPPED and removes them from both data structures.
     * A skipped citizen goes to the back of the queue if they return
     * (handled by re-enqueuing from the service layer with a new token / reset
     * wait timer – not the scheduler's concern).
     *
     * @param token the citizen's token
     * @return true if found and skipped, false if not found
     */
    public boolean skip(String token) {
        Optional<Citizen> found = priorityHeap.remove(token);
        found.ifPresent(c -> {
            c.setStatus(Status.SKIPPED);
            if (c.getCategory() == Category.STANDARD) {
                standardFifoLane.removeIf(s -> s.getToken().equals(token));
            }
        });
        return found.isPresent();
    }

    // ---------------------------------------------------------------
    // Statistics helpers used by QueueService
    // ---------------------------------------------------------------

    /** Returns total number of citizens currently tracked by the heap. */
    public int totalTracked() { return priorityHeap.size(); }

    /** Returns true if no citizens are waiting. */
    public boolean isEmpty() { return priorityHeap.isEmpty(); }

    /** How many citizens in the heap are in WAITING status. */
    public long countWaiting() {
        return priorityHeap.toList().stream()
                .filter(Citizen::isWaiting)
                .count();
    }

    /** How many WAITING citizens are in the PRIORITY_ASSISTANCE lane. */
    public long countPriorityWaiting() {
        return priorityHeap.toList().stream()
                .filter(c -> c.isWaiting() && c.getCategory() == Category.PRIORITY_ASSISTANCE)
                .count();
    }

    /** Clears all internal data structures (e.g. end-of-day reset). */
    public void reset() {
        priorityHeap.clear();
        standardFifoLane.clear();
    }

    /**
     * Rebuilds the priority heap in O(n) after bulk wait-time updates.
     * Delegates to {@link CitizenPriorityQueue#rebuild()}.
     */
    public void rebuildHeap() {
        priorityHeap.rebuild();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Traverses the heap to find the highest-priority citizen who is still WAITING.
     * Stale CALLED/SERVED entries are lazily evicted.
     */
    private Optional<Citizen> findNextWaiting() {
        while (!priorityHeap.isEmpty()) {
            Citizen top = priorityHeap.peekMin();
            if (top.isWaiting()) {
                return Optional.of(top);
            }
            // Stale entry – evict it
            priorityHeap.extractMin();
        }
        return Optional.empty();
    }

    /** Removes the named token from the standard FIFO lane (best-effort). */
    private void syncStandardLane(String token) {
        standardFifoLane.removeIf(c -> c.getToken().equals(token));
    }
}
