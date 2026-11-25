package com.duc.crqa.admin.filter;

import com.duc.crqa.admin.service.UiSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Supplies the global "rows per page" UI setting as the default value of the {@code size} request parameter.
 *
 * <p>Every paginated controller declares {@code @RequestParam(defaultValue = "10") int size}; by injecting the
 * parameter when it is absent (GET only, excluding the REST API and static assets) the configured page size applies to
 * all tables without touching each controller. An explicit {@code ?size=} still wins.
 */
@Component
public class PageSizeFilter extends OncePerRequestFilter {

    private static final Set<String> STATIC_PREFIXES =
            Set.of("/css/", "/js/", "/images/", "/vendor/", "/webjars/", "/favicon.ico");

    private final UiSettingsService uiSettingsService;

    public PageSizeFilter(UiSettingsService uiSettingsService) {
        this.uiSettingsService = uiSettingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shouldApply(request)) {
            int size = uiSettingsService.rowsPerPage();
            chain.doFilter(new SizeDefaultRequestWrapper(request, String.valueOf(size)), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean shouldApply(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod()) || request.getParameter("size") != null) {
            return false;
        }
        return !isApiOrStatic(request);
    }

    private boolean isApiOrStatic(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/api")) {
            return true;
        }
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final class SizeDefaultRequestWrapper extends HttpServletRequestWrapper {

        private final String size;

        private SizeDefaultRequestWrapper(HttpServletRequest request, String size) {
            super(request);
            this.size = size;
        }

        @Override
        public String getParameter(String name) {
            if ("size".equals(name)) {
                return size;
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            if ("size".equals(name)) {
                String[] values = super.getParameterValues(name);
                return values != null ? values : new String[] {size};
            }
            return super.getParameterValues(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> values = new LinkedHashMap<>(super.getParameterMap());
            values.putIfAbsent("size", new String[] {size});
            return values;
        }
    }
}
