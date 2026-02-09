package com.rain.uvc.demo.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rain.uvc.demo.provider.OverallContext
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * @author yuan
 * @createTime: 2025/2/28
 * @des
 */
object WorkHelper {
	
	@JvmStatic
	fun startWork() {
		//设置执行任务
		val request = PeriodicWorkRequest.Builder(TestWork::class.java, 15, TimeUnit.MINUTES).setInputData(Data.Builder().putString("testName", "111").build()) //设置传递的参数
			.setInitialDelay(0, TimeUnit.MILLISECONDS) //设置延迟时间
			.build()
		val op = WorkManager.getInstance(OverallContext.baseContext).enqueueUniquePeriodicWork("test_detay_work", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
		Log.d("WorkHelper", "op:${op.state}")
		Log.d("WorkHelper", "op:${op.result}")
	}
}

class TestWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		Log.d("WorkHelper", "name:${inputData.getString("testName")}")
		delay(3000)
		return Result.success()
		
	}
	
}