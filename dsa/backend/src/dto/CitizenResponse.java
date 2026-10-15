package dto;

import model.Citizen;

import java.time.format.DateTimeFormatter;

/**
 * CitizenResponse – the JSON shape that the frontend receives for every citizen
 * in the queue table and in action responses.
 *
 * <p>This deliberately mirrors the shape of the JavaScript {@code queue} array
 * so that {@code js/app.js} requires zero mapping changes when the backend is
 * wired in:
 *
 * <pre>
 * {
 *   "token"    : "Q101",
 *   "name"     : "Ravi Kumar",
 *   "service"  : "Document Services",
 *   "category" : "Priority Assistance",
 *   "wait"     : 12,
 *   "status"   : "Waiting",
 *   "arrival"  : 1
 * }
 * </pre>
 */
public class CitizenResponse {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private String token;
    private String name;
    private String service;   // human label, e.g. "General Services"
    private String category;  // human label, e.g. "Priority Assistance"
    private int    wait;      // minutes
    private String status;    // "Waiting", "Called", "Served", "Skipped"
    private int    arrival;   // ordinal from registration

    // Optional enrichment fields (used in detailed views)
    private String enqueuedAt;  // formatted timestamp
    private String servedAt;    // null until served

    // ---------------------------------------------------------------
    // Factory – build from domain object
    // ---------------------------------------------------------------
    public static CitizenResponse from(Citizen c) {
        CitizenResponse r = new CitizenResponse();
        r.token      = c.getToken();
        r.name       = c.getName();
        r.service    = c.getServiceLabel();
        r.category   = c.getCategoryLabel();
        r.wait       = c.getWaitMinutes();
        r.status     = capitalise(c.getStatus().name()); // WAITING → "Waiting"
        r.arrival    = c.getArrivalOrder();
        r.enqueuedAt = c.getEnqueuedAt() != null ? c.getEnqueuedAt().format(FMT) : null;
        r.servedAt   = c.getServedAt()   != null ? c.getServedAt().format(FMT)   : null;
        return r;
    }

    private static String capitalise(String raw) {
        // "PRIORITY_ASSISTANCE" → keep only after underscore logic not needed here;
        // status names are single words: WAITING, CALLED, SERVED, SKIPPED
        if (raw == null || raw.isEmpty()) return raw;
        return raw.charAt(0) + raw.substring(1).toLowerCase();
    }

    // ---------------------------------------------------------------
    // Accessors (required for JSON serialisation)
    // ---------------------------------------------------------------
    public String getToken()      { return token; }
    public String getName()       { return name; }
    public String getService()    { return service; }
    public String getCategory()   { return category; }
    public int    getWait()       { return wait; }
    public String getStatus()     { return status; }
    public int    getArrival()    { return arrival; }
    public String getEnqueuedAt() { return enqueuedAt; }
    public String getServedAt()   { return servedAt; }
}
