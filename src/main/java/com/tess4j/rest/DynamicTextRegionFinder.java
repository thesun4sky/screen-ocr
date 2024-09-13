package com.tess4j.rest;

import lombok.ToString;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class DynamicTextRegionFinder {
    private Logger LOGGER = LoggerFactory.getLogger(DynamicTextRegionFinder.class);
    private static final double HEIGHT_RATIO = 0.04;
    private static final double[] Y_RATIOS = {0.2, 0.25, 0.48, 0.71, 0.94};
    private static final double MAX_WIDTH_RATIO = 0.16;

    public List<Player> findDynamicRegions(BufferedImage image) throws TesseractException {
        List<Player> players = new ArrayList<>();
        ITesseract tesseract = new Tesseract();
        tesseract.setLanguage("kor+eng");

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int centerX = imageWidth / 2;

        for (double yRatio : Y_RATIOS) {
            int y = (int) (yRatio * imageHeight);
            int height = (int) (HEIGHT_RATIO * imageHeight);

            if (yRatio != 0.94) {
                // 왼쪽 탐색
                Player leftPlayer = findPlayerLeft(image, centerX, y, height, tesseract);
                if (leftPlayer != null) players.add(leftPlayer);

                // 오른쪽 탐색
                Player rightPlayer = findPlayerRight(image, centerX, y, height, tesseract);
                if (rightPlayer != null) players.add(rightPlayer);
            } else {
                // 0.94 구간은 기존 방식대로 처리
                Player centerPlayer = findPlayerCenter(image, centerX, y, height, tesseract);
                if (centerPlayer != null) players.add(centerPlayer);
            }

            LOGGER.info("Players found for yRatio ({}): {}", yRatio, players);
        }

        return players;
    }

    private Player findPlayerLeft(BufferedImage image, int startX, int y, int height, ITesseract tesseract) throws TesseractException {
        int imageWidth = image.getWidth();
        int width = (int) (imageWidth * MAX_WIDTH_RATIO);
        Pattern startPattern = Pattern.compile("^[A-Za-z]");
        Pattern endPattern = Pattern.compile("버$");
        LOGGER.info("[findPlayerLeft] startX({}) - width({}) = {}", startX, width, startX - width);

        for (int x = startX - width; x >= 0; x -= 100) {
            Rectangle rect = new Rectangle(x, y, width, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[findPlayerLeft] result of x,y ({},{}) : {}", x, y, result);
            if (startPattern.matcher(result).find() && endPattern.matcher(result).find()) {
                double xRatio = (double) x / imageWidth;
                double widthRatio = (double) width / imageWidth;
                return new Player(0, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
            }
        }
        return null;
    }

    private Player findPlayerRight(BufferedImage image, int startX, int y, int height, ITesseract tesseract) throws TesseractException {
        int imageWidth = image.getWidth();
        int width = (int) (imageWidth * MAX_WIDTH_RATIO);
        Pattern startPattern = Pattern.compile("^[0-9]");
        Pattern endPattern = Pattern.compile("[A-Za-z]$");
        LOGGER.info("[findPlayerRight] startX({}) - width({}) = {}", startX, width, startX - width);

        for (int x = startX; x + width <= imageWidth; x += 100) {
            Rectangle rect = new Rectangle(x, y, width, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[findPlayerRight] result of x,y ({},{}) : {}", x, y, result);
            if (startPattern.matcher(result).find() && endPattern.matcher(result).find()) {
                double xRatio = (double) x / imageWidth;
                double widthRatio = (double) width / imageWidth;
                return new Player(0, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
            }
        }
        return null;
    }

    private Player findPlayerCenter(BufferedImage image, int centerX, int y, int height, ITesseract tesseract) throws TesseractException {
        int imageWidth = image.getWidth();
        int maxWidth = (int) (MAX_WIDTH_RATIO * imageWidth);

        for (int width = 50; width <= maxWidth; width += 10) {
            int x = centerX - width / 2;
            Rectangle rect = new Rectangle(x, y, width, height);
            String result = tesseract.doOCR(image, rect).trim();

            if (result.contains("백") && result.contains("실") && result.contains("버")) {
                double xRatio = (double) x / imageWidth;
                double widthRatio = (double) width / imageWidth;
                return new Player(0, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
            }
        }
        return null;
    }

    // Player 클래스 정의 (이전과 동일)
    @ToString
    public class Player {
        int num;
        double x, y, width, height;

        public Player(int num, double x, double y, double width, double height) {
            this.num = num;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        // 이미지를 기준으로 절대좌표 Rectangle로 변환
        public Rectangle toAbsoluteRectangle(int imageWidth, int imageHeight) {
            int absX = (int) (x * imageWidth);
            int absY = (int) (y * imageHeight);
            int absWidth = (int) (width * imageWidth);
            int absHeight = (int) (height * imageHeight);
            return new Rectangle(absX, absY, absWidth, absHeight);
        }

        public String getDisplayText(String recognizedText) {
            var player = "[Player " + this.num + "] ";
            if (Set.of(3,4,6,9).contains(this.num) && recognizedText.split("백 실버 ").length > 1) {
                return player + recognizedText.split("백 실버 ")[1] + " " + recognizedText.split("백 실버")[0] + "백 실버";
            }
            return player + recognizedText;
        }
    }
}
