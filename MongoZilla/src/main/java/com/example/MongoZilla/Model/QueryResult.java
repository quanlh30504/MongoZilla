package com.example.MongoZilla.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bson.Document;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class QueryResult {
    private double queryTimeMs;
    private List<?> documents;
}
