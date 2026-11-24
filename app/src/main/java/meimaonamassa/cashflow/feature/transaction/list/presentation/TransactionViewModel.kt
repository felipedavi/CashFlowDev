package meimaonamassa.cashflow.feature.transaction.list.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import meimaonamassa.cashflow.data.TransactionRepository
import meimaonamassa.cashflow.data.entity.TransactionEntity
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

data class MonthlyProjection(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val totalAmount: Double,
    val transactions: List<TransactionEntity>
)

data class ProjectionData(
    val nextMonthLabel: String,
    val nextMonthAmount: Double,
    val monthlyProjections: List<MonthlyProjection>
)

enum class DateType {
    PAYMENT_DATE,
    ISSUE_DATE
}

enum class SortOrder {
    DATE_DESCENDING,
    DATE_ASCENDING,
    AMOUNT_DESCENDING,
    AMOUNT_ASCENDING
}

data class TransactionSortConfig(
    val dateType: DateType = DateType.PAYMENT_DATE,
    val sortOrder: SortOrder = SortOrder.DATE_DESCENDING
)

enum class IssueDateFilterMode {
    ALL,
    DEFAULT_ONLY,
    NON_DEFAULT_ONLY
}

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    val currentMonth = MutableStateFlow(YearMonth.now())

    private val _selectedBankAccounts = MutableStateFlow<Set<String>>(emptySet())
    val selectedBankAccounts: StateFlow<Set<String>> = _selectedBankAccounts.asStateFlow()

    private val monthPrefixFlow = currentMonth.map {
        it.toString()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsByMonthFlow = monthPrefixFlow
        .flatMapLatest { prefix -> repository.getTransactionsByMonth(prefix) }

    val allTransactions: LiveData<List<TransactionEntity>> = transactionsByMonthFlow
        .asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: LiveData<Double> = combine(
        transactionsByMonthFlow,
        _selectedBankAccounts
    ) { transactions, selectedAccounts ->
        transactions.filter { trans ->
            trans.transactionType && (selectedAccounts.isEmpty() || trans.bankAccount in selectedAccounts)
        }.sumOf { it.monetaryValue }
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpense: LiveData<Double> = combine(
        transactionsByMonthFlow,
        _selectedBankAccounts
    ) { transactions, selectedAccounts ->
        transactions.filter { trans ->
            !trans.transactionType && (selectedAccounts.isEmpty() || trans.bankAccount in selectedAccounts)
        }.sumOf { it.monetaryValue }
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyTotals: LiveData<Pair<Double, Double>> = monthPrefixFlow.flatMapLatest { prefix ->
        combine(
            repository.getTotalIncomeByMonth(prefix),
            repository.getTotalExpenseByMonth(prefix)
        ) { income, expense ->
            Pair(income ?: 0.0, expense ?: 0.0)
        }
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val previousBalance: LiveData<Double> = monthPrefixFlow
        .flatMapLatest { prefix -> repository.getPreviousBalance(prefix) }
        .map { it ?: 0.0 }
        .asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val balance: LiveData<Double> = monthPrefixFlow.flatMapLatest { prefix ->
        combine(
            repository.getPreviousTransactions(prefix),
            repository.getTransactionsByMonth(prefix),
            _selectedBankAccounts
        ) { prevList, currentList, selectedAccounts ->
            val prevBalance = prevList.filter { trans ->
                selectedAccounts.isEmpty() || trans.bankAccount in selectedAccounts
            }.sumOf { if (it.transactionType) it.monetaryValue else -it.monetaryValue }

            val income = currentList.filter { trans ->
                trans.transactionType && (selectedAccounts.isEmpty() || trans.bankAccount in selectedAccounts)
            }.sumOf { it.monetaryValue }

            val expense = currentList.filter { trans ->
                !trans.transactionType && (selectedAccounts.isEmpty() || trans.bankAccount in selectedAccounts)
            }.sumOf { it.monetaryValue }

            prevBalance + income - expense
        }
    }.asLiveData()

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasPreviousMonth: LiveData<Boolean> = currentMonth.flatMapLatest { month ->
        val firstDayOfMonth = month.atDay(1).toString()
        val now = YearMonth.now()
        repository.hasTransactionsBefore(firstDayOfMonth).map { hasBefore ->
            month > now || hasBefore
        }
    }.asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val hasNextMonth: LiveData<Boolean> = currentMonth.flatMapLatest { month ->
        val firstDayOfNextMonth = month.plusMonths(1).atDay(1).toString()
        val now = YearMonth.now()
        repository.hasTransactionsAfter(firstDayOfNextMonth).map { hasAfter ->
            month < now || hasAfter
        }
    }.asLiveData()

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    private val defaultBankOrder = listOf("Carteira", "Flash", "Nubank", "Mercado Pago", "Inter")

    private val bankComparator = Comparator<String> { b1, b2 ->
        val idx1 = defaultBankOrder.indexOfFirst { it.equals(b1.trim(), ignoreCase = true) }
        val idx2 = defaultBankOrder.indexOfFirst { it.equals(b2.trim(), ignoreCase = true) }
        when {
            idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
            idx1 != -1 -> -1
            idx2 != -1 -> 1
            else -> b1.compareTo(b2, ignoreCase = true)
        }
    }

    private val _issueDateFilterMode = MutableStateFlow(IssueDateFilterMode.ALL)
    val issueDateFilterMode: StateFlow<IssueDateFilterMode> = _issueDateFilterMode.asStateFlow()

    val filterDefaultIssueDateOnly: StateFlow<Boolean> = _issueDateFilterMode.map {
        it == IssueDateFilterMode.DEFAULT_ONLY
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val bankAccountBalances: StateFlow<Map<String, Double>> = monthPrefixFlow.flatMapLatest { prefix ->
        combine(
            repository.getPreviousTransactions(prefix),
            repository.getTransactionsByMonth(prefix)
        ) { prevList, currentList ->
            val allUntilNow = prevList + currentList
            val bankMap = mutableMapOf<String, Double>()
            allUntilNow.forEach { trans ->
                val bank = trans.bankAccount.trim().ifEmpty { "Carteira" }
                val amount = if (trans.transactionType) trans.monetaryValue else -trans.monetaryValue
                bankMap[bank] = (bankMap[bank] ?: 0.0) + amount
            }
            val sortedKeys = bankMap.keys.sortedWith(bankComparator)
            sortedKeys.associateWith { bankMap[it] ?: 0.0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val futureProjectionData: StateFlow<ProjectionData> = currentMonth.flatMapLatest { month ->
        val nextMonth = month.plusMonths(1)
        val startDate = nextMonth.atDay(1).toString()
        val next6Months = (1..6).map { month.plusMonths(it.toLong()) }
        val formatter = DateTimeFormatter.ofPattern("MMM/yy", Locale.forLanguageTag("pt-BR"))

        repository.getFutureExpensesFrom(startDate).map { futureExpenses ->
            val projections = next6Months.map { ym ->
                val ymExpenses = futureExpenses.filter { trans ->
                    trans.paymentDate?.let { date ->
                        YearMonth.of(date.year, date.monthValue) == ym
                    } ?: false
                }
                val total = ymExpenses.sumOf { it.monetaryValue }
                val label = ym.format(formatter).replaceFirstChar { it.uppercase() }
                MonthlyProjection(ym, label, total, ymExpenses)
            }

            val nextMonthFullFormatter = DateTimeFormatter.ofPattern("MMMM/yyyy", Locale.forLanguageTag("pt-BR"))
            val nextMonthFullLabel = nextMonth.format(nextMonthFullFormatter).replaceFirstChar { it.uppercase() }
            val nextMonthTotal = projections.firstOrNull()?.totalAmount ?: 0.0

            ProjectionData(
                nextMonthLabel = nextMonthFullLabel,
                nextMonthAmount = nextMonthTotal,
                monthlyProjections = projections
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectionData("", 0.0, emptyList()))

    val availableBankAccounts: StateFlow<List<String>> = transactionsByMonthFlow
        .map { list ->
            list.map { it.bankAccount }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedWith(bankComparator)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        transactionsByMonthFlow,
        _selectedBankAccounts,
        _issueDateFilterMode
    ) { transactions, selectedAccounts, issueDateMode ->
        transactions.filter { transaction ->
            val matchesBank = selectedAccounts.isEmpty() || transaction.bankAccount in selectedAccounts
            val matchesIssueDate = when (issueDateMode) {
                IssueDateFilterMode.ALL -> true
                IssueDateFilterMode.DEFAULT_ONLY -> transaction.issueDate.year == 1900
                IssueDateFilterMode.NON_DEFAULT_ONLY -> transaction.issueDate.year != 1900
            }
            matchesBank && matchesIssueDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortConfig = MutableStateFlow(TransactionSortConfig())
    val sortConfig: StateFlow<TransactionSortConfig> = _sortConfig.asStateFlow()

    fun updateSortConfig(dateType: DateType? = null, sortOrder: SortOrder? = null) {
        _sortConfig.update { current ->
            current.copy(
                dateType = dateType ?: current.dateType,
                sortOrder = sortOrder ?: current.sortOrder
            )
        }
    }

    fun setIssueDateFilterMode(mode: IssueDateFilterMode) {
        _issueDateFilterMode.value = mode
    }

    fun toggleBankAccountFilter(account: String, isChecked: Boolean) {
        _selectedBankAccounts.update { current ->
            if (isChecked) current + account else current - account
        }
    }

    fun toggleDefaultIssueDateFilter(isChecked: Boolean) {
        _issueDateFilterMode.value = if (isChecked) IssueDateFilterMode.DEFAULT_ONLY else IssueDateFilterMode.ALL
    }

    fun clearAllFilters() {
        _selectedBankAccounts.value = emptySet()
        _issueDateFilterMode.value = IssueDateFilterMode.ALL
    }
}