/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.ext.wires.core.grids.client.util;

import com.ait.lienzo.client.core.shape.Viewport;
import com.ait.lienzo.client.core.types.Transform;
import org.uberfire.ext.wires.core.grids.client.model.Bounds;
import org.uberfire.ext.wires.core.grids.client.widget.grid.GridWidget;
import org.uberfire.ext.wires.core.grids.client.widget.grid.renderers.grids.impl.BaseGridRendererHelper;
import org.uberfire.ext.wires.core.grids.client.widget.layer.impl.DefaultGridLayer;
import org.uberfire.ext.wires.core.grids.client.widget.layer.impl.GridLienzoPanel;

public class GridHighlightHelper {

    private double paddingX = 0d;

    private double paddingY = 0d;

    private double minX = 0d;

    private double minY = 0d;

    private boolean isPinnedGrid = false;

    private final GridLienzoPanel gridPanel;

    private final GridWidget gridWidget;

    public GridHighlightHelper(final GridLienzoPanel gridPanel,
                               final GridWidget gridWidget) {
        this.gridPanel = gridPanel;
        this.gridWidget = gridWidget;
    }

    public void highlight(final int row,
                          final int column) {

        final double rowOffset = calculateRowOffset(row);
        final double columnOffset = calculateColumnOffset(column);
        final double y = applyPinnedGridConstraints(rowOffset);
        final double x = applyPinnedGridConstraints(columnOffset);

        select(row, column);
        moveCanvasTo(x, y);
    }

    public void clearSelections() {

        final GridWidget gridWidget = getGridWidget();

        gridWidget.getModel().clearSelections();
        gridWidget.draw();
    }

    public GridHighlightHelper withPaddingX(final double paddingX) {
        this.paddingX = paddingX;
        return this;
    }

    public GridHighlightHelper withPaddingY(final double paddingY) {
        this.paddingY = paddingY;
        return this;
    }

    public GridHighlightHelper withPinnedGrid() {
        this.isPinnedGrid = true;
        return this;
    }

    public GridHighlightHelper withMinX(final double minX) {
        this.minX = minX;
        return this;
    }

    public GridHighlightHelper withMinY(final double minY) {
        this.minY = minY;
        return this;
    }

    private void select(final int row,
                        final int column) {
        gridWidget.selectCell(row, column, false, false);
        gridWidget.draw();
    }

    void moveCanvasTo(final double x,
                      final double y) {

        final double deltaY = calculateDeltaY(y);
        final double deltaX = calculateDeltaX(x);
        final Transform newTransform = getTransform().copy().translate(deltaX, deltaY);

        getViewport().setTransform(newTransform);
        getDefaultGridLayer().batch();
        getGridPanel().refreshScrollPosition();
    }

    private double calculateDeltaY(final double y) {

        final double rawY = -(y - getPaddingY());
        final Range yRange = new Range(getVisibleBounds().getY() + getPaddingY(),
                                       getVisibleBounds().getY() + getVisibleBounds().getHeight() - getPaddingY());

        if (yRange.contains(rawY)) {
            return 0d;
        }

        return y - (getTransform().getTranslateY() / getTransform().getScaleY());
    }

    private double calculateDeltaX(final double x) {

        final double rawX = -(x - getPaddingX());
        final Range xRange = new Range(getVisibleBounds().getX() + getPaddingX(),
                                       getVisibleBounds().getX() + getVisibleBounds().getWidth() - getPaddingX());

        if (xRange.contains(rawX)) {
            return 0d;
        }

        return x - (getTransform().getTranslateX() / getTransform().getScaleX());
    }

    private double calculateColumnOffset(final int column) {
        return -(getMinX() + getRendererHelper().getColumnOffset(column) - getPaddingX());
    }

    private double calculateRowOffset(final int row) {
        return -(getMinY() + getRendererHelper().getRowOffset(row) - getPaddingY());
    }

    private BaseGridRendererHelper getRendererHelper() {
        return getGridWidget().getRendererHelper();
    }

    private double applyPinnedGridConstraints(final double value) {
        if (isPinnedGrid && value > 0) {
            return 0;
        } else {
            return value;
        }
    }

    private Bounds getVisibleBounds() {
        return getDefaultGridLayer().getVisibleBounds();
    }

    private Transform getTransform() {
        return getViewport().getTransform();
    }

    private Viewport getViewport() {
        return getDefaultGridLayer().getViewport();
    }

    private GridLienzoPanel getGridPanel() {
        return gridPanel;
    }

    private GridWidget getGridWidget() {
        return gridWidget;
    }

    private double getPaddingX() {
        return paddingX;
    }

    private double getPaddingY() {
        return paddingY;
    }

    private double getMinX() {
        return minX;
    }

    private double getMinY() {
        return minY;
    }

    private DefaultGridLayer getDefaultGridLayer() {
        return getGridPanel().getDefaultGridLayer();
    }

    class Range {

        final double min;
        final double max;

        Range(final double min,
              final double max) {
            this.min = min;
            this.max = max;
        }

        boolean contains(final double value) {
            return value > min && value < max;
        }
    }
}
