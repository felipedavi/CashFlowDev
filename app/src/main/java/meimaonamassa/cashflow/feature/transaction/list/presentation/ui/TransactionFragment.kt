package meimaonamassa.cashflow.feature.transaction.list.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import meimaonamassa.cashflow.MainApplication
import meimaonamassa.cashflow.R
import meimaonamassa.cashflow.base.DateConverters
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.databinding.FragmentTransactionBinding
import meimaonamassa.cashflow.feature.transaction.list.presentation.TransactionViewModel
import meimaonamassa.cashflow.feature.transaction.list.presentation.TransactionViewModelFactory
import meimaonamassa.cashflow.feature.transaction.list.presentation.ui.adapter.GroupedTransactionAdapter
import meimaonamassa.cashflow.util.extension.fromFormattedDate
import meimaonamassa.cashflow.util.extension.toCurrency
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import androidx.core.view.isVisible

class TransactionFragment : Fragment() {
    private lateinit var viewModel: TransactionViewModel
    private lateinit var adapter: GroupedTransactionAdapter
    private var _binding: FragmentTransactionBinding? = null
    private val binding get() = _binding!!

    private val monthFormatter =
        DateTimeFormatter.ofPattern("MMMM / yyyy", Locale.forLanguageTag("pt-BR"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this, TransactionViewModelFactory(
                (requireActivity().application as MainApplication).repository
            )
        )[TransactionViewModel::class.java]

        adapter = GroupedTransactionAdapter(::transactionListClickListener, viewModel)

        setupMenu()

        return binding.root
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.main_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_filter -> {
                        toggleFilterPanelVisibility()
                        true
                    }
                    R.id.settingsFragment, R.id.action_settings -> {
                        findNavController().navigate(R.id.navigateToTransactionSettingsFragment)
                        true
                    }
                    R.id.action_summary -> {
                        val monthStr = viewModel.currentMonth.value.toString()
                        val action = TransactionFragmentDirections.navigateToSummaryFragment(monthStr)
                        findNavController().navigate(action)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setFragmentResultListener("settingsAction") { _, bundle ->
            when (bundle.getString("action")) {
                "import" -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.alert_import_data_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.currentMonth.value = YearMonth.now()
                }

                "delete" -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.alert_reset_data_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.currentMonth.value = YearMonth.now()
                }

                "export" -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.alert_export_data_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.recyclerTransactions.layoutManager = LinearLayoutManager(context)
        binding.recyclerTransactions.adapter = adapter

        setupFilterMenu()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentMonth.collect { month ->
                        val formattedMonth =
                            month.format(monthFormatter).replaceFirstChar { it.uppercase() }
                        _binding?.monthSelectorContainer?.textCurrentMonth?.text = formattedMonth
                    }
                }

                launch {
                    viewModel.availableBankAccounts.collect { accounts ->
                        populateBankAccountChips(
                            accounts = accounts,
                            selectedAccounts = viewModel.selectedBankAccounts.value
                        )
                    }
                }

                launch {
                    viewModel.filteredTransactions.collect { transactions ->
                        renderTransactions(transactions)
                        updateFilterCounter(transactions.size)
                    }
                }
            }
        }

        viewModel.totalIncome.observe(viewLifecycleOwner) { totalIncome ->
            _binding?.balanceContainer?.textIncome?.text = totalIncome.toCurrency()
        }

        viewModel.totalExpense.observe(viewLifecycleOwner) { totalExpense ->
            _binding?.balanceContainer?.textExpense?.text = totalExpense.toCurrency()
        }

        viewModel.balance.observe(viewLifecycleOwner) { balance ->
            _binding?.balanceContainer?.textBalance?.text = balance.toCurrency()
        }

        viewModel.hasPreviousMonth.observe(viewLifecycleOwner) { hasPrevious ->
            binding.monthSelectorContainer.btnPreviousMonth.isEnabled = hasPrevious
            binding.monthSelectorContainer.btnPreviousMonth.alpha = if (hasPrevious) 1.0f else 0.3f
        }

        viewModel.hasNextMonth.observe(viewLifecycleOwner) { hasNext ->
            binding.monthSelectorContainer.btnNextMonth.isEnabled = hasNext
            binding.monthSelectorContainer.btnNextMonth.alpha = if (hasNext) 1.0f else 0.3f
        }

        setListeners()
    }

    private fun setupFilterMenu() {
        val filterView = binding.root.findViewById<View>(R.id.filter_menu_container) ?: return
        filterView.findViewById<TextView>(R.id.tvFilterBankAccountTitle)?.text =
            getString(R.string.text_bank_account)
        filterView.findViewById<TextView>(R.id.tvFilterIssueDateTitle)?.text =
            getString(R.string.filter_issue_date_title)

        val chipDefaultIssueDate =
            filterView.findViewById<Chip>(R.id.chipFilterDefaultIssueDate)
        chipDefaultIssueDate?.isChecked = viewModel.filterDefaultIssueDateOnly.value
        chipDefaultIssueDate?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleDefaultIssueDateFilter(isChecked)
        }

        filterView.findViewById<ImageButton>(R.id.btnCloseFilter)?.setOnClickListener {
            filterView.visibility = View.GONE
        }

        filterView.findViewById<ImageButton>(R.id.btnClearFilters)?.setOnClickListener {
            viewModel.clearAllFilters()
            filterView.findViewById<ChipGroup>(R.id.chipGroupBankAccount)?.clearCheck()
            chipDefaultIssueDate?.isChecked = false
        }
    }

    private fun populateBankAccountChips(
        accounts: List<String>,
        selectedAccounts: Set<String>
    ) {
        val filterView = binding.root.findViewById<View>(R.id.filter_menu_container) ?: return
        val chipGroup = filterView.findViewById<ChipGroup>(R.id.chipGroupBankAccount) ?: return
        chipGroup.removeAllViews()

        accounts.forEach { account ->
            val chip = layoutInflater.inflate(
                R.layout.item_filter_chip,
                chipGroup,
                false
            ) as Chip

            chip.text = account
            chip.isChecked = account in selectedAccounts
            chip.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleBankAccountFilter(account, isChecked)
            }

            chipGroup.addView(chip)
        }
    }

    private fun renderTransactions(transactions: List<TransactionEntity>) {
        _binding?.let { safeBinding ->
            if (transactions.isEmpty()) {
                safeBinding.recyclerTransactions.visibility = View.GONE
                safeBinding.emptyContainer.root.visibility = View.VISIBLE
                safeBinding.balanceContainer.root.visibility =
                    if (viewModel.selectedBankAccounts.value.isNotEmpty() || viewModel.filterDefaultIssueDateOnly.value) View.VISIBLE else View.GONE
            } else {
                safeBinding.recyclerTransactions.visibility = View.VISIBLE
                safeBinding.emptyContainer.root.visibility = View.GONE
                safeBinding.balanceContainer.root.visibility = View.VISIBLE

                val groupedList = mutableListOf<ListItem>()
                val groupedByDate = transactions.groupBy {
                    it.paymentDate?.let { date ->
                        DateConverters.fromOffsetDateTime(date)
                    }
                }
                groupedByDate.keys.sortedByDescending { it }.forEach { date ->
                    date?.let { d ->
                        groupedList.add(ListItem.Header(d.fromFormattedDate()))
                        val sortedItems =
                            groupedByDate[d]?.sortedByDescending { it.monetaryValue }
                        sortedItems?.forEach { transaction ->
                            groupedList.add(ListItem.TransactionItem(transaction))
                        }
                    }
                }
                adapter.submitList(groupedList)
            }
        }
    }

    private fun toggleFilterPanelVisibility() {
        val filterView = binding.root.findViewById<View>(R.id.filter_menu_container) ?: return
        filterView.visibility = if (filterView.isVisible) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun updateFilterCounter(count: Int) {
        val label = if (count == 1) "1 resultado" else "$count resultados"
        val filterView = binding.root.findViewById<View>(R.id.filter_menu_container)
        filterView?.findViewById<TextView>(R.id.tvFilterResultCount)?.text = label
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setListeners() {
        binding.floatingActionButton.setOnClickListener {
            findNavController().navigate(R.id.navigateToTransactionAddFragment)
        }

        binding.monthSelectorContainer.btnNextMonth.setOnClickListener {
            viewModel.nextMonth()
        }

        binding.monthSelectorContainer.btnPreviousMonth.setOnClickListener {
            viewModel.previousMonth()
        }
    }

    private fun transactionListClickListener(transaction: TransactionEntity) {
        val action = TransactionFragmentDirections.navigateToTransactionDetailFragment(
            transaction.id
        )
        findNavController().navigate(action)
    }
}