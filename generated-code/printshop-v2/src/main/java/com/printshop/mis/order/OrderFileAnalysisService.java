package com.printshop.mis.order;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Service
public class OrderFileAnalysisService {

    private static final BigDecimal MILLIMETERS_PER_INCH = new BigDecimal("25.4");
    private static final BigDecimal POINTS_PER_INCH = new BigDecimal("72");
    private static final float PAGE_SIZE_TOLERANCE_POINTS = 0.5f;
    private static final int MAX_PAGE_SIZE_CHECKS = 5000;

    public AnalysisResult analyze(Path path, String extension) {
        try {
            return switch (extension.toLowerCase(Locale.ROOT)) {
                case "pdf" -> analyzePdf(path);
                case "jpg", "jpeg", "png" -> analyzeImage(path);
                default -> AnalysisResult.unsupported("该文件类型暂不支持自动识别，请人工填写订单参数。");
            };
        } catch (Exception | LinkageError error) {
            return AnalysisResult.failed("文件分析失败：" + safeMessage(error));
        }
    }

    private AnalysisResult analyzePdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                return AnalysisResult.failed("PDF 不包含可打印页面，请检查文件。");
            }
            PageSize first = pageSize(document.getPage(0));
            boolean mixed = false;
            int checkedPages = Math.min(pageCount, MAX_PAGE_SIZE_CHECKS);
            for (int index = 1; index < checkedPages; index++) {
                PageSize current = pageSize(document.getPage(index));
                if (Math.abs(first.widthPoints - current.widthPoints) > PAGE_SIZE_TOLERANCE_POINTS
                        || Math.abs(first.heightPoints - current.heightPoints) > PAGE_SIZE_TOLERANCE_POINTS) {
                    mixed = true;
                    break;
                }
            }
            BigDecimal widthMm = pointsToMillimeters(first.widthPoints);
            BigDecimal heightMm = pointsToMillimeters(first.heightPoints);
            String message = "已识别 PDF：" + pageCount + " 页，首页尺寸 "
                    + widthMm.toPlainString() + " x " + heightMm.toPlainString() + " mm";
            if (mixed) {
                message += "，包含不同页面尺寸";
            }
            if (pageCount > MAX_PAGE_SIZE_CHECKS) {
                message += "；页数超出订单上限，未自动回填";
            }
            return new AnalysisResult(
                    "DETECTED",
                    pageCount,
                    widthMm,
                    heightMm,
                    null,
                    null,
                    null,
                    null,
                    mixed,
                    message + "。"
            );
        }
    }

    private AnalysisResult analyzeImage(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return AnalysisResult.failed("无法读取图片文件。");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return AnalysisResult.failed("无法识别图片编码。");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                IIOMetadata metadata = reader.getImageMetadata(0);
                BigDecimal horizontalPixelSize = pixelSizeMillimeters(metadata, "HorizontalPixelSize");
                BigDecimal verticalPixelSize = pixelSizeMillimeters(metadata, "VerticalPixelSize");
                BigDecimal dpiX = dpi(horizontalPixelSize);
                BigDecimal dpiY = dpi(verticalPixelSize);
                BigDecimal widthMm = physicalSize(width, horizontalPixelSize);
                BigDecimal heightMm = physicalSize(height, verticalPixelSize);
                String message = "已识别单页图片：" + width + " x " + height + " px";
                if (dpiX != null && dpiY != null) {
                    message += "，约 " + dpiX.toPlainString() + " x " + dpiY.toPlainString() + " DPI";
                } else {
                    message += "，文件未提供可用 DPI";
                }
                return new AnalysisResult(
                        "DETECTED",
                        1,
                        widthMm,
                        heightMm,
                        width,
                        height,
                        dpiX,
                        dpiY,
                        false,
                        message + "。"
                );
            } finally {
                reader.dispose();
            }
        }
    }

    private PageSize pageSize(PDPage page) {
        PDRectangle box = page.getCropBox();
        float width = box.getWidth();
        float height = box.getHeight();
        int rotation = Math.floorMod(page.getRotation(), 360);
        if (rotation == 90 || rotation == 270) {
            return new PageSize(height, width);
        }
        return new PageSize(width, height);
    }

    private BigDecimal pixelSizeMillimeters(IIOMetadata metadata, String nodeName) {
        if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
            return null;
        }
        try {
            Node root = metadata.getAsTree("javax_imageio_1.0");
            Node dimension = child(root, "Dimension");
            Node size = child(dimension, nodeName);
            if (size == null) {
                return null;
            }
            NamedNodeMap attributes = size.getAttributes();
            Node value = attributes == null ? null : attributes.getNamedItem("value");
            if (value == null) {
                return null;
            }
            BigDecimal result = new BigDecimal(value.getNodeValue());
            return result.signum() > 0 ? result : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Node child(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private BigDecimal dpi(BigDecimal millimetersPerPixel) {
        if (millimetersPerPixel == null) {
            return null;
        }
        return MILLIMETERS_PER_INCH.divide(millimetersPerPixel, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal physicalSize(int pixels, BigDecimal millimetersPerPixel) {
        if (millimetersPerPixel == null) {
            return null;
        }
        return millimetersPerPixel.multiply(BigDecimal.valueOf(pixels)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal pointsToMillimeters(float points) {
        return BigDecimal.valueOf(points)
                .multiply(MILLIMETERS_PER_INCH)
                .divide(POINTS_PER_INCH, 2, RoundingMode.HALF_UP);
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n]+", " ").trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private record PageSize(float widthPoints, float heightPoints) {
    }

    public record AnalysisResult(
            String status,
            Integer pageCount,
            BigDecimal widthMm,
            BigDecimal heightMm,
            Integer pixelWidth,
            Integer pixelHeight,
            BigDecimal dpiX,
            BigDecimal dpiY,
            boolean mixedPageSizes,
            String message
    ) {
        public static AnalysisResult unsupported(String message) {
            return new AnalysisResult("UNSUPPORTED", null, null, null, null, null, null, null, false, message);
        }

        public static AnalysisResult failed(String message) {
            return new AnalysisResult("FAILED", null, null, null, null, null, null, null, false, message);
        }
    }
}
