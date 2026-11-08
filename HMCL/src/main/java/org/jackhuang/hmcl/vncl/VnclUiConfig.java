// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.jackhuang.hmcl.Metadata;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// VNCL-specific appearance tokens: corner radius, interface density and font scale.
///
/// Values are persisted in `config/vncl-ui.json` inside the launcher user home and exposed as
/// JavaFX properties so the settings page and the generated style sheet stay in sync.
@NotNullByDefault
public final class VnclUiConfig {

    /// Default corner radius in pixels.
    public static final double DEFAULT_CORNER_RADIUS = 14.0;
    /// Default interface density multiplier.
    public static final double DEFAULT_DENSITY = 1.0;
    /// Default font scale multiplier.
    public static final double DEFAULT_FONT_SCALE = 1.0;

    /// Allowed corner radius range.
    public static final double MIN_CORNER_RADIUS = 0.0;
    public static final double MAX_CORNER_RADIUS = 24.0;
    /// Allowed interface density range.
    public static final double MIN_DENSITY = 0.85;
    public static final double MAX_DENSITY = 1.20;
    /// Allowed font scale range.
    public static final double MIN_FONT_SCALE = 0.90;
    public static final double MAX_FONT_SCALE = 1.15;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final DoubleProperty cornerRadius = new SimpleDoubleProperty(DEFAULT_CORNER_RADIUS);
    private static final DoubleProperty density = new SimpleDoubleProperty(DEFAULT_DENSITY);
    private static final DoubleProperty fontScale = new SimpleDoubleProperty(DEFAULT_FONT_SCALE);

    static {
        load();
        cornerRadius.addListener(observable -> save());
        density.addListener(observable -> save());
        fontScale.addListener(observable -> save());
    }

    /// Returns the corner radius token in pixels.
    public static DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    /// Returns the interface density token; 1.0 is the default spacing.
    public static DoubleProperty densityProperty() {
        return density;
    }

    /// Returns the font scale token; 1.0 is the default text size.
    public static DoubleProperty fontScaleProperty() {
        return fontScale;
    }

    /// Restores every appearance token to its default value.
    public static void reset() {
        cornerRadius.set(DEFAULT_CORNER_RADIUS);
        density.set(DEFAULT_DENSITY);
        fontScale.set(DEFAULT_FONT_SCALE);
    }

    /// Applies a named preset: `compact`, `relaxed` or anything else for the default look.
    public static void applyPreset(String preset) {
        switch (preset) {
            case "compact" -> {
                cornerRadius.set(8.0);
                density.set(0.88);
                fontScale.set(0.95);
            }
            case "relaxed" -> {
                cornerRadius.set(20.0);
                density.set(1.15);
                fontScale.set(1.08);
            }
            default -> reset();
        }
    }

    private static Path file() {
        return Metadata.HMCL_USER_HOME.resolve("config").resolve("vncl-ui.json");
    }

    private static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                return;
            }
            cornerRadius.set(clamp(data.cornerRadius, MIN_CORNER_RADIUS, MAX_CORNER_RADIUS, DEFAULT_CORNER_RADIUS));
            density.set(clamp(data.density, MIN_DENSITY, MAX_DENSITY, DEFAULT_DENSITY));
            fontScale.set(clamp(data.fontScale, MIN_FONT_SCALE, MAX_FONT_SCALE, DEFAULT_FONT_SCALE));
        } catch (Exception exception) {
            LOG.warning("Failed to load VNCL appearance settings", exception);
        }
    }

    private static void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                Data data = new Data();
                data.cornerRadius = cornerRadius.get();
                data.density = density.get();
                data.fontScale = fontScale.get();
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            LOG.warning("Failed to save VNCL appearance settings", exception);
        }
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private VnclUiConfig() {
    }

    /// On-disk representation of [#cornerRadius], [#density] and [#fontScale].
    private static final class Data {
        double cornerRadius = DEFAULT_CORNER_RADIUS;
        double density = DEFAULT_DENSITY;
        double fontScale = DEFAULT_FONT_SCALE;
    }
}
