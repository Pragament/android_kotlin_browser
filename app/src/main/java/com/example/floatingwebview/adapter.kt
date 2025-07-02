import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.floatingwebview.R
import com.example.floatingwebview.home.VisitedPage

class VisitedPageAdapter(
    private val onClick: (VisitedPage) -> Unit
) : ListAdapter<VisitedPage, VisitedPageAdapter.PageViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_main1, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = getItem(position)
        holder.bind(page)
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.faviconImageView)
        private val title: TextView = itemView.findViewById(R.id.titleTextView)

        fun bind(page: VisitedPage) {
            val displayTitle = page.title.ifBlank { Uri.parse(page.url).host ?: page.url }
            title.text = displayTitle

            val faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain_url=${page.url}"
            Glide.with(itemView)
                .load(faviconUrl)
                .placeholder(R.drawable.ic_reload)
                .into(icon)

            itemView.setOnClickListener { onClick(page) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<VisitedPage>() {
        override fun areItemsTheSame(oldItem: VisitedPage, newItem: VisitedPage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: VisitedPage, newItem: VisitedPage) = oldItem == newItem
    }
}
