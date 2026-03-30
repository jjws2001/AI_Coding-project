package com.aicoding.Entity;

import lombok.Data;

@Data
public class SandboxResponse {
    private String status; // SUCCESS, CODE_ERROR, SYSTEM_ERROR
    private String output;
    private String errorLog;
}
