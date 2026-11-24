package meimaonamassa.cashflow.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import meimaonamassa.cashflow.base.TransactionDatabase
import meimaonamassa.cashflow.data.TransactionRepository
import meimaonamassa.cashflow.data.export.CsvImportResult
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(private val repository: TransactionRepository) : ViewModel() {

    fun exportData(outputStream: OutputStream, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val transactionsList = repository.getAllTransactionsStatic()
                val csvData = repository.exportTransactionsToCSV(transactionsList)

                outputStream.bufferedWriter().use { writer ->
                    writer.write(csvData)
                }

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importData(
        inputStream: InputStream,
        onSuccess: (CsvImportResult) -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.importTransactionsFromCSV(inputStream)

                if (result.transactions.isNotEmpty() || result.warnings.isNotEmpty()) {
                    result.transactions.forEach { repository.insert(it) }
                    withContext(Dispatchers.Main) {
                        onSuccess(result)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onError()
                }
            }
        }
    }

    fun exportDatabase(
        context: Context,
        outputStream: OutputStream,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = TransactionDatabase.exportDatabase(context, outputStream)
            withContext(Dispatchers.Main) {
                if (success) onSuccess() else onError()
            }
        }
    }

    fun importDatabase(
        context: Context,
        inputStream: InputStream,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = TransactionDatabase.importDatabase(context, inputStream)
            withContext(Dispatchers.Main) {
                if (success) onSuccess() else onError()
            }
        }
    }

    fun clearAllData(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAll()
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }
}
