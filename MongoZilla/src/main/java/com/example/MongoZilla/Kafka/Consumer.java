//package com.example.MongoZilla.Kafka;
//
//import com.example.MongoZilla.Model.IndexMetadata;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import org.bson.Document;
//import org.bson.types.ObjectId;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.data.mongodb.core.query.Update;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.Map;
//
//@Component
//@RequiredArgsConstructor
//public class Consumer {
//
//    private final MongoTemplate mongoTemplate;
//
//
//    @KafkaListener(topics = "index-sync", groupId = "index-sync-group")
//    public void handleIndexSync(String message) throws JsonProcessingException {
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode json = mapper.readTree(message);
//
//        String action = json.get("action").asText();
//        String collection = json.get("collection").asText();
//        JsonNode dataNode = json.get("data");
//
//        // Tìm các index cấu hình tương ứng
//        List<IndexMetadata> indices = mongoTemplate.find(
//                Query.query(Criteria.where("collectionName").is(collection)),
//                IndexMetadata.class,
//                "index_config"
//        );
//
//        for (IndexMetadata index : indices) {
//            String indexCollection = index.getIndexCollection();
//            List<String> fields = index.getFields();
//
//            Document indexKey = new Document();
//            for (String field : fields) {
//                if (dataNode.has(field)) {
//                    indexKey.append(field, dataNode.get(field).asText());
//                } else {
//                    // Nếu không tìm thấy trường, bỏ qua hoặc thêm giá trị null
//                    indexKey.append(field, null);
//                }
//            }
//
//            // Xử lý _id an toàn
//            Object mainId;
//            JsonNode idNode = dataNode.get("_id");
//
//            // Kiểm tra nếu _id là một ObjectId MongoDB theo định dạng mở rộng JSON
//            if (idNode.isObject() && idNode.has("$oid")) {
//                String oidStr = idNode.get("$oid").asText();
//                // Kiểm tra tính hợp lệ của chuỗi OID
//                if (oidStr.length() == 24 && oidStr.matches("[0-9a-fA-F]{24}")) {
//                    mainId = new ObjectId(oidStr);
//                } else {
//                    mainId = oidStr;
//                }
//            }
//            // Nếu _id là một chuỗi trực tiếp
//            else if (idNode.isTextual()) {
//                String idStr = idNode.asText();
//                if (idStr.length() == 24 && idStr.matches("[0-9a-fA-F]{24}")) {
//                    mainId = new ObjectId(idStr);
//                } else {
//                    mainId = idStr;
//                }
//            }
//            // Trường hợp khác, sử dụng giá trị string của node
//            else {
//                mainId = idNode.toString();
//            }
//
//            Document indexDoc = new Document("indexKey", indexKey)
//                    .append("mainId", mainId);
//
//            if (index.isIncludeFullData()) {
//                indexDoc.append("fullData", mapper.convertValue(dataNode, Map.class));
//            }
//
//            switch (action) {
//                case "INSERT":
//                case "UPDATE":
//                    Query q = new Query(Criteria.where("mainId").is(mainId));
//                    mongoTemplate.upsert(q, Update.fromDocument(new Document("$set", indexDoc)), indexCollection);
//                    break;
//                case "DELETE":
//                    mongoTemplate.remove(Query.query(Criteria.where("mainId").is(mainId)), indexCollection);
//                    break;
//            }
//        }
//    }
//
//}
