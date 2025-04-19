package com.example.MongoZilla.Utils;

import org.bson.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CsvUtils {
    public static List<Document> parseCsvToDocuments(MultipartFile file) throws IOException {
        List<Document> docs = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // Đọc dòng header
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return docs; // File rỗng
            }
            
            // Xử lý header, loại bỏ dấu cách thừa và dấu ngoặc kép nếu có
            String[] headers = parseCSVLine(headerLine);
            
            String line;
            while ((line = reader.readLine()) != null) {
                // Xử lý dòng dữ liệu, hỗ trợ các trường hợp có dấu phẩy trong giá trị
                String[] values = parseCSVLine(line);
                
                Document doc = new Document();
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    // Chuyển đổi giá trị sang kiểu dữ liệu phù hợp
                    Object convertedValue = Converter.convertToAppropriateType(values[i]);
                    doc.append(headers[i], convertedValue);
                }
                docs.add(doc);
            }
        }
        return docs;
    }
    
    /**
     * Phân tích một dòng CSV, hỗ trợ các trường hợp có dấu phẩy trong giá trị được đặt trong dấu ngoặc kép
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentValue = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes; // Đảo trạng thái inQuotes
            } else if (c == ',' && !inQuotes) {
                // Kết thúc một trường
                result.add(currentValue.toString().trim());
                currentValue = new StringBuilder();
            } else {
                currentValue.append(c);
            }
        }
        
        // Thêm giá trị cuối cùng
        result.add(currentValue.toString().trim());
        
        // Loại bỏ dấu ngoặc kép ở đầu và cuối các giá trị
        for (int i = 0; i < result.size(); i++) {
            String value = result.get(i);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                result.set(i, value.substring(1, value.length() - 1));
            }
        }
        
        return result.toArray(new String[0]);
    }
}
