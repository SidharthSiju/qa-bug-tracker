package com.duc.crqa.controller;

import com.duc.crqa.report.service.ExportAllService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/export-all")
public class ExportAllController {

    private final ExportAllService exportAllService;

    public ExportAllController(ExportAllService exportAllService) {
        this.exportAllService = exportAllService;
    }

    @GetMapping
    public void exportAll(HttpServletResponse response) throws IOException {
        byte[] archive = exportAllService.exportAllZip();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"crqa-export-all.zip\"");
        response.setContentLength(archive.length);
        response.getOutputStream().write(archive);
        response.getOutputStream().flush();
    }
}
