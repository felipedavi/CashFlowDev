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

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    val currentMonth = MutableStateFlow(YearMonth.now())

    private val monthPrefixFlow = currentMonth.map {
        it.toString()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsByMonthFlow = monthPrefixFlow
        .flatMapLatest { prefix -> repository.getTransactionsByMonth(prefix) }

    val allTransactions: LiveData<List<TransactionEntity>> = transactionsByMonthFlow
        .asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalIncome: LiveData<Double> = monthPrefixFlow
        .flatMapLatest { prefix -> repository.getTotalIncomeByMonth(prefix) }
        .map { it ?: 0.0 }
        .asLiveData()

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpense: LiveData<Double> = monthPrefixFlow
        .flatMapLatest { prefix -> repository.getTotalExpenseByMonth(prefix) }
        .map { it ?: 0.0 }
        .asLiveData()

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
    val balance: LiveData<Double> = monthPrefixFlow.flatMapLatest { prefix ->
        combine(
            repository.getTotalIncomeByMonth(prefix),
            repository.getTotalExpenseByMonth(prefix)
        ) { income, expense ->
            (income ?: 0.0) - (expense ?: 0.0)
        }
    }.asLiveData()

    fun nextMonth() {
        clearAllFilters()
        currentMonth.value = currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        clearAllFilters()
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

    private val _selectedBankAccounts = MutableStateFlow<Set<String>>(emptySet())
    val selectedBankAccounts: StateFlow<Set<String>> = _selectedBankAccounts.asStateFlow()

    private val _filterDefaultIssueDateOnly = MutableStateFlow(false)
    val filterDefaultIssueDateOnly: StateFlow<Boolean> = _filterDefaultIssueDateOnly.asStateFlow()

    val availableBankAccounts: StateFlow<List<String>> = transactionsByMonthFlow
        .map { list ->
            list.map { it.bankAccount }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        transactionsByMonthFlow,
        _selectedBankAccounts,
        _filterDefaultIssueDateOnly
    ) { transactions, selectedAccounts, defaultIssueDateOnly ->
        transactions.filter { transaction ->
            val matchesBank = selectedAccounts.isEmpty() || transaction.bankAccount in selectedAccounts
            val matchesIssueDate = !defaultIssueDateOnly || transaction.issueDate.year == 1900
            matchesBank && matchesIssueDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBankAccountFilter(account: String, isChecked: Boolean) {
        _selectedBankAccounts.update { current ->
            if (isChecked) current + account else current - account
        }
    }

    fun toggleDefaultIssueDateFilter(isChecked: Boolean) {
        _filterDefaultIssueDateOnly.value = isChecked
    }

    fun clearAllFilters() {
        _selectedBankAccounts.value = emptySet()
        _filterDefaultIssueDateOnly.value = false
    }
}