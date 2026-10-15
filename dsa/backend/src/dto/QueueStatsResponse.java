package dto;

import java.util.List;

/**
 * QueueStatsResponse – the analytics payload consumed by the Overview section
 * of the frontend (stats cards + activity bars).
 *
 * <pre>
 * {
 *   "waitingCount"    : 4,
 *   "priorityCount"   : 1,
 *   "servedToday"     : 7,
 *   "averageWaitMin"  : 9,
 *   "skippedToday"    : 2,
 *   "currentToken"    : "Q101",   // null when no active citizen
 *   "nextToken"       : "Q103",   // null when queue is empty
 *   "currentCounter"  : 1,
 *   "queue"           : [ ... ]   // full citizen list
 * }
 * </pre>
 */
public class QueueStatsResponse {

    private int               waitingCount;
    private int               priorityCount;
    private int               servedToday;
    private int               skippedToday;
    private double            averageWaitMin;
    private String            currentToken;
    private String            nextToken;
    private int               currentCounter;
    private List<CitizenResponse> queue;

    // ---------------------------------------------------------------
    // Builder pattern for clean construction in QueueService
    // ---------------------------------------------------------------
    public static Builder builder() { return new Builder(); }

    private QueueStatsResponse() {}

    public static final class Builder {
        private final QueueStatsResponse obj = new QueueStatsResponse();

        public Builder waitingCount(int v)       { obj.waitingCount    = v; return this; }
        public Builder priorityCount(int v)      { obj.priorityCount   = v; return this; }
        public Builder servedToday(int v)        { obj.servedToday     = v; return this; }
        public Builder skippedToday(int v)       { obj.skippedToday    = v; return this; }
        public Builder averageWaitMin(double v)  { obj.averageWaitMin  = v; return this; }
        public Builder currentToken(String v)    { obj.currentToken    = v; return this; }
        public Builder nextToken(String v)       { obj.nextToken       = v; return this; }
        public Builder currentCounter(int v)     { obj.currentCounter  = v; return this; }
        public Builder queue(List<CitizenResponse> v) { obj.queue = v; return this; }

        public QueueStatsResponse build() { return obj; }
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------
    public int               getWaitingCount()   { return waitingCount; }
    public int               getPriorityCount()  { return priorityCount; }
    public int               getServedToday()    { return servedToday; }
    public int               getSkippedToday()   { return skippedToday; }
    public double            getAverageWaitMin() { return averageWaitMin; }
    public String            getCurrentToken()   { return currentToken; }
    public String            getNextToken()      { return nextToken; }
    public int               getCurrentCounter() { return currentCounter; }
    public List<CitizenResponse> getQueue()      { return queue; }
}
