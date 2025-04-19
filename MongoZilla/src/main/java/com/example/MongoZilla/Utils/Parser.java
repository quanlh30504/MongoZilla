package com.example.MongoZilla.Utils;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {
    private static Map<String, String> parseConditionExpression(String expression) {
        Map<String, String> map = new HashMap<>();
        if (expression == null || expression.isEmpty()) return map;

        // Tách theo AND
        String[] parts = expression.split("(?i)\\s+AND\\s+");

        for (String part : parts) {
            // Hỗ trợ >=, >, = đơn giản
            Matcher matcher = Pattern.compile("(\\w+)\\s*(=|>=|>|<=|<)\\s*(:\\w+)").matcher(part.trim());
            if (matcher.find()) {
                String field = matcher.group(1).trim();
                String placeholder = matcher.group(3).trim();
                
                // Loại bỏ dấu : ở đầu placeholder để khớp với key trong valueMap
                String placeholderKey = placeholder.startsWith(":") ? 
                                      placeholder.substring(1) : placeholder;
                
                map.put(field, placeholderKey);
            }
        }
        return map;
    }

    public static Criteria parseKeyConditionExpression(String keyConditionExp, Map<String, Object> valueMap, String partitionKey, String sortKey) {
        // Nếu keyConditionExp là null hoặc rỗng, trả về null
        if (keyConditionExp == null || keyConditionExp.isEmpty()) {
            return null;
        }

        // Phân tích KeyConditionExpression
        Map<String, String> keyConditionMap = parseConditionExpression(keyConditionExp);
        
        // Nếu không có partition key, trả về null
        if (!keyConditionMap.containsKey(partitionKey)) {
            return null;
        }
        
        String partitionPlaceholder = keyConditionMap.get(partitionKey);
        // Loại bỏ dấu : ở đầu placeholder để khớp với key trong valueMap
        String partitionPlaceholderKey = partitionPlaceholder.startsWith(":") ? 
                                        partitionPlaceholder.substring(1) : partitionPlaceholder;
        
        if (!valueMap.containsKey(partitionPlaceholderKey)) {
            return null;
        }
        
        Object partitionVal = valueMap.get(partitionPlaceholderKey);
        
        if (partitionVal == null) {
            return null; // Không thể tạo query nếu giá trị partition key là null
        }
        
        Criteria keyCriteria = Criteria.where(partitionKey).is(partitionVal);

        // Xử lý sort key nếu có
        if (sortKey != null && !sortKey.isEmpty() && keyConditionMap.containsKey(sortKey)) {
            String sortPlaceholder = keyConditionMap.get(sortKey);
            // Loại bỏ dấu : ở đầu placeholder để khớp với key trong valueMap
            String sortPlaceholderKey = sortPlaceholder.startsWith(":") ? 
                                       sortPlaceholder.substring(1) : sortPlaceholder;
            
            if (!valueMap.containsKey(sortPlaceholderKey)) {
                // Nếu không tìm thấy giá trị cho sort key, bỏ qua phần này
                return keyCriteria;
            }
            
            Object sortVal = valueMap.get(sortPlaceholderKey);
            
            if (sortVal != null) {
                // Tìm toán tử được sử dụng với sort key
                // Sử dụng ":" + sortPlaceholder để tìm đúng placeholder trong biểu thức điều kiện
                Pattern pattern = Pattern.compile(sortKey + "\\s*(=|>=|>|<=|<)\\s*:" + Pattern.quote(sortPlaceholder));
                Matcher matcher = pattern.matcher(keyConditionExp);
                
                if (matcher.find()) {
                    String operator = matcher.group(1).trim();
                    
                    switch (operator) {
                        case "=":
                            keyCriteria = keyCriteria.and(sortKey).is(sortVal);
                            break;
                        case ">=":
                            keyCriteria = keyCriteria.and(sortKey).gte(sortVal);
                            break;
                        case ">":
                            keyCriteria = keyCriteria.and(sortKey).gt(sortVal);
                            break;
                        case "<=":
                            keyCriteria = keyCriteria.and(sortKey).lte(sortVal);
                            break;
                        case "<":
                            keyCriteria = keyCriteria.and(sortKey).lt(sortVal);
                            break;
                        default:
                            // Mặc định sử dụng = nếu không tìm thấy toán tử
                            keyCriteria = keyCriteria.and(sortKey).is(sortVal);
                    }
                } else {
                    // Nếu không tìm thấy toán tử, giả định là =
                    keyCriteria = keyCriteria.and(sortKey).is(sortVal);
                }
            }
        }

        return keyCriteria;
    }

    public static Criteria parseFilterExpression(String expression, Map<String, Object> valueMap) {
        // Nếu expression là null hoặc rỗng, trả về null để xử lý ở hàm gọi
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        
        List<Criteria> filterCriteriaList = new ArrayList<>();

        // Tách các phần của FilterExpression theo toán tử AND
        String[] parts = expression.split("(?i)\\s+AND\\s+");
        for (String part : parts) {
            // Tìm các toán tử trong biểu thức (>, <, >=, <=, =, BETWEEN, IN)
            // Đảm bảo tìm đúng placeholder với dấu : ở đầu
            Matcher matcher = Pattern.compile("(\\w+)\\s*(=|>|>=|<|<=|BETWEEN|IN)\\s*(:\\w+)").matcher(part.trim());

            if (matcher.find()) {
                String field = matcher.group(1).trim();
                String operator = matcher.group(2).trim();
                String placeholder = matcher.group(3).trim();
                
                // Loại bỏ dấu : ở đầu placeholder để khớp với key trong valueMap
                String placeholderKey = placeholder.startsWith(":") ? 
                                      placeholder.substring(1) : placeholder;
                
                // Kiểm tra xem placeholder có tồn tại trong valueMap không
                if (!valueMap.containsKey(placeholderKey)) {
                    continue; // Bỏ qua điều kiện này nếu không tìm thấy giá trị
                }
                
                Object value = valueMap.get(placeholderKey);
                if (value == null) {
                    continue; // Bỏ qua nếu giá trị là null
                }

                // Kiểm tra kiểu dữ liệu và xử lý toán tử tương ứng
                try {
                    switch (operator) {
                        case "=":
                            filterCriteriaList.add(Criteria.where(field).is(value));
                            break;
                        case ">":
                            filterCriteriaList.add(Criteria.where(field).gt(value));
                            break;
                        case ">=":
                            filterCriteriaList.add(Criteria.where(field).gte(value));
                            break;
                        case "<":
                            filterCriteriaList.add(Criteria.where(field).lt(value));
                            break;
                        case "<=":
                            filterCriteriaList.add(Criteria.where(field).lte(value));
                            break;
                        case "BETWEEN":
                            // Xử lý BETWEEN biểu thức
                            if (part.contains(" AND ")) {
                                // Tìm hai placeholder trong biểu thức BETWEEN
                                // Đảm bảo tìm đúng placeholder với dấu : ở đầu
                                Matcher betweenMatcher = Pattern.compile("BETWEEN\\s*(:\\w+)\\s+AND\\s*(:\\w+)").matcher(part);
                                if (betweenMatcher.find()) {
                                    String startPlaceholder = betweenMatcher.group(1).trim();
                                    String endPlaceholder = betweenMatcher.group(2).trim();
                                    
                                    // Loại bỏ dấu : ở đầu placeholder
                                    String startPlaceholderKey = startPlaceholder.startsWith(":") ? 
                                                              startPlaceholder.substring(1) : startPlaceholder;
                                    String endPlaceholderKey = endPlaceholder.startsWith(":") ? 
                                                            endPlaceholder.substring(1) : endPlaceholder;
                                    
                                    Object startValue = valueMap.get(startPlaceholderKey);
                                    Object endValue = valueMap.get(endPlaceholderKey);
                                    
                                    if (startValue != null && endValue != null) {
                                        filterCriteriaList.add(Criteria.where(field).gte(startValue).lte(endValue));
                                    }
                                }
                            }
                            break;
                        case "IN":
                            // Xử lý IN biểu thức
                            if (value instanceof List) {
                                filterCriteriaList.add(Criteria.where(field).in((List<?>) value));
                            } else if (value instanceof Object[]) {
                                filterCriteriaList.add(Criteria.where(field).in((Object[]) value));
                            } else {
                                // Nếu chỉ có một giá trị, xử lý như is
                                filterCriteriaList.add(Criteria.where(field).is(value));
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported operator: " + operator);
                    }
                } catch (Exception e) {
                    // Log lỗi và bỏ qua điều kiện này
                    System.err.println("Error processing filter condition: " + part + ". Error: " + e.getMessage());
                }
            }
        }

        // Nếu không có điều kiện filter nào, trả về null
        if (filterCriteriaList.isEmpty()) {
            return null;
        }
        
        // Nếu chỉ có một điều kiện, trả về điều kiện đó
        if (filterCriteriaList.size() == 1) {
            return filterCriteriaList.get(0);
        }
        
        // Nếu có nhiều điều kiện, kết hợp chúng với AND
        return new Criteria().andOperator(filterCriteriaList.toArray(new Criteria[0]));
    }




}
