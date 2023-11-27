/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.celllayout;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.CellLayout;
import com.android.launcher3.util.CellAndSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

/**
 * Contains the logic of a reorder.
 *
 * The content of this class was extracted from {@link CellLayout} and should mimic the exact
 * same behaviour.
 */
public class ReorderAlgorithm {

    CellLayout mCellLayout;

    public ReorderAlgorithm(CellLayout cellLayout) {
        mCellLayout = cellLayout;
    }

    /**
     * This method differs from closestEmptySpaceReorder and dropInPlaceSolution because this method
     * will move items around and will change the shape of the item if possible to try to find a
     * solution.
     *
     * When changing the size of the widget this method will try first subtracting -1 in the x
     * dimension and then subtracting -1 in the y dimension until finding a possible solution or
     * until it no longer can reduce the span.
     *
     * @param pixelX    X coordinate in pixels in the screen
     * @param pixelY    Y coordinate in pixels in the screen
     * @param minSpanX  minimum possible horizontal span it will try to find a solution for.
     * @param minSpanY  minimum possible vertical span it will try to find a solution for.
     * @param spanX     horizontal cell span
     * @param spanY     vertical cell span
     * @param direction direction in which it will try to push the items intersecting the desired
     *                  view
     * @param dragView  view being dragged in reorder
     * @param decX      whether it will decrease the horizontal or vertical span if it can't find a
     *                  solution for the current span.
     * @param solution  variable to store the solution
     * @return the same solution variable
     */
    public ItemConfiguration findReorderSolution(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, int[] direction, View dragView, boolean decX,
            ItemConfiguration solution) {
        // Copy the current state into the solution. This solution will be manipulated as necessary.
        mCellLayout.copyCurrentStateToSolution(solution);
        // Copy the current occupied array into the temporary occupied array. This array will be
        // manipulated as necessary to find a solution.
        mCellLayout.getOccupied().copyTo(mCellLayout.mTmpOccupied);

        // We find the nearest cell into which we would place the dragged item, assuming there's
        // nothing in its way.
        int[] result = new int[2];
        result = mCellLayout.findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY, result);

        boolean success;
        // First we try the exact nearest position of the item being dragged,
        // we will then want to try to move this around to other neighbouring positions
        success = rearrangementExists(result[0], result[1], spanX, spanY, direction,
                dragView, solution);

