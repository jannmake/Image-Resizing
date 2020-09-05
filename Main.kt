package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


operator fun Color.minus (other: Color): Color {
    return Color(abs(this.red - other.red), abs(this.green - other.green), abs(this.blue - other.blue))
}

fun BufferedImage.deepCopy(): BufferedImage {
    val cm = this.colorModel
    val isAlphaPremultiplied = cm.isAlphaPremultiplied
    val raster = this.copyData(null)
    return BufferedImage(cm, raster, isAlphaPremultiplied, null)
}

fun BufferedImage.rotated(): BufferedImage {
    val img = BufferedImage(this.height, this.width, this.type)
    for (x in 0 until this.width) {
        for (y in 0 until this.height) {
            img.setRGB(y, x, this.getRGB(x, y))
        }
    }
    return img
}

fun BufferedImage.resized(count: Int): BufferedImage {
    var shrinkImg = this.deepCopy()

    repeat(count) {
        val newImg = BufferedImage(shrinkImg.width - 1, shrinkImg.height, this.type)
        val energyArray = getEnergyArray(shrinkImg)
        val seam = Graph(energyArray).getPath()
        for (n in seam) {
            val row = IntArray(shrinkImg.width) { shrinkImg.getRGB(it, n.y) }.toMutableList()
            row.removeAt(n.x)
            for ((i, rgb) in row.withIndex()) {
                newImg.setRGB(i, n.y, rgb)
            }
        }
        shrinkImg = newImg.deepCopy()
    }

    return shrinkImg
}

data class Energy (var value: Double) {
    fun toColor(maxEnergy: Double): Color {
        val intensity = (255.0 * value / maxEnergy).toInt()
        return Color(intensity, intensity, intensity)
    }
}

class EnergyArray(var width: Int, var height: Int) {
    private val energies = Array(width * height) { Energy(0.0) }
    private var maxEnergy = -1.0

    fun setEnergy(x: Int, y: Int, energy: Energy) {
        energies[y * width + x] = energy
        if (energy.value > maxEnergy)
            maxEnergy = energy.value
    }

    fun getEnergy(x: Int, y: Int): Energy {
        return energies[y * width + x]
    }

    fun getColor(x: Int, y: Int): Color {
        return energies[y * width + x].toColor(maxEnergy)
    }
}


data class Node(val x: Int, val y: Int, var distance: Double, val energy: Double, var isHandled: Boolean)

class Graph (private val energyArray: EnergyArray) {
    private val nodes = Array(energyArray.width * energyArray.height) {
        // i = y * width + x
        val x = it % energyArray.width
        val y = it / energyArray.width
        val energy = energyArray.getEnergy(x, y).value
        var distance = Double.MAX_VALUE
        if (y == 0)
            distance = energy
        Node(x, y, distance, energy, false)
    }

    private fun getNode(x: Int, y: Int): Node {
        if (y == -1) {
            return nodes.slice(0 until energyArray.width).sortedBy { it.distance }[0]
        }
        return nodes[y * energyArray.width + x]
    }

    private fun getAdjacentNodes(x: Int, y: Int): List<Node> {
        val nodes = mutableListOf<Node>()

        if (x > 0 && y < energyArray.height - 1)
            nodes.add(getNode(x - 1, y + 1))
        if (y < energyArray.height - 1)
            nodes.add(getNode(x, y + 1))
        if (x < energyArray.width - 1 && y < energyArray.height - 1)
            nodes.add(getNode(x + 1, y + 1))
        return nodes.sortedBy { it.distance }
    }

    private fun getNearestNode(x: Int, y: Int): Node? {
        val nodes = mutableListOf<Node>()

        if (x > 0 && y > 0)
            nodes.add(getNode(x - 1, y - 1))
        if (y > 0)
            nodes.add(getNode(x, y - 1))
        if (x < energyArray.width - 1 && y > 0)
            nodes.add(getNode(x + 1, y - 1))
        return nodes.sortedBy { it.distance }.getOrNull(0)
    }

    fun getPath(): List<Node> {
        val nodeQue = mutableListOf<Node>()
        val firstRow = nodes.slice(0 until energyArray.width).sortedBy { it.distance }
        nodeQue.addAll(firstRow)
        var currNode: Node
        while (nodeQue.isNotEmpty()) {
            currNode = nodeQue.removeAt(0)
            if (currNode.isHandled)
                continue
            val adjacent = getAdjacentNodes(currNode.x, currNode.y)
            for (n in adjacent) {
                if (!n.isHandled) {
                    val d = currNode.distance + n.energy
                    if (d < n.distance)
                        n.distance = d
                    nodeQue.add(n)
                }
            }
            currNode.isHandled = true
        }

        var leastDistant = nodes.slice(nodes.size - energyArray.width until nodes.size).minBy { it.distance }
        val bestPath = mutableListOf<Node>()
        if (leastDistant != null) {
            bestPath.add(leastDistant)
            while (leastDistant != null) {
                leastDistant = getNearestNode(leastDistant.x, leastDistant.y)
                if (leastDistant != null) {
                    bestPath.add(leastDistant)
                }
            }
        }

        return bestPath
    }
}

fun calculateEnergy(img: BufferedImage, x: Int, y: Int): Energy {
    val xmax = img.width - 1
    val ymax = img.height - 1
    val xdiff = when (x) {
        0 -> arrayOf(0, 2)
        xmax -> arrayOf(-2, 0)
        else -> arrayOf(1, -1)
    }
    val ydiff = when (y) {
        0 -> arrayOf(0, 2)
        ymax -> arrayOf(-2, 0)
        else -> arrayOf(1, -1)
    }
    val (x1, x2) = xdiff.map { Color(img.getRGB(x + it, y)) }
    val (y1, y2) = ydiff.map { Color(img.getRGB(x, y + it)) }
    val colorDiffX = (x1 - x2)
    val colorDiffY = (y1 - y2)
    val energy = sqrt(
            colorDiffX.red.toDouble().pow(2) + colorDiffX.green.toDouble().pow(2) + colorDiffX.blue.toDouble().pow(2) +
               colorDiffY.red.toDouble().pow(2) + colorDiffY.green.toDouble().pow(2) + colorDiffY.blue.toDouble().pow(2)
            )
    return Energy(energy)
}

fun getEnergyArray(img: BufferedImage): EnergyArray {
    val energies = EnergyArray(img.width, img.height)
    for (x in 0 until img.width) {
        for (y in 0 until img.height) {
            val energy = calculateEnergy(img, x, y)
            energies.setEnergy(x, y, energy)
        }
    }
    return energies
}

fun main(args: Array<String>) {
    val inInd = args.indexOf("-in")
    val outInd = args.indexOf("-out")
    val widthInd = args.indexOf("-width")
    val heightInd = args.indexOf("-height")

    if (inInd > -1 && outInd > -1) {
        val path = args[inInd + 1]
        var img = ImageIO.read(File(path))

        if (widthInd > -1)
            img = img.resized(args[widthInd + 1].toInt())

        if (heightInd > -1) {
            img = img.rotated()
            img = img.resized(args[heightInd + 1].toInt())
            img = img.rotated()
        }

        val fileName = args[outInd + 1]
        ImageIO.write(img, "png", File(fileName))
    }
}
