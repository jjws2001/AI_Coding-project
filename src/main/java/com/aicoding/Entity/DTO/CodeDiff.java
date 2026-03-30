package com.aicoding.Entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodeDiff {
    public Long projectId;
    public String filePath;
    public String diff;
}
