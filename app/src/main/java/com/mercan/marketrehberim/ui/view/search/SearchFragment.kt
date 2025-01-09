package com.mercan.marketrehberim.ui.view.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
class SearchFragment : Fragment(), View.OnFocusChangeListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var etSearch: TextInputEditText

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
    }

    private fun observeData() {
        lifecycleScope.launch {
            searchViewModel.searchResults.collect { state ->
                when (state) {
                    is UIItemState.Loading -> loadingLogic()
                    is UIItemState.Success -> successLogic(state.items)
                    is UIItemState.Error -> errorLogic(state.message)
                }
            }
        }
    }

    private fun fetchData() {
        etSearch.onFocusChangeListener = this
    }

    private fun loadingLogic() {

    }

    private fun successLogic(items: List<Item>) {
        val adapter = ItemAdapter(items)
        binding.recyclerView.adapter = adapter
    }

    private fun errorLogic(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        if (hasFocus) return
        val itemName = etSearch.text.toString()
        searchViewModel.fetchItems(itemName)
    }
}