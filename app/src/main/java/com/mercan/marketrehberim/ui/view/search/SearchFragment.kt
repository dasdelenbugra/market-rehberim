package com.mercan.marketrehberim.ui.view.search

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.mercan.marketrehberim.data.model.Item
import com.mercan.marketrehberim.databinding.FragmentSearchBinding
import com.mercan.marketrehberim.ui.adapter.ItemAdapter
import com.mercan.marketrehberim.ui.state.UIItemState
import com.mercan.marketrehberim.ui.viewmodel.SearchViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(), TextView.OnEditorActionListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var etSearch: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView

    private val searchViewModel: SearchViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
        observeData()
        fetchData()
    }

    private fun bindViews() {
        etSearch = binding.etSearch
        progressBar = binding.progressBar
        recyclerView = binding.recyclerView
    }

    private fun observeData() {
        lifecycleScope.launch {
            searchViewModel.searchResults.collect { state ->
                when (state) {
                    is UIItemState.Idle -> idleLogic()
                    is UIItemState.Loading -> loadingLogic()
                    is UIItemState.Success -> successLogic(state.items)
                    is UIItemState.Error -> errorLogic(state.message)
                }
            }
        }
    }

    private fun fetchData() {
        etSearch.setOnEditorActionListener(this)
    }

    private fun idleLogic() {
        etSearch.isEnabled = true
        etSearch.requestFocus()
    }

    private fun loadingLogic() {
        etSearch.isEnabled = false
        etSearch.clearFocus()

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun successLogic(items: List<Item>) {
        etSearch.isEnabled = true

        val adapter = ItemAdapter(items)
        recyclerView.adapter = adapter

        progressBar.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun errorLogic(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
            val itemName = etSearch.text.toString()
            searchViewModel.fetchItems(itemName)
            return true
        }
        return false
    }
}