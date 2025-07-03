import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.floatingwebview.R
import com.example.floatingwebview.home.VisitedPage


class VisitedPageAdapter1(
    private val onItemClick: (VisitedPage) -> Unit,
    private val onDeleteClick: (VisitedPage) -> Unit
) : ListAdapter<VisitedPage, VisitedPageAdapter1.ViewHolder>(VisitedPageDiffCallback()) {


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val favicon: ImageView = itemView.findViewById(R.id.faviconImage)
        private val titleText: TextView = itemView.findViewById(R.id.pageTitle)
        private val urlText: TextView = itemView.findViewById(R.id.pageUrl)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(page: VisitedPage) {
            titleText.text = page.title.ifBlank { "Untitled" }
            urlText.text = page.url

            if (page.faviconUrl.isNotBlank()) {
                Glide.with(itemView.context).load(page.faviconUrl).into(favicon)
            } else {
                favicon.setImageResource(R.drawable.ic_reload)
            }

            itemView.setOnClickListener { onItemClick(page) }
            deleteBtn.setOnClickListener { onDeleteClick(page) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_visited_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class VisitedPageDiffCallback : DiffUtil.ItemCallback<VisitedPage>() {
    override fun areItemsTheSame(oldItem: VisitedPage, newItem: VisitedPage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: VisitedPage, newItem: VisitedPage): Boolean {
        return oldItem == newItem
    }
}
