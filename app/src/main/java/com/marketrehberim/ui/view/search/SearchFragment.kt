package com.marketrehberim.ui.view.search

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.FragmentSearchBinding
import com.marketrehberim.ui.adapter.ItemAdapter
import com.marketrehberim.ui.adapter.SkeletonAdapter
import com.marketrehberim.ui.state.UIItemState
import com.marketrehberim.ui.viewmodel.SearchViewModel
import com.marketrehberim.ui.viewmodel.SortOrder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(), android.widget.TextView.OnEditorActionListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val itemAdapter = ItemAdapter(onClick = ::openDetail)
    private val skeletonAdapter = SkeletonAdapter()
    private val searchViewModel: SearchViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.adapter = itemAdapter
        binding.etSearch.setOnEditorActionListener(this)
        binding.btnSort.setOnClickListener { showSortMenu() }
        observeData()
        observeMarkets()
        handleIncomingQuery(savedInstanceState)
    }

    /**
     * Anasayfadaki kamera/son-arama akışından gelen sorguyla (varsa) otomatik arama
     * başlatır. Argüman tek kullanımlıktır: tüketildikten sonra silinir, yoksa
     * kullanıcı Ara sekmesine her dönüşünde eski sorgu kendiliğinden yeniden aranır.
     */
    private fun handleIncomingQuery(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val query = arguments?.getString("query")
        arguments?.remove("query")
        if (!query.isNullOrBlank()) {
            binding.etSearch.setText(query)
            searchViewModel.fetchItems(query)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
    }

    private fun observeMarkets() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.markets.collect(::buildMarketChips)
            }
        }
    }

    private fun buildMarketChips(markets: List<String>) {
        val group = binding.marketChips
        group.removeAllViews()
        if (markets.isEmpty()) return

        addChip(group, getString(R.string.all_markets), checked = true, market = null) {
            searchViewModel.setMarketFilter(null)
        }
        markets.forEach { market ->
            addChip(group, market, checked = false, market = market) {
                searchViewModel.setMarketFilter(market)
            }
        }
    }

    /**
     * @param market doluysa çipe marka rengi noktası eklenir; aynı renk sonuç
     *   satırlarında tekrar ettiği için göz eşleştirmeyi bir kez öğrenir.
     */
    private inline fun addChip(
        group: com.google.android.material.chip.ChipGroup,
        label: String,
        checked: Boolean,
        market: String?,
        crossinline onSelected: () -> Unit,
    ) {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = checked
            if (market != null) {
                chipIcon = androidx.core.content.ContextCompat.getDrawable(
                    context, R.drawable.shape_market_dot
                )
                chipIconTint = android.content.res.ColorStateList.valueOf(
                    com.marketrehberim.ui.theme.MarketPalette.colorFor(context, market)
                )
                chipIconSize = resources.getDimension(R.dimen.chip_dot_size)
            }
            setOnClickListener { if (isChecked) onSelected() }
        }
        group.addView(chip)
    }

    private fun showSortMenu() {
        PopupMenu(requireContext(), binding.btnSort).apply {
            SortOrder.entries.forEach { order ->
                menu.add(GROUP_SORT, order.ordinal, order.ordinal, order.label)
            }
            // Hangi sıralamanın açık olduğu menüyü açmadan görünmüyordu.
            menu.setGroupCheckable(GROUP_SORT, true, true)
            menu.findItem(searchViewModel.currentSort.ordinal)?.isChecked = true
            setOnMenuItemClickListener { menuItem ->
                searchViewModel.setSortOrder(SortOrder.entries[menuItem.itemId])
                true
            }
        }.show()
    }

    private fun openDetail(item: Item) {
        // Seçim = "bu fiyata razı oldum". Kazanç burada kaydedilir, anasayfadaki
        // tasarruf şeridi bunu toplar.
        searchViewModel.recordSelection(item)

        val navOptions = androidx.navigation.navOptions {
            anim {
                enter = R.anim.nav_enter
                exit = R.anim.nav_exit
                popEnter = R.anim.nav_pop_enter
                popExit = R.anim.nav_pop_exit
            }
        }
        findNavController().navigate(
            R.id.productDetailFragment,
            bundleOf("item" to item),
            navOptions,
        )
    }

    private fun idleLogic() {
        binding.etSearch.isEnabled = true
        binding.etSearch.requestFocus()
        binding.recyclerView.visibility = GONE
        binding.emptyView.visibility = GONE
        binding.filterRow.visibility = GONE
        binding.tvResultMeta.visibility = GONE
    }

    /**
     * Yüklenirken listenin yerini iskelet kartlar tutar. Boş ekran + dönen çark
     * yerine gelecek düzen görünür; scraping saniyeler sürdüğü için ne beklendiği
     * de yazılır.
     */
    private fun loadingLogic() {
        binding.etSearch.isEnabled = false
        binding.etSearch.clearFocus()

        if (binding.recyclerView.adapter !== skeletonAdapter) {
            binding.recyclerView.adapter = skeletonAdapter
        }
        binding.recyclerView.visibility = VISIBLE
        binding.emptyView.visibility = GONE
        binding.filterRow.visibility = GONE

        binding.tvResultMeta.text =
            getString(R.string.search_status_loading, searchViewModel.cityLabel)
        binding.tvResultMeta.visibility = VISIBLE
    }

    private fun successLogic(items: List<Item>) {
        binding.etSearch.isEnabled = true

        if (binding.recyclerView.adapter !== itemAdapter) {
            binding.recyclerView.adapter = itemAdapter
        }
        itemAdapter.submitList(items) {
            if (items.isNotEmpty()) binding.recyclerView.scheduleLayoutAnimation()
        }

        val hasResults = items.isNotEmpty()
        binding.recyclerView.visibility = if (hasResults) VISIBLE else GONE
        binding.emptyView.visibility = if (hasResults) GONE else VISIBLE
        binding.filterRow.visibility =
            if (searchViewModel.markets.value.isNotEmpty()) VISIBLE else GONE

        bindResultMeta(items)
    }

    /** "Tokat · 9 sonuç · en ucuz 38,50 ₺" — sayfa üstünde tek satırlık özet. */
    private fun bindResultMeta(items: List<Item>) {
        val cheapest = items.map { it.priceValue }.filter { it != Double.MAX_VALUE }.minOrNull()
        if (items.isEmpty() || cheapest == null) {
            binding.tvResultMeta.visibility = GONE
            return
        }
        binding.tvResultMeta.text = getString(
            R.string.search_meta,
            searchViewModel.cityLabel,
            items.size,
            cheapest,
        )
        binding.tvResultMeta.visibility = VISIBLE
    }

    private fun errorLogic(message: String) {
        binding.etSearch.isEnabled = true
        binding.recyclerView.visibility = GONE
        binding.tvResultMeta.visibility = GONE
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onEditorAction(v: android.widget.TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_SEARCH ||
            actionId == EditorInfo.IME_ACTION_DONE ||
            actionId == EditorInfo.IME_ACTION_NEXT
        ) {
            searchViewModel.fetchItems(binding.etSearch.text.toString())
            return true
        }
        return false
    }

    private companion object {
        const val GROUP_SORT = 1
    }
}
