package com.marketrehberim.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.marketrehberim.R
import com.marketrehberim.databinding.ItemSkeletonBinding

/**
 * Arama yüklenirken gösterilen sahte satırlar. Sabit sayıda öğe üretir; aşağı
 * indikçe solarak listenin devam ettiğini ima eder.
 */
class SkeletonAdapter(
    private val itemCount: Int = DEFAULT_COUNT,
) : RecyclerView.Adapter<SkeletonAdapter.VH>() {

    class VH(val binding: ItemSkeletonBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSkeletonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val root = holder.binding.root
        // Alt sıralar daha soluk: liste ekranın dışında sürüyormuş hissi.
        root.alpha = FADE_STEPS.getOrElse(position) { FADE_STEPS.last() }
        root.startAnimation(
            AnimationUtils.loadAnimation(root.context, R.anim.skeleton_pulse)
        )
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.binding.root.clearAnimation()
    }

    override fun getItemCount(): Int = itemCount

    private companion object {
        const val DEFAULT_COUNT = 5
        val FADE_STEPS = listOf(1f, 1f, 1f, 0.62f, 0.32f)
    }
}
