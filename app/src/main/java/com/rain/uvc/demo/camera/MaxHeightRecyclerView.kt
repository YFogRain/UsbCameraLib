package com.rain.uvc.demo.camera

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.withStyledAttributes
import com.rain.uvc.demo.R

/**
 * 最大高度的recyclerView
 */
class MaxHeightRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : RecyclerView(
	context, attrs, defStyle
) {
	
	init {
		initialize(attrs)
	}
	
	private fun initialize(attrs: AttributeSet?) {
		if (attrs == null) return
		context.withStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView) {
			mMaxHeight = getLayoutDimension(R.styleable.MaxHeightRecyclerView_maxHeight, mMaxHeight)
		}
	}
	
	private var mMaxHeight = 0
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		var heightSpec = heightMeasureSpec
		if (mMaxHeight > 0) {
			heightSpec = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST)
		}
		super.onMeasure(widthMeasureSpec, heightSpec)
	}
}