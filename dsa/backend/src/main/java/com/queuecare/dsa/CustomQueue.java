package com.queuecare.dsa;

import com.queuecare.model.Citizen;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * CustomQueue&lt;T&gt; – manually-implemented singly-linked FIFO queue.
 * Enqueue O(1) · Dequeue O(1) · Search/Remove O(n)
 */
public class CustomQueue<T> {

    private static final class Node<T> {
        T       data;
        Node<T> next;
        Node(T data) { this.data = data; }
    }

    private Node<T> head;
    private Node<T> tail;
    private int     size;

    /** O(1) — insert at tail. */
    public void enqueue(T element) {
        if (element == null) throw new IllegalArgumentException("Cannot enqueue null");
        Node<T> node = new Node<>(element);
        if (isEmpty()) { head = node; tail = node; }
        else           { tail.next = node; tail = node; }
        size++;
    }

    /** O(1) — remove from head. */
    public T dequeue() {
        if (isEmpty()) throw new NoSuchElementException("Queue is empty");
        T data = head.data;
        head   = head.next;
        if (head == null) tail = null;
        size--;
        return data;
    }

    /** O(1) — inspect head without removing. */
    public T peek() {
        if (isEmpty()) throw new NoSuchElementException("Queue is empty");
        return head.data;
    }

    /** O(n) — remove first element matching predicate. */
    public Optional<T> removeIf(Predicate<T> predicate) {
        if (isEmpty() || predicate == null) return Optional.empty();
        Node<T> cur = head, prev = null;
        while (cur != null) {
            if (predicate.test(cur.data)) {
                if (prev == null) { head = cur.next; if (head == null) tail = null; }
                else              { prev.next = cur.next; if (cur.next == null) tail = prev; }
                size--;
                return Optional.of(cur.data);
            }
            prev = cur; cur = cur.next;
        }
        return Optional.empty();
    }

    /** O(n) — find without removing. */
    public Optional<T> find(Predicate<T> predicate) {
        Node<T> cur = head;
        while (cur != null) {
            if (predicate.test(cur.data)) return Optional.of(cur.data);
            cur = cur.next;
        }
        return Optional.empty();
    }

    /** O(n) — immutable snapshot. */
    public List<T> toList() {
        List<T> list = new ArrayList<>(size);
        Node<T> cur  = head;
        while (cur != null) { list.add(cur.data); cur = cur.next; }
        return List.copyOf(list);
    }

    public boolean isEmpty() { return head == null; }
    public int     size()    { return size; }
    public void    clear()   { head = null; tail = null; size = 0; }
}
