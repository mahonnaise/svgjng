package com.kaioa.svgjng;

import javax.imageio.*;
import java.awt.image.*;
import java.awt.*;
import java.io.*;

public class Main {

    public static void main(String[] args) throws Exception {
        if ((args.length != 4 && args.length != 5) || (args.length != 4 && !(args[0].equals("split") || args[0].equals("fill") || args[0].equals("join"))) && (args.length != 5 && args[0].equals("join"))) {
            System.out.println("Usage:" +
                    "\njava -jar SVGJNG.jar split <argb image> <rgb file name> <alpha file name>" +
                    "\n\tSplits an RGBA image into one RGB image and one alpha image." +
                    "\nOR" +
                    "\njava -jar SVGJNG.jar fill <rgb image> <alpha image> <filled-rgb file name>" +
                    "\n\tFills all 8x8 blocks which are fully transparent." +
                    "\nOR" +
                    "\njava -jar SVGJNG.jar join <rgb image> <alpha image> [alt alpha image] <svg file name>" +
                    "\n\tCreates an JNG-like SVG.");
            System.exit(1);
        }
        if (args[0].equals("split")) {
            splitOp(args[1], args[2], args[3]);
        } else if (args[0].equals("fill")) {
            fillOp(args[1], args[2], args[3]);
        } else { // arg[0] does equal "join"
            if (args.length == 4) {
                joinOp(args[1], args[2], null, args[3]);
            } else {
                joinOp(args[1], args[2], args[3], args[4]);
            }
        }
    }

    // Performs the "split" operation. Turns one RGBA image into one RGB image and one alpha image.
    private static void splitOp(String inName, String outRgbName, String outAlphaName) throws Exception {
        BufferedImage srcImage = (BufferedImage) ImageIO.read(new File(inName));

        int w = srcImage.getWidth();
        int h = srcImage.getHeight();

        BufferedImage colorImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_BGR);
        BufferedImage alphaImage = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                colorImage.setRGB(x, y, srcImage.getRGB(x, y));
            }
        }

        WritableRaster alphaImageRaster = (WritableRaster) alphaImage.getRaster();
        alphaImageRaster.setDataElements(0, 0, srcImage.getAlphaRaster());

        ImageIO.write(colorImage, "png", new File(outRgbName));
        ImageIO.write(alphaImage, "png", new File(outAlphaName));
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
        out.write("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"");
        out.write("" + alphaImage.getWidth());
        out.write("\" height=\"");
        out.write("" + alphaImage.getHeight());
        out.write("\"><defs><mask id=\"m\"><image xlink:href=\"");
        out.write(base64Header(smallerAlphaName));
        out.write(base64Data(smallerAlphaName));
        out.write("\" width=\"100%\" height=\"100%\"/></mask></defs><image xlink:href=\"");
        out.write(base64Header(inRgbName));
        out.write(base64Data(inRgbName));
        out.write("\" mask=\"url(#m)\" width=\"100%\" height=\"100%\"/></svg>");
        out.flush();
        out.close();
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
}
