//package com.example.MongoZilla.Service;
//
//import com.example.MongoZilla.Kafka.Producer;
//import com.example.MongoZilla.Model.PageResponse;
//import lombok.RequiredArgsConstructor;
//import org.bson.Document;
//import org.bson.types.ObjectId;
//import org.springframework.data.domain.Sort;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class DocumentService {
//
//    private final MongoTemplate mongoTemplate;
//    private final Producer producer;
//
//    public Document insert(String collectionName, Document data) {
//        Document saved = mongoTemplate.insert(data, collectionName);
//        producer.publishSyncMessage("INSERT", collectionName, saved);
//        return saved;
//    }
//
//    public Document update(String collectionName, String id, Document updateData) {
//        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
//        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
//        if (existing == null) throw new RuntimeException("Document not found");
//
//        updateData.put("_id", existing.getObjectId("_id"));
//        mongoTemplate.save(updateData, collectionName);
//        producer.publishSyncMessage("UPDATE", collectionName, updateData);
//        return updateData;
//    }
//
//    public void delete(String collectionName, String id) {
//        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
//        Document deleted = mongoTemplate.findAndRemove(query, Document.class, collectionName);
//        if (deleted != null) {
//            producer.publishSyncMessage("DELETE", collectionName, deleted);
//        }
//    }
//
//    public PageResponse<Document> findAll(String collectionName, int page, int size, String sortBy, String sortDirection) {
//        Query query = new Query();
//
//        // Apply sorting if sortBy is provided
//        if (sortBy != null && !sortBy.isEmpty()) {
//            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
//                Sort.Direction.DESC : Sort.Direction.ASC;
//            query.with(Sort.by(direction, sortBy));
//        }
//
//        // Get total count
//        long total = mongoTemplate.count(query, collectionName);
//
//        // Apply pagination
//        query.skip((long) page * size);
//        query.limit(size);
//
//        // Get paginated results
//        List<Document> documents = mongoTemplate.find(query, Document.class, collectionName);
//
//        // Calculate pagination metadata
//        int totalPages = (int) Math.ceil((double) total / size);
//        boolean hasNext = (page + 1) < totalPages;
//        boolean hasPrevious = page > 0;
//
//        return new PageResponse<>(
//            documents,
//            total,
//            totalPages,
//            page,
//            size,
//            hasNext,
//            hasPrevious
//        );
//    }
//
//    public Document findById(String collectionName, String id) {
//        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
//        return mongoTemplate.findOne(query, Document.class, collectionName);
//    }
//}
