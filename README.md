#SVGJNG#

A command line tool for creating JNG-like SVGs

##Usage#

	Usage:
	java -jar SVGJNG.jar split <argb image> <rgb file name> <alpha file name>
		Splits an RGBA image into one RGB image and one alpha image.
	OR
	java -jar SVGJNG.jar fill <rgb image> <alpha image> <filled-rgb file name>
		Fills all 8x8 blocks which are fully transparent.
	OR
	java -jar SVGJNG.jar join <rgb image> <alpha image> [alt alpha image] <svg file name>
		Creates an JNG-like SVG.


##Split#

Splits an RGBA image into one RGB image and one alpha image. This should be the first step if your source is a single 32bit PNG.


##Fill#

JPEG compresses each 8x8 block/cell individually. There is no need to keep fully translucent blocks. Discarding RGB data on the block level also means that there won't be any extra artefact on edges, which you'd get if you discard RGB data on a by pixel level.

Saving as 32bit PNG often discards the color information of fully translucent pixels though. So, this step will be pointless in many cases.


##Join#

This is the final step. It creates an SVG file out of that RGB image and that alpha image. If two alpha images are provided (typically one PNG and one JPG), the smaller one will be used.


##Example Usage#

1. `java -jar SVGJNG.jar split example32.png example_rgb.png example_a.png`

2. `java -jar SVGJNG.jar fill example_rgb.png example_a.png example_rgb_filled.png`

3. `pngout example_a.png` (optional)

4. `deflopt example_a.png` (optional)

5. Save *example_rgb_filled.png* as *example_rgb_filled.jpg* (by using Photoshop's "Save for web" or something similar).

6. `java -jar SVGJNG.jar join example_rgb_filled.jpg example_a.png example.svg`

7. `java -jar GZRepack.jar example.svg example.svgz` (you can of course use other tools for gzipping that SVG)