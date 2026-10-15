package dsa;

import model.Citizen;
import model.Citizen.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * CitizenPriorityQueue – a manually-implemented binary min-heap that orders
 * {@link Citizen} objects by the following scheduling policy:
 *
 * <ol>
 *   <li>PRIORITY_ASSISTANCE citizens always come before STANDARD ones.</li>
 *   <li>Within the same category, the citizen with the <em>longer</em> wait
 *       time is served first (longest-wait-first fairness).</li>
 *   <li>Arrival order breaks remaining ties (first-come-first-served).</li>
 * </ol>
 *
 * <p>Internally the heap is stored as a dynamic array (ArrayList) so the
 * structure never needs resizing logic to be written by hand.  All heap
 * invariant maintenance is done explicitly via sift-up / sift-down.
 *
 * <ul>
 *   <li>{@code insert}    – O(log n)</li>
 *   <li>{@code extractMin} – O(log n)</li>
 *   <li>{@code peekMin}   – O(1)</li>
 *   <li>{@code remove(token)} – O(n) search + O(log n) fix-up</li>
 * </ul>
 */
public class CitizenPriorityQueue {

    // ---------------------------------------------------------------
    // Heap storage  (index 0 = highest-priority / "min")
    // ---------------------------------------------------------------
    private final List<Citizen> heap = new ArrayList<>();

    // ---------------------------------------------------------------
    // Comparison helper
    // ---------------------------------------------------------------

    /**
     * Returns a negative number when {@code a} should be served BEFORE {@code b}.
     * A "smaller" compare value means higher priority in this min-heap.
     */
    private int compare(Citizen a, Citizen b) {
        // Rule 1 – PRIORITY_ASSISTANCE wins
        int catA = (a.getCategory() == Category.PRIORITY_ASSISTANCE) ? 0 : 1;
        int catB = (b.getCategory() == Category.PRIORITY_ASSISTANCE) ? 0 : 1;
        if (catA != catB) return catA - catB;

        // Rule 2 – longer waiter wins (descending wait → negate for min-heap)
        if (a.getWaitMinutes() != b.getWaitMinutes())
            return b.getWaitMinutes() - a.getWaitMinutes();   // higher wait = lower "min" index

        // Rule 3 – arrive earlier = serve earlier
        return Integer.compare(a.getArrivalOrder(), b.getArrivalOrder());
    }

    // ---------------------------------------------------------------
    // Index helpers
    // ---------------------------------------------------------------
    private int parent(int i) { return (i - 1) / 2; }
    private int left(int i)   { return 2 * i + 1; }
    private int right(int i)  { return 2 * i + 2; }

    private void swap(int i, int j) {
        Citizen tmp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, tmp);
    }

    // ---------------------------------------------------------------
    // Heap maintenance
    // ---------------------------------------------------------------

    /** After inserting at the end, bubble the element up to restore heap property. */
    private void siftUp(int i) {
        while (i > 0) {
            int p = parent(i);
            if (compare(heap.get(i), heap.get(p)) < 0) {
                swap(i, p);
                i = p;
            } else {
                break;
            }
        }
    }

    /** After replacing the root, push it down to restore heap property. */
    private void siftDown(int i) {
        int n = heap.size();
        while (true) {
            int smallest = i;
            int l = left(i);
            int r = right(i);

            if (l < n && compare(heap.get(l), heap.get(smallest)) < 0) smallest = l;
            if (r < n && compare(heap.get(r), heap.get(smallest)) < 0) smallest = r;

            if (smallest == i) break;
            swap(i, smallest);
            i = smallest;
        }
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Inserts a citizen into the heap in O(log n).
     *
     * @throws IllegalArgumentException if citizen is null
     */
    public void insert(Citizen citizen) {
        if (citizen == null) throw new IllegalArgumentException("Citizen must not be null");
        heap.add(citizen);
        siftUp(heap.size() - 1);
    }

    /**
     * Removes and returns the highest-priority citizen in O(log n).
     *
     * @throws NoSuchElementException if the heap is empty
     */
    public Citizen extractMin() {
        if (isEmpty()) throw new NoSuchElementException("Priority queue is empty");

        Citizen min = heap.get(0);

        int last = heap.size() - 1;
        heap.set(0, heap.get(last));
        heap.remove(last);

        if (!heap.isEmpty()) siftDown(0);

        return min;
    }

    /**
     * Peeks at the highest-priority citizen without removing it in O(1).
     *
     * @throws NoSuchElementException if the heap is empty
     */
    public Citizen peekMin() {
        if (isEmpty()) throw new NoSuchElementException("Priority queue is empty");
        return heap.get(0);
    }

    /**
     * Removes the citizen with the given token in O(n) search + O(log n) fix-up.
     * This is needed for skip/cancel operations.
     *
     * @return the removed Citizen wrapped in Optional, or {@link Optional#empty()}
     */
    public Optional<Citizen> remove(String token) {
        if (token == null) return Optional.empty();

        for (int i = 0; i < heap.size(); i++) {
            if (heap.get(i).getToken().equals(token)) {
                Citizen removed = heap.get(i);
                int last = heap.size() - 1;

                if (i == last) {
                    heap.remove(last);
                } else {
                    heap.set(i, heap.get(last));
                    heap.remove(last);
                    // restore heap – element may need to go up or down
                    siftUp(i);
                    siftDown(i);
                }
                return Optional.of(removed);
            }
        }
        return Optional.empty();
    }

    /**
     * Re-heapifies the entire heap in O(n).  Call this after bulk wait-time
     * updates so the ordering stays correct.
     */
    public void rebuild() {
        // Floyd's build-heap algorithm: sift down from last non-leaf to root
        for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
            siftDown(i);
        }
    }

    /**
     * Returns an immutable snapshot of all citizens in the heap
     * (order is heap-internal, NOT guaranteed to be sorted).
     * Use {@code extractMin} repeatedly for sorted output.
     */
    public List<Citizen> toList() {
        return List.copyOf(heap);
    }

    /** Returns {@code true} if the heap contains no elements. */
    public boolean isEmpty() { return heap.isEmpty(); }

    /** Returns the number of citizens in the heap. */
    public int size() { return heap.size(); }

    /** Clears all citizens from the heap. */
    public void clear() { heap.clear(); }

    @Override
    public String toString() {
        return "CitizenPriorityQueue{size=" + heap.size() + "}";
    }
}
