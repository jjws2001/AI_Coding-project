package com.aicoding.Entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTreeNode {
    private String name;
    private String path;
    private FileType type;
    private Long size;
    private String extension;
    private List<FileTreeNode> children;

    public enum FileType {
        FILE, DIRECTORY
    }

    public void addChild(FileTreeNode child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }
}
