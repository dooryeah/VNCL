// SPDX-FileCopyrightText: Copyright (C) 2026 The VNCL Contributors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.vncl;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/// Generates the VNCL token style sheet from [#VnclUiConfig].
///
/// JavaFX only supports looked-up colors as CSS variables, so numeric tokens are rendered into
/// concrete rules each time a token changes; the result is registered as a data URI style sheet.
@NotNullByDefault
public final class VnclStyleSheets {

    /// Builds the current token style sheet as a data URI.
    public static String getStyleSheet() {
        double radius = VnclUiConfig.cornerRadiusProperty().get();
        double density = VnclUiConfig.densityProperty().get();
        double fontScale = VnclUiConfig.fontScaleProperty().get();

        long radiusPx = Math.round(radius);
        long radiusSmallPx = Math.round(radius * 0.6);
        long padVertical = Math.round(26 * density);
        long padHorizontal = Math.round(30 * density);
        long rowVertical = Math.round(10 * density);
        long rowHorizontal = Math.round(16 * density);
        long baseFont = Math.round(13 * fontScale);

        String css = """
                .root {
                    -fx-font-size: %dpx;
                }
                /* Switches compute their geometry from the font size; keep them stable. */
                .jfx-switch {
                    -fx-font-size: 13px;
                }
                .vncl-home-card {
                    -fx-background-radius: %dpx;
                    -fx-padding: %dpx %dpx;
                }
                .launch-pane > .jfx-button {
                    -fx-background-radius: %dpx;
                }
                .line-component .line-component-container {
                    -fx-padding: %dpx %dpx %dpx %dpx;
                }
                .card, .component-list {
                    -fx-background-radius: %dpx;
                }
                """.formatted(
                baseFont,
                radiusPx, padVertical, padHorizontal,
                radiusSmallPx,
                rowVertical, rowHorizontal, rowVertical, rowHorizontal,
                radiusPx
        );

        return "data:text/css;charset=UTF-8;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    /// Formats a numeric token for debugging output.
    public static String describe() {
        return String.format(Locale.ROOT, "radius=%.0fpx density=%.2f fontScale=%.2f",
                VnclUiConfig.cornerRadiusProperty().get(),
                VnclUiConfig.densityProperty().get(),
                VnclUiConfig.fontScaleProperty().get());
    }

    private VnclStyleSheets() {
    }
}
