// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import org.jackhuang.hmcl.vncl.VnclSkeletonList;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.construct.SpinnerPane.FAILED_ACTION;

/// Base control for list pages with loading and failure state.
public class ListPageBase<T> extends Control {
    private final ListProperty<T> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", false);
    private final StringProperty failedReason = new SimpleStringProperty(this, "failed");

    /// Skeleton shown instead of the spinner while the page is loading.
    private final ObjectProperty<Node> skeleton = new SimpleObjectProperty<>(this, "skeleton", new VnclSkeletonList(6));

    /// Optional empty-state node shown instead of the plain failed-reason text.
    private final ObjectProperty<@Nullable Node> emptyState = new SimpleObjectProperty<>(this, "emptyState");

    /// Whether the page is empty rather than failed.
    private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", false);

    public ObservableList<T> getItems() {
        return items.get();
    }

    public void setItems(ObservableList<T> items) {
        this.items.set(items);
    }

    public ListProperty<T> itemsProperty() {
        return items;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public String getFailedReason() {
        return failedReason.get();
    }

    public StringProperty failedReasonProperty() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason.set(failedReason);
    }

    /// Returns the skeleton node shown while loading.
    public ObjectProperty<Node> skeletonProperty() {
        return skeleton;
    }

    /// Returns the optional empty-state node.
    public ObjectProperty<@Nullable Node> emptyStateProperty() {
        return emptyState;
    }

    /// Returns whether the page is empty.
    public BooleanProperty emptyProperty() {
        return empty;
    }

    /// Returns whether the page is empty.
    public boolean isEmpty() {
        return empty.get();
    }

    /// Sets whether the page is empty.
    ///
    /// @param empty whether the page has no items
    public void setEmpty(boolean empty) {
        this.empty.set(empty);
    }

    /// Sets the empty-state node shown instead of the plain failed-reason text.
    ///
    /// @param emptyState the empty-state node, or null to fall back to text
    public void setEmptyState(@Nullable Node emptyState) {
        this.emptyState.set(emptyState);
    }

    public final ObjectProperty<EventHandler<Event>> onFailedActionProperty() {
        return onFailedAction;
    }

    public final void setOnFailedAction(EventHandler<Event> value) {
        onFailedActionProperty().set(value);
    }

    public final EventHandler<Event> getOnFailedAction() {
        return onFailedActionProperty().get();
    }

    private ObjectProperty<EventHandler<Event>> onFailedAction = new SimpleObjectProperty<EventHandler<Event>>(this, "onFailedAction") {
        @Override
        protected void invalidated() {
            setEventHandler(FAILED_ACTION, get());
        }
    };
}
