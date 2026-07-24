package com.marketrehberim.ui.view.basket

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.marketrehberim.R
import com.marketrehberim.data.remote.dto.BasketResponse
import com.marketrehberim.databinding.FragmentBasketBinding
import com.marketrehberim.ui.adapter.BasketSplitAdapter
import com.marketrehberim.ui.adapter.MarketBasketAdapter
import com.marketrehberim.ui.viewmodel.BasketViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BasketFragment : Fragment() {
    private var _binding: FragmentBasketBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BasketViewModel by viewModels()
    private val marketAdapter = MarketBasketAdapter()
    private val splitAdapter = BasketSplitAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBasketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.marketList.adapter = marketAdapter
        binding.splitList.adapter = splitAdapter

        binding.btnAdd.setOnClickListener { addFromInput() }
        binding.etItem.setOnEditorActionListener { _, _, _ -> addFromInput(); true }
        binding.btnOptimize.setOnClickListener { viewModel.optimize() }

        observeItems()
        observeLoading()
        observeResult()
    }

    private fun addFromInput() {
        val text = binding.etItem.text?.toString().orEmpty()
        if (text.isBlank()) return
        viewModel.addItem(text)
        binding.etItem.text?.clear()
    }

    private fun observeItems() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    binding.basketChips.removeAllViews()
                    items.forEach { name ->
                        val chip = Chip(requireContext()).apply {
                            text = name
                            isCloseIconVisible = true
                            setOnCloseIconClickListener { viewModel.removeItem(name) }
                        }
                        binding.basketChips.addView(chip)
                    }
                    binding.btnOptimize.isEnabled = items.isNotEmpty()
                    updateEmptyState()
                }
            }
        }
    }

    private fun observeLoading() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observeResult() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.result.collect(::renderResult)
            }
        }
    }

    /** Liste boş ve elde sonuç yokken ekran sadece boş bir forma bakmasın. */
    private fun updateEmptyState() {
        val showEmpty = viewModel.items.value.isEmpty() && viewModel.result.value == null
        binding.emptyCard.visibility = if (showEmpty) View.VISIBLE else View.GONE
    }

    private fun renderResult(result: BasketResponse?) {
        if (result == null || result.byMarket.isEmpty()) {
            binding.resultContainer.visibility = View.GONE
            updateEmptyState()
            return
        }
        binding.resultContainer.visibility = View.VISIBLE
        binding.emptyCard.visibility = View.GONE

        binding.optimalTotal.text = "%.2f ₺".format(result.optimalSplit.total)

        // Metin bloğu yerine yapılandırılmış satırlar: market noktası, ürün, hizalı fiyat.
        splitAdapter.submitList(result.optimalSplit.items)
        marketAdapter.submitList(result.byMarket)

        bindSavings(result)
    }

    /**
     * Kazanç = en pahalı tek-market sepeti − en ucuz dağıtım. Yanına kaç markete
     * uğranacağı da yazılır; yalnız tutarı göstermek yanıltıcı olurdu, iki market
     * gezmek de bir bedel.
     *
     * Karşılaştırma sadece sepeti tam karşılayan marketlerle yapılır — eksik
     * ürünlü bir marketin düşük toplamı "tasarruf" üretmemeli.
     */
    private fun bindSavings(result: BasketResponse) {
        val worst = result.byMarket.filter { it.complete }.maxOfOrNull { it.total }
        val saving = if (worst != null) worst - result.optimalSplit.total else 0.0

        if (worst == null || saving <= 0.0) {
            binding.tvSavings.visibility = View.GONE
            return
        }

        val stops = result.optimalSplit.items.mapNotNull { it.market }.distinct().size
        binding.tvSavings.text = if (stops <= 1) {
            getString(R.string.basket_savings_single, saving)
        } else {
            getString(R.string.basket_savings, saving, stops)
        }
        binding.tvSavings.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
