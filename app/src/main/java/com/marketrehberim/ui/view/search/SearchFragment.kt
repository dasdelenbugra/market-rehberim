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
import com.marketrehberim.R
import com.marketrehberim.data.model.Item
import com.marketrehberim.databinding.FragmentSearchBinding
import com.marketrehberim.data.remote.dto.ProductGroup
import com.marketrehberim.ui.adapter.ProductGroupAdapter
import com.marketrehberim.ui.adapter.SkeletonAdapter
import com.marketrehberim.ui.state.SearchError
import com.marketrehberim.ui.state.SearchRow
import com.marketrehberim.ui.state.UIItemState
import com.marketrehberim.ui.viewmodel.SearchViewModel
import com.marketrehberim.ui.viewmodel.SortOrder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(), android.widget.TextView.OnEditorActionListener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val groupAdapter = ProductGroupAdapter(
        onProductClick = ::openGroup,
        onRelatedToggle = { searchViewModel.toggleRelated() },
    )
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
        binding.recyclerView.adapter = groupAdapter
        binding.etSearch.setOnEditorActionListener(this)
        binding.btnSort.setOnClickListener { showSortMenu() }
        binding.btnRetry.setOnClickListener { searchViewModel.retry() }
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
                        is UIItemState.Success -> successLogic(state.rows)
                        is UIItemState.Error -> errorLogic(state.kind)
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

    /**
     * Gruba dokunuldu: detaya grubun **en ucuz** teklifi taşınır. Detay ekranı
     * zaten aynı adı yeniden arayıp marketleri yan yana koyuyor, yani grubun
     * tamamı orada görünür.
     */
    private fun openGroup(group: ProductGroup) {
        openDetail(group.cheapestOffer ?: return)
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
        binding.tvUpdated.visibility = GONE
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
        binding.tvUpdated.visibility = GONE
    }

    private fun successLogic(rows: List<SearchRow>) {
        binding.etSearch.isEnabled = true

        if (binding.recyclerView.adapter !== groupAdapter) {
            binding.recyclerView.adapter = groupAdapter
        }
        groupAdapter.submitList(rows) {
            if (rows.isNotEmpty()) binding.recyclerView.scheduleLayoutAnimation()
        }

        val hasResults = rows.isNotEmpty()
        resetEmptyView()
        binding.recyclerView.visibility = if (hasResults) VISIBLE else GONE
        binding.emptyView.visibility = if (hasResults) GONE else VISIBLE
        binding.filterRow.visibility =
            if (searchViewModel.markets.value.isNotEmpty()) VISIBLE else GONE

        bindResultMeta()
        bindUpdated(hasResults)
        bindSuggestion(hasResults)
    }

    /**
     * "Bunu mu demek istediniz: çikolata" — boş sonuçta tek dokunuşla düzeltme.
     *
     * Sunucu öneriyi yalnız yeterince emin olduğunda gönderiyor; markette
     * gerçekten bulunmayan bir ürün ("pırasa") için bilerek göndermiyor. Bu
     * yüzden düğme çoğu boş sonuçta görünmez ve görünmemesi doğrudur — yanlış
     * bir öneri, öneri vermemekten kötüdür.
     */
    private fun bindSuggestion(hasResults: Boolean) {
        val suggestion = searchViewModel.suggestion
        if (hasResults || suggestion.isNullOrBlank()) {
            binding.btnSuggestion.visibility = GONE
            return
        }
        binding.btnSuggestion.text = getString(R.string.search_did_you_mean, suggestion)
        binding.btnSuggestion.setOnClickListener {
            // Arama kutusu da güncellenir: kullanıcı ne arandığını görsün ve
            // üstünde değişiklik yapabilsin.
            binding.etSearch.setText(suggestion)
            searchViewModel.fetchItems(suggestion)
        }
        binding.btnSuggestion.visibility = VISIBLE
    }

    /**
     * "Son güncelleme: 25 Tem 03:10" — fiyatlar günlük tazelendiği için kullanıcı
     * verinin ne kadar taze olduğunu görsün. Kaynak zaman bildirmezse (mock, ağ
     * hatası) ya da sonuç yoksa gizli kalır; uydurma bir zaman göstermeyiz.
     */
    private fun bindUpdated(hasResults: Boolean) {
        val iso = searchViewModel.lastUpdatedIso
        if (!hasResults || iso.isNullOrBlank()) {
            binding.tvUpdated.visibility = GONE
            return
        }
        binding.tvUpdated.text = getString(R.string.search_updated, formatUpdated(iso))
        binding.tvUpdated.visibility = VISIBLE
    }

    /** ISO 8601 ("2026-07-25T03:10:00") → "25 Tem 03:10". Ayrıştırılamazsa ham metin. */
    private fun formatUpdated(iso: String): String = runCatching {
        val tr = java.util.Locale("tr", "TR")
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", tr).parse(iso)
        java.text.SimpleDateFormat("d MMM HH:mm", tr).format(parsed!!)
    }.getOrDefault(iso)

    /**
     * "Tokat · 24 ürün · en ucuz 8,00 ₺" — sayfa üstünde tek satırlık özet.
     * Artık "sonuç" değil "ürün" sayılıyor: aynı muzun beş marketi tek üründür.
     */
    private fun bindResultMeta() {
        val count = searchViewModel.visibleGroupCount
        val cheapest = searchViewModel.cheapestVisiblePrice
        if (count == 0 || cheapest == null) {
            binding.tvResultMeta.visibility = GONE
            return
        }
        binding.tvResultMeta.text = getString(
            R.string.search_meta_groups,
            searchViewModel.cityLabel,
            count,
            cheapest,
        )
        binding.tvResultMeta.visibility = VISIBLE
    }

    /**
     * Hata, "sonuç bulunamadı"dan ayrı görünmeli: aynı boş ekranı gösterirsek
     * kullanıcı internetinin kapalı olduğunu değil, ürünün olmadığını sanıyor.
     * Aynı yerleşimi kullanır ama ikon, metin ve "Tekrar dene" butonu değişir.
     */
    private fun errorLogic(kind: SearchError) {
        binding.etSearch.isEnabled = true
        binding.recyclerView.visibility = GONE
        binding.tvResultMeta.visibility = GONE
        binding.tvUpdated.visibility = GONE
        binding.filterRow.visibility = GONE

        binding.tvEmpty.setText(
            when (kind) {
                SearchError.NETWORK -> R.string.error_network
                SearchError.SERVER -> R.string.error_server
                SearchError.UNKNOWN -> R.string.error_unknown
            }
        )
        binding.ivEmptyIcon.setImageResource(R.drawable.ic_cloud_off)
        // Ağ hatasında öneri anlamsız: sorgu kaynağa hiç ulaşmadı.
        binding.btnSuggestion.visibility = GONE
        binding.btnRetry.visibility = VISIBLE
        binding.emptyView.visibility = VISIBLE
    }

    /** Hata görünümünden normal "sonuç yok" görünümüne döner. */
    private fun resetEmptyView() {
        binding.tvEmpty.setText(R.string.empty_results)
        binding.ivEmptyIcon.setImageResource(R.drawable.ic_search_outlined)
        binding.btnRetry.visibility = GONE
        binding.btnSuggestion.visibility = GONE
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
