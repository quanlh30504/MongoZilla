package com.example.MongoZilla.Controller;

import com.example.MongoZilla.Model.QueryResult;
import com.example.MongoZilla.Model.CollectionInfo;
import com.example.MongoZilla.Model.IndexInfo;
import com.example.MongoZilla.Service.IndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class IndexController {

    private final IndexService indexService;

    @PostMapping("/create-collection")
    public ResponseEntity<String> createCollection(@RequestParam("name") String name,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body("Only CSV files are allowed.");
        }
        indexService.importCsvAndCreateCollection(name, file);
        return ResponseEntity.ok("Collection created from CSV.");
    }

    @PostMapping("/create-index")
    public ResponseEntity<String> createIndex(@RequestParam String collectionName,
                                              @RequestParam String includeFullData,
                                              @RequestParam String indexFields) {
        boolean includeFullDataBool = Boolean.parseBoolean(includeFullData);
        List<String> indexFieldsList = List.of(indexFields.split(","));
        indexService.createIndex(collectionName, indexFieldsList, includeFullDataBool);
        return ResponseEntity.ok("Index created.");
    }

   @GetMapping("/query")
   public ResponseEntity<QueryResult> query(@RequestParam String collectionName,
                                            @RequestBody Map<String, String> conditions) {
       return ResponseEntity.ok(indexService.query(collectionName, conditions));
   }

   /**
    * Lấy danh sách các collections chính (không bao gồm các bảng index)
    * @return Danh sách thông tin về các collections
    */
   @GetMapping()
   public ResponseEntity<List<CollectionInfo>> getCollections() {
       return ResponseEntity.ok(indexService.getMainCollections());
   }
   
   /**
    * Lấy thông tin về các bảng index liên quan đến một collection
    * @param collectionName Tên collection cần lấy thông tin index
    * @return Danh sách thông tin về các bảng index
    */
   @GetMapping("/indexes")
   public ResponseEntity<List<IndexInfo>> getCollectionIndexes(@RequestParam String collectionName) {
       return ResponseEntity.ok(indexService.getCollectionIndexes(collectionName));
   }
}
