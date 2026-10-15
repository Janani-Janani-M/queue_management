package com.queuecare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 60, message = "Name must be 2–60 characters")
    private String name;

    @NotBlank(message = "Service type is required")
    private String service;

    @NotBlank(message = "Category is required")
    private String category;

    public RegisterRequest() {}

    public RegisterRequest(String name, String service, String category) {
        this.name = name; this.service = service; this.category = category;
    }

    public String getName()     { return name; }
    public String getService()  { return service; }
    public String getCategory() { return category; }

    public void setName(String name)         { this.name = name; }
    public void setService(String service)   { this.service = service; }
    public void setCategory(String category) { this.category = category; }
}
