package com.hezb.hplayer.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.hezb.hplayer.R
import com.hezb.hplayer.databinding.ViewItemVideoListBinding
import com.hezb.clingupnp.model.MediaInfo

class VideoListAdapter(private val mediaList: MutableList<MediaInfo>) :
    RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {

    var onItemClickListener: OnItemClickListener? = null

    override fun getItemCount(): Int {
        return mediaList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_item_video_list, parent, false)
        return VideoViewHolder(ViewItemVideoListBinding.bind(itemView))
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bindModel(mediaList[position])
        holder.binding.root.setOnClickListener {
            onItemClickListener?.onItemClick(mediaList[position])
        }
    }

    class VideoViewHolder(val binding: ViewItemVideoListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindModel(model: MediaInfo) {
            binding.apply {
                video = model
                executePendingBindings()

                Glide.with(ivMediaImage)
                    .applyDefaultRequestOptions(RequestOptions().placeholder(R.drawable.xml_vector_image_default))
                    .asBitmap()
                    .load(model.path)
                    .into(ivMediaImage)
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(video: MediaInfo)
    }

}