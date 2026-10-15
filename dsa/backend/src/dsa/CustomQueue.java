package dsa;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * CustomQueue&lt;T&gt; – a generic, manually-implemented singly-linked FIFO queue.
 *
 * <p>This is NOT a wrapper around java.util.Queue; the node structure and all
 * pointer operations are written by hand to demonstrate DSA knowledge.
 *
 * <ul>
 *   <li>{@code enqueue} – O(1) insertion at tail</li>
 *   <li>{@code dequeue} – O(1) removal from head</li>
 *   <li>{@code peek}    – O(1) read of head element</li>
 *   <li>{@code remove(token)} – O(n) search-and-remove by token</li>
 *   <li>{@code toList} – O(n) snapshot for serialisation</li>
 * </ul>
 *
 * <p>Thread-safety is intentionally left to the service layer (the queue itself
 * is not synchronised so the inner workings remain readable).
 *
 * @param <T> element type stored in each node
 */
public class CustomQueue<T> {

    // ---------------------------------------------------------------
    // Inner node type
    // ---------------------------------------------------------------
    private static final class Node<T> {
        T       data;
        Node<T> next;

        Node(T data) {
            this.data = data;
            this.next = null;
        }
    }

    // ---------------------------------------------------------------
    // State
    // ---------------------------------------------------------------
    private Node<T> head;   // front of the queue (dequeue point)
    private Node<T> tail;   // back  of the queue (enqueue point)
    private int     size;

    // ---------------------------------------------------------------
    // Core FIFO operations
    // ---------------------------------------------------------------

    /**
     * Enqueue – adds {@code element} to the back of the queue in O(1).
     *
     * @throws IllegalArgumentException if element is null
     */
    public void enqueue(T element) {
        if (element == null) throw new IllegalArgumentException("Cannot enqueue null element");

        Node<T> newNode = new Node<>(element);

        if (isEmpty()) {
            head = newNode;
            tail = newNode;
        } else {
            tail.next = newNode;   // wire in at tail
            tail      = newNode;   // advance tail pointer
        }
        size++;
    }

    /**
     * Dequeue – removes and returns the element at the front of the queue in O(1).
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public T dequeue() {
        if (isEmpty()) throw new NoSuchElementException("Queue is empty – nothing to dequeue");

        T data   = head.data;
        head     = head.next;      // advance head pointer
        if (head == null) tail = null; // queue is now empty
        size--;
        return data;
    }

    /**
     * Peek – returns (without removing) the element at the front in O(1).
     *
     * @throws NoSuchElementException if the queue is empty
     */
    public T peek() {
        if (isEmpty()) throw new NoSuchElementException("Queue is empty – nothing to peek");
        return head.data;
    }

    // ---------------------------------------------------------------
    // Secondary operations
    // ---------------------------------------------------------------

    /**
     * Returns {@code true} if the queue contains no elements.
     */
    public boolean isEmpty() { return head == null; }

    /**
     * Returns the number of elements currently in the queue.
     */
    public int size() { return size; }

    /**
     * Removes the first element that satisfies the given predicate in O(n).
     *
     * @param predicate filter condition
     * @return the removed element wrapped in Optional, or {@link Optional#empty()}
     */
    public Optional<T> removeIf(Predicate<T> predicate) {
        if (isEmpty())    return Optional.empty();
        if (predicate == null) throw new IllegalArgumentException("predicate must not be null");

        Node<T> current  = head;
        Node<T> previous = null;

        while (current != null) {
            if (predicate.test(current.data)) {
                if (previous == null) {
                    // removing the head node
                    head = current.next;
                    if (head == null) tail = null;
                } else {
                    previous.next = current.next;
                    if (current.next == null) tail = previous; // removed the tail
                }
                size--;
                return Optional.of(current.data);
            }
            previous = current;
            current  = current.next;
        }
        return Optional.empty();
    }

    /**
     * Finds (without removing) the first element matching the predicate in O(n).
     */
    public Optional<T> find(Predicate<T> predicate) {
        if (predicate == null) throw new IllegalArgumentException("predicate must not be null");

        Node<T> current = head;
        while (current != null) {
            if (predicate.test(current.data)) return Optional.of(current.data);
            current = current.next;
        }
        return Optional.empty();
    }

    /**
     * Returns an immutable snapshot list of all elements in FIFO order in O(n).
     * Safe to iterate while the queue is live.
     */
    public List<T> toList() {
        List<T>  result  = new ArrayList<>(size);
        Node<T>  current = head;
        while (current != null) {
            result.add(current.data);
            current = current.next;
        }
        return List.copyOf(result);
    }

    /**
     * Convenience: counts how many elements satisfy the predicate in O(n).
     */
    public long countIf(Predicate<T> predicate) {
        if (predicate == null) throw new IllegalArgumentException("predicate must not be null");
        long count   = 0;
        Node<T> cur  = head;
        while (cur != null) {
            if (predicate.test(cur.data)) count++;
            cur = cur.next;
        }
        return count;
    }

    /** Removes all elements (resets the queue to an empty state). */
    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    @Override
    public String toString() {
        return "CustomQueue{size=" + size + ", elements=" + toList() + "}";
    }
}
