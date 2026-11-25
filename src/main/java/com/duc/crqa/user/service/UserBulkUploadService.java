package com.duc.crqa.user.service;

import com.duc.crqa.common.dto.BulkUploadResult;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.util.CsvUtil;
import com.duc.crqa.user.dto.UserForm;
import com.duc.crqa.user.repository.UserRepository;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserBulkUploadService {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserBulkUploadService(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public BulkUploadResult importFile(MultipartFile file) throws IOException {
        List<CSVRecord> records = CsvUtil.parse(file);
        BulkUploadResult result = new BulkUploadResult();

        int rowNumber = 0;
        for (CSVRecord record : records) {
            rowNumber++;
            String reference = BulkUploadResult.part("username", CsvUtil.value(record, "username"));
            try {
                userService.create(toForm(record));
                result.addSuccess();
            } catch (IllegalArgumentException e) {
                result.addError(rowNumber, BulkUploadResult.withReference(e.getMessage(), reference));
            } catch (Exception e) {
                result.addError(rowNumber,
                        BulkUploadResult.withReference("Failed to create user: " + e.getMessage(), reference));
            }
        }
        return result;
    }

    private UserForm toForm(CSVRecord record) {
        String username = CsvUtil.value(record, "username");
        if (username == null) {
            throw new IllegalArgumentException("username is required");
        }
        String displayName = CsvUtil.value(record, "displayName");
        if (displayName == null) {
            throw new IllegalArgumentException("displayName is required");
        }
        String password = CsvUtil.value(record, "password");
        if (password == null) {
            throw new IllegalArgumentException("password is required");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        UserForm form = new UserForm();
        form.setUsername(username);
        form.setDisplayName(displayName);
        form.setEmail(CsvUtil.value(record, "email"));
        form.setPassword(password);
        form.setActive(parseActive(CsvUtil.value(record, "active")));
        form.setRoles(parseRoles(CsvUtil.value(record, "roles")));
        return form;
    }

    private boolean parseActive(String value) {
        if (value == null) {
            return true;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid active value: " + value);
        };
    }

    private Set<Role> parseRoles(String value) {
        if (value == null) {
            return Set.of();
        }
        Set<Role> roles = new HashSet<>();
        for (String part : value.split(",")) {
            String name = part.trim();
            if (name.isBlank()) {
                continue;
            }
            try {
                roles.add(Role.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid role: " + name);
            }
        }
        return roles;
    }
}
