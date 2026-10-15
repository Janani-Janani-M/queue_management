package com.queuecare.dsa;

import com.queuecare.model.Citizen;
import com.queuecare.model.Citizen.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * CitizenPriorityQueue – binary min-heap for priority scheduling.
 *
 * Scheduling policy (comparator):
 *   1. PRIORITY_ASSISTANCE before STANDARD
 *   2. Longer wait time first (within same category)
 *   3. Earlier arrival order breaks ties
 *
 * Insert O(log n) · ExtractMin O(log n) · PeekMin O(1) · Remove O(n+log n)
 */
public class CitizenPriorityQueue {

    private final List<Citizen> heap = new ArrayList<>();

    // ── Comparator ──────────────────────────────────────────────────
    private int compare(Citizen a, Citizen b) {
        int catA = (a.getCategory() == Category.PRIORITY_ASSISTANCE) ? 0 : 1;
        int catB = (b.getCategory() == Category.PRIORITY_ASSISTANCE) ? 0 : 1;
        if (catA != catB) return catA - catB;
        if (a.getWaitMinutes() != b.getWaitMinutes()) return b.getWaitMinutes() - a.getWaitMinutes();
        return Integer.compare(a.getArrivalOrder(), b.getArrivalOrder());
    }

    // ── Heap indices ─────────────────────────────────────────────────
    private int parent(int i) { return (i - 1) / 2; }
    private int left(int i)   { return 2 * i + 1; }
    private int right(int i)  { return 2 * i + 2; }

    private void swap(int i, int j) {
        Citizen t = heap.get(i); heap.set(i, heap.get(j)); heap.set(j, t);
    }

    // ── Heap maintenance ─────────────────────────────────────────────
    private void siftUp(int i) {
        while (i > 0) {
            int p = parent(i);
            if (compare(heap.get(i), heap.get(p)) < 0) { swap(i, p); i = p; }
            else break;
        }
    }

    private void siftDown(int i) {
        int n = heap.size();
        while (true) {
            int s = i, l = left(i), r = right(i);
            if (l < n && compare(heap.get(l), heap.get(s)) < 0) s = l;
            if (r < n && compare(heap.get(r), heap.get(s)) < 0) s = r;
            if (s == i) break;
            swap(i, s); i = s;
        }
    }

    // ── Public API ───────────────────────────────────────────────────
    public void insert(Citizen c) {
        if (c == null) throw new IllegalArgumentException("Citizen must not be null");
        heap.add(c);
        siftUp(heap.size() - 1);
    }

    public Citizen extractMin() {
        if (isEmpty()) throw new NoSuchElementException("Heap is empty");
        Citizen min  = heap.get(0);
        int     last = heap.size() - 1;
        heap.set(0, heap.get(last));
        heap.remove(last);
        if (!heap.isEmpty()) siftDown(0);
        return min;
    }

    public Citizen peekMin() {
        if (isEmpty()) throw new NoSuchElementException("Heap is empty");
        return heap.get(0);
    }

    public Optional<Citizen> remove(String token) {
        for (int i = 0; i < heap.size(); i++) {
            if (heap.get(i).getToken().equals(token)) {
                Citizen removed = heap.get(i);
                int last = heap.size() - 1;
                if (i == last) { heap.remove(last); }
                else { heap.set(i, heap.get(last)); heap.remove(last); siftUp(i); siftDown(i); }
                return Optional.of(removed);
            }
        }
        return Optional.empty();
    }

    /** Floyd's O(n) build-heap – call after bulk wait-time update. */
    public void rebuild() {
        for (int i = (heap.size() / 2) - 1; i >= 0; i--) siftDown(i);
    }

    public List<Citizen> toList() { return List.copyOf(heap); }
    public boolean isEmpty()      { return heap.isEmpty(); }
    public int     size()         { return heap.size(); }
    public void    clear()        { heap.clear(); }
}