        if (!success) {
            // We try shrinking the widget down to size in an alternating pattern, shrink 1 in
            // x, then 1 in y etc.
            if (spanX > minSpanX && (minSpanY == spanY || decX)) {
                return findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX - 1, spanY,
                        direction, dragView, false, solution);
            } else if (spanY > minSpanY) {
                return findReorderSolution(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY - 1,
                        direction, dragView, true, solution);
            }
            solution.isSolution = false;
        } else {
            solution.isSolution = true;
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = spanX;
            solution.spanY = spanY;
        }
        return solution;
    }

    private boolean rearrangementExists(int cellX, int cellY, int spanX, int spanY, int[] direction,
            View ignoreView, ItemConfiguration solution) {
        // Return early if get invalid cell positions
        if (cellX < 0 || cellY < 0) return false;

        ArrayList<View> intersectingViews = new ArrayList<>();
        Rect occupiedRect = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);

        // Mark the desired location of the view currently being dragged.
        if (ignoreView != null) {
            CellAndSpan c = solution.map.get(ignoreView);
            if (c != null) {
                c.cellX = cellX;
                c.cellY = cellY;
            }
        }
        Rect r0 = new Rect(cellX, cellY, cellX + spanX, cellY + spanY);
        Rect r1 = new Rect();
        // The views need to be sorted so that the results are deterministic on the views positions
        // and not by the views hash which is "random".
        // The views are sorted twice, once for the X position and a second time for the Y position
        // to ensure same order everytime.
        Comparator comparator = Comparator.comparing(view ->
                        ((CellLayoutLayoutParams) ((View) view).getLayoutParams()).getCellX())
                .thenComparing(view ->
                        ((CellLayoutLayoutParams) ((View) view).getLayoutParams()).getCellY());
        List<View> views = solution.map.keySet().stream().sorted(comparator).toList();
        for (View child : views) {
            if (child == ignoreView) continue;
            CellAndSpan c = solution.map.get(child);
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) child.getLayoutParams();
            r1.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY);
            if (Rect.intersects(r0, r1)) {
                if (!lp.canReorder) {
                    return false;
                }
                intersectingViews.add(child);
            }
        }

        solution.intersectingViews = intersectingViews;

        // First we try to find a solution which respects the push mechanic. That is,
        // we try to find a solution such that no displaced item travels through another item
        // without also displacing that item.
        if (mCellLayout.attemptPushInDirection(intersectingViews, occupiedRect, direction,
                ignoreView,
                solution)) {
            return true;
        }

        // Next we try moving the views as a block, but without requiring the push mechanic.
        if (mCellLayout.addViewsToTempLocation(intersectingViews, occupiedRect, direction,
                ignoreView,
                solution)) {
            return true;
        }

        // Ok, they couldn't move as a block, let's move them individually
        for (View v : intersectingViews) {
            if (!mCellLayout.addViewToTempLocation(v, occupiedRect, direction, solution)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a "reorder" if there is empty space without rearranging anything.
     *
     * @param pixelX   X coordinate in pixels in the screen
     * @param pixelY   Y coordinate in pixels in the screen
     * @param spanX    horizontal cell span
     * @param spanY    vertical cell span
     * @param dragView view being dragged in reorder
     * @return the configuration that represents the found reorder
     */
    public ItemConfiguration dropInPlaceSolution(int pixelX, int pixelY, int spanX,
            int spanY, View dragView) {
        int[] result = mCellLayout.findNearestAreaIgnoreOccupied(pixelX, pixelY, spanX, spanY,
                new int[2]);
        ItemConfiguration solution = new ItemConfiguration();
        mCellLayout.copyCurrentStateToSolution(solution);

        solution.isSolution = !isConfigurationRegionOccupied(
                new Rect(result[0], result[1], result[0] + spanX, result[1] + spanY),
                solution,
                dragView
        );
        if (!solution.isSolution) {
            return solution;
        }
        solution.cellX = result[0];
        solution.cellY = result[1];
        solution.spanX = spanX;
        solution.spanY = spanY;
        return solution;
    }

    private boolean isConfigurationRegionOccupied(Rect region,
            ItemConfiguration configuration, View ignoreView) {
        return configuration.map.entrySet()
                .stream()
                .filter(entry -> entry.getKey() != ignoreView)
                .map(Entry::getValue)
                .anyMatch(cellAndSpan -> region.intersect(cellAndSpan.cellX, cellAndSpan.cellY,
                        cellAndSpan.cellX + cellAndSpan.spanX,
                        cellAndSpan.cellY + cellAndSpan.spanY));
    }

    /**
     * Returns a "reorder" where we simply drop the item in the closest empty space, without moving
     * any other item in the way.
     *
     * @param pixelX X coordinate in pixels in the screen
     * @param pixelY Y coordinate in pixels in the screen
     * @param spanX  horizontal cell span
     * @param spanY  vertical cell span
     * @return the configuration that represents the found reorder
     */
    public ItemConfiguration closestEmptySpaceReorder(int pixelX, int pixelY,
            int minSpanX, int minSpanY, int spanX, int spanY) {
        ItemConfiguration solution = new ItemConfiguration();
        int[] result = new int[2];
        int[] resultSpan = new int[2];
        mCellLayout.findNearestVacantArea(pixelX, pixelY, minSpanX, minSpanY, spanX, spanY, result,
                resultSpan);
        if (result[0] >= 0 && result[1] >= 0) {
            mCellLayout.copyCurrentStateToSolution(solution);
            solution.cellX = result[0];
            solution.cellY = result[1];
            solution.spanX = resultSpan[0];
            solution.spanY = resultSpan[1];
            solution.isSolution = true;
        } else {
            solution.isSolution = false;
        }
        return solution;
    }

    /**
     * When the user drags an Item in the workspace sometimes we need to move the items already in
     * the workspace to make space for the new item, this function return a solution for that
     * reorder.
     *
     * @param pixelX   X coordinate in the screen of the dragView in pixels
     * @param pixelY   Y coordinate in the screen of the dragView in pixels
     * @param minSpanX minimum horizontal span the item can be shrunk to
     * @param minSpanY minimum vertical span the item can be shrunk to
     * @param spanX    occupied horizontal span
     * @param spanY    occupied vertical span
     * @param dragView the view of the item being draged
     * @return returns a solution for the given parameters, the solution contains all the icons and
     * the locations they should be in the given solution.
     */
    public ItemConfiguration calculateReorder(int pixelX, int pixelY, int minSpanX,
            int minSpanY, int spanX, int spanY, View dragView) {
        mCellLayout.getDirectionVectorForDrop(pixelX, pixelY, spanX, spanY, dragView,
                mCellLayout.mDirectionVector);

        ItemConfiguration dropInPlaceSolution = dropInPlaceSolution(pixelX, pixelY,
                spanX, spanY,
                dragView);

        // Find a solution involving pushing / displacing any items in the way
        ItemConfiguration swapSolution = findReorderSolution(pixelX, pixelY, minSpanX,
                minSpanY, spanX, spanY, mCellLayout.mDirectionVector, dragView, true,
                new ItemConfiguration());

        // We attempt the approach which doesn't shuffle views at all
        ItemConfiguration closestSpaceSolution = closestEmptySpaceReorder(
                pixelX, pixelY, minSpanX, minSpanY, spanX, spanY);

        // If the reorder solution requires resizing (shrinking) the item being dropped, we instead
        // favor a solution in which the item is not resized, but
        if (swapSolution.isSolution && swapSolution.area() >= closestSpaceSolution.area()) {
            return swapSolution;
        } else if (closestSpaceSolution.isSolution) {
            return closestSpaceSolution;
        } else if (dropInPlaceSolution.isSolution) {
            return dropInPlaceSolution;
        }
        return null;
    }
}
