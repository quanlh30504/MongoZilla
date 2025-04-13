package com.example.MongoZilla.Model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CollectionInfo {
    private String name;
    private long documentCount;
    private long sizeBytes;
    private String[] sampleFields;
}
