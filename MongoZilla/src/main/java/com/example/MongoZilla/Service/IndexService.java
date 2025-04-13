package com.example.MongoZilla.Service;

import com.example.MongoZilla.Model.IndexMetadata;
import com.example.MongoZilla.Model.QueryResult;
import com.example.MongoZilla.Model.CollectionInfo;
import com.example.MongoZilla.Model.IndexInfo;
import com.example.MongoZilla.Utils.CsvUtils;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.mongodb.client.MongoDatabase;
import com.mongodb.BasicDBObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IndexService {

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void importCsvAndCreateCollection(String name, MultipartFile file) throws IOException {
        if (mongoTemplate.collectionExists(name)) {
            throw new IllegalStateException("Collection already exists");
        }

        List<Document> docs = CsvUtils.parseCsvToDocuments(file);
        mongoTemplate.insert(docs, name);
    }


    public void createIndex(String collectionName, List<String> fields, boolean includeFullData) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            throw new IllegalArgumentException("Source collection does not exist.");
        }

        String indexCollection = "index_" + collectionName + "_" + String.join("_", fields);
        if (mongoTemplate.collectionExists(indexCollection)) {
            throw new IllegalStateException("Index already exists.");
        }

        List<Document> sourceDocs = mongoTemplate.findAll(Document.class, collectionName);
        List<Document> indexDocs = new ArrayList<>();

        for (Document doc : sourceDocs) {
            Document indexKey = new Document();
            for (String f : fields) {
                if (!doc.containsKey(f)) throw new IllegalArgumentException("Field " + f + " not found.");
                indexKey.append(f, doc.get(f));
            }

            Document indexDoc = new Document("indexKey", indexKey)
                    .append("mainId", doc.get("_id"));

            if (includeFullData) {
                indexDoc.append("fullData", doc);
            }

            indexDocs.add(indexDoc);
        }

        // ✅ Insert dữ liệu index
        mongoTemplate.insert(indexDocs, indexCollection);

        // ✅ Tạo index thực sự trong MongoDB cho các trường cần
        for (String field : fields) {
            mongoTemplate.indexOps(indexCollection)
                    .ensureIndex(new Index().on("indexKey." + field, Sort.Direction.ASC));
        }

        // ✅ Lưu metadata
        mongoTemplate.insert(
                new IndexMetadata(null, collectionName, indexCollection, fields, includeFullData),
                "index_config"
        );
    }


    /**
     * Thực hiện tìm kiếm thông minh sử dụng index tối ưu nhất
     * @param collectionName Tên collection cần tìm kiếm
     * @param conditions Điều kiện tìm kiếm dạng Map<String, String>
     * @return QueryResult chứa kết quả và thông tin về truy vấn
     */
    public QueryResult query(String collectionName, Map<String, String> conditions) {
        long startTime = System.currentTimeMillis();
        
        // Lấy tất cả các index được tạo cho collection này
        List<IndexMetadata> indices = mongoTemplate.find(
                Query.query(Criteria.where("collectionName").is(collectionName)),
                IndexMetadata.class,
                "index_config"
        );

        // Tìm index tối ưu nhất dựa trên các tiêu chí
        IndexMetadata bestIndex = findOptimalIndex(indices, conditions);

        List<Document> results;
        String indexUsed = "none";
        boolean usedIndex = false;
        long queryTimeMs = 0;
        
        // Bật MongoDB profiler tạm thởi để đo thởi gian thực hiện truy vấn
        MongoDatabase db = mongoTemplate.getDb();
        db.runCommand(new BasicDBObject("profile", 2)); // Bật profiler ở mức cao nhất
        
        try {
            // Nếu tìm thấy index phù hợp, sử dụng nó
            if (bestIndex != null) {
                long queryStartTime = System.currentTimeMillis();
                results = queryUsingIndex(bestIndex, conditions, collectionName);
                queryTimeMs = System.currentTimeMillis() - queryStartTime;
                indexUsed = bestIndex.getIndexCollection();
                usedIndex = true;
            } else {
                // Fallback: full scan nếu không tìm thấy index phù hợp
                long queryStartTime = System.currentTimeMillis();
                results = fallbackFullScan(collectionName, conditions);
                queryTimeMs = System.currentTimeMillis() - queryStartTime;
            }
            
            // Lấy thông tin profiler (tùy chọn, nếu muốn thông tin chi tiết hơn)
            Document profileData = mongoTemplate.getCollection("system.profile")
                    .find()
                    .sort(new BasicDBObject("ts", -1))
                    .first();
            
            if (profileData != null) {
                // Sử dụng thởi gian từ MongoDB profiler nếu có
                Number mongoTime = (Number) profileData.get("millis");
                if (mongoTime != null) {
                    queryTimeMs = mongoTime.longValue();
                }
            }
        } finally {
            // Tắt profiler sau khi hoàn thành
            db.runCommand(new BasicDBObject("profile", 0));
        }
        
        long totalTimeMs = System.currentTimeMillis() - startTime;
        
        Map<String, Object> performanceInfo = new HashMap<>();
        performanceInfo.put("totalProcessingTimeMs", totalTimeMs);
        performanceInfo.put("actualQueryTimeMs", queryTimeMs);
        performanceInfo.put("indexSelectionTimeMs", totalTimeMs - queryTimeMs);
        
        return new QueryResult(results, queryTimeMs, indexUsed, usedIndex, performanceInfo);
    }

    /**
     * Tìm index tối ưu nhất cho truy vấn
     */
    private IndexMetadata findOptimalIndex(List<IndexMetadata> indices, Map<String, String> conditions) {
        IndexMetadata bestIndex = null;
        int maxFieldsCovered = 0;

        for (IndexMetadata index : indices) {
            List<String> fields = index.getFields();
            
            // Kiểm tra xem index có thỏa mãn điều kiện không
            if (conditions.keySet().containsAll(fields)) {
                int fieldsCovered = fields.size();
                
                // Trường hợp 1: Index này có nhiều trường hơn index tốt nhất hiện tại
                if (fieldsCovered > maxFieldsCovered) {
                    maxFieldsCovered = fieldsCovered;
                    bestIndex = index;
                } 
                // Trường hợp 2: Cùng số lượng trường, xét thêm các tiêu chí phụ
                else if (fieldsCovered == maxFieldsCovered) {
                    // Tiêu chí 2.1: Ưu tiên index có includeFullData=true
                    if (index.isIncludeFullData() && bestIndex != null && !bestIndex.isIncludeFullData()) {
                        bestIndex = index;
                    }
                    // Tiêu chí 2.2: Nếu cả hai index đều có includeFullData=false, 
                    // chọn index có thứ tự tạo gần đây nhất (thường có id lớn hơn)
                    else if (!index.isIncludeFullData() && bestIndex != null && !bestIndex.isIncludeFullData() 
                             && index.getId() != null && bestIndex.getId() != null 
                             && index.getId().compareTo(bestIndex.getId()) > 0) {
                        bestIndex = index;
                    }
                }
            }
        }
        
        return bestIndex;
    }

    /**
     * Thực hiện truy vấn sử dụng index
     */
    private List<Document> queryUsingIndex(IndexMetadata index, Map<String, String> conditions, String collectionName) {
        List<String> fields = index.getFields();
        
        // Xây dựng query dựa trên các trường của index
        Query q = new Query();
        for (String key : fields) {
            q.addCriteria(Criteria.where("indexKey." + key).is(conditions.get(key)));
        }

        // Log query và collection để debug
        System.out.println("Query on index: " + q.toString() + ", Collection: " + index.getIndexCollection());

        // Thực hiện tìm kiếm trên bảng index
        List<Document> result = mongoTemplate.find(q, Document.class, index.getIndexCollection());
        
        System.out.println("Index query result count: " + result.size());
        
        // Nếu không tìm thấy kết quả, trả về list rỗng
        if (result.isEmpty()) {
            return result;
        }

        // Trả về kết quả
        if (index.isIncludeFullData()) {
            // Nếu index chứa dữ liệu đầy đủ, trích xuất trực tiếp từ index
            return result.stream()
                .map(d -> (Document) d.get("fullData"))
                .filter(d -> d != null) // Lọc bỏ các document null
                .toList();
        } else {
            // Ngược lại, query lại bảng gốc theo ID
            try {
                List<Object> ids = new ArrayList<>();
                for (Document doc : result) {
                    Object mainId = doc.get("mainId");
                    if (mainId != null) {
                        // Cố gắng chuyển đổi sang ObjectId nếu có thể
                        if (mainId instanceof String && ((String) mainId).length() == 24 && 
                            ((String) mainId).matches("[0-9a-fA-F]{24}")) {
                            ids.add(new ObjectId((String) mainId));
                        } else {
                            ids.add(mainId);
                        }
                    }
                }
                
                if (ids.isEmpty()) {
                    return new ArrayList<>();
                }
                
                System.out.println("Querying main collection with " + ids.size() + " IDs");
                
                // Thử query với mỗi ID riêng lẻ và gộp kết quả
                List<Document> mainResults = new ArrayList<>();
                for (Object id : ids) {
                    List<Document> found = mongoTemplate.find(
                        Query.query(Criteria.where("_id").is(id)), 
                        Document.class, 
                        collectionName
                    );
                    mainResults.addAll(found);
                }
                
                System.out.println("Main collection query result count: " + mainResults.size());
                return mainResults;
            } catch (Exception e) {
                System.err.println("Error querying main collection: " + e.getMessage());
                e.printStackTrace();
                return new ArrayList<>();
            }
        }
    }

    /**
     * Thực hiện full scan khi không tìm thấy index phù hợp
     */
    private List<Document> fallbackFullScan(String collectionName, Map<String, String> conditions) {
        Query fallback = new Query();
        for (var entry : conditions.entrySet()) {
            fallback.addCriteria(Criteria.where(entry.getKey()).is(entry.getValue()));
        }

        System.out.println("Fallback query: " + fallback.toString() + ", Collection: " + collectionName);
        List<Document> results = mongoTemplate.find(fallback, Document.class, collectionName);
        System.out.println("Fallback query result count: " + results.size());
        
        return results;
    }

    /**
     * Lấy danh sách các collections chính (không bao gồm các bảng index)
     * @return Danh sách thông tin về các collections
     */
    public List<CollectionInfo> getMainCollections() {
        List<CollectionInfo> result = new ArrayList<>();
        
        // Lấy tất cả collections trong database
        Set<String> collectionNames = mongoTemplate.getCollectionNames();
        
        for (String name : collectionNames) {
            // Bỏ qua các bảng index và bảng cấu hình
            if (name.startsWith("index_") || name.equals("index_config") || name.equals("system.profile")) {
                continue;
            }
            
            // Lấy thông tin về collection
            Document stats = mongoTemplate.getDb().runCommand(
                new BasicDBObject("collStats", name)
            );
            
            long count = stats.getInteger("count", 0);
            long size = stats.get("size") != null ? ((Number) stats.get("size")).longValue() : 0;
            
            // Lấy mẫu các trường từ document đầu tiên
            String[] sampleFields = {};
            if (count > 0) {
                Document sample = mongoTemplate.findOne(new Query().limit(1), Document.class, name);
                if (sample != null) {
                    sampleFields = sample.keySet().toArray(new String[0]);
                }
            }
            
            result.add(new CollectionInfo(name, count, size, sampleFields));
        }
        
        return result;
    }
    
    /**
     * Lấy thông tin về các bảng index liên quan đến một collection
     * @param collectionName Tên collection cần lấy thông tin index
     * @return Danh sách thông tin về các bảng index
     */
    public List<IndexInfo> getCollectionIndexes(String collectionName) {
        List<IndexInfo> result = new ArrayList<>();
        
        // Lấy thông tin metadata về các index của collection này
        List<IndexMetadata> indices = mongoTemplate.find(
            Query.query(Criteria.where("collectionName").is(collectionName)),
            IndexMetadata.class,
            "index_config"
        );
        
        for (IndexMetadata metadata : indices) {
            String indexName = metadata.getIndexCollection();
            
            // Lấy thông tin về bảng index
            Document stats = mongoTemplate.getDb().runCommand(
                new BasicDBObject("collStats", indexName)
            );
            
            long count = stats.getInteger("count", 0);
            long size = stats.get("size") != null ? ((Number) stats.get("size")).longValue() : 0;
            
            result.add(new IndexInfo(
                indexName,
                metadata.getCollectionName(),
                metadata.getFields(),
                metadata.isIncludeFullData(),
                count,
                size
            ));
        }
        
        return result;
    }
}
