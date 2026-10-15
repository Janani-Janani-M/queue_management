package com.queuecare.dto;

import com.queuecare.model.Citizen;
import java.time.format.DateTimeFormatter;

public class CitizenResponse {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private Long   id;
    private String token;
    private String name;
    private String service;
    private String category;
    private int    wait;
    private String status;
    private int    arrival;
    private String enqueuedAt;
    private String servedAt;

    public static CitizenResponse from(Citizen c) {
        CitizenResponse r = new CitizenResponse();
        r.id         = c.getId();
        r.token      = c.getToken();
        r.name       = c.getName();
        r.service    = c.getServiceLabel();
        r.category   = c.getCategoryLabel();
        r.wait       = c.getWaitMinutes();
        r.status     = capitalise(c.getStatus().name());
        r.arrival    = c.getArrivalOrder();
        r.enqueuedAt = c.getEnqueuedAt() != null ? c.getEnqueuedAt().format(FMT) : null;
        r.servedAt   = c.getServedAt()   != null ? c.getServedAt().format(FMT)   : null;
        return r;
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    public Long   getId()        { return id; }
    public String getToken()     { return token; }
    public String getName()      { return name; }
    public String getService()   { return service; }
    public String getCategory()  { return category; }
    public int    getWait()      { return wait; }
    public String getStatus()    { return status; }
    public int    getArrival()   { return arrival; }
    public String getEnqueuedAt(){ return enqueuedAt; }
    public String getServedAt()  { return servedAt; }
}
