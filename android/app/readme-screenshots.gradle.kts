import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The README's screenshots come from the screenshot tests (ScreenshotTest), never by hand: ./gradlew readmeScreenshots.
 * Each is scaled to twice the width the README shows it at, sharp on high-density screens and still small: a new
 * pixel is the average of the captured pixels it covers, weighted by how much of each it covers. Screens are
 * opaque, so the PNGs carry no alpha.
 */
abstract class ReadmeScreenshots : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val screens: ConfigurableFileCollection
    @get:Input abstract val width: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun export() {
        screens.files.forEach { ImageIO.write(scaled(ImageIO.read(it), width.get()), "png", outputDir.get().file(it.name).asFile) }
    }

    private fun scaled(image: BufferedImage, width: Int): BufferedImage {
        val height = (image.height.toDouble() * width / image.width).roundToInt()
        val sx = image.width.toDouble() / width
        val sy = image.height.toDouble() / height
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) {
            val top = y * sy
            val left = x * sx
            val sum = DoubleArray(3)
            var area = 0.0
            for (row in top.toInt() until min(ceil(top + sy).toInt(), image.height)) {
                val h = min(row + 1.0, top + sy) - max(row.toDouble(), top)
                for (col in left.toInt() until min(ceil(left + sx).toInt(), image.width)) {
                    val w = (min(col + 1.0, left + sx) - max(col.toDouble(), left)) * h
                    val p = pixels[row * image.width + col]
                    for (c in 0..2) sum[c] += (p shr (16 - 8 * c) and 0xff) * w
                    area += w
                }
            }
            out.setRGB(x, y, sum.fold(0xff) { rgb, s -> (rgb shl 8) or (s / area).roundToInt() })
        }
        return out
    }
}

tasks.register<ReadmeScreenshots>("readmeScreenshots") {
    dependsOn("testDebugUnitTest")
    screens.from(listOf("home-light", "home-dark", "detail-light", "detail-dark").map { layout.buildDirectory.file("outputs/roborazzi/$it.png") })
    width.set(600) // twice the README's <img width="300">
    outputDir.set(layout.projectDirectory.dir("../../docs/screenshots"))
}
