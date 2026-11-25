package com.duc.crqa.admin.service;

import com.duc.crqa.admin.entity.UiSettings;
import com.duc.crqa.admin.repository.UiSettingsRepository;
import com.duc.crqa.config.ThemeProperties;
import com.duc.crqa.config.UiOptionsProperties;
import com.duc.crqa.security.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UiSettingsService {

    public static final List<String> THEMES = List.of("red", "blue", "dark");
    public static final List<String> FONTS =
            List.of("system", "verdana", "courier", "inter", "roboto", "opensans", "ubuntu");

    public static final String DEFAULT_THEME = "red";
    public static final String DEFAULT_FONT = "system";
    public static final int DEFAULT_ROWS_PER_PAGE = 10;
    public static final int DEFAULT_FONT_SIZE = 10;

    private static final Long SETTINGS_ID = 1L;

    private final UiSettingsRepository uiSettingsRepository;
    private final ThemeProperties themeProperties;
    private final UiOptionsProperties uiOptionsProperties;
    private final CurrentUserService currentUserService;

    private volatile UiSettings cached;

    public UiSettingsService(UiSettingsRepository uiSettingsRepository,
                             ThemeProperties themeProperties,
                             UiOptionsProperties uiOptionsProperties,
                             CurrentUserService currentUserService) {
        this.uiSettingsRepository = uiSettingsRepository;
        this.themeProperties = themeProperties;
        this.uiOptionsProperties = uiOptionsProperties;
        this.currentUserService = currentUserService;
    }

    /** Rows-per-page options, from {@code app.ui.page-sizes} in application.yml. */
    public List<Integer> pageSizes() {
        return uiOptionsProperties.pageSizeOptions();
    }

    /** Table-row font-size options, from {@code app.ui.font-sizes} in application.yml. */
    public List<Integer> fontSizes() {
        return uiOptionsProperties.fontSizeOptions();
    }

    /** The single global UI-settings row, created from the configured defaults on first use. */
    public UiSettings current() {
        UiSettings local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = load();
                    cached = local;
                }
            }
        }
        return local;
    }

    public int rowsPerPage() {
        return current().getRowsPerPage();
    }

    @Transactional
    public void update(String theme, String font, Integer rowsPerPage, Integer fontSize) {
        UiSettings settings = uiSettingsRepository.findById(SETTINGS_ID).orElseGet(() -> {
            UiSettings created = new UiSettings();
            created.setId(SETTINGS_ID);
            return created;
        });
        settings.setTheme(normalizeTheme(theme));
        settings.setFont(normalizeFont(font));
        settings.setRowsPerPage(normalizeRows(rowsPerPage));
        settings.setFontSize(normalizeFontSize(fontSize));
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(currentUserService.currentUserId());
        cached = uiSettingsRepository.save(settings);
    }

    private UiSettings load() {
        UiSettings settings = uiSettingsRepository.findById(SETTINGS_ID).orElse(null);
        if (settings == null) {
            UiSettings created = new UiSettings();
            created.setId(SETTINGS_ID);
            created.setTheme(normalizeTheme(themeProperties.getTheme()));
            created.setFont(DEFAULT_FONT);
            created.setRowsPerPage(DEFAULT_ROWS_PER_PAGE);
            created.setFontSize(DEFAULT_FONT_SIZE);
            return uiSettingsRepository.save(created);
        }
        // Never let an unexpected stored value break a page.
        String theme = normalizeTheme(settings.getTheme());
        String font = normalizeFont(settings.getFont());
        int rows = normalizeRows(settings.getRowsPerPage());
        int fontSize = normalizeFontSize(settings.getFontSize());
        if (!theme.equals(settings.getTheme()) || !font.equals(settings.getFont())
                || rows != settings.getRowsPerPage() || fontSize != settings.getFontSize()) {
            settings.setTheme(theme);
            settings.setFont(font);
            settings.setRowsPerPage(rows);
            settings.setFontSize(fontSize);
            return uiSettingsRepository.save(settings);
        }
        return settings;
    }

    private static String normalizeTheme(String value) {
        return normalize(value, THEMES, DEFAULT_THEME);
    }

    private static String normalizeFont(String value) {
        return normalize(value, FONTS, DEFAULT_FONT);
    }

    private int normalizeRows(Integer value) {
        List<Integer> allowed = pageSizes();
        if (value != null && allowed.contains(value)) {
            return value;
        }
        return allowed.isEmpty() ? DEFAULT_ROWS_PER_PAGE : allowed.get(0);
    }

    private int normalizeFontSize(Integer value) {
        List<Integer> allowed = fontSizes();
        if (value != null && allowed.contains(value)) {
            return value;
        }
        return allowed.isEmpty() ? DEFAULT_FONT_SIZE : allowed.get(0);
    }

    private static String normalize(String value, List<String> allowed, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }
}
