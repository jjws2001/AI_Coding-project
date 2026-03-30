package com.aicoding.Entity.DTO;

import lombok.Data;

@Data
public class ChatRequest {
    public String sessionId;

    public String message;

    public Long projectId;
}
