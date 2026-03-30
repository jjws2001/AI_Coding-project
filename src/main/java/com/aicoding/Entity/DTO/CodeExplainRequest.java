package com.aicoding.Entity.DTO;

import lombok.Data;

@Data
public class CodeExplainRequest {
    public Long projectId;
    public String code;
    public String sessionId;
}
