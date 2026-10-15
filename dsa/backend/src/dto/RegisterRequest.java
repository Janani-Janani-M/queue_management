package dto;

/**
 * RegisterRequest – payload the frontend sends when a citizen clicks
 * "Generate Token".
 *
 * <p>Field names are intentionally snake_case-friendly so that a thin JSON
 * layer can map them directly from the HTML form body.
 *
 * <pre>
 * {
 *   "name"     : "Ravi Kumar",
 *   "service"  : "Document Services",
 *   "category" : "Priority Assistance"
 * }
 * </pre>
 */
public class RegisterRequest {

    /** Citizen's full name (max 60 chars, matches frontend maxlength). */
    private String name;

    /**
     * Service the citizen needs.  Accepted values (case-insensitive):
     * "General Services", "Document Services", "Accessibility Support".
     */
    private String service;

    /**
     * Queue lane.  Accepted values (case-insensitive):
     * "Priority Assistance", "Standard", "priority", "normal".
     */
    private String category;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------
    public RegisterRequest() {}

    public RegisterRequest(String name, String service, String category) {
        this.name     = name;
        this.service  = service;
        this.category = category;
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------
    public String getName()     { return name; }
    public String getService()  { return service; }
    public String getCategory() { return category; }

    public void setName(String name)         { this.name     = name; }
    public void setService(String service)   { this.service  = service; }
    public void setCategory(String category) { this.category = category; }

    @Override
    public String toString() {
        return "RegisterRequest{name='" + name + "', service='" + service
                + "', category='" + category + "'}";
    }
}
