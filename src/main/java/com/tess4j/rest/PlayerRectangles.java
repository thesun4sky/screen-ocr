package com.tess4j.rest;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.awt.*;
import java.util.List;

@NoArgsConstructor
@Setter
@Getter
@ToString
public class PlayerRectangles {
    private RectangleInfo player1;
    private RectangleInfo player2;
    private RectangleInfo player3;
    private RectangleInfo player4;
    private RectangleInfo player5;
    private RectangleInfo player6;
    private RectangleInfo player7;
    private RectangleInfo player8;

    @NoArgsConstructor
    @Setter
    @Getter
    @ToString
    public static class RectangleInfo {
        private Integer index;
        private String x;
        private String y;
        private String width;
        private String height;

        public Rectangle getRectangle() {
            var x = Integer.parseInt(this.x);
            var y = Integer.parseInt(this.y);
            var width = Integer.parseInt(this.width);
            var height = Integer.parseInt(this.height);
            return new Rectangle(x, y, width, height);
        }
    }

    public List<RectangleInfo> getRectangleInfos() {
        return List.of(player1, player2, player3, player4, player5, player6, player7, player8);
    }
}
