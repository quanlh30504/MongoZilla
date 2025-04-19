package com.example.MongoZilla.Service;

import com.google.common.hash.Hashing;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class HashService {


    /**
     * Tính index của partition dựa trên partition key value
     */
    public int getPartitionIndex(String partitionKeyValue, int numberPartition) {
        int hash = Hashing.murmur3_32().hashString(partitionKeyValue, StandardCharsets.UTF_8).asInt();
        return Math.abs(hash % numberPartition);
    }

    /**
     * Trả về tên collection chứa dữ liệu partition tương ứng
     */
    public String getPartitionCollectionName(String baseCollectionName, String partitionKeyValue, int numberPartition) {
        int index = getPartitionIndex(partitionKeyValue, numberPartition);
        return baseCollectionName + "_partition_" + index;
    }



}
