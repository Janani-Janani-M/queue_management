package com.queuecare.dto;

import java.util.List;

public class QueueStatsResponse {

    private int    waitingCount;
    private int    priorityCount;
    private int    servedToday;
    private int    skippedToday;
    private double averageWaitMin;
    private String currentToken;
    private String nextToken;
    private int    currentCounter;
    private List<CitizenResponse> queue;

    private QueueStatsResponse() {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final QueueStatsResponse o = new QueueStatsResponse();
        public Builder waitingCount(int v)        { o.waitingCount    = v; return this; }
        public Builder priorityCount(int v)       { o.priorityCount   = v; return this; }
        public Builder servedToday(int v)         { o.servedToday     = v; return this; }
        public Builder skippedToday(int v)        { o.skippedToday    = v; return this; }
        public Builder averageWaitMin(double v)   { o.averageWaitMin  = v; return this; }
        public Builder currentToken(String v)     { o.currentToken    = v; return this; }
        public Builder nextToken(String v)        { o.nextToken       = v; return this; }
        public Builder currentCounter(int v)      { o.currentCounter  = v; return this; }
        public Builder queue(List<CitizenResponse> v) { o.queue = v; return this; }
        public QueueStatsResponse build()         { return o; }
    }

    public int    getWaitingCount()    { return waitingCount; }
    public int    getPriorityCount()   { return priorityCount; }
    public int    getServedToday()     { return servedToday; }
    public int    getSkippedToday()    { return skippedToday; }
    public double getAverageWaitMin()  { return averageWaitMin; }
    public String getCurrentToken()    { return currentToken; }
    public String getNextToken()       { return nextToken; }
    public int    getCurrentCounter()  { return currentCounter; }
    public List<CitizenResponse> getQueue() { return queue; }
}
