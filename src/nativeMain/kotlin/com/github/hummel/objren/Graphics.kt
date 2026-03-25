@file:Suppress("NOTHING_TO_INLINE")

package com.github.hummel.objren

import kotlinx.cinterop.*
import platform.windows.*
import kotlin.math.abs

private val white: RGB = RGB(255, 255, 255)
private val zBuffer: FloatArray = FloatArray(windowWidth * windowHeight)

private lateinit var displayMatrix: Array<FloatArray>
private lateinit var lightPos: Vertex
private lateinit var eyePos: Vertex

const val ambientIntencity: Float = 0.0f
const val diffuseIntencity: Float = 0.8f


fun renderObject(eye: Vertex) {
	displayMatrix = getDisplayMatrix(eye)
	lightPos = getLightPos(eye)
	eyePos = eye

	bitmapData.fill(0)
	zBuffer.fill(Float.POSITIVE_INFINITY)

	memScoped {
		val params = Array(kernels) {
			alloc<IntVar>()
		}

		params.forEachIndexed { index, param -> param.value = index }

		val threads = Array(kernels) {
			CreateThread(null, 0u, staticCFunction(::tfDrawVertices), params[it].ptr, 0u, null)
		}

		threads.forEach {
			WaitForSingleObject(it, INFINITE)
			CloseHandle(it)
		}
	}
}

private inline fun tfDrawVertices(parameters: LPVOID?): DWORD {
	val id = parameters?.reinterpret<IntVar>()?.pointed?.value!!

	threadFaces[id].forEach {
		val viewDir = -it.realVertices[0] + eyePos
		val cosAngle = it.poliNormal scalarMul viewDir

		if (!isSurfaceMode || cosAngle > 0 && isSurfaceMode) {
			it.viewVertices[0] = multiplyVertexByMatrix(it.realVertices[0], displayMatrix)
			it.viewVertices[1] = multiplyVertexByMatrix(it.realVertices[1], displayMatrix)
			it.viewVertices[2] = multiplyVertexByMatrix(it.realVertices[2], displayMatrix)

			for (i in it.viewVertices.indices) {
				it.savedW[i] = it.viewVertices[i].w
				it.viewVertices[i] divSelf it.viewVertices[i].w
			}

			if (isSurfaceMode) {
				fillTriangle(it)
			} else {
				drawTriangleBorders(it)
			}
		}
	}

	return 0u
}

private inline fun drawTriangleBorders(face: Face) {
	drawLine(face.viewVertices[0], face.viewVertices[1], white)
	drawLine(face.viewVertices[1], face.viewVertices[2], white)
	drawLine(face.viewVertices[2], face.viewVertices[0], white)
}

private inline fun drawLine(v1: Vertex, v2: Vertex, rgb: RGB) {
	var x1 = v1.x.toInt()
	val x2 = v2.x.toInt()
	var y1 = v1.y.toInt()
	val y2 = v2.y.toInt()

	val dx = abs(x2 - x1)
	val dy = abs(y2 - y1)
	val sx = if (x1 < x2) 1 else -1
	val sy = if (y1 < y2) 1 else -1
	var err = dx - dy

	while (x1 != x2 || y1 != y2) {
		if (x1 in 0 until windowWidth && y1 in 0 until windowHeight) {
			bitmapData.setRGB(x1, y1, rgb)
		}

		val err2 = 2 * err

		if (err2 > -dy) {
			err -= dy
			x1 += sx
		}

		if (err2 < dx) {
			err += dx
			y1 += sy
		}
	}
}

private inline fun fillTriangle(face: Face) {
	val minY = face.viewVertices.minOfOrNull { it.y.toInt() } ?: Int.MAX_VALUE
	val maxY = face.viewVertices.maxOfOrNull { it.y.toInt() } ?: Int.MIN_VALUE

	for (y in minY..maxY) {
		if (y in 0 until windowHeight) {
			val xIntersections = IntArray(2)
			var intersectionCount = 0
			for (i in 0..2) {
				val v0 = face.viewVertices[i]
				val v1 = face.viewVertices[(i + 1) % 3]
				val y0 = v0.y.toInt()
				val y1 = v1.y.toInt()
				if (y in y0 until y1 || y in y1 until y0) {
					val t = (y - y0) / (y1 - y0).toFloat()
					val x = (v0.x + t * (v1.x - v0.x)).toInt()
					xIntersections[intersectionCount] = x
					intersectionCount++
				}
			}

			if (intersectionCount == 2 && xIntersections[0] > xIntersections[1]) {
				val temp = xIntersections[0]
				xIntersections[0] = xIntersections[1]
				xIntersections[1] = temp
			}

			if (intersectionCount == 2) {
				for (x in xIntersections[0]..xIntersections[1]) {
					if (x in 0 until windowWidth) {
						val v0 = face.viewVertices[0]
						val v1 = face.viewVertices[1]
						val v2 = face.viewVertices[2]

						val coords = face.getBarycentricCoords(x, y)

						var alpha = coords[0]
						var beta = coords[1]
						var gamma = coords[2]

						alpha /= face.savedW[0]
						beta /= face.savedW[1]
						gamma /= face.savedW[2]

						val sum = alpha + beta + gamma

						alpha /= sum
						beta /= sum
						gamma /= sum

						val zFragment = alpha * v0.z + beta * v1.z + gamma * v2.z

						if (zBuffer[x * windowHeight + y] > zFragment) {
							zBuffer[x * windowHeight + y] = zFragment

							val rgb = getResultRgb(face)

							bitmapData.setRGB(x, y, rgb)
						}
					}
				}
			}
		}
	}
}

private inline fun getResultRgb(face: Face): RGB {
	val point = face.realVertices[0]
	val normal = face.poliNormal

	val brightness = getBrightness(point, normal)

	val colorVal = (if (brightness * 255 > 255) 255 else brightness * 255).toInt()

	return RGB(colorVal, colorVal, colorVal)
}

private inline fun getBrightness(point: Vertex, normal: Vertex): Float {
	val ray = lightPos - point
	var brightness = 0.0f
	val angle = normal scalarMul ray

	if (angle > 0) {
		brightness += diffuseIntencity * angle / (ray.magnitude * normal.magnitude)
	}

	return brightness + ambientIntencity
}