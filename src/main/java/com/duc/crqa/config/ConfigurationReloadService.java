package com.duc.crqa.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationReloadService {

    private final ApplicationContext applicationContext;
    private final ConfigurableEnvironment environment;
    private final ThemeProperties themeProperties;
    private final SlaProperties slaProperties;
    private final UiOptionsProperties uiOptionsProperties;

    public ConfigurationReloadService(ApplicationContext applicationContext,
                                      ConfigurableEnvironment environment,
                                      ThemeProperties themeProperties,
                                      SlaProperties slaProperties,
                                      UiOptionsProperties uiOptionsProperties) {
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.themeProperties = themeProperties;
        this.slaProperties = slaProperties;
        this.uiOptionsProperties = uiOptionsProperties;
    }

    public Map<String, String> reload() {
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> loadedFiles = new ArrayList<>();

        String[] classpathPatterns = {
                "classpath*:application*.yml",
                "classpath*:application*.yaml",
                "classpath*:application*.properties"
        };

        ResourcePatternResolver resolver = (ResourcePatternResolver) applicationContext;
        for (String pattern : classpathPatterns) {
            try {
                for (Resource resource : resolver.getResources(pattern)) {
                    if (resource.isReadable()) {
                        collect(resource, filename(resource), merged, loadedFiles);
                    }
                }
            } catch (IOException ignored) {
                // Continue with other locations when a pattern does not resolve.
            }
        }

        loadFromDirectory(Path.of("config"), merged, loadedFiles);
        loadFromDirectory(Path.of("."), merged, loadedFiles);

        MapPropertySource reloaded = new MapPropertySource("dynamicConfigReload", merged);
        var propertySources = environment.getPropertySources();
        if (propertySources.contains("dynamicConfigReload")) {
            propertySources.replace("dynamicConfigReload", reloaded);
        } else {
            propertySources.addFirst(reloaded);
        }

        rebindApplicationProperties();

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("filesLoaded", String.valueOf(loadedFiles.size()));
        summary.put("app.theme", themeProperties.getTheme());
        summary.put("app.sla.blocker-hours", String.valueOf(slaProperties.getBlockerHours()));
        summary.put("app.sla.critical-hours", String.valueOf(slaProperties.getCriticalHours()));
        return summary;
    }

    private void loadFromDirectory(Path dir, Map<String, Object> merged, List<String> loadedFiles) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isConfigFile)
                    .forEach(path -> {
                        Resource resource = new FileSystemResource(path);
                        try {
                            collect(resource, path.getFileName().toString(), merged, loadedFiles);
                        } catch (IOException ignored) {
                            // Skip unreadable files.
                        }
                    });
        } catch (IOException ignored) {
            // Directory may not be accessible; continue.
        }
    }

    private boolean isConfigFile(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith("application")
                && (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties"));
    }

    private void collect(Resource resource, String filename, Map<String, Object> merged, List<String> loadedFiles)
            throws IOException {
        List<PropertySource<?>> sources;
        if (filename.endsWith(".properties")) {
            sources = new PropertiesPropertySourceLoader().load(filename, resource);
        } else {
            sources = new YamlPropertySourceLoader().load(filename, resource);
        }

        for (PropertySource<?> propertySource : sources) {
            if (propertySource.getSource() instanceof Map<?, ?> map) {
                map.forEach((key, value) -> merged.put(String.valueOf(key), unwrap(value)));
            }
        }
        loadedFiles.add(resource.getDescription());
    }

    private void rebindApplicationProperties() {
        themeProperties.setTheme(environment.getProperty("app.theme", themeProperties.getTheme()));

        uiOptionsProperties.setPageSizes(listProperty("app.ui.page-sizes", uiOptionsProperties.getPageSizes()));
        uiOptionsProperties.setFontSizes(listProperty("app.ui.font-sizes", uiOptionsProperties.getFontSizes()));

        slaProperties.setBlockerHours(intProperty("app.sla.blocker-hours", slaProperties.getBlockerHours()));
        slaProperties.setCriticalHours(intProperty("app.sla.critical-hours", slaProperties.getCriticalHours()));
        slaProperties.setMajorHours(intProperty("app.sla.major-hours", slaProperties.getMajorHours()));
        slaProperties.setMinorHours(intProperty("app.sla.minor-hours", slaProperties.getMinorHours()));
        slaProperties.setTrivialHours(intProperty("app.sla.trivial-hours", slaProperties.getTrivialHours()));

        slaProperties.setEscalationEnabled(
                booleanProperty("app.sla.escalation-enabled", slaProperties.isEscalationEnabled()));
        slaProperties.setEscalationCron(
                stringProperty("app.sla.escalation-cron", slaProperties.getEscalationCron()));
        slaProperties.setEscalateEveryHours(
                intProperty("app.sla.escalate-every-hours", slaProperties.getEscalateEveryHours()));
    }

    private boolean booleanProperty(String key, boolean fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private String stringProperty(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int intProperty(String key, int fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    private List<String> listProperty(String key, List<String> fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : List.of(value.split(","));
    }

    private Object unwrap(Object value) {
        return value instanceof OriginTrackedValue tracked ? tracked.getValue() : value;
    }

    private String filename(Resource resource) {
        return resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
    }
}
