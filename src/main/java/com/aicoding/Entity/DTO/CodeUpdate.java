package com.aicoding.Entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodeUpdate {
    public Long projectId;
    public String filePath;
    public String content;
}
