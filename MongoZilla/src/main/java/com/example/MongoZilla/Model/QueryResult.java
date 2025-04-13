package com.example.MongoZilla.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bson.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class QueryResult {
    private List<Document> documents;
    private long queryTimeMs;
    private String indexUsed;
    private boolean usedIndex;
    private Map<String, Object> performanceInfo;
    
    public QueryResult(List<Document> documents, long queryTimeMs, String indexUsed, boolean usedIndex) {
        this.documents = documents;
        this.queryTimeMs = queryTimeMs;
        this.indexUsed = indexUsed;
        this.usedIndex = usedIndex;
        this.performanceInfo = null;
    }
}
