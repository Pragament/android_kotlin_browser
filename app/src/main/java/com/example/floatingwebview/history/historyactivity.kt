package com.example.floatingwebview.history

import VisitedPageAdapter
import VisitedPageAdapter1
import androidx.appcompat.app.AppCompatActivity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels

import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.floatingwebview.R
import com.example.floatingwebview.YourApplication
import com.example.floatingwebview.home.HomeViewModel
import com.example.floatingwebview.home.HomeViewModelFactory

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: VisitedPageAdapter1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)



        val toolbar = findViewById<Toolbar>(R.id.historyToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "History"
        toolbar.setNavigationOnClickListener { finish() }

        val dao = (application as YourApplication).database.visitedPageDao()
        viewModel = ViewModelProvider(this, HomeViewModelFactory(dao))[HomeViewModel::class.java]

        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        val clearButton = findViewById<Button>(R.id.clearAllButton)

        adapter = VisitedPageAdapter1(
            onItemClick = { /* Optional: Handle item click */ },
            onDeleteClick = { viewModel.deletePage(it.id)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.recentPages.collectLatest {
                adapter.submitList(it)
                clearButton.visibility = if (it.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        clearButton.setOnClickListener {
            viewModel.clearHistory()
        }
    }
}
