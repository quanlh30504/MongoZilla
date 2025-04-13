package com.example.MongoZilla.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("index_config")
public class IndexMetadata {
    @Id
    private String id;
    private String collectionName;
    private String indexCollection;
    private List<String> fields;
    private boolean includeFullData;
}

