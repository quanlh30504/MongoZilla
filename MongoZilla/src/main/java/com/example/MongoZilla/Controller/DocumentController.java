//package com.example.MongoZilla.Controller;
//
//import com.example.MongoZilla.Model.PageResponse;
//import com.example.MongoZilla.Service.DocumentService;
//import lombok.RequiredArgsConstructor;
//import org.bson.Document;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/documents")
//@RequiredArgsConstructor
//public class DocumentController {
//
//    private final DocumentService documentService;
//
//    @PostMapping("/{collection}")
//    public ResponseEntity<?> insert(@PathVariable String collection, @RequestBody Map<String, Object> body) {
//        Document doc = new Document(body);
//        return ResponseEntity.ok(documentService.insert(collection, doc));
//    }
//
//    @PutMapping("/{collection}/{id}")
//    public ResponseEntity<?> update(@PathVariable String collection, @PathVariable String id, @RequestBody Map<String, Object> body) {
//        Document doc = new Document(body);
//        return ResponseEntity.ok(documentService.update(collection, id, doc));
//    }
//
//    @DeleteMapping("/{collection}/{id}")
//    public ResponseEntity<?> delete(@PathVariable String collection, @PathVariable String id) {
//        documentService.delete(collection, id);
//        return ResponseEntity.ok().build();
//    }
//
//    @GetMapping("/{collection}")
//    public ResponseEntity<PageResponse<Document>> getAll(
//            @PathVariable String collection,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(required = false) String sortBy,
//            @RequestParam(defaultValue = "asc") String sortDirection) {
//        return ResponseEntity.ok(documentService.findAll(collection, page, size, sortBy, sortDirection));
//    }
//
//    @GetMapping("/{collection}/{id}")
//    public ResponseEntity<?> getById(@PathVariable String collection, @PathVariable String id) {
//        return ResponseEntity.ok(documentService.findById(collection, id));
//    }
//}
