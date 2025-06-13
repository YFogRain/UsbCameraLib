package com.rain.uvc.demo.camera

import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.os.Parcelable
import android.util.Log
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.rain.uvc.demo.BR
import com.rain.uvc.demo.R
import com.rain.uvc.demo.base.adapter.BaseRecAdapter
import com.rain.uvc.demo.databinding.ItemHomeSelectGroupBinding
import com.rain.uvc.demo.utils.getBind
import com.rain.uvc.demo.utils.singleClick
import kotlinx.parcelize.Parcelize
import java.util.Locale

/**
 * @author yuan
 * @createTime: 2025/2/19
 * @des
 */
class HomeSelectAdapter() : RecyclerView.Adapter<HomeSelectGroupHolder>() {
	
	private var mItemChildClickListener: ((groupPosition: Int, childPosition: Int) -> Unit)? = null
	private var cameraDevices: MutableList<HomeSelectMode>? = null
	
	fun clear() {
		val count = cameraDevices?.size ?: return
		cameraDevices = null
		notifyItemRangeRemoved(0, count)
	}
	
	fun setData(cameraDevices: MutableList<HomeSelectMode>) {
		this.cameraDevices = cameraDevices
		notifyItemRangeInserted(0, cameraDevices.size)
	}
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeSelectGroupHolder {
		val homeSelectGroupHolder = HomeSelectGroupHolder(parent.getBind(R.layout.item_home_select_group))
		homeSelectGroupHolder.mBinding.tvDeviceTitle.singleClick {
			//打开关闭
			val adapterPosition = homeSelectGroupHolder.adapterPosition
			if (adapterPosition == RecyclerView.NO_POSITION) return@singleClick
			val homeSelectMode = cameraDevices?.getOrNull(adapterPosition) ?: return@singleClick
			val openArrow = homeSelectMode.isOpenArrow
			homeSelectMode.isOpenArrow = !openArrow
			notifyItemChanged(adapterPosition, "updateArrow")
		}
		homeSelectGroupHolder.setChildItemListener {
			val adapterPosition = homeSelectGroupHolder.adapterPosition
			if (adapterPosition == RecyclerView.NO_POSITION) return@setChildItemListener
			mItemChildClickListener?.invoke(adapterPosition, it)
		}
		return homeSelectGroupHolder
	}
	
	override fun getItemCount(): Int = cameraDevices?.size ?: 0
	
	override fun onBindViewHolder(holder: HomeSelectGroupHolder, position: Int) {
		val homeSelectMode = cameraDevices?.getOrNull(position) ?: return
		holder.setData(homeSelectMode)
	}
	
	override fun onBindViewHolder(holder: HomeSelectGroupHolder, position: Int, payloads: MutableList<Any>) {
		if (payloads.isEmpty()) {
			onBindViewHolder(holder, position)
			return
		}
		val homeSelectMode = cameraDevices?.getOrNull(position) ?: return
		holder.setData(homeSelectMode)
	}
	
	fun setListener(itemChildClickListener: ((groupPosition: Int, childPosition: Int) -> Unit)?) {
		this.mItemChildClickListener = itemChildClickListener
	}
	
	fun getItem(groupPosition: Int, childPosition: Int): HomeDeviceMode? {
		return cameraDevices?.getOrNull(groupPosition)?.devices?.getOrNull(childPosition)
	}
}

class HomeDeviceAdapter : BaseRecAdapter<HomeDeviceMode>() {
	override fun getLayoutResId(viewType: Int): Int = R.layout.item_device_view
	override fun getVariableId(viewType: Int): Int = BR.homeDeviceMode
}

class HomeSelectGroupHolder(val mBinding: ItemHomeSelectGroupBinding) : ViewHolder(mBinding.root) {
	
	private val adapter = HomeDeviceAdapter()
	
	init {
		mBinding.recDevices.layoutManager = if (mBinding.root.context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			//横屏时，一行展示五个
			GridLayoutManager(mBinding.root.context, 5)
		} else LinearLayoutManager(mBinding.root.context)
		mBinding.recDevices.adapter = adapter
	}
	
	fun setData(mode: HomeSelectMode) {
		mBinding.setVariable(BR.homeSelectMode, mode)
		adapter.setData(mode.devices)
		mBinding.executePendingBindings()
	}
	
	fun setChildItemListener(block: (position: Int) -> Unit) {
		adapter.setOnItemClickListener {
			block(it)
		}
	}
}

data class HomeSelectMode(val name: String, val devices: MutableList<HomeDeviceMode>, var isOpenArrow: Boolean = false)

data class HomeDeviceMode(val deviceName: String, val deviceType: CameraDeviceMode)

@Parcelize
sealed class CameraDeviceMode : Parcelable {
	data class Native1(val cameraId: Int) : CameraDeviceMode()
	data class Native2(val cameraId: String) : CameraDeviceMode()
	data class USB(val device: UsbDevice) : CameraDeviceMode()
	data class V4L2(val videoPath: String) : CameraDeviceMode()
	data class LOCALE(val locale: Locale) : CameraDeviceMode()
	data object OTHER : CameraDeviceMode()
}