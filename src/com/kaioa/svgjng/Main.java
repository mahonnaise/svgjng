package com.kaioa.svgjng;

import javax.imageio.*;
import java.awt.image.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.text.*;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 5) {
            printUsageAndDie();
        }

        String op = args[0];

        // split <rgba image> <rgb file name> <alpha file name>
        if (op.equals("split")) {
            if (args.length == 4) {
                splitOp(args[1], args[2], args[3]);
            } else {
                printUsageAndDie();
            }
        } // join <rgb image> <alpha image> [alt alpha image] <svg file name>
        else if (op.equals("join")) {
            if (args.length == 4) {
                joinOp(args[1], args[2], null, args[3]);
            } else if (args.length == 5) {
                joinOp(args[1], args[2], args[3], args[4]);
            } else {
                printUsageAndDie();
            }
        } // fill <rgb image> <alpha image> <filled-rgb file name>
        else if (op.equals("fill")) {
            if (args.length == 4) {
                fillOp(args[1], args[2], args[3]);
            } else {
                printUsageAndDie();
            }
        } // <rgba image> <svgz file name>
        else if (args.length == 2) {
            quickOp(args[0], args[1], "80", "60");
        } else if (args.length == 3) {
            quickOp(args[0], args[1], args[2], "60");
        } else if (args.length == 4) {
            quickOp(args[0], args[1], args[2], args[3]);
        } else {
            printUsageAndDie();
        }
    }

    // Prints usage and terminates the application.
    private static void printUsageAndDie() {
        System.out.println("Usage:" +
                "\njava -jar SVGJNG.jar [mode] <parameters>" +
                "\n" +
                "\nsplit <rgba image> <rgb file name> <alpha file name>" +
                "\n\tSplits an RGBA image into one RGB image and one alpha image." +
                "\n" +
                "\nfill <rgb image> <alpha image> <filled-rgb file name>" +
                "\n\tFills all 8x8 blocks which are fully transparent." +
                "\n" +
                "\njoin <rgb image> <alpha image> [alt alpha image] <svg file name>" +
                "\n\tCreates an JNG-like SVG." +
                "\n" +
                "\n<rgba image> <svgz file name> [color quality] [alpha quality]" +
                "\n\tCreates an unoptimized JNG-like SVGZ." +
                "\n\tDefault color quality is 80 and default alpha quality is 60.");
        System.exit(1);
    }

    // Performs the "split" operation. Turns one RGBA image into one RGB image and one alpha image.
    private static void splitOp(String inName, String outRgbName, String outAlphaName) throws Exception {
        BufferedImage srcImage = (BufferedImage) ImageIO.read(new File(inName));

        BufferedImage colorImage = splitRgb(srcImage);
        BufferedImage alphaImage = splitAlpha(srcImage);

        ImageIO.write(colorImage, "png", new File(outRgbName));
        ImageIO.write(alphaImage, "png", new File(outAlphaName));
    }

    // RGBA -> RGB
    private static BufferedImage splitRgb(BufferedImage srcImage) {
        int w = srcImage.getWidth();
        int h = srcImage.getHeight();

        BufferedImage colorImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);

        // XXX: slow and silly
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                colorImage.setRGB(x, y, srcImage.getRGB(x, y));
            }
        }

        return colorImage;
    }

    // RGBA -> A
    private static BufferedImage splitAlpha(BufferedImage srcImage) {
        int w = srcImage.getWidth();
        int h = srcImage.getHeight();

        BufferedImage alphaImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        WritableRaster alphaImageRaster = (WritableRaster) alphaImage.getRaster();
        alphaImageRaster.setDataElements(0, 0, srcImage.getAlphaRaster());

        return alphaImage;
    }

    // Performs the "fill" operation. Fills every completely translucent 8x8 block with black.
    private static void fillOp(String inRgbName, String inAlphaName, String outRgbName) throws Exception {
        BufferedImage alphaImage = (BufferedImage) ImageIO.read(new File(inAlphaName));
        BufferedImage colorImage = (BufferedImage) ImageIO.read(new File(inRgbName));

        fillInvisibleBlocks(alphaImage, colorImage);

        ImageIO.write(colorImage, "png", new File(outRgbName));
    }

    // Fills all completely invisible 8x8 blocks with black.
    private static void fillInvisibleBlocks(BufferedImage alphaImage, BufferedImage colorImage) {
        int w = alphaImage.getWidth();
        int h = alphaImage.getHeight();
        // XXX: slow and silly
        for (int x = 0; x < w; x += 8) {
            for (int y = 0; y < h; y += 8) {
                if (blockInvisible(alphaImage, x, y)) {
                    fillBlock(colorImage, x, y);
                }
            }
        }
    }

    // Checks if a 8x8 block is completely invisible.
    private static boolean blockInvisible(BufferedImage alphaImage, int ox, int oy) {
        // XXX: slow and silly
        for (int x = 0; x < 8 && ox + x < alphaImage.getWidth(); x++) {
            for (int y = 0; y < 8 && oy + y < alphaImage.getHeight(); y++) {
                if ((alphaImage.getRGB(ox + x, oy + y) & 0x00ffffff) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // Fills a 8x8 block with black.
    private static void fillBlock(BufferedImage colorImage, int ox, int oy) {
        // XXX: slow and silly
        for (int x = 0; x < 8 && ox + x < colorImage.getWidth(); x++) {
            for (int y = 0; y < 8 && oy + y < colorImage.getHeight(); y++) {
                colorImage.setRGB(ox + x, oy + y, 0x00000000);
            }
        }
    }

    // Performs the "join" operation.
    private static void joinOp(String inRgbName, String inAlphaName, String inAltAlphaName, String outSvgName) throws Exception {
        File alphaFile = new File(inAlphaName);

        String smallerAlphaName = inAlphaName;
        if (inAltAlphaName != null) {
            File altAlphaFile = new File(inAltAlphaName);
            if (alphaFile.length() > altAlphaFile.length()) {
                smallerAlphaName = inAltAlphaName;
            }
        }

        BufferedImage alphaImage = (BufferedImage) ImageIO.read(alphaFile);

        BufferedWriter out = new BufferedWriter(new FileWriter(new File(outSvgName)));
        out.write(createSvg(alphaImage.getWidth(), alphaImage.getHeight(),
                base64Header(smallerAlphaName), base64Data(smallerAlphaName),
                base64Header(inRgbName), base64Data(inRgbName)));
        out.flush();
        out.close();
    }

    // Creates an SVG file.
    private static String createSvg(int width, int height, String alphaHeader, char[] alphaData, String colorHeader, char[] colorData) {
        StringBuilder sb = new StringBuilder(0x10000); // 64k
        sb.append("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"");
        sb.append("" + width);
        sb.append("\" height=\"");
        sb.append("" + height);
        sb.append("\"><defs><mask id=\"m\"><image xlink:href=\"");
        sb.append(alphaHeader);
        sb.append(alphaData);
        sb.append("\" width=\"100%\" height=\"100%\"/></mask></defs><image xlink:href=\"");
        sb.append(colorHeader);
        sb.append(colorData);
        sb.append("\" mask=\"url(#m)\" width=\"100%\" height=\"100%\"/></svg>");
        return sb.toString();
    }

    // Returns the base64 header thingy, which includes the mime type.
    private static String base64Header(String fileName) throws Exception {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            throw new RuntimeException(fileName + " got no file extension, can't determine the mime type");
        }
        String ext = fileName.toLowerCase().substring(dot + 1);
        if (ext.equals("png")) {
            return "data:image/png;base64,";
        } else if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("jpe")) {
            return "data:image/jpeg;base64,";
        }
        throw new RuntimeException("don't know the mime of " + ext + " files");
    }

    // Returns "raw" base64 encoded data.
    private static char[] base64Data(String fileName) throws Exception {
        File file = new File(fileName);
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
        byte[] data = new byte[(int) file.length()];
        int offset = 0;
        int read;
        while ((read = in.read(data, offset, data.length)) != -1) {
            offset += read;
            if (offset == data.length) {
                break;
            }
        }
        return base64Encode(data);
    }

    // Base64 encodes some byte array.
    private static char[] base64Encode(byte[] data) {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toCharArray();
        int size = data.length;
        char[] out = new char[((size + 2) / 3) * 4];
        int a = 0;
        int i = 0;
        while (i < size) {
            byte b0 = data[i++];
            byte b1 = (i < size) ? data[i++] : 0;
            byte b2 = (i < size) ? data[i++] : 0;

            out[a++] = chars[(b0 >> 2) & 0x3F];
            out[a++] = chars[((b0 << 4) | ((b1 & 0xFF) >> 4)) & 0x3F];
            out[a++] = chars[((b1 << 2) | ((b2 & 0xFF) >> 6)) & 0x3F];
            out[a++] = chars[b2 & 0x3F];
        }
        if (size % 3 == 1) {
            out[--a] = '=';
        } else if (size % 3 == 2) {
            out[--a] = '=';
        }
        return out;
    }

    // Performs the convenient switch-less quick conversion.
    private static void quickOp(String inName, String outName, String colorQualityText, String alphaQualityText) throws Exception {
        // split
        File srcFile = new File(inName);
        BufferedImage srcImage = (BufferedImage) ImageIO.read(srcFile);

        BufferedImage colorImage = splitRgb(srcImage);
        BufferedImage alphaImage = splitAlpha(srcImage);

        // fill
        fillInvisibleBlocks(alphaImage, colorImage);

        // encode images
        int colorQuality = Integer.parseInt(colorQualityText);
        int alphaQuality = Integer.parseInt(alphaQualityText);
        ByteArrayOutputStream colorBaos;
        ByteArrayOutputStream alphaBaosPng = new ByteArrayOutputStream(0x10000); //64k
        ByteArrayOutputStream alphaBaosJpg;

        colorBaos = jpgEncode(colorImage, colorQuality);
        alphaBaosJpg = jpgEncode(alphaImage, alphaQuality);
        // the compression level for PNGs is always 9 (maximum) and cannot be changed via ImageWriteParam
        ImageIO.write(alphaImage, "png", alphaBaosPng);

        // identify smaller alpha image
        ByteArrayOutputStream smallerAlphaBaos, biggerAlphaBaos;
        String alphaExt, using, notUsing;

        if (alphaBaosPng.size() < alphaBaosJpg.size()) {
            alphaExt = ".png";

            smallerAlphaBaos = alphaBaosPng;
            biggerAlphaBaos = alphaBaosJpg;

            using = "PNG";
            notUsing = "JPG";
        } else {
            alphaExt = ".jpg";

            smallerAlphaBaos = alphaBaosJpg;
            biggerAlphaBaos = alphaBaosPng;

            using = "JPG";
            notUsing = "PNG";
        }

        // create svg
        String svg = createSvg(colorImage.getWidth(), colorImage.getHeight(),
                base64Header(alphaExt), base64Encode(smallerAlphaBaos.toByteArray()),
                base64Header(".jpg"), base64Encode(colorBaos.toByteArray()));

        // write svgz
        File svgzFile = new File(outName);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(svgzFile)) {

            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }));

        out.write(svg);
        out.flush();
        out.close();

        // output stats
        DecimalFormat byteFormat = new DecimalFormat("###,###");
        System.out.printf("Alpha size: %10s bytes [%s] (%s was %s bytes, %d%% bigger)\n",
                byteFormat.format(smallerAlphaBaos.size()), using, notUsing, byteFormat.format(biggerAlphaBaos.size()),
                Math.round((100f * biggerAlphaBaos.size() / smallerAlphaBaos.size()) - 100));

        System.out.printf("Color size: %10s bytes\n", byteFormat.format(colorBaos.size()));
        System.out.printf("SVG size  : %10s bytes\n", byteFormat.format(svg.length()));
        System.out.printf("SVGZ size : %10s bytes (%d%% of source image)\n",
                byteFormat.format(svgzFile.length()), Math.round(100f * svgzFile.length() / srcFile.length()));
    }

    // Encodes a JPG image with a specific quality (in percent).
    private static ByteArrayOutputStream jpgEncode(BufferedImage image, int quality) throws Exception {
        Iterator iter = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = (ImageWriter) iter.next(); // first one will do

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality / 100f);

        ByteArrayOutputStream out = new ByteArrayOutputStream(0x10000); //64k
        writer.setOutput(ImageIO.createImageOutputStream(out));

        writer.write(null, new IIOImage(image, null, null), param);

        return out;
    }
}
