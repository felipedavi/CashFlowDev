package meimaonamassa.cashflow.data.export

import meimaonamassa.cashflow.data.entity.TransactionEntity
import org.threeten.bp.LocalDate
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class CsvImportResult(
    val transactions: List<TransactionEntity>,
    val warnings: List<String>
)

object CSVHelper {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private const val DELIMITER = ";"
    private val VALID_EXPENSE_CATEGORIES = setOf("Necessidades", "Desejos", "Investimento")
    private val DEFAULT_ISSUE_DATE: OffsetDateTime = OffsetDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    private const val HEADER =
        "payerPayee${DELIMITER}description${DELIMITER}issueDate${DELIMITER}paymentDate${DELIMITER}monetaryValue${DELIMITER}transactionType${DELIMITER}isInstallment${DELIMITER}installmentCurrent${DELIMITER}installmentTotal${DELIMITER}category${DELIMITER}bankAccount"

    fun exportTransactions(transactions: List<TransactionEntity>): String {
        val rows = transactions.map { transaction ->
            // Se a emissão for a data padrão 01/01/1900, exporta em branco para manter o CSV limpo
            val issueDateStr = if (transaction.issueDate.toLocalDate() == DEFAULT_ISSUE_DATE.toLocalDate()) {
                ""
            } else {
                transaction.issueDate.format(formatter)
            }

            listOf(
                escapeCSV(transaction.payerPayee),
                escapeCSV(transaction.description),
                issueDateStr,
                transaction.paymentDate?.format(formatter) ?: "",
                transaction.monetaryValue.toString(),
                transaction.transactionType.toString(),
                transaction.isInstallment.toString(),
                transaction.installmentCurrent?.toString() ?: "",
                transaction.installmentTotal?.toString() ?: "",
                escapeCSV(transaction.category ?: ""),
                escapeCSV(transaction.bankAccount)
            ).joinToString(DELIMITER)
        }

        return buildString {
            append(HEADER)
            append("\n")
            if (rows.isNotEmpty()) {
                append(rows.joinToString("\n"))
                append("\n")
            }
        }
    }

