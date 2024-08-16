package com.rain.uvc.demo.base.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.rain.uvc.demo.utils.getBind
import com.rain.uvc.demo.utils.singleClick

/**
 * adapter父类
 */
abstract class BaseRecAdapter<T> : RecyclerView.Adapter<BaseRecHolder<T, *>>() {
    private var adapterList: MutableList<T>? = null
    private var recycler: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recycler = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recycler = null
    }

    /**
     * 初始化设置数据
     */
    @SuppressLint("NotifyDataSetChanged")
    open fun setData(list: MutableList<T>?) {
        this.adapterList = list
        notifyDataSetChanged()
    }

    /**
     * 获取集合对象
     */
    fun getLists() = adapterList

    /**
     * 添加数据
     */
    fun addItemData(data: MutableList<T>?) {
        if (recycler?.isComputingLayout == true) return
        val position = adapterList?.size ?: 0
        addItemData(position, data)
    }

    fun addItemData(position: Int, data: MutableList<T>?) {
        if (data.isNullOrEmpty() || recycler?.isComputingLayout == true) return
        if (adapterList == null) adapterList = mutableListOf()
        adapterList?.addAll(position, data)
        notifyItemRangeInserted(position, data.size)
    }

    fun addItemData(data: T?) {
        if (data == null || recycler?.isComputingLayout == true) return
        val position = adapterList?.size ?: 0
        addItemData(position, data)
    }

    fun addItemData(position: Int, data: T?) {
        if (data == null || recycler?.isComputingLayout == true) return
        if (adapterList == null) adapterList = mutableListOf()
        adapterList?.add(position, data)
        notifyItemInserted(position)
    }

    /**
     * 删除数据
     */
    fun removeItemData(position: Int) {
        if (recycler?.isComputingLayout == true) return
        if (adapterList.isNullOrEmpty() || position < 0 || position >= (adapterList?.size ?: 0)) return
        adapterList?.removeAt(position)
        notifyItemRemoved(position)
    }

    fun removeItemData(data: T?) {
        if (recycler?.isComputingLayout == true) return
        if (adapterList.isNullOrEmpty() || data == null) return
        val position = adapterList?.indexOf(data) ?: 0
        removeItemData(position)
    }

    fun getItemData(position: Int) = adapterList?.getOrNull(position)

    /**
     * 更新某条数据
     */
    fun updateItemData(data: T?) {
        if (data == null || adapterList.isNullOrEmpty()) return
        val indexOf = adapterList?.indexOf(data) ?: -1
        if (indexOf >= 0) notifyItemChanged(indexOf)
    }
    /**
     * item点击
     */
    private var itemClickListener: ((Int) -> Unit)? = null

    fun setOnItemClickListener(itemClickListener: ((Int) -> Unit)) {
        this.itemClickListener = itemClickListener
    }

    /**
     * item长按
     */
    private var onItemLongClickListener: ((Int) -> Unit)? = null
    fun setOnItemLongClickListener(onItemLongClickListener: ((Int) -> Unit)) {
        this.onItemLongClickListener = onItemLongClickListener
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseRecHolder<T, *> {
        val holder = createHolder(parent, viewType)
        holder.itemView.singleClick {
            val position = holder.adapterPosition
            if (position == RecyclerView.NO_POSITION) return@singleClick
            itemClickListener?.invoke(position)
        }
        holder.itemView.setOnLongClickListener {
            val position = holder.adapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            onItemLongClickListener?.invoke(position)
            return@setOnLongClickListener true
        }
        return holder
    }

    override fun onBindViewHolder(holder: BaseRecHolder<T, *>, position: Int) {
        Log.d("viewHolderTag", "onBindViewHolder:$holder")
        val t = adapterList?.getOrNull(position)
        if (t != null) holder.setData(t, position)
        collectHolder(holder, position)
    }

    override fun onBindViewHolder(holder: BaseRecHolder<T, *>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        val t = adapterList?.getOrNull(position)
        if (t != null) holder.updateData(t, position, payloads[0])
        collectHolder(holder, position, payloads[0])
    }

    /**
     * 绑定数据
     */
    open fun collectHolder(holder: BaseRecHolder<T, *>, position: Int) {}

    /**
     * 绑定数据，指定更新某些值
     */
    open fun collectHolder(holder: BaseRecHolder<T, *>, position: Int, payload: Any) {}

    override fun getItemCount() = adapterList?.size ?: 0

    /**
     * 获取布局id
     */
    @LayoutRes
    abstract fun getLayoutResId(viewType: Int): Int

    /**
     * 获取绑定的variableId，为-1时表示不做dataBind联动
     */
    abstract fun getVariableId(viewType: Int): Int //綁定的id 為-1時表示不綁定

    private fun createHolder(parent: ViewGroup, viewType: Int): BaseRecHolder<T, *> {
        return createHolder(parent, viewType, getLayoutResId(viewType), getVariableId(viewType))
    }

    /**
     * 创建viewHolder。默认返回baseRecHolder
     */
    open fun createHolder(parent: ViewGroup, viewType: Int, @LayoutRes layoutResId: Int, variableId: Int): BaseRecHolder<T, *> {
        return BaseRecHolder(parent.getBind(layoutResId), variableId)
    }
}

open class BaseRecHolder<T, DB : ViewDataBinding>(val dataBind: DB, val variableId: Int = -1) : RecyclerView.ViewHolder(dataBind.root) {
    protected val mContext: Context = itemView.context

    /**
     * holder刷新当前item的所有数据
     * 由adapter.onBindViewHolder(RecyclerView.ViewHolder holder, int position)回調
     */
    open fun setData(model: T, position: Int) {
        initBindModel(model)
    }

    /**
     * holder局部刷新，只更新改变了的数据，payloads为刷新设置的标记
     * 由adapter.onBindViewHolder(RecyclerView.ViewHolder holder, int position, List<Object> payloads)回調
     */
    open fun updateData(model: T, position: Int, payloads: Any) {
        initBindModel(model)
    }

    /**
     * dataBind更新数据方法
     */
    private fun initBindModel(model: T) {
        if (variableId != -1) {
            dataBind.setVariable(variableId, model)
            dataBind.executePendingBindings()
        }
    }
}
