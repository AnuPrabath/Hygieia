package com.capitalone.dashboard.request;

import com.capitalone.dashboard.model.PerformanceType;
import org.bson.types.ObjectId;

import javax.validation.constraints.NotNull;

public class TemplateRequest {
    private ServiceStatus status;
    private String message;

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Service update(Service service) {
        service.setStatus(status);
        service.setMessage(message);
        return s
}
