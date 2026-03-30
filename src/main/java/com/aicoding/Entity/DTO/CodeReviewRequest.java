package com.aicoding.Entity.DTO;

import lombok.Data;

@Data
public class CodeReviewRequest {
    public Long projectId;
    public String code;
    public String sessionId;
}
