package com.example.MongoZilla.Service;

import com.example.MongoZilla.Model.*;
import com.example.MongoZilla.Utils.Converter;
import com.example.MongoZilla.Utils.CsvUtils;
import com.example.MongoZilla.Utils.Parser;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoDatabase;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CoreService {

    private final MongoTemplate mongoTemplate;
    private final HashService hashService;
    private final IndexService indexService;
    
    @Value("${number_partition.main_collection}")
    private int NUM_MAIN_PARTITIONS;

    public void importCsvAndPartitionData(
            String collectionName,
            MultipartFile file,
            String partitionKey,
            @Nullable String sortKey
    ) throws IOException {

        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalArgumentException("Partition key is required.");
        }

        List<Document> allDocs = CsvUtils.parseCsvToDocuments(file);
        Map<Integer, List<Document>> partitionedDocs = new HashMap<>();
        for (int i = 0; i < NUM_MAIN_PARTITIONS; i++) {
            partitionedDocs.put(i, new ArrayList<>());
        }

        Date now = new Date();
        Set<String> fieldNames = new HashSet<>();

        for (Document doc : allDocs) {
            Object key = doc.get(partitionKey);
            if (key == null) continue;

            int partitionIndex = hashService.getPartitionIndex(key.toString(), NUM_MAIN_PARTITIONS);

            // Thu thập tên trường cho schema tracking
            fieldNames.addAll(doc.keySet());

            partitionedDocs.get(partitionIndex).add(doc);
        }

        List<String> partitionNames = new ArrayList<>();

        for (int i = 0; i < NUM_MAIN_PARTITIONS; i++) {
            String partitionCollectionName = collectionName + "_partition_" + i;
            partitionNames.add(partitionCollectionName);

            List<Document> docsToInsert = partitionedDocs.get(i);
            if (!docsToInsert.isEmpty()) {
                mongoTemplate.insert(docsToInsert, partitionCollectionName);
            }

            indexService.createIndexForPartitions(partitionCollectionName,partitionKey, sortKey);
        }

        // Lưu vào collections_config
        Document configDoc = new Document();
        configDoc.put("collectionName", collectionName);
        configDoc.put("fields", new ArrayList<>(fieldNames));
        configDoc.put("partition_key", partitionKey);
        configDoc.put("sort_key", sortKey);
        configDoc.put("createdFrom", file.getOriginalFilename());
        configDoc.put("numberPartitions", NUM_MAIN_PARTITIONS);
        configDoc.put("partitions", partitionNames);
        configDoc.put("gsiIndexes", new ArrayList<>()); // chưa có GSI thì để trống
        configDoc.put("createdAt", now);
        configDoc.put("updatedAt", now);

        mongoTemplate.insert(configDoc, "collections_config");
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
     * Cải tiến: Hỗ trợ sử dụng index ngay cả khi chỉ có một phần của index được sử dụng trong truy vấn
     * (miễn là phần đó là tiền tố của index)
     */
    private IndexMetadata findOptimalIndex(List<IndexMetadata> indices, Map<String, String> conditions) {
        IndexMetadata bestIndex = null;
        int maxScore = 0;
        Set<String> queryFields = conditions.keySet();

        for (IndexMetadata index : indices) {
            List<String> indexFields = index.getFields();
            int prefixScore = 0;

            // Tính điểm cho tiền tố liên tục của index
            for (String field : indexFields) {
                if (queryFields.contains(field)) {
                    prefixScore++;
                } else {
                    // Dừng ngay khi gặp trường không có trong truy vấn
                    break;
                }
            }

            // Chỉ xem xét index nếu có ít nhất một trường tiền tố khớp
            if (prefixScore > 0) {
                // Trường hợp 1: Index này có điểm cao hơn index tốt nhất hiện tại
                if (prefixScore > maxScore) {
                    maxScore = prefixScore;
                    bestIndex = index;
                }
                // Trường hợp 2: Cùng điểm, xét thêm các tiêu chí phụ
                else if (prefixScore == maxScore) {
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
            String value = conditions.get(key);
            if (value != null) {
                // Chuyển đổi giá trị sang kiểu dữ liệu phù hợp
                Object convertedValue = Converter.convertToAppropriateType(value);
                q.addCriteria(Criteria.where("indexKey." + key).is(convertedValue));
            }
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
            String value = entry.getValue();
            if (value != null) {
                // Chuyển đổi giá trị sang kiểu dữ liệu phù hợp
                Object convertedValue = Converter.convertToAppropriateType(value);
                fallback.addCriteria(Criteria.where(entry.getKey()).is(convertedValue));
            }
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


    public List<Document> queryMain(QueryRequest request) {
        String tableName = request.getTableName();
        Document config = mongoTemplate.findOne(
                Query.query(Criteria.where("collectionName").is(tableName)),
                Document.class,
                "collections_config"
        );

        if (config == null) {
            throw new IllegalArgumentException("Không tìm thấy config cho bảng: " + tableName);
        }

        int numPartitions = config.getInteger("numberPartitions");
        String partitionKey = config.getString("partition_key");
        String sortKey = config.getString("sort_key");

        String keyConditionExp = request.getKeyConditionExpression();
        String filterExp = request.getFilterExpression();
        Map<String, Object> valueMap = Converter.convertAttributeValues(request.getExpressionAttributeValues());

        // Parse KeyConditionExpression
        Criteria keyCriteria = Parser.parseKeyConditionExpression(keyConditionExp, valueMap, partitionKey, sortKey);

        // Parse FilterExpression
        Criteria filterCriteria = Parser.parseFilterExpression(filterExp, valueMap);

        // Xây dựng tiêu chí truy vấn cuối cùng
        Criteria finalCriteria;
        if (keyCriteria == null && filterCriteria == null) {
            // Nếu không có điều kiện nào, truy vấn tất cả
            finalCriteria = new Criteria();
        } else if (keyCriteria == null) {
            // Chỉ có điều kiện lọc
            finalCriteria = filterCriteria;
        } else if (filterCriteria == null) {
            // Chỉ có điều kiện khóa
            finalCriteria = keyCriteria;
        } else {
            // Kết hợp cả hai điều kiện
            finalCriteria = new Criteria().andOperator(keyCriteria, filterCriteria);
        }

        Query query = new Query(finalCriteria);

        List<Document> results = new ArrayList<>();

        // Kiểm tra xem có partition key trong điều kiện truy vấn không
        // Đơn giản hóa: Sử dụng keyCriteria để xác định xem có partition key hay không
        boolean hasPartitionKey = keyCriteria != null && keyCriteria.getCriteriaObject() != null && 
                                 keyCriteria.getCriteriaObject().containsKey(partitionKey);

        if (hasPartitionKey && keyCriteria != null && keyCriteria.getCriteriaObject() != null) {
            // Nếu có partition key, xác định partition cần quét
            // Lấy giá trị partition key từ keyCriteria
            Object partitionVal = keyCriteria.getCriteriaObject().get(partitionKey);
            if (partitionVal != null) {
                int partitionIndex = hashService.getPartitionIndex(partitionVal.toString(), numPartitions);
                String partitionName = tableName + "_partition_" + partitionIndex;
                results.addAll(mongoTemplate.find(query, Document.class, partitionName));
            }
        } else {
            // Quét toàn bộ các partition nếu không có partition key
            for (int i = 0; i < numPartitions; i++) {
                String partitionName = tableName + "_partition_" + i;
                results.addAll(mongoTemplate.find(query, Document.class, partitionName));
            }
        }

        return results;
    }

    public List<Document> queryGSI(QueryRequest request) {
        String gsiIndexName = request.getIndexName();

        // Lấy thông tin config của GSI
        Document gsiConfig = mongoTemplate.findOne(
                Query.query(Criteria.where("indexTableName").is(gsiIndexName)),
                Document.class,
                "index_table_config"
        );

        if (gsiConfig == null) {
            throw new IllegalArgumentException("Không tìm thấy GSI: " + gsiIndexName);
        }

        String gsiPartitionKey = gsiConfig.getString("partition_key");
        String gsiSortKey = gsiConfig.getString("sort_key");
        List<String> partitions = (List<String>) gsiConfig.get("partitions");
        String mainCollectionName = gsiConfig.getString("mainCollection");
        
        // Lấy thông tin projection từ index_table_config
        String projectionType = gsiConfig.getString("attribute_projections");
        if (projectionType == null) {
            projectionType = "ONLY_KEYS"; // Mặc định là ONLY_KEYS nếu không có trong config
        }
        
        List<String> attributesToInclude = null;
        if (gsiConfig.get("includedFields") instanceof List) {
            attributesToInclude = (List<String>) gsiConfig.get("includedFields");
        }

        String keyConditionExp = request.getKeyConditionExpression();
        String filterExp = request.getFilterExpression();
        Map<String, Object> valueMap = Converter.convertAttributeValues(request.getExpressionAttributeValues());

        // Parse KeyConditionExpression
        Criteria keyCriteria = Parser.parseKeyConditionExpression(keyConditionExp, valueMap, gsiPartitionKey, gsiSortKey);

        // Parse FilterExpression
        Criteria filterCriteria = Parser.parseFilterExpression(filterExp, valueMap);

        // Xây dựng tiêu chí truy vấn cuối cùng
        Criteria finalCriteria;
        if (keyCriteria == null && filterCriteria == null) {
            // Nếu không có điều kiện nào, truy vấn tất cả
            finalCriteria = new Criteria();
        } else if (keyCriteria == null) {
            // Chỉ có điều kiện lọc
            finalCriteria = filterCriteria;
        } else if (filterCriteria == null) {
            // Chỉ có điều kiện khóa
            finalCriteria = keyCriteria;
        } else {
            // Kết hợp cả hai điều kiện
            finalCriteria = new Criteria().andOperator(keyCriteria, filterCriteria);
        }

        Query query = new Query(finalCriteria);
        List<Document> rawResults = new ArrayList<>();

        // Kiểm tra xem có partition key trong điều kiện truy vấn không
        boolean hasPartitionKey = keyCriteria != null && keyCriteria.getCriteriaObject() != null && 
                                 keyCriteria.getCriteriaObject().containsKey(gsiPartitionKey);

        if (hasPartitionKey && keyCriteria != null && keyCriteria.getCriteriaObject() != null) {
            // Nếu có partition key, xác định partition cần quét dựa trên giá trị partition key
            Object partitionVal = keyCriteria.getCriteriaObject().get(gsiPartitionKey);
            if (partitionVal != null) {
                // Sử dụng HashService để xác định chính xác partition cần truy cập
                // Lấy số lượng partition từ cấu hình GSI hoặc sử dụng kích thước của danh sách partitions
                int numGsiPartitions = partitions.size();
                
                // Tính toán partition index dựa trên giá trị partition key
                int partitionIndex = hashService.getPartitionIndex(partitionVal.toString(), numGsiPartitions);
                
                // Đảm bảo partitionIndex nằm trong phạm vi hợp lệ
                if (partitionIndex >= 0 && partitionIndex < numGsiPartitions) {
                    // Lấy tên partition từ danh sách partitions
                    String gsiPartition = partitions.get(partitionIndex);
                    
                    // Thực hiện truy vấn trên partition cụ thể
                    rawResults.addAll(mongoTemplate.find(query, Document.class, gsiPartition));
                }
            }
        } else {
            // Quét toàn bộ các partition nếu không có partition key
            for (String gsiPartition : partitions) {
                rawResults.addAll(mongoTemplate.find(query, Document.class, gsiPartition));
            }
        }
        
        // Xử lý projection theo cấu hình trong index_table_config
        // projectionType và attributesToInclude đã được lấy từ gsiConfig ở trên
        List<Document> results = new ArrayList<>();

        switch (projectionType) {
            case "ONLY_KEYS":
                for (Document rawResult : rawResults) {
                    // Lấy thông tin về document gốc
                    if (rawResult.containsKey("main_partition_key") && mainCollectionName != null) {

                        String mainPartitionKey = rawResult.get("main_partition_key").toString();
                        String mainSortKey = rawResult.containsKey("main_sort_key") ? 
                                rawResult.get("main_sort_key").toString() : null;


                        
                        // Tính toán partition index dựa trên main_partition_key
                        int partitionIndex = hashService.getPartitionIndex(mainPartitionKey, NUM_MAIN_PARTITIONS);
                        
                        // Tạo tên partition của bảng chính
                        String mainPartitionName = mainCollectionName + "_partition_" + partitionIndex;
                        
                        // Tạo truy vấn để tìm document gốc
                        Criteria mainCriteria = Criteria.where(gsiConfig.get("main_partition_key").toString()).is(rawResult.get("main_partition_key"));
                        if (mainSortKey != null) {
                            mainCriteria = mainCriteria.and(gsiConfig.get("main_sort_key").toString()).is(rawResult.get("main_sort_key"));
                        }
                        
                        // Thực hiện truy vấn trên bảng chính
                        Document mainDoc = mongoTemplate.findOne(
                                Query.query(mainCriteria),
                                Document.class,
                                mainPartitionName
                        );
                        
                        if (mainDoc != null) {
                            // Nếu tìm thấy document gốc, sử dụng nó
                            results.add(mainDoc);
                        } else {
                            // Nếu không tìm thấy, tạo document mới chỉ với các khóa
                            Document result = new Document();
                            
                            // Lấy các trường khóa từ GSI
                            if (rawResult.containsKey(gsiPartitionKey)) {
                                result.put(gsiPartitionKey, rawResult.get(gsiPartitionKey));
                            }
                            if (gsiSortKey != null && rawResult.containsKey(gsiSortKey)) {
                                result.put(gsiSortKey, rawResult.get(gsiSortKey));
                            }
                            
                            // Thêm thông tin về document gốc
                            result.put("main_partition_key", rawResult.get("main_partition_key"));
                            if (mainSortKey != null) {
                                result.put("main_sort_key", rawResult.get("main_sort_key"));
                            }
                            
                            results.add(result);
                        }
                    } else {
                        // Nếu không có thông tin về document gốc, chỉ trả về các khóa GSI
                        Document result = new Document();
                        
                        // Lấy các trường khóa từ GSI
                        if (rawResult.containsKey(gsiPartitionKey)) {
                            result.put(gsiPartitionKey, rawResult.get(gsiPartitionKey));
                        }
                        if (gsiSortKey != null && rawResult.containsKey(gsiSortKey)) {
                            result.put(gsiSortKey, rawResult.get(gsiSortKey));
                        }
                        
                        results.add(result);
                    }
                }
                break;
                
            case "INCLUDE":
                for (Document rawResult : rawResults) {
                    Document result = new Document((Document) rawResult.get("includeData"));

                    // Luôn bao gồm các khóa
                    if (rawResult.containsKey(gsiPartitionKey)) {
                        result.put(gsiPartitionKey, rawResult.get(gsiPartitionKey));
                    }
                    if (gsiSortKey != null && rawResult.containsKey(gsiSortKey)) {
                        result.put(gsiSortKey, rawResult.get(gsiSortKey));
                    }
                    
//                    // Lấy dữ liệu từ includeData
//                    if (rawResult.containsKey("includeData")) {
//                        Object includeDataObj = rawResult.get("includeData");
//                        if (includeDataObj instanceof Document) {
//                            Document includeData = (Document) includeDataObj;
//
//                            // Thêm các trường được chỉ định
//                            if (attributesToInclude != null) {
//                                for (String attr : attributesToInclude) {
//                                    if (includeData.containsKey(attr)) {
//                                        result.put(attr, includeData.get(attr));
//                                    }
//                                }
//                            } else {
//                                // Nếu không có danh sách cụ thể, thêm tất cả các trường
//                                for (String key : includeData.keySet()) {
//                                    result.put(key, includeData.get(key));
//                                }
//                            }
//                        }
//                    }
                    
                    results.add(result);
                }
                break;
                
            case "ALL":
                for (Document rawResult : rawResults) {
                    Document result = new Document((Document) rawResult.get("fullData"));
                    
//                    // Lấy dữ liệu đầy đủ từ fullData
//                    if (rawResult.containsKey("fullData")) {
//                        Object fullDataObj = rawResult.get("fullData");
//                        if (fullDataObj instanceof Document) {
//                            result = new Document((Document) fullDataObj);
//                        } else {
//                            result = new Document();
//                        }
//                    } else {
//                        // Nếu không có fullData, sử dụng toàn bộ document
//                        result = new Document(rawResult);
//                    }
                    
                    results.add(result);
                }
                break;
                
            default:
                results = rawResults;
        }

        
        return results;
    }
}