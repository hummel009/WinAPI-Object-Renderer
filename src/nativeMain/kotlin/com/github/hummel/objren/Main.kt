package com.github.hummel.objren

import kotlinx.cinterop.*
import platform.windows.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.time.measureTime

private val execTimes: MutableList<Long> = mutableListOf()
private var min: Long = Long.MAX_VALUE
private var max: Long = Long.MIN_VALUE

const val windowWidth: Int = 1280
const val windowHeight: Int = 720

var bitmapData: ByteArray = ByteArray(windowWidth * windowHeight * 4)
var faces: MutableList<Face> = mutableListOf()

var isSurfaceMode: Boolean = true

private var dist: Float = 10.0f

fun main() {
	println("Surface mode (true) or wire mode (false)?")

	try {
		isSurfaceMode = readln().toBoolean()
	} catch (e: Exception) {
		e.printStackTrace()
	}

	parse("input/model.obj")

	memScoped {
		val className = "Hummel009's Object Renderer"
		val windowTitle = "Hummel009's Object Renderer"

		val windowClass = alloc<WNDCLASS>()
		windowClass.style = 0u
		windowClass.lpfnWndProc = staticCFunction(::wndProc)
		windowClass.cbClsExtra = 0
		windowClass.cbWndExtra = 0
		windowClass.hInstance = null
		windowClass.hIcon = null
		windowClass.hCursor = null
		windowClass.hbrBackground = (COLOR_WINDOW + 1).toLong().toCPointer()
		windowClass.lpszMenuName = null
		windowClass.lpszClassName = className.wcstr.ptr

		SetProcessDPIAware()

		RegisterClassW(windowClass.ptr)

		val screenWidth = GetSystemMetrics(SM_CXSCREEN)
		val screenHeight = GetSystemMetrics(SM_CYSCREEN)

		val windowX = max(0, (screenWidth - windowWidth) / 2)
		val windowY = max(0, (screenHeight - windowHeight) / 2)

		CreateWindowExW(
			0u,
			className,
			windowTitle,
			(WS_VISIBLE or WS_CAPTION or WS_SYSMENU).toUInt(),
			windowX,
			windowY,
			windowWidth,
			windowHeight,
			null,
			null,
			null,
			null
		)

		val msg = alloc<MSG>()
		while (GetMessageW(msg.ptr, null, 0u, 0u) != 0) {
			TranslateMessage(msg.ptr)
			DispatchMessageW(msg.ptr)
		}
	}
}

private var rotateX: Float = 0.0f
private var rotateY: Float = 0.0f
private var prevMouseX: Int = 0
private var prevMouseY: Int = 0
private var isDragging: Boolean = false

private fun wndProc(window: HWND?, msg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
	when (msg.toInt()) {
		WM_MOUSEMOVE -> {
			if (isDragging) {
				val currMouseX = lParam.toInt() and 0xffff
				val currMouseY = (lParam.toInt() shr 16) and 0xffff

				rotateY += (currMouseX - prevMouseX) / 200.0f
				rotateX += (currMouseY - prevMouseY) / 200.0f

				prevMouseX = currMouseX
				prevMouseY = currMouseY

				InvalidateRect(window, null, FALSE)
			}
		}

		WM_LBUTTONDOWN -> {
			isDragging = true
			prevMouseX = lParam.toInt() and 0xffff
			prevMouseY = (lParam.toInt() shr 16) and 0xffff
			SetCapture(window)
		}

		WM_LBUTTONUP -> {
			isDragging = false
			ReleaseCapture()
		}

		WM_PAINT -> {
			val time = measureTime {
				memScoped {
					val paintStructure = alloc<PAINTSTRUCT>()
					val deviceContext = BeginPaint(window, paintStructure.ptr)
					val deviceContextMemory = CreateCompatibleDC(deviceContext)

					val eye = Vertex(
						dist * cos(rotateX) * cos(rotateY),
						dist * sin(rotateX),
						dist * cos(rotateX) * sin(rotateY)
					)

					renderObject(eye)

					val bitmap = CreateBitmap(windowWidth, windowHeight, 1u, 32u, bitmapData.refTo(0))
					val bitmapOld = SelectObject(deviceContextMemory, bitmap)

					BitBlt(deviceContext, 0, 0, windowWidth, windowHeight, deviceContextMemory, 0, 0, SRCCOPY)

					SelectObject(deviceContextMemory, bitmapOld)
					DeleteObject(bitmap)

					DeleteDC(deviceContextMemory)
					EndPaint(window, paintStructure.ptr)
				}
			}.inWholeNanoseconds

			val fps = (1_000_000_000.0 / time).toLong()

			execTimes.add(fps)

			min = min.coerceAtMost(fps)
			max = max.coerceAtLeast(fps)

			val avg = execTimes.average().toLong()

			println("$fps FPS, [$min; $max]; AVG: $avg; Lag: ${time / 1_000_000}ms")
		}

		WM_CLOSE -> DestroyWindow(window)

		WM_DESTROY -> PostQuitMessage(0)

		else -> {}
	}
	return DefWindowProcW(window, msg, wParam, lParam)
}