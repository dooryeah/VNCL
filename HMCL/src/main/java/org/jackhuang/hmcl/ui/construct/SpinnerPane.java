// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSpinner;
import javafx.beans.property.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jetbrains.annotations.Nullable;

/// A spinner pane that can show spinner, failed reason, or content.
public class SpinnerPane extends Control {
    private static final String DEFAULT_STYLE_CLASS = "spinner-pane";

    public SpinnerPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public void showSpinner() {
        setLoading(true);
    }

    public void hideSpinner() {
        setFailedReason(null);
        setLoading(false);
    }

    /// Refreshes the displayed state and its cache preference, including before skin creation.
    private void updateContent() {
        @Nullable Node displayedContent = content == null ? null : content.get();
        TransitionPane.setCacheAllowed(this, !isLoading() && (getFailedReason() != null
                || displayedContent == null || TransitionPane.isCacheAllowed(displayedContent)));
        if (getSkin() instanceof Skin skin) {
            skin.updateContent();
        }
    }

    private ObjectProperty<Node> content;

    public ObjectProperty<Node> contentProperty() {
        if (content == null)
            content = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "content";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return content;
    }

    public Node getContent() {
        return contentProperty().get();
    }

    public void setContent(Node content) {
        contentProperty().set(content);
    }

    private BooleanProperty loading;

    public BooleanProperty loadingProperty() {
        if (loading == null)
            loading = new BooleanPropertyBase() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "loading";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return loading;
    }

    public boolean isLoading() {
        return loading != null && loading.get();
    }

    public void setLoading(boolean loading) {
        loadingProperty().set(loading);
    }

    private StringProperty failedReason;

    /// Returns the optional skeleton shown instead of the spinner while loading.
    public ObjectProperty<@Nullable Node> skeletonProperty() {
        if (skeleton == null) {
            skeleton = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "skeleton";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        }
        return skeleton;
    }

    /// Returns the skeleton node, or null when the spinner should be used.
    public @Nullable Node getSkeleton() {
        return skeleton != null ? skeleton.get() : null;
    }

    /// Sets the skeleton node shown while loading.
    ///
    /// @param skeleton the skeleton node, or null to use the spinner
    public void setSkeleton(@Nullable Node skeleton) {
        skeletonProperty().set(skeleton);
    }

    private ObjectProperty<@Nullable Node> skeleton;

    /// Returns the optional empty-state node shown instead of the failed-reason text.
    public ObjectProperty<@Nullable Node> emptyStateProperty() {
        if (emptyState == null) {
            emptyState = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "emptyState";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        }
        return emptyState;
    }

    /// Returns the empty-state node, or null when the plain text should be used.
    public @Nullable Node getEmptyState() {
        return emptyState != null ? emptyState.get() : null;
    }

    /// Sets the empty-state node shown when a failed/empty reason is present.
    ///
    /// @param emptyState the empty-state node, or null
    public void setEmptyState(@Nullable Node emptyState) {
        emptyStateProperty().set(emptyState);
    }

    private ObjectProperty<@Nullable Node> emptyState;

    private BooleanProperty empty;

    /// Returns whether the pane represents an empty result rather than a failure.
    public BooleanProperty emptyProperty() {
        if (empty == null) {
            empty = new BooleanPropertyBase() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "empty";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        }
        return empty;
    }

    /// Returns whether the pane represents an empty result.
    public boolean isEmpty() {
        return empty != null && empty.get();
    }

    /// Sets whether the pane represents an empty result.
    ///
    /// @param empty whether the pane is empty
    public void setEmpty(boolean empty) {
        emptyProperty().set(empty);
    }

    public StringProperty failedReasonProperty() {
        if (failedReason == null)
            failedReason = new StringPropertyBase() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "failedReason";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return failedReason;
    }

    public String getFailedReason() {
        return failedReason != null ? failedReason.get() : null;
    }

    public void setFailedReason(String failedReason) {
        failedReasonProperty().set(failedReason);
    }

    private ObjectProperty<EventHandler<Event>> onFailedAction;

    public final ObjectProperty<EventHandler<Event>> onFailedActionProperty() {
        if (onFailedAction == null) {
            onFailedAction = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "onFailedAction";
                }

                @Override
                protected void invalidated() {
                    setEventHandler(FAILED_ACTION, get());
                }
            };
        }
        return onFailedAction;
    }

    public final EventHandler<Event> getOnFailedAction() {
        return onFailedAction != null ? onFailedAction.get() : null;
    }

    public final void setOnFailedAction(EventHandler<Event> value) {
        onFailedActionProperty().set(value);
    }

    @Override
    protected SkinBase<SpinnerPane> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends SkinBase<SpinnerPane> {
        private final TransitionPane root = new TransitionPane();

        Skin(SpinnerPane control) {
            super(control);
            root.setClip(null);

            updateContent();
            this.getChildren().setAll(root);
        }

        private StackPane contentPane;
        private StackPane spinnerPane;
        private StackPane skeletonPane;
        private StackPane failedPane;
        private StackPane emptyPane;
        private Label failedReasonLabel;

        /// Selects the visible state and passes its cache preference to the transition target.
        void updateContent() {
            SpinnerPane control = getSkinnable();

            Node nextContent;
            if (control.isLoading()) {
                @Nullable Node skeleton = control.getSkeleton();
                if (skeleton != null) {
                    if (skeletonPane == null) {
                        skeletonPane = new StackPane();
                        skeletonPane.getStyleClass().add("notice-pane");
                        TransitionPane.setCacheAllowed(skeletonPane, false);
                    }
                    skeletonPane.getChildren().setAll(skeleton);
                    nextContent = skeletonPane;
                } else {
                    if (spinnerPane == null) {
                        spinnerPane = new StackPane(new JFXSpinner());
                        spinnerPane.getStyleClass().add("notice-pane");
                        TransitionPane.setCacheAllowed(spinnerPane, false);
                    }
                    nextContent = spinnerPane;
                }
            } else if (control.getFailedReason() != null) {
                @Nullable Node emptyState = control.getEmptyState();
                if (control.isEmpty() && emptyState != null) {
                    if (emptyPane == null) {
                        emptyPane = new StackPane();
                        emptyPane.getStyleClass().add("notice-pane");
                    }
                    emptyPane.getChildren().setAll(emptyState);
                    nextContent = emptyPane;
                } else {
                    if (failedPane == null) {
                        failedReasonLabel = new Label();
                        failedPane = new StackPane(failedReasonLabel);
                        failedPane.getStyleClass().add("notice-pane");
                        FXUtils.onClicked(failedPane, () -> control.fireEvent(new Event(SpinnerPane.FAILED_ACTION)));
                    }
                    failedReasonLabel.setText(control.getFailedReason());
                    nextContent = failedPane;
                }
            } else {
                if (contentPane == null) {
                    contentPane = new StackPane();
                }

                @Nullable Node content = control.getContent();
                if (content != null)
                    contentPane.getChildren().setAll(content);
                else
                    contentPane.getChildren().clear();

                TransitionPane.setCacheAllowed(contentPane, content == null || TransitionPane.isCacheAllowed(content));
                nextContent = contentPane;
            }

            if (nextContent != failedPane && failedReasonLabel != null) {
                failedReasonLabel.setText(null);
            }

            TransitionPane.setCacheAllowed(control, TransitionPane.isCacheAllowed(nextContent));
            root.setContent(nextContent, ContainerAnimations.FADE);
        }
    }

    public static final EventType<Event> FAILED_ACTION = new EventType<>(Event.ANY, "FAILED_ACTION");
}
