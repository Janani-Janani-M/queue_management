package model;

import java.time.LocalDateTime;

/**
 * Citizen – the core domain object for the QueueCare system.
 *
 * Every attribute maps directly to what the frontend already displays
 * (token, name, service, category, wait time, status).  Additional fields
 * (arrivalOrder, enqueuedAt, servedAt) are needed by the DSA layer for
 * correct priority / tie-breaking decisions.
 */
public class Citizen {

    // ---------------------------------------------------------------
    // Queue categories (matches frontend dropdown values)
    // ---------------------------------------------------------------
    public enum Category {
        PRIORITY_ASSISTANCE,   // elderly / accessibility needs → served first
        STANDARD               // regular walk-in
    }

    // ---------------------------------------------------------------
    // Lifecycle statuses  (matches frontend status badges)
    // ---------------------------------------------------------------
    public enum Status {
        WAITING,   // in the queue
        CALLED,    // called to a counter but not yet marked served
        SERVED,    // successfully completed
        SKIPPED    // staff explicitly skipped (missed call)
    }

    // ---------------------------------------------------------------
    // Service types (matches frontend select options)
    // ---------------------------------------------------------------
    public enum ServiceType {
        GENERAL_SERVICES,
        DOCUMENT_SERVICES,
        ACCESSIBILITY_SUPPORT
    }

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------
    private final String        token;          // e.g. "Q101"
    private final String        name;           // citizen full name
    private final ServiceType   service;        // what the citizen needs
    private final Category      category;       // determines queue lane
    private volatile Status     status;         // mutable lifecycle state
    private final int           arrivalOrder;   // monotonically increasing, used for tie-breaking
    private volatile int        waitMinutes;    // estimated wait in minutes (updated periodically)
    private final LocalDateTime enqueuedAt;     // wall-clock time of registration
    private volatile LocalDateTime servedAt;    // set when status → SERVED

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------
    public Citizen(String token,
                   String name,
                   ServiceType service,
                   Category category,
                   int arrivalOrder) {
        if (token == null || token.isBlank())  throw new IllegalArgumentException("Token must not be blank");
        if (name  == null || name.isBlank())   throw new IllegalArgumentException("Name must not be blank");
        if (service  == null)                  throw new IllegalArgumentException("ServiceType must not be null");
        if (category == null)                  throw new IllegalArgumentException("Category must not be null");
        if (arrivalOrder < 1)                  throw new IllegalArgumentException("arrivalOrder must be ≥ 1");

        this.token        = token;
        this.name         = name;
        this.service      = service;
        this.category     = category;
        this.arrivalOrder = arrivalOrder;
        this.status       = Status.WAITING;
        this.waitMinutes  = 0;
        this.enqueuedAt   = LocalDateTime.now();
    }

    // ---------------------------------------------------------------
    // Accessors (no public setters for immutable fields)
    // ---------------------------------------------------------------
    public String        getToken()        { return token; }
    public String        getName()         { return name; }
    public ServiceType   getService()      { return service; }
    public Category      getCategory()     { return category; }
    public Status        getStatus()       { return status; }
    public int           getArrivalOrder() { return arrivalOrder; }
    public int           getWaitMinutes()  { return waitMinutes; }
    public LocalDateTime getEnqueuedAt()   { return enqueuedAt; }
    public LocalDateTime getServedAt()     { return servedAt; }

    // ---------------------------------------------------------------
    // Mutators for mutable state
    // ---------------------------------------------------------------
    public void setStatus(Status status) {
        if (status == null) throw new IllegalArgumentException("Status must not be null");
        this.status = status;
        if (status == Status.SERVED) {
            this.servedAt = LocalDateTime.now();
        }
    }

    public void setWaitMinutes(int waitMinutes) {
        if (waitMinutes < 0) throw new IllegalArgumentException("waitMinutes must be ≥ 0");
        this.waitMinutes = waitMinutes;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    public boolean isWaiting() { return status == Status.WAITING; }
    public boolean isCalled()  { return status == Status.CALLED;  }

    /** Human-readable category string for the frontend ("Priority Assistance" / "Standard"). */
    public String getCategoryLabel() {
        return category == Category.PRIORITY_ASSISTANCE ? "Priority Assistance" : "Standard";
    }

    /** Human-readable service string for the frontend. */
    public String getServiceLabel() {
        return switch (service) {
            case GENERAL_SERVICES    -> "General Services";
            case DOCUMENT_SERVICES   -> "Document Services";
            case ACCESSIBILITY_SUPPORT -> "Accessibility Support";
        };
    }

    @Override
    public String toString() {
        return String.format("Citizen{token='%s', name='%s', category=%s, status=%s, arrival=%d}",
                token, name, category, status, arrivalOrder);
    }
}
