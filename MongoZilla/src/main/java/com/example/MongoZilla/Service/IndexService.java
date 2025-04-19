package com.example.MongoZilla.Service;

import com.example.MongoZilla.Enum.AttributeProjection;
import com.example.MongoZilla.Model.IndexMetadata;
import com.example.MongoZilla.Model.QueryResult;
import com.example.MongoZilla.Model.CollectionInfo;
import com.example.MongoZilla.Model.IndexInfo;
import com.example.MongoZilla.Utils.CsvUtils;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.mongodb.client.MongoDatabase;
import com.mongodb.BasicDBObject;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IndexService {

    private final MongoTemplate mongoTemplate;
    private final HashService hashService;

    @Value("${number_partition.GSI_collection}")
    private int NUM_GSI_PARTITIONS;

//    private final KafkaTemplate<String, String> kafkaTemplate;


//    public void createIndex(String collectionName, List<String> fields, boolean includeFullData) {
//        if (!mongoTemplate.collectionExists(collectionName)) {
//            throw new IllegalArgumentException("Source collection does not exist.");
//        }
//
//        String indexCollection = "index_" + collectionName + "_" + String.join("_", fields);
//        if (mongoTemplate.collectionExists(indexCollection)) {
//            throw new IllegalStateException("Index already exists.");
//        }
//
//        // Validate fields exist in at least one document
//        Document sampleDoc = mongoTemplate.findOne(new Query().limit(1), Document.class, collectionName);
//        if (sampleDoc != null) {
//            for (String f : fields) {
//                if (!sampleDoc.containsKey(f)) {
//                    throw new IllegalArgumentException("Field " + f + " not found in sample document.");
//                }
//            }
//        }
//
//        // Create the index collection first
//        mongoTemplate.createCollection(indexCollection);
//
//        // Process in batches to avoid OutOfMemoryError
//        final int BATCH_SIZE = 1000;
//        long totalDocuments = mongoTemplate.count(new Query(), collectionName);
//        long processedDocuments = 0;
//
//        // Send initial progress to Kafka
//        kafkaTemplate.send("index-progress", collectionName + "-" + indexCollection,
//                "Started index creation: 0/" + totalDocuments);
//
//        // Use pagination to process the collection in batches
//        Query query = new Query();
//        query.with(Sort.by(Sort.Direction.ASC, "_id"));
//
//        boolean hasMoreData = true;
//        Object lastId = null;
//
//        while (hasMoreData) {
//            // If we have a lastId, add it to the query to get the next batch
//            if (lastId != null) {
//                query = new Query(Criteria.where("_id").gt(lastId));
//                query.with(Sort.by(Sort.Direction.ASC, "_id"));
//            }
//
//            query.limit(BATCH_SIZE);
//            List<Document> batch = mongoTemplate.find(query, Document.class, collectionName);
//
//            if (batch.isEmpty()) {
//                hasMoreData = false;
//                continue;
//            }
//
//            // Remember the last ID for the next iteration
//            lastId = batch.get(batch.size() - 1).get("_id");
//
//            List<Document> indexBatch = new ArrayList<>(batch.size());
//
//            for (Document doc : batch) {
//                Document indexKey = new Document();
//                boolean allFieldsExist = true;
//
//                for (String f : fields) {
//                    if (!doc.containsKey(f)) {
//                        allFieldsExist = false;
//                        break;
//                    }
//                    indexKey.append(f, doc.get(f));
//                }
//
//                // Skip documents that don't have all required fields
//                if (!allFieldsExist) continue;
//
//                Document indexDoc = new Document("indexKey", indexKey)
//                        .append("mainId", doc.get("_id"));
//
//                if (includeFullData) {
//                    indexDoc.append("fullData", doc);
//                }
//
//                indexBatch.add(indexDoc);
//            }
//
//            // Insert this batch
//            if (!indexBatch.isEmpty()) {
//                mongoTemplate.insert(indexBatch, indexCollection);
//            }
//
//            // Update progress
//            processedDocuments += batch.size();
//            kafkaTemplate.send("index-progress", collectionName + "-" + indexCollection,
//                    "Indexing progress: " + processedDocuments + "/" + totalDocuments);
//
//            // Help GC by clearing references
//            batch.clear();
//            indexBatch.clear();
//        }
//
//        // 3. Create the necessary indexes
//
//        // 3.1 Create compound index if there are multiple fields
//        if (fields.size() > 1) {
//            Document indexKeys = new Document();
//            for (String field : fields) {
//                indexKeys.append("indexKey." + field, 1);
//            }
//            mongoTemplate.getCollection(indexCollection).createIndex(indexKeys);
//        } else if (fields.size() == 1) {
//            // If there's only one field, create a single field index
//            mongoTemplate.getCollection(indexCollection)
//                    .createIndex(new Document("indexKey." + fields.get(0), 1));
//        }
//
//        // 3.2 Create text index for fullData if needed
//        if (includeFullData) {
//            mongoTemplate.getCollection(indexCollection)
//                    .createIndex(new Document("fullData", "text"));
//        }
//
//        // Always create an index on mainId for efficient lookups
//        mongoTemplate.getCollection(indexCollection)
//                .createIndex(new Document("mainId", 1));
//
//        // Save metadata
//        mongoTemplate.insert(
//                new IndexMetadata(null, collectionName, indexCollection, fields, includeFullData),
//                "index_config"
//        );
//
//        // Send completion notification
//        kafkaTemplate.send("index-progress", collectionName + "-" + indexCollection,
//                "Index creation completed: " + processedDocuments + " documents processed");
//    }



     /**
     * Tạo index cho các partition collection
     */
    public void createIndexForPartitions(String collectionName, String partitionKey, @Nullable String sortKey) {
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalArgumentException("Partition key is required");
        }
        Index index = new Index().on(partitionKey, Sort.Direction.ASC);

        if (sortKey != null && !sortKey.isBlank()) {
            index.on(sortKey, Sort.Direction.ASC);
        }

        mongoTemplate.indexOps(collectionName).ensureIndex(index);

    }

    /**
     * Tạo global secondary index cho collection chính
     */
    public void createGlobalSecondaryIndex(
            String mainCollection,
            String indexTableName,
            String partitionKey,
            @Nullable String sortKey,
            AttributeProjection projection,
            List<String> includedFields
    ) {
        
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalArgumentException("Partition key is required for GSI.");
        }

        List<String> gsiPartitionNames = new ArrayList<>();
        List<Document> allDocs = new ArrayList<>();

        // Lấy số lượng partition từ collection_config
        Document collection_config = mongoTemplate.findOne(
                Query.query(Criteria.where("collectionName").is(mainCollection)),
                Document.class,
                "collections_config"
        );

        if (collection_config == null || !collection_config.containsKey("numberPartitions")) {
            throw new IllegalStateException("Could not find configuration for main collection or numberPartitions missing.");
        }

        int numMainPartitions = collection_config.getInteger("numberPartitions");

        // Đọc toàn bộ dữ liệu từ các partition collection của main collection
        for (int i = 0; i < numMainPartitions; i++) {
            String partitionName = mainCollection + "_partition_" + i;
            List<Document> docs = mongoTemplate.findAll(Document.class, partitionName);
            allDocs.addAll(docs);
        }


        // Lấy tên trường làm partition_key và sort_key trong collection main
        String mainPartitionKey = collection_config.getString("partition_key");
        String mainSortKey = collection_config.getString("sort_key");


        // Phân chia GSI
        Map<Integer, List<Document>> gsiPartitions = new HashMap<>();
        for (int i = 0; i < NUM_GSI_PARTITIONS; i++) {
            gsiPartitions.put(i, new ArrayList<>());
        }

        for (Document doc : allDocs) {
            Object key = doc.get(partitionKey);
            if (key == null) continue;

            int index = hashService.getPartitionIndex(key.toString(), NUM_GSI_PARTITIONS);
            Document gsiDoc = new Document();

            // 1. GSI keys
            gsiDoc.append(partitionKey, key);
            if (sortKey != null && doc.containsKey(sortKey)) {
                gsiDoc.append(sortKey, doc.get(sortKey));
            }

            // 2. Main keys
            Object mainPk = doc.get(mainPartitionKey);
            gsiDoc.append("main_partition_key", mainPk);
            if (mainSortKey != null && doc.containsKey(mainSortKey)) {
                gsiDoc.append("main_sort_key", doc.get(mainSortKey));
            }

            // 3. Main collection name
            gsiDoc.append("mainCollection", mainCollection);

            // 4. Projection data
            if (projection == AttributeProjection.ALL) {
                gsiDoc.append("fullData", doc);
            } else if (projection == AttributeProjection.INCLUDE && includedFields != null) {
                Document includeData = new Document();
                for (String field : includedFields) {
                    if (doc.containsKey(field)) {
                        includeData.append(field, doc.get(field));
                    }
                }
                gsiDoc.append("includeData", includeData);
            }
            // ONLY_KEYS không thêm fullData hay includeData gì cả

            // 6. Add to partition
            gsiPartitions.get(index).add(gsiDoc);
        }


        // Tạo collection và insert dữ liệu
        for (int i = 0; i < NUM_GSI_PARTITIONS; i++) {
            String gsiPartitionName = indexTableName + "_partition_" + i;
            gsiPartitionNames.add(gsiPartitionName);

            List<Document> toInsert = gsiPartitions.get(i);
            if (!toInsert.isEmpty()) {
                mongoTemplate.insert(toInsert, gsiPartitionName);
            }

            createIndexForPartitions(gsiPartitionName,partitionKey,sortKey);
        }

        // Lưu metadata vào index_table_config
        Document config = new Document()
                .append("mainCollection", mainCollection)
                .append("partition_key", partitionKey)
                .append("sort_key", sortKey)
                .append("partitions", gsiPartitionNames)
                .append("indexTableName", indexTableName)
                .append("main_partition_key", mainPartitionKey)
                .append("main_sort_key", mainSortKey)
                .append("attribute_projections", projection.name())
                .append("includedFields", projection == AttributeProjection.INCLUDE ? includedFields : Collections.emptyList())
                .append("createdAt", new Date())
                .append("updatedAt", new Date());

        mongoTemplate.insert(config, "index_table_config");

        // Cập nhật thêm GSI vào collections_config
        Update update = new Update().push("gsiIndexes", new Document()
                .append("indexTableName", indexTableName)
                .append("partition_key", partitionKey)
                .append("sort_key", sortKey)
                .append("projection", projection.name())
                .append("includedFields", projection == AttributeProjection.INCLUDE ? includedFields : Collections.emptyList())
                .append("createdAt", new Date())
        );

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("collectionName").is(mainCollection)),
                update,
                "collections_config"
        );


    }



}
