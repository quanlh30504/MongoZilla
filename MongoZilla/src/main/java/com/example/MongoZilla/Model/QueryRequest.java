package com.example.MongoZilla.Model;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryRequest {
    private String tableName;
    private String indexName; // nullable, nếu là null => bảng chính
    private String keyConditionExpression;
    private String filterExpression;
    private Map<String, Map<String, String>> expressionAttributeValues;
    
    // Loại projection: "ALL" (mặc định, trả về tất cả dữ liệu), "KEYS_ONLY" (chỉ trả về các khóa), "INCLUDE" (trả về các khóa và các trường được chỉ định)
    @Builder.Default
    private String projectionType = "ALL";
    
    // Danh sách các trường cần bao gồm khi projectionType là "INCLUDE"
    private List<String> attributesToInclude;
}
