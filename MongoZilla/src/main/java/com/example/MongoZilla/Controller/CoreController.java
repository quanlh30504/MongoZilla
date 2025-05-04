package com.example.MongoZilla.Controller;

import com.example.MongoZilla.Enum.AttributeProjection;
import com.example.MongoZilla.Model.QueryRequest;
import com.example.MongoZilla.Model.QueryResult;
import com.example.MongoZilla.Model.CollectionInfo;
import com.example.MongoZilla.Model.IndexInfo;
import com.example.MongoZilla.Service.CoreService;
import com.example.MongoZilla.Service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/core")
@RequiredArgsConstructor
public class CoreController {

    private final IndexService indexService;
    private final CoreService coreService;

    @PostMapping("/create-collection")
    public ResponseEntity<String> createCollection(@RequestParam("name") String name,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam("partition_key") String partitionKey,
                                                   @Nullable @RequestParam("sort_key") String sortKey) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Only CSV files are allowed.");
        }
        coreService.importCsvAndPartitionData(name, file, partitionKey, sortKey);
        return ResponseEntity.ok("Collection created from CSV.");
    }

    @PostMapping("/create-index")
    public ResponseEntity<String> createIndex(@RequestParam String collectionName,
                                              @RequestParam String indexTableName,
                                              @RequestParam String partitionKey,
                                              @Nullable @RequestParam String sortKey,
                                              @RequestParam String projection,
                                              @Nullable @RequestParam String includedFields) {
        AttributeProjection projectionEnum;
        try {
            projectionEnum = AttributeProjection.valueOf(projection.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid projection type. Use ONLY_KEYS, INCLUDE, or ALL.");
        }

        List<String> includedFieldsList = null;
        if (projectionEnum == AttributeProjection.INCLUDE) {
            if (includedFields == null || includedFields.isBlank()) {
                return ResponseEntity.badRequest().body("Included fields must be provided when projection is INCLUDE.");
            }
            includedFieldsList = Arrays.stream(includedFields.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }

        indexService.createGlobalSecondaryIndex(
                collectionName,
                indexTableName,
                partitionKey,
                sortKey,
                projectionEnum,
                includedFieldsList
        );

        return ResponseEntity.ok("Index created successfully.");
    }

//
//    @PostMapping("/query")
//   public ResponseEntity<QueryResult> query(@RequestParam String collectionName,
//                                            @RequestBody Map<String, String> conditions) {
//       return ResponseEntity.ok(coreService.query(collectionName, conditions));
//   }

   /**
    * Lấy danh sách các collections chính (không bao gồm các bảng index)
    * @return Danh sách thông tin về các collections
    */
   @GetMapping()
   public ResponseEntity<List<CollectionInfo>> getCollections() {
       return ResponseEntity.ok(coreService.getMainCollections());
   }
   
   /**
    * Lấy thông tin về các bảng index liên quan đến một collection
    * @param collectionName Tên collection cần lấy thông tin index
    * @return Danh sách thông tin về các bảng index
    */
   @GetMapping("/indexes")
   public ResponseEntity<List<IndexInfo>> getCollectionIndexes(@RequestParam String collectionName) {
       return ResponseEntity.ok(coreService.getCollectionIndexes(collectionName));
   }

    @PostMapping("/query-main")
    public ResponseEntity<QueryResult> queryMain(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(coreService.queryMain(request));
    }

    @PostMapping("/query-gsi")
    public ResponseEntity<QueryResult> queryGSI(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(coreService.queryGSI(request));
    }



}
