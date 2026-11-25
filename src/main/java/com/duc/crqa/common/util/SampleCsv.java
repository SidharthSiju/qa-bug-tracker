package com.duc.crqa.common.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public final class SampleCsv {

    private SampleCsv() {
    }

    public static void write(String resourcePath, String downloadName, HttpServletResponse response)
            throws IOException {
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Template not found: " + resourcePath);
            return;
        }
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + downloadName + "\"");
        try (InputStream in = resource.getInputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }
}
