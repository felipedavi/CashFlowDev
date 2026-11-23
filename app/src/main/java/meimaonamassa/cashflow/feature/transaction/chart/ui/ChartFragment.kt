package meimaonamassa.cashflow.feature.transaction.chart.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import meimaonamassa.cashflow.MainApplication
import meimaonamassa.cashflow.R
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.databinding.FragmentChartBinding
import meimaonamassa.cashflow.feature.transaction.list.presentation.MonthlyProjection
import meimaonamassa.cashflow.feature.transaction.list.presentation.TransactionViewModel
import meimaonamassa.cashflow.feature.transaction.list.presentation.TransactionViewModelFactory
import meimaonamassa.cashflow.util.PreferenceManager
import meimaonamassa.cashflow.util.extension.toCurrency
import org.threeten.bp.YearMonth
import java.util.Locale

class ChartFragment : Fragment() {
    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefManager: PreferenceManager

    private val args: ChartFragmentArgs by navArgs()
    private val viewModel: TransactionViewModel by activityViewModels {
        TransactionViewModelFactory((requireActivity().application as MainApplication).repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        prefManager = PreferenceManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pieChart.clear()
        binding.pieChartCategories.clear()
        binding.barChartCategoryComparison.clear()
        binding.barChartProjection.clear()
        try {
            val selectedMonth = YearMonth.parse(args.selectedMonth)
            if (viewModel.currentMonth.value != selectedMonth) {
                viewModel.currentMonth.value = selectedMonth
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupChartConfiguration()
        observeFinancialData()
    }

    private fun setupChartConfiguration() {
        setupTabs()

        binding.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            setNoDataText(getString(R.string.no_expenses_text))
        }

        binding.pieChartCategories.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            setNoDataText(getString(R.string.no_expenses_text))
        }

        binding.barChartCategoryComparison.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            legend.isEnabled = true
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            setNoDataText(getString(R.string.no_expenses_text))

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                isGranularityEnabled = true
                valueFormatter = IndexAxisValueFormatter(arrayOf("Necessidades", "Desejos", "Investimentos"))
                setCenterAxisLabels(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toDouble().toCurrency()
                    }
                }
            }
            axisRight.isEnabled = false
        }

        binding.barChartProjection.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            legend.isEnabled = false
            setNoDataText(getString(R.string.no_projection_expenses))

            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toDouble().toCurrency()
                    }
                }
            }
            axisRight.isEnabled = false
        }
    }

    private fun setupTabs() {
        binding.tabLayoutCharts.removeAllTabs()
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_progress)), true)
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_balance)))
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_bank_accounts)))
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_projection)))
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_categories)))
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_comparison)))
        binding.tabLayoutCharts.addTab(binding.tabLayoutCharts.newTab().setText(getString(R.string.tab_chart_all)))

        binding.tabLayoutCharts.clearOnTabSelectedListeners()
        binding.tabLayoutCharts.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateChartVisibility(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        updateChartVisibility(0)
    }

    private fun updateChartVisibility(position: Int) {
        val showProgress = position == 0 || position == 6
        val showBalance = position == 1 || position == 6
        val showBankAccounts = position == 2 || position == 6
        val showProjection = position == 3 || position == 6
        val showCategories = position == 4 || position == 6
        val showComparison = position == 5 || position == 6

        binding.layoutBudgetStatus.visibility = if (showProgress) View.VISIBLE else View.GONE
        binding.pieChart.visibility = if (showBalance) View.VISIBLE else View.GONE

        binding.textBankAccountsChartTitle.visibility = if (showBankAccounts) View.VISIBLE else View.GONE
        binding.layoutBankAccountsStatus.visibility = if (showBankAccounts) View.VISIBLE else View.GONE

        binding.cardProjectionSummary.visibility = if (showProjection) View.VISIBLE else View.GONE
        binding.textProjectionChartTitle.visibility = if (showProjection) View.VISIBLE else View.GONE
        binding.barChartProjection.visibility = if (showProjection) View.VISIBLE else View.GONE
        binding.layoutProjectionDetails.visibility = if (showProjection) View.VISIBLE else View.GONE

        binding.textCategoryChartTitle.visibility = if (showCategories) View.VISIBLE else View.GONE
        binding.pieChartCategories.visibility = if (showCategories) View.VISIBLE else View.GONE

        binding.textBarChartTitle.visibility = if (showComparison) View.VISIBLE else View.GONE
        binding.barChartCategoryComparison.visibility = if (showComparison) View.VISIBLE else View.GONE
    }

    private fun observeFinancialData() {
        val currentTotals = viewModel.monthlyTotals.value
        if (currentTotals != null) {
            updateChartData(currentTotals.first, currentTotals.second)
        }
        viewModel.monthlyTotals.observe(viewLifecycleOwner) { totals ->
            updateChartData(totals.first, totals.second)
        }
        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            val budget = prefManager.getMonthlyBudget()
            updateBudgetStatus(transactions, budget)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.bankAccountBalances.collect { bankMap ->
                        updateBankAccountsUI(bankMap)
                    }
                }
                launch {
                    viewModel.futureProjectionData.collect { projectionData ->
                        if (projectionData.nextMonthLabel.isNotEmpty()) {
                            binding.textProjectionSummary.text = getString(
                                R.string.projection_summary_format,
                                projectionData.nextMonthLabel,
                                projectionData.nextMonthAmount.toCurrency()
                            )
                        } else {
                            binding.textProjectionSummary.text = getString(R.string.no_projection_expenses)
                        }
                        updateProjectionBarChart(projectionData.monthlyProjections)
                        updateProjectionDetailsUI(projectionData.monthlyProjections)
                    }
                }
            }
        }
    }

    private fun updateProjectionBarChart(projections: List<MonthlyProjection>) {
        if (projections.isEmpty() || projections.all { it.totalAmount <= 0 }) {
            binding.barChartProjection.clear()
            binding.barChartProjection.invalidate()
            return
        }

        val entries = projections.mapIndexed { index, proj ->
            BarEntry(index.toFloat(), proj.totalAmount.toFloat())
        }

        val labels = projections.map { it.monthLabel }.toTypedArray()

        val currencyFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) value.toDouble().toCurrency() else ""
            }
        }

        val dataSet = BarDataSet(entries, "Compromissos").apply {
            color = "#2E7D32".toColorInt()
            valueTextSize = 10f
            valueTextColor = "#2E7D32".toColorInt()
            valueFormatter = currencyFormatter
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.45f

        binding.barChartProjection.apply {
            data = barData
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                isGranularityEnabled = true
                axisMinimum = -0.5f
                axisMaximum = (projections.size - 0.5).toFloat()
            }
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun updateProjectionDetailsUI(projections: List<MonthlyProjection>) {
        val container = binding.layoutProjectionDetails
        container.removeAllViews()

        val activeProjections = projections.filter { it.totalAmount > 0 }

        if (activeProjections.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(R.string.no_projection_expenses)
                setTextColor(Color.GRAY)
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            container.addView(emptyText)
            return
        }

        activeProjections.forEach { proj ->
            val monthCard = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 16)
                background = requireContext().getDrawable(R.drawable.rounded_border)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
            }

            val headerLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val titleText = TextView(requireContext()).apply {
                text = proj.monthLabel
                setTextColor("#2E7D32".toColorInt())
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val totalText = TextView(requireContext()).apply {
                text = proj.totalAmount.toCurrency()
                setTextColor("#2E7D32".toColorInt())
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            headerLayout.addView(titleText)
            headerLayout.addView(totalText)
            monthCard.addView(headerLayout)

            proj.transactions.forEach { trans ->
                val itemText = TextView(requireContext()).apply {
                    val desc = if (trans.isInstallment && trans.installmentCurrent != null && trans.installmentTotal != null) {
                        "${trans.description} (Par. ${trans.installmentCurrent}/${trans.installmentTotal})"
                    } else {
                        trans.description
                    }
                    text = "• $desc — ${trans.monetaryValue.toCurrency()}"
                    setTextColor("#455A64".toColorInt())
                    textSize = 13f
                    setPadding(8, 4, 0, 0)
                }
                monthCard.addView(itemText)
            }

            container.addView(monthCard)
        }
    }

    private fun updateBankAccountsUI(bankMap: Map<String, Double>) {
        val container = binding.layoutBankAccountsStatus
        container.removeAllViews()

        if (bankMap.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = getString(R.string.no_expenses_text)
                setTextColor(Color.GRAY)
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            container.addView(emptyText)
            return
        }

        val maxPositiveBalance = bankMap.values.filter { it > 0 }.sum().let { if (it > 0) it else 1.0 }

        bankMap.forEach { (bankName, balance) ->
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 16)
            }

            val headerLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val nameTextView = TextView(requireContext()).apply {
                text = bankName
                setTextColor("#455A64".toColorInt())
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val balanceTextView = TextView(requireContext()).apply {
                text = balance.toCurrency()
                setTextColor(if (balance >= 0) "#4CAF50".toColorInt() else Color.RED)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            headerLayout.addView(nameTextView)
            headerLayout.addView(balanceTextView)

            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                val progressPercent = if (balance > 0) ((balance / maxPositiveBalance) * 100).toInt() else 0
                progress = progressPercent
                progressTintList = android.content.res.ColorStateList.valueOf(
                    if (balance >= 0) "#4CAF50".toColorInt() else Color.RED
                )
                progressDrawable = requireContext().getDrawable(R.drawable.rounded_border)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8
                }
            }

            layout.addView(headerLayout)
            layout.addView(progressBar)
            container.addView(layout)
        }
    }

    private fun updateChartData(income: Double, expense: Double) {
        val entries = ArrayList<PieEntry>()
        val dynamicColors = ArrayList<Int>()

        if (income > 0) {
            entries.add(PieEntry(income.toFloat(), "Receitas"))
            dynamicColors.add("#4CAF50".toColorInt())
        }

        if (expense > 0) {
            entries.add(PieEntry(expense.toFloat(), "Despesas"))
            dynamicColors.add("#F44336".toColorInt())
        }

        if (entries.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.invalidate()
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = dynamicColors
            valueTextSize = 14f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }

        val pieData = PieData(dataSet)

        pieData.setValueFormatter(object : PercentFormatter(binding.pieChart) {
            override fun getFormattedValue(value: Float): String {
                return if (value < 2f) "" else super.getFormattedValue(value)
            }
        })

        binding.pieChart.apply {
            data = pieData
            centerText = "Saldo Mensal\n${(income - expense).toCurrency()}"
            setCenterTextSize(16f)
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun updateBudgetStatus(transactions: List<TransactionEntity>, totalBudget: Float) {
        val configuredBudget = totalBudget.toDouble()
        val totalIncome = transactions.filter { it.transactionType }.sumOf { it.monetaryValue }

        val effectiveBudget = if (totalIncome > configuredBudget) {
            totalIncome
        } else {
            configuredBudget
        }

        val spent = mutableMapOf("Necessidades" to 0.0, "Desejos" to 0.0, "Investimentos" to 0.0)
        var totalExpenses = 0.0

        transactions.filter { !it.transactionType }.forEach { trans ->
            totalExpenses += trans.monetaryValue
            val cat = trans.category ?: ""
            if (spent.containsKey(cat)) {
                spent[cat] = spent[cat]!! + trans.monetaryValue
            } else if (cat.equals("Investimento", ignoreCase = true)) {
                spent["Investimentos"] = (spent["Investimentos"] ?: 0.0) + trans.monetaryValue
            }
        }

        updateCategoryPieChart(spent, totalExpenses)

        if (effectiveBudget > 0) {
            updateCategoryBarChart(spent, effectiveBudget)

            updateCategoryUI(
                binding.textNeedsStatus, binding.progressNeeds,
                spent["Necessidades"] ?: 0.0, effectiveBudget * 0.5, effectiveBudget, "Necessidades",
                "#2196F3".toColorInt()
            )

            updateCategoryUI(
                binding.textWantsStatus, binding.progressWants,
                spent["Desejos"] ?: 0.0, effectiveBudget * 0.3, effectiveBudget, "Desejos",
                "#FFC107".toColorInt()
            )

            updateCategoryUI(
                binding.textInvestmentsStatus, binding.progressInvestments,
                spent["Investimentos"] ?: 0.0, effectiveBudget * 0.2, effectiveBudget, "Investimentos",
                "#9C27B0".toColorInt()
            )
        }

        updateChartVisibility(binding.tabLayoutCharts.selectedTabPosition)
    }

    private fun updateCategoryPieChart(spent: Map<String, Double>, totalExpenses: Double) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        val needs = spent["Necessidades"] ?: 0.0
        val wants = spent["Desejos"] ?: 0.0
        val investments = spent["Investimentos"] ?: 0.0

        if (needs > 0) {
            entries.add(PieEntry(needs.toFloat(), "Necessidades"))
            colors.add("#2196F3".toColorInt())
        }
        if (wants > 0) {
            entries.add(PieEntry(wants.toFloat(), "Desejos"))
            colors.add("#FFC107".toColorInt())
        }
        if (investments > 0) {
            entries.add(PieEntry(investments.toFloat(), "Investimentos"))
            colors.add("#9C27B0".toColorInt())
        }

        if (entries.isEmpty()) {
            binding.pieChartCategories.clear()
            binding.pieChartCategories.invalidate()
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 14f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }

        val pieData = PieData(dataSet)
        pieData.setValueFormatter(object : PercentFormatter(binding.pieChartCategories) {
            override fun getFormattedValue(value: Float): String {
                return if (value < 2f) "" else super.getFormattedValue(value)
            }
        })

        binding.pieChartCategories.apply {
            data = pieData
            centerText = "Total Despesas\n${totalExpenses.toCurrency()}"
            setCenterTextSize(15f)
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun updateCategoryBarChart(spent: Map<String, Double>, effectiveBudget: Double) {
        val needsSpent = (spent["Necessidades"] ?: 0.0).toFloat()
        val wantsSpent = (spent["Desejos"] ?: 0.0).toFloat()
        val investmentsSpent = (spent["Investimentos"] ?: 0.0).toFloat()

        val needsLimit = (effectiveBudget * 0.5).toFloat()
        val wantsLimit = (effectiveBudget * 0.3).toFloat()
        val investmentsLimit = (effectiveBudget * 0.2).toFloat()

        val entriesSpent = listOf(
            BarEntry(0f, needsSpent),
            BarEntry(1f, wantsSpent),
            BarEntry(2f, investmentsSpent)
        )

        val entriesLimit = listOf(
            BarEntry(0f, needsLimit),
            BarEntry(1f, wantsLimit),
            BarEntry(2f, investmentsLimit)
        )

        val currencyFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) value.toDouble().toCurrency() else ""
            }
        }

        val setSpent = BarDataSet(entriesSpent, getString(R.string.legend_actual_spent)).apply {
            color = "#FF5722".toColorInt()
            valueTextSize = 10f
            valueFormatter = currencyFormatter
        }

        val setLimit = BarDataSet(entriesLimit, getString(R.string.legend_target_limit)).apply {
            color = "#3F51B5".toColorInt()
            valueTextSize = 10f
            valueFormatter = currencyFormatter
        }

        val groupSpace = 0.3f
        val barSpace = 0.05f
        val barWidth = 0.3f

        val barData = BarData(setSpent, setLimit)
        barData.barWidth = barWidth

        binding.barChartCategoryComparison.apply {
            data = barData
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = 3f
            groupBars(0f, groupSpace, barSpace)
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun updateCategoryUI(
        label: TextView,
        bar: ProgressBar,
        spent: Double,
        limit: Double,
        totalBudget: Double,
        title: String,
        categoryColor: Int
    ) {
        val locale = Locale.forLanguageTag("pt-BR")
        val spentPercentTotal = if (totalBudget > 0) (spent / totalBudget) * 100 else 0.0
        val remaining = limit - spent

        val statusLine2 = if (remaining >= 0) {
            "Resta ${remaining.toCurrency()}"
        } else {
            "Ultrapassou ${kotlin.math.abs(remaining).toCurrency()}"
        }

        label.text = String.format(
            locale,
            "%s : %s / %s (%.1f%% do total)\n%s",
            title,
            spent.toCurrency(),
            limit.toCurrency(),
            spentPercentTotal,
            statusLine2
        )

        bar.max = 100
        val progressPercent = if (limit > 0) ((spent / limit) * 100).toInt() else 0
        bar.progress = progressPercent

        val finalColor = if (spent > limit) Color.RED else categoryColor
        bar.progressTintList = android.content.res.ColorStateList.valueOf(finalColor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
