package com.duc.crqa.common.util;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

public final class CsvUtil {

    private static final String BOM = "\uFEFF";

    private CsvUtil() {
    }

    public static List<CSVRecord> parse(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a CSV file to upload");
        }
        String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (content.startsWith(BOM)) {
            content = content.substring(1);
        }
        String[] header = readHeader(content);
        if (header.length == 0) {
            return List.of();
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(header)
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (Reader reader = new StringReader(content);
             CSVParser parser = format.parse(reader)) {
            return parser.getRecords();
        }
    }

    private static String[] readHeader(String content) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (Reader reader = new StringReader(content);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                String[] header = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    String name = record.get(i);
                    if (i == 0 && name != null && name.startsWith(BOM)) {
                        name = name.substring(1);
                    }
                    name = name == null ? "" : name.trim();
                    header[i] = name.isEmpty() ? "column" + (i + 1) : name;
                }
                return header;
            }
        }
        return new String[0];
    }

    public static String value(CSVRecord record, String header) {
        if (!record.isMapped(header)) {
            return null;
        }
        String value;
        try {
            value = record.get(header);
        } catch (RuntimeException ex) {
            return null;
        }
        return value == null || value.isBlank() ? null : value;
    }

    public static String value(CSVRecord record, String... headers) {
        for (String header : headers) {
            String value = value(record, header);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