    fun importTransactions(inputStream: InputStream): CsvImportResult {
        val transactions = mutableListOf<TransactionEntity>()
        val warnings = mutableListOf<String>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.use { r ->
            r.readLine()
            var lineNumber = 1

            var line: String? = r.readLine()
            while (line != null) {
                lineNumber++

                if (line.isNotBlank()) {
                    val tokens = parseCSVLine(line)

                    // Agora exigimos pelo menos 6 colunas (até transactionType)
                    if (tokens.size < 6) {
                        warnings.add("Linha $lineNumber: quantidade de colunas insuficiente.")
                        line = r.readLine()
                        continue
                    }

                    val payerPayee = tokens[0].trim()
                    val description = tokens[1].trim()
                    val issueDateStr = tokens[2].trim()
                    val paymentDateStr = tokens[3].trim()
                    val valueStr = tokens[4].trim()
                    val typeStr = tokens[5].trim().lowercase()

                    // 1. Valida Pagador/Recebedor
                    if (payerPayee.isEmpty()) {
                        warnings.add("Linha $lineNumber: campo pagador/recebedor está vazio.")
                        line = r.readLine()
                        continue
                    }

                    // 2. Valida Emissão (Opcional: se vazio assume 01/01/1900; se preenchido valida o formato)
                    val parsedIssueDate = if (issueDateStr.isNotEmpty()) {
                        try {
                            LocalDate.parse(issueDateStr, formatter)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toOffsetDateTime()
                        } catch (_: Exception) {
                            warnings.add("Linha $lineNumber: data de emissão '$issueDateStr' inválida (esperado dd/MM/yyyy).")
                            line = r.readLine()
                            continue
                        }
                    } else {
                        DEFAULT_ISSUE_DATE
                    }

                    // 3. Valida Vencimento/Recebimento (Obrigatório)
                    if (paymentDateStr.isEmpty()) {
                        warnings.add("Linha $lineNumber: data de vencimento/recebimento é obrigatória e está vazia.")
                        line = r.readLine()
                        continue
                    }

                    val parsedPaymentDate = try {
                        LocalDate.parse(paymentDateStr, formatter)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toOffsetDateTime()
                    } catch (_: Exception) {
                        warnings.add("Linha $lineNumber: data de vencimento/recebimento '$paymentDateStr' inválida (esperado dd/MM/yyyy).")
                        line = r.readLine()
                        continue
                    }

                    // 4. Valida Valor Monetário
                    val monetaryValue = valueStr.toDoubleOrNull()
                    if (monetaryValue == null || monetaryValue <= 0.0) {
                        warnings.add("Linha $lineNumber: valor '$valueStr' inválido.")
                        line = r.readLine()
                        continue
                    }

                    // 5. Valida Tipo da Transação (true = Receita, false = Despesa)
                    val transactionType = when (typeStr) {
                        "true" -> true
                        "false" -> false
                        else -> {
                            warnings.add("Linha $lineNumber: tipo de transação '$typeStr' inválido (use true ou false).")
                            line = r.readLine()
                            continue
                        }
                    }

                    // 6. Valida Parcelamento (índices deslocados +1)
                    val isInstallment = tokens.size > 6 && tokens[6].trim().equals("true", ignoreCase = true)
                    val installmentCurrent =
                        if (tokens.size > 7 && tokens[7].isNotBlank()) tokens[7].trim().toIntOrNull() else null
                    val installmentTotal =
                        if (tokens.size > 8 && tokens[8].isNotBlank()) tokens[8].trim().toIntOrNull() else null

                    if (isInstallment && (installmentCurrent == null || installmentTotal == null || installmentCurrent <= 0 || installmentTotal <= 0)) {
                        warnings.add("Linha $lineNumber: dados de parcelamento inválidos.")
                        line = r.readLine()
                        continue
                    }

                    // 7. Valida Categoria (índice 9)
                    val rawCategory = if (tokens.size > 9) tokens[9].trim() else ""
                    val category = if (!transactionType) {
                        val matchedCategory = VALID_EXPENSE_CATEGORIES.firstOrNull {
                            it.equals(rawCategory, ignoreCase = true)
                        }
                        if (matchedCategory == null) {
                            val motivo = if (rawCategory.isEmpty()) "vazia" else "'$rawCategory'"
                            warnings.add("Linha $lineNumber: categoria $motivo inválida para despesa (use Necessidades, Desejos ou Investimento).")
                            line = r.readLine()
                            continue
                        }
                        matchedCategory
                    } else {
                        rawCategory.ifEmpty { null }
                    }

                    // 8. Conta Bancária (índice 10 - assume "Carteira" se vazio)
                    val hasBankAccount = tokens.size > 10 && tokens[10].isNotBlank()

                    val entity = if (hasBankAccount) {
                        TransactionEntity(
                            id = 0,
                            payerPayee = payerPayee,
                            description = description,
                            issueDate = parsedIssueDate,
                            paymentDate = parsedPaymentDate,
                            monetaryValue = monetaryValue,
                            transactionType = transactionType,
                            isInstallment = isInstallment,
                            installmentCurrent = installmentCurrent,
                            installmentTotal = installmentTotal,
                            category = category,
                            bankAccount = tokens[10].trim()
                        )
                    } else {
                        TransactionEntity(
                            id = 0,
                            payerPayee = payerPayee,
                            description = description,
                            issueDate = parsedIssueDate,
                            paymentDate = parsedPaymentDate,
                            monetaryValue = monetaryValue,
                            transactionType = transactionType,
                            isInstallment = isInstallment,
                            installmentCurrent = installmentCurrent,
                            installmentTotal = installmentTotal,
                            category = category
                        )
                    }

                    transactions.add(entity)
                }
                line = r.readLine()
            }
        }

        return CsvImportResult(
            transactions = transactions,
            warnings = warnings
        )
    }

    private fun escapeCSV(value: String): String {
        if (value.contains(DELIMITER) || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun parseCSVLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val char = line[i]
            if (char == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    currentToken.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char.toString() == DELIMITER && !inQuotes) {
                tokens.add(currentToken.toString())
                currentToken = StringBuilder()
            } else {
                currentToken.append(char)
            }
            i++
        }
        tokens.add(currentToken.toString())
        return tokens
    }
}