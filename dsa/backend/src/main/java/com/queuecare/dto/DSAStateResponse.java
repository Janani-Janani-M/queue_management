package com.queuecare.dto;

import java.util.List;

/**
 * DSAStateResponse – payload returned by GET /api/queue/dsa-state.
 *
 * <p>Gives the frontend everything it needs to render:
 * <ul>
 *   <li>The binary heap as an array of indexed nodes (heap order)</li>
 *   <li>The FIFO standard lane in enqueue order</li>
 *   <li>The current scheduling policy description</li>
 *   <li>The last scheduling decision text</li>
 * </ul>
 */
public class DSAStateResponse {

    /** One node in the binary min-heap (stored at index {@code heapIndex}). */
    public static class HeapNode {
        private int    heapIndex;   // 0-based position in the heap array
        private int    depth;       // tree depth (root = 0)
        private String token;
        private String name;
        private String category;   // "Priority Assistance" | "Standard"
        private int    wait;       // wait minutes
        private int    arrival;    // arrival order
        private String status;     // "Waiting" | "Called" etc.
        private boolean isNext;    // true = this is the root (would be served next)

        public HeapNode() {}

        public HeapNode(int heapIndex, int depth, String token, String name,
                        String category, int wait, int arrival, String status, boolean isNext) {
            this.heapIndex = heapIndex;
            this.depth     = depth;
            this.token     = token;
            this.name      = name;
            this.category  = category;
            this.wait      = wait;
            this.arrival   = arrival;
            this.status    = status;
            this.isNext    = isNext;
        }

        public int    getHeapIndex() { return heapIndex; }
        public int    getDepth()     { return depth; }
        public String getToken()     { return token; }
        public String getName()      { return name; }
        public String getCategory()  { return category; }
        public int    getWait()      { return wait; }
        public int    getArrival()   { return arrival; }
        public String getStatus()    { return status; }
        public boolean isIsNext()    { return isNext; }
    }

    /** One element in the FIFO standard lane. */
    public static class FifoNode {
        private int    position;   // 0 = head (dequeue point)
        private String token;
        private String name;
        private int    wait;

        public FifoNode() {}

        public FifoNode(int position, String token, String name, int wait) {
            this.position = position;
            this.token    = token;
            this.name     = name;
            this.wait     = wait;
        }

        public int    getPosition() { return position; }
        public String getToken()    { return token; }
        public String getName()     { return name; }
        public int    getWait()     { return wait; }
    }

    // ── Fields ──────────────────────────────────────────────────────────
    private List<HeapNode> heapNodes;
    private int            heapSize;
    private List<FifoNode> fifoLane;
    private int            fifoSize;
    private String         schedulingPolicy;
    private String         lastDecision;
    private String         algorithmComplexity;

    public DSAStateResponse() {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<HeapNode> heapNodes;
        private int            heapSize;
        private List<FifoNode> fifoLane;
        private int            fifoSize;
        private String         schedulingPolicy;
        private String         lastDecision;
        private String         algorithmComplexity;

        public Builder heapNodes(List<HeapNode> v)        { heapNodes = v;            return this; }
        public Builder heapSize(int v)                    { heapSize = v;             return this; }
        public Builder fifoLane(List<FifoNode> v)         { fifoLane = v;             return this; }
        public Builder fifoSize(int v)                    { fifoSize = v;             return this; }
        public Builder schedulingPolicy(String v)         { schedulingPolicy = v;     return this; }
        public Builder lastDecision(String v)             { lastDecision = v;         return this; }
        public Builder algorithmComplexity(String v)      { algorithmComplexity = v;  return this; }

        public DSAStateResponse build() {
            DSAStateResponse r = new DSAStateResponse();
            r.heapNodes          = heapNodes;
            r.heapSize           = heapSize;
            r.fifoLane           = fifoLane;
            r.fifoSize           = fifoSize;
            r.schedulingPolicy   = schedulingPolicy;
            r.lastDecision       = lastDecision;
            r.algorithmComplexity = algorithmComplexity;
            return r;
        }
    }

    public List<HeapNode> getHeapNodes()          { return heapNodes; }
    public int            getHeapSize()           { return heapSize; }
    public List<FifoNode> getFifoLane()           { return fifoLane; }
    public int            getFifoSize()           { return fifoSize; }
    public String         getSchedulingPolicy()   { return schedulingPolicy; }
    public String         getLastDecision()       { return lastDecision; }
    public String         getAlgorithmComplexity(){ return algorithmComplexity; }
}
