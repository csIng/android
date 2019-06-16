/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.draw;

import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.ACTION_STROKE;
import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getArrowPoint;
import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getCurvePoints;
import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getDestinationDirection;
import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getRegularActionIconRect;
import static com.android.tools.idea.uibuilder.surface.ScreenViewLayer.HQ_RENDERING_HINTS;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.ArrowDirection;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.FillArrow;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.draw.DrawCommandBase;
import com.android.tools.idea.common.scene.draw.DrawCommandSerializationHelperKt;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.scene.ConnectionDirection;
import com.android.tools.idea.naveditor.scene.CurvePoints;
import com.android.tools.idea.naveditor.scene.NavActionHelperKt;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import org.jetbrains.annotations.NotNull;

/**
 * {@link DrawCommand} that draw a nav editor action (an arrow between two screens).
 */
public class DrawAction extends DrawCommandBase {
  private static final GeneralPath PATH = new GeneralPath();
  @SwingCoordinate private final Rectangle2D.Float mySource = new Rectangle2D.Float();
  @SwingCoordinate private final Rectangle2D.Float myDest = new Rectangle2D.Float();

  private final Color myColor;

  public DrawAction(@NotNull String serialized) {
    this(DrawCommandSerializationHelperKt.parse(serialized, 3));
  }

  @Override
  public String serialize() {
    return DrawCommandSerializationHelperKt
      .buildString(getClass().getSimpleName(), DrawCommandSerializationHelperKt.rect2DToString(mySource),
                   DrawCommandSerializationHelperKt.rect2DToString(myDest), DrawCommandSerializationHelperKt.colorToString(myColor));
  }

  @Override
  protected void onPaint(@NotNull Graphics2D g, @NotNull SceneContext sceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS);
    draw(g, myColor, mySource, myDest, sceneContext);
  }

  public DrawAction(@SwingCoordinate Rectangle2D.Float source,
                    @SwingCoordinate Rectangle2D.Float dest,
                    @NotNull Color color) {
    mySource.setRect(source);
    myDest.setRect(dest);
    myColor = color;
  }

  private DrawAction(@NotNull String[] s) {
    this(DrawCommandSerializationHelperKt.stringToRect2D(s[0]),
         DrawCommandSerializationHelperKt.stringToRect2D(s[1]), DrawCommandSerializationHelperKt.stringToColor(s[2]));
  }

  public static void buildDisplayList(@NotNull DisplayList list,
                                      @NotNull SceneView sceneView,
                                      boolean isPopAction,
                                      @SwingCoordinate Rectangle2D.Float source,
                                      @SwingCoordinate Rectangle2D.Float dest,
                                      @NotNull Color color) {
    SceneContext sceneContext = SceneContext.get(sceneView);
    list.add(new DrawAction(source, dest, color));
    ConnectionDirection direction = getDestinationDirection(source, dest);
    Point2D.Float arrowPoint = getArrowPoint(sceneContext, dest, direction);

    ArrowDirection arrowDirection = getArrowDirection(direction);
    Rectangle2D.Float arrowRectangle = NavActionHelperKt.getArrowRectangle(sceneView, arrowPoint, direction);


    list.add(new FillArrow(arrowDirection, arrowRectangle, color, 0));

    if (isPopAction) {
      Rectangle2D.Float iconRectangle = getRegularActionIconRect(source, dest, sceneContext);
      list.add(new DrawIcon(iconRectangle, DrawIcon.IconType.POP_ACTION, color));
    }
  }

  private static void draw(@NotNull Graphics2D g,
                           @NotNull Color color,
                           @SwingCoordinate Rectangle2D.Float source,
                           @SwingCoordinate Rectangle2D.Float dest,
                           @NotNull SceneContext sceneContext) {
    PATH.reset();

    CurvePoints points = getCurvePoints(source, dest, sceneContext);
    PATH.moveTo(points.p1.x, points.p1.y);
    PATH.curveTo(points.p2.x, points.p2.y, points.p3.x, points.p3.y, points.p4.x, points.p4.y);

    g.setStroke(ACTION_STROKE);
    g.setColor(color);
    g.draw(PATH);
  }

  @NotNull
  private static ArrowDirection getArrowDirection(@NotNull ConnectionDirection direction) {
    switch (direction) {
      case LEFT:
        return ArrowDirection.RIGHT;
      case RIGHT:
        return ArrowDirection.LEFT;
      case TOP:
        return ArrowDirection.DOWN;
      case BOTTOM:
        return ArrowDirection.UP;
    }

    throw new IllegalArgumentException();
  }
}
