// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// Optional VNCL-only appearance for a game instance: an accent color, either solid or a gradient.
///
/// The style lives next to the instance metadata so it travels with the instance when it is copied,
/// renamed, or moved. Missing files mean "no custom style" and keep the launcher default look.
@NotNullByDefault
public record VnclInstanceStyle(boolean gradient, @Nullable String color1, @Nullable String color2) {

    /// Serializer for the instance style file.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /// The default style used when no style file exists.
    public static final VnclInstanceStyle DEFAULT = new VnclInstanceStyle(false, null, null);

    /// Returns whether this style has no custom accent.
    public boolean isEmpty() {
        return isBlank(color1) && isBlank(color2);
    }

    /// Returns the primary accent color, or null when absent.
    public @Nullable Color primaryColor() {
        return parseColor(color1);
    }

    /// Returns the secondary accent color, or null when absent.
    public @Nullable Color secondaryColor() {
        return parseColor(color2);
    }

    /// Builds the paint used for banners and rings.
    ///
    /// @return the accent paint, or null when no accent is configured
    public @Nullable Paint paint() {        Color first = primaryColor();
        if (first == null) {
            return null;
        }
        if (!gradient) {
            return first;
        }
        Color second = secondaryColor();
        if (second == null) {
            return first;
        }
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, first), new Stop(1, second));
    }

    /// Returns a CSS paint value for borders and inline styles.
    ///
    /// @return the CSS paint, or null when no accent is configured
    public @Nullable String cssPaint() {
        if (isBlank(color1)) {
            return null;
        }
        if (!gradient || isBlank(color2)) {
            return color1;
        }
        return "linear-gradient(to bottom right, " + color1 + ", " + color2 + ")";
    }

    /// Loads the style from an instance metadata file.
    ///
    /// @param file the style JSON file
    /// @return the loaded style, or [#DEFAULT] when the file is missing or malformed
    public static VnclInstanceStyle load(Path file) {
        if (!Files.isRegularFile(file)) {
            return DEFAULT;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            VnclInstanceStyle style = GSON.fromJson(reader, VnclInstanceStyle.class);
            return style != null ? style.normalized() : DEFAULT;
        } catch (Exception exception) {
            return DEFAULT;
        }
    }

    /// Saves the style to an instance metadata file.
    ///
    /// @param file the destination JSON file
    /// @throws IOException if the file cannot be written
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(normalized(), writer);
        }
    }

    /// Returns a normalized copy with blank colors represented as null.
    public VnclInstanceStyle normalized() {
        return new VnclInstanceStyle(gradient, blankToNull(color1), blankToNull(color2));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static @Nullable Color parseColor(@Nullable String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Color.web(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
