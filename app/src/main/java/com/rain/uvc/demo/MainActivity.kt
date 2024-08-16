package com.rain.uvc.demo

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rain.uvc.demo.base.activity.BaseDataBindActivity
import com.rain.uvc.demo.base.adapter.BaseRecAdapter
import com.rain.uvc.demo.base.viewModel.BaseViewModel
import com.rain.uvc.demo.camera.CameraActivity
import com.rain.uvc.demo.databinding.ActivityMainBinding

class MainActivity : BaseDataBindActivity<ActivityMainBinding>() {
	override val mViewModel: BaseViewModel? = null
	override fun initLayoutResId() = R.layout.activity_main
	private val permissionCall = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
	
	}
	private val testList = mutableListOf(
		"开启预览",
	)
	private val adapter by lazy {
		object : BaseRecAdapter<String>() {
			override fun getLayoutResId(viewType: Int) = R.layout.item_test_click_view
			override fun getVariableId(viewType: Int) = BR.itemTestModel
		}
	}
	
	override fun initView() {
		initRec()
		//申请权限
		if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionCall.launch(android.Manifest.permission.CAMERA)
		}
	}
	
	private fun initRec() {
		mBinding.recTestClick.layoutManager = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			GridLayoutManager(this, 4)
		} else LinearLayoutManager(this)
		adapter.setOnItemClickListener {
			itemClick(adapter.getItemData(it))
		}
		mBinding.recTestClick.adapter = adapter
		adapter.setData(testList)
	}
	
	private fun itemClick(str: String?) {
		when (str) {
			"开启预览" -> {
				if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
					permissionCall.launch(android.Manifest.permission.CAMERA)
				} else startActivity(Intent(this, CameraActivity::class.java))
			}
			else -> {
				Toast.makeText(this, "未知操作", Toast.LENGTH_SHORT).show()
			}
		}
	}
}