package com.example.MongoZilla.Model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IndexInfo {
    private String indexName;
    private String sourceCollection;
    private List<String> fields;
    private boolean includeFullData;
    private long documentCount;
    private long sizeBytes;
}
