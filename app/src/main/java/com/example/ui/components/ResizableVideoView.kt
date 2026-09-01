package com.example.ui.components

import android.content.Context
import android.widget.VideoView

class ResizableVideoView(context: Context) : VideoView(context) {
    var scaleMode: Int = 0 // 0 = Fit, 1 = Crop, 2 = Stretch
        set(value) {
            field = value
            requestLayout()
        }
        
    private var videoWidth = 0
    private var videoHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (scaleMode == 0) { // Fit (Default VideoView behavior)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var width = getDefaultSize(videoWidth, widthMeasureSpec)
        var height = getDefaultSize(videoHeight, heightMeasureSpec)
        
        if (videoWidth > 0 && videoHeight > 0) {
            val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSpecSize = MeasureSpec.getSize(widthMeasureSpec)
            val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSpecSize = MeasureSpec.getSize(heightMeasureSpec)

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                width = widthSpecSize
                height = heightSpecSize
                
                if (scaleMode == 1) { // Crop (PanScan)
                    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
                    val containerRatio = width.toFloat() / height.toFloat()
                    
                    if (videoRatio > containerRatio) {
                        // Video is wider than container -> match height, crop width
                        width = (height * videoRatio).toInt()
                    } else {
                        // Video is taller than container -> match width, crop height
                        height = (width / videoRatio).toInt()
                    }
                } 
                // If scaleMode == 2 (Stretch), just keep width and height exact.
            }
        }
        
        setMeasuredDimension(width, height)
    }
    
    // We need to capture the video dimensions when prepared
    fun setVideoSize(w: Int, h: Int) {
        videoWidth = w
        videoHeight = h
        requestLayout()
    }
}
