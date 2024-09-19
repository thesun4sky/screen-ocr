package com.tess4j.rest;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DynamicTextRegionFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicTextRegionFinder.class);
    private static final double HEIGHT_RATIO = 0.03;
    private static final double[] Y_RATIOS = {0.2, 0.26, 0.48, 0.71, 0.94};
    private static final double MIN_WIDTH_RATIO = 0.15;
    private static final double MAX_WIDTH_RATIO = 0.25;

    private static final Pattern START_PATTERN_LEFT = Pattern.compile("^\\s?[A-Za-z0-9]{5,}");
    private static final Pattern END_PATTERN_LEFT = Pattern.compile("버\\s*$");
    private static final Pattern START_PATTERN_RIGHT = Pattern.compile("^\\s?\\d{2,}");
    private static final Pattern END_PATTERN_RIGHT = Pattern.compile("[A-Za-z0-9]{5,}\\s*$");
    private static final Pattern MID_PATTERN = Pattern.compile(".*백.*실.*버.*");

    public List<Player> findDynamicRegions(BufferedImage image, List<Player> existPlayer) throws TesseractException {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int centerX = imageWidth / 2;

        return Arrays.stream(Y_RATIOS)
                .boxed()
                .parallel()
                .flatMap(yRatio -> processYRatio(image, imageWidth, imageHeight, centerX, yRatio, existPlayer))
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
    }

    private Stream<Player> processYRatio(BufferedImage image, int imageWidth, int imageHeight, int centerX, double yRatio, List<Player> existPlayer) {
        List<Player> localPlayers = new ArrayList<>();
        try {
            int y = (int) (yRatio * imageHeight);
            int height = (int) (HEIGHT_RATIO * imageHeight);
            ITesseract tesseract = initializeTesseract();

            if (yRatio != 0.94) {
                processLeftAndRightPlayers(image, imageWidth, centerX, yRatio, y, height, tesseract, localPlayers, existPlayer);
            } else {
                processCenterPlayer(image, centerX, y, height, tesseract, localPlayers, existPlayer);
            }
        } catch (TesseractException e) {
            LOGGER.error("Error processing yRatio {}: {}", yRatio, e.getMessage(), e);
        }

        LOGGER.info("Players found for yRatio ({}): {}", yRatio, localPlayers);
        return localPlayers.stream();
    }

    private ITesseract initializeTesseract() {
        ITesseract tesseract = new Tesseract();
        tesseract.setPageSegMode(7);
        tesseract.setOcrEngineMode(1);
        tesseract.setLanguage("kor+eng");
        return tesseract;
    }

    private int getLeftIndex(double yRatio) {
        if (yRatio == Y_RATIOS[0]) return 2;
        if (yRatio == Y_RATIOS[1]) return 1;
        if (yRatio == Y_RATIOS[2]) return 5;
        if (yRatio == Y_RATIOS[3]) return 7;
        return -1;
    }

    private int getRightIndex(double yRatio) {
        if (yRatio == Y_RATIOS[0]) return 3;
        if (yRatio == Y_RATIOS[1]) return 4;
        if (yRatio == Y_RATIOS[2]) return 6;
        if (yRatio == Y_RATIOS[3]) return 9;
        return -1;
    }

    private void processCenterPlayer(BufferedImage image, int centerX, int y, int height, ITesseract tesseract, List<Player> localPlayers, List<Player> existPlayer) throws TesseractException {
        int centerIndex = 8;
        Player centerPlayer = existPlayer(existPlayer, centerIndex) ? null : findPlayer(centerIndex, image, 0, centerX, y, height, tesseract, true);
        if (centerPlayer != null) localPlayers.add(centerPlayer);
    }


    private void processLeftAndRightPlayers(BufferedImage image, int imageWidth, int centerX, double yRatio, int y, int height, ITesseract tesseract, List<Player> localPlayers, List<Player> existPlayer) throws TesseractException {
        int leftIndex = getLeftIndex(yRatio);
        int rightIndex = getRightIndex(yRatio);

        Player leftPlayer = existPlayer(existPlayer, leftIndex) ? null : findPlayer(leftIndex, image, 0, centerX, y, height, tesseract, true);
        Player rightPlayer = existPlayer(existPlayer, rightIndex) ? null : findPlayer(rightIndex, image, centerX, imageWidth, y, height, tesseract, false);

        if (leftPlayer != null) localPlayers.add(leftPlayer);
        if (rightPlayer != null) localPlayers.add(rightPlayer);
    }

    private boolean existPlayer(List<Player> existPlayer, int index) {
        return existPlayer.stream().anyMatch(p -> p.getIndex() == index);
    }

    private Player findPlayer(int index, BufferedImage image, int startX, int endX, int y, int height, ITesseract tesseract, boolean isLeft) throws TesseractException {
        int stepSize = 20;
        double minWidthRatio = index == 8? MIN_WIDTH_RATIO + 0.05 : MIN_WIDTH_RATIO;
        int initialWidth = (int) (image.getWidth() * minWidthRatio);
        Pattern startPattern = isLeft ? START_PATTERN_LEFT : START_PATTERN_RIGHT;
        Pattern endPattern = isLeft ? END_PATTERN_LEFT : END_PATTERN_RIGHT;

        for (int x = startX; isLeft ? x < endX : x + initialWidth <= endX; x += stepSize) {
            Rectangle rect = new Rectangle(x, y, initialWidth, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[findPlayer{}] result of index ({}) : {}", isLeft ? "Left" : "Right", index, result);

            if ((isLeft && startPattern.matcher(result).find() && result.contains("백")) || ((!isLeft && startPattern.matcher(result).find()) && MID_PATTERN.matcher(result).find())) {
                LOGGER.info("start Found");
                return expandSearch(image, x, y, height, initialWidth, endPattern, tesseract, index, isLeft, endX);
            }
        }
        return null;
    }

    private Player expandSearch(BufferedImage image, int x, int y, int height, int initialWidth, Pattern endPattern, ITesseract tesseract, int index, boolean isLeft, int endX) throws TesseractException {
        int width = initialWidth;
        while (isLeft ? x + width <= endX : x + width <= image.getWidth()) {
            Rectangle rect = new Rectangle(x, y, width, height);
            String result = tesseract.doOCR(image, rect).trim();
            LOGGER.info("[expandSearch{}] Expanded result of index ({}) : {}", isLeft ? "Left" : "Right", index, result);

            if (((isLeft && endPattern.matcher(result).find()) && MID_PATTERN.matcher(result).find()) || (!isLeft && endPattern.matcher(result).find())) {
                double xRatio = (double) x / image.getWidth();
                double widthRatio = (double) width / image.getWidth();
                return new Player(index, xRatio, (double) y / image.getHeight(), widthRatio, HEIGHT_RATIO);
            } else if (width > image.getWidth() * MAX_WIDTH_RATIO) {
                LOGGER.info("[expandSearch{}] Expanded search fail of index ({})", isLeft ? "Left" : "Right", index);
                return null;
            }

            width += 20;
        }
        return null;
    }

    @ToString
    @Getter
    @Setter
    public static class Player {
        int index;
        double x, y, width, height;

        public Player(int index, double x, double y, double width, double height) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Rectangle toAbsoluteRectangle(int imageWidth, int imageHeight) {
            int absX = (int) (x * imageWidth);
            int absY = (int) (y * imageHeight);
            int absWidth = (int) (width * imageWidth);
            int absHeight = (int) (height * imageHeight);
            return new Rectangle(absX, absY, absWidth, absHeight);
        }

        public String getDisplayText(String recognizedText) {
            var player = "[Player " + this.index + "] ";
            if (Set.of(3,4,6,9).contains(this.index)
                    && recognizedText.contains("백") && recognizedText.contains("버")
                    && recognizedText.split("버").length > 1) {
                return player + recognizedText.split("버")[1] + " " + recognizedText.split("백")[0] + " WON";
            }
            return player + recognizedText.split("백")[0] + " WON";
        }
    }
}