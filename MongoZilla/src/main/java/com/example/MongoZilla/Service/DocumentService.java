package com.example.MongoZilla.Service;

import com.example.MongoZilla.Kafka.Producer;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Flow;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final MongoTemplate mongoTemplate;
    private final Producer producer;

    public Document insert(String collectionName, Document data) {
        Document saved = mongoTemplate.insert(data, collectionName);
        producer.publishSyncMessage("INSERT", collectionName, saved);
        return saved;
    }

    public Document update(String collectionName, String id, Document updateData) {
        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        Document existing = mongoTemplate.findOne(query, Document.class, collectionName);
        if (existing == null) throw new RuntimeException("Document not found");

        updateData.put("_id", existing.getObjectId("_id"));
        mongoTemplate.save(updateData, collectionName);
        producer.publishSyncMessage("UPDATE", collectionName, updateData);
        return updateData;
    }

    public void delete(String collectionName, String id) {
        Query query = new Query(Criteria.where("_id").is(new ObjectId(id)));
        Document deleted = mongoTemplate.findAndRemove(query, Document.class, collectionName);
        if (deleted != null) {
            producer.publishSyncMessage("DELETE", collectionName, deleted);
        }
    }

    public List<Document> findAll(String collectionName) {
        return mongoTemplate.findAll(Document.class, collectionName);
    }

    public Document findById(String collectionName, String id) {
        return mongoTemplate.findById(new ObjectId(id), Document.class, collectionName);
    }
}

