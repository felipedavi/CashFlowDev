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
import androidx.appcompat.widget.PopupMenu
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
import meimaonamassa.cashflow.feature.transaction.list.presentation.DateType
import meimaonamassa.cashflow.feature.transaction.list.presentation.IssueDateFilterMode
import meimaonamassa.cashflow.feature.transaction.list.presentation.SortOrder
import meimaonamassa.cashflow.feature.transaction.list.presentation.TransactionSortConfig
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
                    R.id.action_sort -> {
                        val toolbar = requireActivity().findViewById<View>(R.id.toolbar)
                        val anchor = toolbar?.findViewById<View>(R.id.action_sort) ?: toolbar ?: binding.root
                        showSortPopupMenu(anchor)
                        true
                    }
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

    private fun showSortPopupMenu(anchorView: View) {
        val wrapper = android.view.ContextThemeWrapper(requireContext(), androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light)
        val popup = PopupMenu(wrapper, anchorView)
        popup.menuInflater.inflate(R.menu.sort_menu, popup.menu)

        val currentConfig = viewModel.sortConfig.value

        val dateTypeId = when (currentConfig.dateType) {
            DateType.PAYMENT_DATE -> R.id.sort_date_payment
            DateType.ISSUE_DATE -> R.id.sort_date_issue
        }
        popup.menu.findItem(dateTypeId)?.isChecked = true

        val sortOrderId = when (currentConfig.sortOrder) {
            SortOrder.DATE_DESCENDING -> R.id.sort_order_date_desc
            SortOrder.DATE_ASCENDING -> R.id.sort_order_date_asc
            SortOrder.AMOUNT_DESCENDING -> R.id.sort_order_amount_desc
            SortOrder.AMOUNT_ASCENDING -> R.id.sort_order_amount_asc
        }
        popup.menu.findItem(sortOrderId)?.isChecked = true

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_date_payment -> {
                    viewModel.updateSortConfig(dateType = DateType.PAYMENT_DATE)
                    true
                }
                R.id.sort_date_issue -> {
                    viewModel.updateSortConfig(dateType = DateType.ISSUE_DATE)
                    true
                }
                R.id.sort_order_date_desc -> {
                    viewModel.updateSortConfig(sortOrder = SortOrder.DATE_DESCENDING)
                    true
                }
                R.id.sort_order_date_asc -> {
                    viewModel.updateSortConfig(sortOrder = SortOrder.DATE_ASCENDING)
                    true
                }
                R.id.sort_order_amount_desc -> {
                    viewModel.updateSortConfig(sortOrder = SortOrder.AMOUNT_DESCENDING)
                    true
                }
                R.id.sort_order_amount_asc -> {
                    viewModel.updateSortConfig(sortOrder = SortOrder.AMOUNT_ASCENDING)
                    true
                }
                else -> false
            }
        }
        popup.show()
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

                "import_db" -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.alert_import_db_success),
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

                "export_db" -> {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.alert_export_db_success),
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
                    kotlinx.coroutines.flow.combine(
                        viewModel.availableBankAccounts,
                        viewModel.selectedBankAccounts
                    ) { accounts, selectedAccounts ->
                        Pair(accounts, selectedAccounts)
                    }.collect { (accounts, selectedAccounts) ->
                        populateBankAccountChips(
                            accounts = accounts,
                            selectedAccounts = selectedAccounts
                        )
                    }
                }

                launch {
                    viewModel.issueDateFilterMode.collect { mode ->
                        val filterView = binding.root.findViewById<View>(R.id.filter_menu_container)
                        val chipDefault = filterView?.findViewById<Chip>(R.id.chipFilterDefaultIssueDate)
                        val chipNonDefault = filterView?.findViewById<Chip>(R.id.chipFilterNonDefaultIssueDate)

                        val isDefaultChecked = mode == IssueDateFilterMode.DEFAULT_ONLY
                        val isNonDefaultChecked = mode == IssueDateFilterMode.NON_DEFAULT_ONLY

                        if (chipDefault != null && chipDefault.isChecked != isDefaultChecked) {
                            chipDefault.setOnCheckedChangeListener(null)
                            chipDefault.isChecked = isDefaultChecked
                            chipDefault.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    viewModel.setIssueDateFilterMode(IssueDateFilterMode.DEFAULT_ONLY)
                                } else {
                                    viewModel.setIssueDateFilterMode(IssueDateFilterMode.ALL)
                                }
                            }
                        }
                        if (chipNonDefault != null && chipNonDefault.isChecked != isNonDefaultChecked) {
                            chipNonDefault.setOnCheckedChangeListener(null)
                            chipNonDefault.isChecked = isNonDefaultChecked
                            chipNonDefault.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) {
                                    viewModel.setIssueDateFilterMode(IssueDateFilterMode.NON_DEFAULT_ONLY)
                                } else {
                                    viewModel.setIssueDateFilterMode(IssueDateFilterMode.ALL)
                                }
                            }
                        }
                    }
                }

                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModel.filteredTransactions,
                        viewModel.sortConfig
                    ) { transactions, sortConfig ->
                        Pair(transactions, sortConfig)
                    }.collect { (transactions, sortConfig) ->
                        renderTransactions(transactions, sortConfig)
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
        val chipNonDefaultIssueDate =
            filterView.findViewById<Chip>(R.id.chipFilterNonDefaultIssueDate)

        val currentMode = viewModel.issueDateFilterMode.value
        chipDefaultIssueDate?.isChecked = currentMode == IssueDateFilterMode.DEFAULT_ONLY
        chipNonDefaultIssueDate?.isChecked = currentMode == IssueDateFilterMode.NON_DEFAULT_ONLY

        chipDefaultIssueDate?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                chipNonDefaultIssueDate?.isChecked = false
                viewModel.setIssueDateFilterMode(IssueDateFilterMode.DEFAULT_ONLY)
            } else if (chipNonDefaultIssueDate?.isChecked != true) {
                viewModel.setIssueDateFilterMode(IssueDateFilterMode.ALL)
            }
        }

        chipNonDefaultIssueDate?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                chipDefaultIssueDate?.isChecked = false
                viewModel.setIssueDateFilterMode(IssueDateFilterMode.NON_DEFAULT_ONLY)
            } else if (chipDefaultIssueDate?.isChecked != true) {
                viewModel.setIssueDateFilterMode(IssueDateFilterMode.ALL)
            }
        }

        filterView.findViewById<ImageButton>(R.id.btnCloseFilter)?.setOnClickListener {
            filterView.visibility = View.GONE
        }

        filterView.findViewById<ImageButton>(R.id.btnClearFilters)?.setOnClickListener {
            viewModel.clearAllFilters()
            filterView.findViewById<ChipGroup>(R.id.chipGroupBankAccount)?.clearCheck()
            chipDefaultIssueDate?.isChecked = false
            chipNonDefaultIssueDate?.isChecked = false
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

    private fun renderTransactions(
        transactions: List<TransactionEntity>,
        sortConfig: TransactionSortConfig
    ) {
        _binding?.let { safeBinding ->
            if (transactions.isEmpty()) {
                safeBinding.recyclerTransactions.visibility = View.GONE
                safeBinding.emptyContainer.root.visibility = View.VISIBLE
                safeBinding.balanceContainer.root.visibility =
                    if (viewModel.selectedBankAccounts.value.isNotEmpty() || viewModel.issueDateFilterMode.value != IssueDateFilterMode.ALL) View.VISIBLE else View.GONE
            } else {
                safeBinding.recyclerTransactions.visibility = View.VISIBLE
                safeBinding.emptyContainer.root.visibility = View.GONE
                safeBinding.balanceContainer.root.visibility = View.VISIBLE

                val groupedList = mutableListOf<ListItem>()
                val groupedByDate = transactions.groupBy { transaction ->
                    val targetDate = if (sortConfig.dateType == DateType.ISSUE_DATE) {
                        transaction.issueDate
                    } else {
                        transaction.paymentDate ?: transaction.issueDate
                    }
                    DateConverters.fromOffsetDateTime(targetDate)
                }

                val dateKeys = groupedByDate.keys.filterNotNull()
                val sortedDateKeys = when (sortConfig.sortOrder) {
                    SortOrder.DATE_ASCENDING -> dateKeys.sortedBy { it }
                    SortOrder.DATE_DESCENDING -> dateKeys.sortedByDescending { it }
                    SortOrder.AMOUNT_DESCENDING -> dateKeys.sortedByDescending { date ->
                        groupedByDate[date]?.maxOfOrNull { it.monetaryValue } ?: 0.0
                    }
                    SortOrder.AMOUNT_ASCENDING -> dateKeys.sortedBy { date ->
                        groupedByDate[date]?.minOfOrNull { it.monetaryValue } ?: 0.0
                    }
                }

                sortedDateKeys.forEach { d ->
                    groupedList.add(ListItem.Header(d.fromFormattedDate()))
                    val itemsInGroup = groupedByDate[d] ?: emptyList()
                    val sortedItems = when (sortConfig.sortOrder) {
                        SortOrder.AMOUNT_ASCENDING -> itemsInGroup.sortedBy { it.monetaryValue }
                        SortOrder.AMOUNT_DESCENDING -> itemsInGroup.sortedByDescending { it.monetaryValue }
                        else -> itemsInGroup.sortedByDescending { it.monetaryValue }
                    }
                    sortedItems.forEach { transaction ->
                        groupedList.add(ListItem.TransactionItem(transaction))
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