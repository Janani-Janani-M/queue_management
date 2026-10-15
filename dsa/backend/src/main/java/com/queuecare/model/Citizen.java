package com.queuecare.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Citizen – JPA entity persisted to MySQL + in-memory DSA structure.
 *
 * Mirrors the plain Citizen model from the earlier DSA layer but adds
 * JPA annotations for MySQL persistence.
 */
@Entity
@Table(name = "citizens", indexes = {
        @Index(name = "idx_status",       columnList = "status"),
        @Index(name = "idx_session_date", columnList = "sessionDate"),
        @Index(name = "idx_token",        columnList = "token",  unique = true)
})
public class Citizen {

    // ---------------------------------------------------------------
    // Enums
    // ---------------------------------------------------------------
    public enum Category {
        PRIORITY_ASSISTANCE,
        STANDARD
    }

    public enum Status {
        WAITING,
        CALLED,
        SERVED,
        SKIPPED
    }

    public enum ServiceType {
        GENERAL_SERVICES,
        DOCUMENT_SERVICES,
        ACCESSIBILITY_SUPPORT
    }

    // ---------------------------------------------------------------
    // Persistence fields
    // ---------------------------------------------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String token;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.WAITING;

    @Column(name = "arrival_order", nullable = false)
    private int arrivalOrder;

    @Column(name = "wait_minutes")
    private int waitMinutes;

    @Column(name = "enqueued_at")
    private LocalDateTime enqueuedAt;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------
    protected Citizen() {}

    public Citizen(String token, String name, ServiceType serviceType, Category category, int arrivalOrder) {
        this.token        = token;
        this.name         = name;
        this.serviceType  = serviceType;
        this.category     = category;
        this.arrivalOrder = arrivalOrder;
        this.status       = Status.WAITING;
        this.waitMinutes  = 0;
        this.enqueuedAt   = LocalDateTime.now();
        this.sessionDate  = LocalDate.now();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    public boolean isWaiting() { return status == Status.WAITING; }
    public boolean isCalled()  { return status == Status.CALLED;  }

    public String getCategoryLabel() {
        return category == Category.PRIORITY_ASSISTANCE ? "Priority Assistance" : "Standard";
    }

    public String getServiceLabel() {
        return switch (serviceType) {
            case GENERAL_SERVICES      -> "General Services";
            case DOCUMENT_SERVICES     -> "Document Services";
            case ACCESSIBILITY_SUPPORT -> "Accessibility Support";
        };
    }

    // ---------------------------------------------------------------
    // Getters & Setters
    // ---------------------------------------------------------------
    public Long          getId()           { return id; }
    public String        getToken()        { return token; }
    public String        getName()         { return name; }
    public ServiceType   getServiceType()  { return serviceType; }
    public Category      getCategory()     { return category; }
    public Status        getStatus()       { return status; }
    public int           getArrivalOrder() { return arrivalOrder; }
    public int           getWaitMinutes()  { return waitMinutes; }
    public LocalDateTime getEnqueuedAt()   { return enqueuedAt; }
    public LocalDateTime getServedAt()     { return servedAt; }
    public LocalDate     getSessionDate()  { return sessionDate; }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.SERVED) this.servedAt = LocalDateTime.now();
    }

    public void setWaitMinutes(int waitMinutes) { this.waitMinutes = waitMinutes; }
}
