package com.example.MongoZilla.Utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Converter {

    public static Map<String, Object> convertAttributeValues(Map<String, Map<String, String>> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : input.entrySet()) {
            String key = entry.getKey();
            // Loại bỏ dấu : ở đầu key nếu có
            String normalizedKey = key.startsWith(":") ? key.substring(1) : key;
            Map<String, String> valueMap = entry.getValue();

            if (valueMap.containsKey("S")) {
                result.put(normalizedKey, valueMap.get("S"));
            } else if (valueMap.containsKey("N")) {
                result.put(normalizedKey, Double.valueOf(valueMap.get("N")));
            } else if (valueMap.containsKey("BOOL")) {
                result.put(normalizedKey, Boolean.valueOf(valueMap.get("BOOL")));
            } else if (valueMap.containsKey("NULL") && valueMap.get("NULL").equalsIgnoreCase("true")) {
                result.put(normalizedKey, null);
            }
            // có thể mở rộng thêm List, Map...
        }
        return result;
    }

    /**
     * Tries to convert a string value to the most appropriate data type
     * @param value String value to convert
     * @return Converted value (Integer, Long, Double, Boolean, Date, or original String)
     */
    public static Object convertToAppropriateType(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Try to convert to number
        try {
            // Check if it's an integer
            if (value.matches("^-?\\d+$")) {
                // Check if it's within Integer range
                long longValue = Long.parseLong(value);
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return (int) longValue;
                } else {
                    return longValue;
                }
            }
            // Check if it's a floating point number
            else if (value.matches("^-?\\d+\\.\\d+$")) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            // Not a number, continue checking other types
        }

        // Check if it's a boolean
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }

        // Check if it's a date (common formats)
        try {
            // Try common date formats
            String[] dateFormats = {
                    "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy"
            };

            for (String format : dateFormats) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat(format);
                    dateFormat.setLenient(false);
                    Date date = dateFormat.parse(value);
                    if (date != null) {
                        return date;
                    }
                } catch (ParseException ignored) {
                    // Try next format
                }
            }
        } catch (Exception e) {
            // Not a date, return original string
        }

        // If no match with any type, keep original string value
        return value;
    }
}
