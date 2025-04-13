package com.example.MongoZilla.Utils;

import org.bson.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CsvUtils {
    public static List<Document> parseCsvToDocuments(MultipartFile file) throws IOException {
        List<Document> docs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String[] headers = reader.readLine().split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                Document doc = new Document();
                for (int i = 0; i < headers.length; i++) {
                    doc.append(headers[i], values[i]);
                }
                docs.add(doc);
            }
        }
        return docs;
    }
}
