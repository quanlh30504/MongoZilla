package com.example.MongoZilla.Controller;

import com.example.MongoZilla.Service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/{collection}")
    public ResponseEntity<?> insert(@PathVariable String collection, @RequestBody Map<String, Object> body) {
        Document doc = new Document(body);
        return ResponseEntity.ok(documentService.insert(collection, doc));
    }

    @PutMapping("/{collection}/{id}")
    public ResponseEntity<?> update(@PathVariable String collection, @PathVariable String id, @RequestBody Map<String, Object> body) {
        Document doc = new Document(body);
        return ResponseEntity.ok(documentService.update(collection, id, doc));
    }

    @DeleteMapping("/{collection}/{id}")
    public ResponseEntity<?> delete(@PathVariable String collection, @PathVariable String id) {
        documentService.delete(collection, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{collection}")
    public ResponseEntity<?> getAll(@PathVariable String collection) {
        return ResponseEntity.ok(documentService.findAll(collection));
    }

    @GetMapping("/{collection}/{id}")
    public ResponseEntity<?> getById(@PathVariable String collection, @PathVariable String id) {
        return ResponseEntity.ok(documentService.findById(collection, id));
    }
}
