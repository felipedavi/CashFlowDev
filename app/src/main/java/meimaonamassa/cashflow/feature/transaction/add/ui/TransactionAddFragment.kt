package meimaonamassa.cashflow.feature.transaction.add.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.textfield.TextInputLayout
import meimaonamassa.cashflow.MainApplication
import meimaonamassa.cashflow.R
import meimaonamassa.cashflow.base.DateConverters
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.databinding.FragmentTransactionAddBinding
import meimaonamassa.cashflow.feature.transaction.add.TransactionAddViewModel
import meimaonamassa.cashflow.feature.transaction.add.TransactionAddViewModelFactory
import meimaonamassa.cashflow.util.CurrencyTextWatcher
import meimaonamassa.cashflow.util.DatePickerHelper
import meimaonamassa.cashflow.util.DateTextWatcher
import meimaonamassa.cashflow.util.extension.fromCurrency
import meimaonamassa.cashflow.util.extension.fromFormattedDate
import meimaonamassa.cashflow.util.extension.hideKeyboard
import meimaonamassa.cashflow.util.extension.isValid
import meimaonamassa.cashflow.util.extension.toCurrency
import meimaonamassa.cashflow.util.extension.toFormattedDate
import org.threeten.bp.LocalDate
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

class TransactionAddFragment : Fragment() {

    private lateinit var viewModel: TransactionAddViewModel
    private var _binding: FragmentTransactionAddBinding? = null
    private val binding get() = _binding!!
    private val args: TransactionAddFragmentArgs by navArgs()

    // region Lifecycle
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionAddBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this, TransactionAddViewModelFactory(
                (requireActivity().application as MainApplication).repository
            )
        )[TransactionAddViewModel::class.java]

        setupViews()
        setListeners()
        setupBankAutoComplete()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion

    // region UI Setup & Listeners
    private fun setupViews() {
        val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        binding.editIssueDate.setText(currentDate)

        if (args.cloneTransactionId > 0) {
            viewModel.getTransaction(args.cloneTransactionId).observe(viewLifecycleOwner) { transaction ->
                if (transaction != null) {
                    populateFromTransaction(transaction)
                }
            }
        }
    }

    private fun populateFromTransaction(transaction: TransactionEntity) {
        binding.editPayerPayee.setText(transaction.payerPayee)
        binding.editDescription.setText(transaction.description)

        val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        binding.editIssueDate.setText(currentDate)
        binding.editPaymentDate.setText("")

        binding.editMoney.setText(transaction.monetaryValue.toCurrency())
        binding.actvBankAccount.setText(transaction.bankAccount, false)

        val isIncome = transaction.transactionType
        binding.radioIncome.isChecked = isIncome
        binding.radioExpense.isChecked = !isIncome

        binding.checkInstallment.isChecked = false
        binding.layoutInstallments.visibility = GONE
        binding.buttonCalculator.visibility = GONE
        binding.editInstallmentCurrent.text.clear()
        binding.editInstallmentFinal.text.clear()

        when (transaction.category) {
            "Necessidades" -> binding.radioNeeds.isChecked = true
            "Desejos" -> binding.radioWants.isChecked = true
            "Investimentos" -> binding.radioInvestments.isChecked = true
        }
    }

    private fun setListeners() {
        binding.editMoney.addTextChangedListener(CurrencyTextWatcher(binding.editMoney))

        setupDateField(binding.editIssueDate, binding.tilIssueDate)
        setupDateField(binding.editPaymentDate, binding.tilPaymentDate)

        binding.buttonCalculator.setOnClickListener {
            showCalculatorDialog()
        }

        binding.checkInstallment.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutInstallments.visibility = if (isChecked) View.VISIBLE else GONE
            binding.buttonCalculator.visibility = if (isChecked) View.VISIBLE else GONE
            if (!isChecked) {
                binding.editInstallmentCurrent.text.clear()
                binding.editInstallmentFinal.text.clear()
            }
        }

        binding.groupRadioTransactionType.setOnCheckedChangeListener { _, checkedId ->
            val isIncome = checkedId == R.id.radio_income
            val isExpense = checkedId == R.id.radio_expense

            binding.textPayerPayee.text =
                if (isIncome) getString(R.string.text_payer) else getString(R.string.text_payee)
            binding.textPaymentDate.text =
                if (isIncome) getString(R.string.text_receipt_date) else getString(R.string.text_due_date)

            if (isIncome) binding.groupCategory.clearCheck()
            binding.groupCategory.isVisible = isExpense
            binding.textCategory.isVisible = isExpense
        }

        binding.buttonSave.setOnClickListener { handleSaveClick() }
    }

    private fun setupDateField(editText: EditText, textInputLayout: TextInputLayout) {
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789/")
        editText.addTextChangedListener(DateTextWatcher())

        textInputLayout.setEndIconOnClickListener {
            hideKeyboard(editText)
            DatePickerHelper.showDatePicker(childFragmentManager, editText)
        }
    }

    private fun setupBankAutoComplete() {
        val defaultBanks = listOf("Carteira", "Flash", "Nubank", "Mercado Pago", "Inter")
        val bankComparator = Comparator<String> { b1, b2 ->
            val idx1 = defaultBanks.indexOfFirst { it.equals(b1.trim(), ignoreCase = true) }
            val idx2 = defaultBanks.indexOfFirst { it.equals(b2.trim(), ignoreCase = true) }
            when {
                idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                idx1 != -1 -> -1
                idx2 != -1 -> 1
                else -> b1.compareTo(b2, ignoreCase = true)
            }
        }

        updateAutoCompleteAdapter(defaultBanks)

        viewModel.distinctBanks.observe(viewLifecycleOwner) { savedBanks ->
            val allBanks = (defaultBanks + savedBanks)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedWith(bankComparator)
            updateAutoCompleteAdapter(allBanks)
        }
    }

    private fun updateAutoCompleteAdapter(banks: List<String>) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            banks
        )
        binding.actvBankAccount.setAdapter(adapter)
    }
    // endregion

    // region UI Helpers & Dialogs
    private fun showCalculatorDialog() {
        val finalInstallmentStr = binding.editInstallmentFinal.text.toString()

        if (finalInstallmentStr.isEmpty()) {
            Toast.makeText(context, "Preencha o número final de parcelas primeiro.", Toast.LENGTH_SHORT).show()
            return
        }

        val paddingPx = (16 * resources.displayMetrics.density).toInt()
        val marginPx = (20 * resources.displayMetrics.density).toInt()

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "R$ 0,00"
            addTextChangedListener(CurrencyTextWatcher(this))
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginPx, 8, marginPx, 8)
            }
        }

        val container = FrameLayout(requireContext()).apply {
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Calcular Valor da Parcela")
            .setMessage("Digite o valor total para dividir por $finalInstallmentStr parcelas:")
            .setView(container)
            .setPositiveButton("Calcular") { _, _ ->
                val totalValue = input.text.toString().fromCurrency()
                val installments = finalInstallmentStr.toDoubleOrNull() ?: 1.0

                if (installments > 0) {
                    val installmentValue = totalValue / installments
                    binding.editMoney.setText(installmentValue.toCurrency())
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getSelectedCategory(): String? {
        return when (binding.groupCategory.checkedRadioButtonId) {
            R.id.radio_needs -> "Necessidades"
            R.id.radio_wants -> "Desejos"
            R.id.radio_investments -> "Investimentos"
            else -> null
        }
    }
    // endregion

    // region Validation & Save Logic
    private fun handleSaveClick() {
        if (!validateFields()) return

        val payerPayee = binding.editPayerPayee.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()
        val issueDate = DateConverters.toOffsetDateTime(binding.editIssueDate.text.toString().toFormattedDate())
        val paymentDate = DateConverters.toOffsetDateTime(binding.editPaymentDate.text.toString().toFormattedDate())
        val monetaryValue = binding.editMoney.text.toString().fromCurrency()
        val transactionType = binding.radioIncome.isChecked
        val category = getSelectedCategory()
        val bankAccount = binding.actvBankAccount.text.toString().trim().ifEmpty { "Carteira" }

        if (binding.checkInstallment.isChecked) {
            val currentStr = binding.editInstallmentCurrent.text.toString()
            val finalStr = binding.editInstallmentFinal.text.toString()

            if (currentStr.isEmpty() || finalStr.isEmpty()) {
                Toast.makeText(context, "Preencha as parcelas.", Toast.LENGTH_SHORT).show()
                return
            }

            val current = currentStr.toInt()
            val final = finalStr.toInt()

            if (current == final) {
                saveInstallments(
                    payerPayee, description, issueDate, paymentDate, monetaryValue,
                    transactionType, category, bankAccount, current, final, false
                )
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Salvar parcelas")
                    .setMessage("Deseja salvar as demais parcelas automaticamente?")
                    .setPositiveButton("Sim") { _, _ ->
                        saveInstallments(
                            payerPayee, description, issueDate, paymentDate, monetaryValue,
                            transactionType, category, bankAccount, current, final, true
                        )
                    }
                    .setNegativeButton("Não") { _, _ ->
                        saveInstallments(
                            payerPayee, description, issueDate, paymentDate, monetaryValue,
                            transactionType, category, bankAccount, current, current, false
                        )
                    }
                    .show()
            }
        } else {
            val transaction = TransactionEntity(
                id = 0,
                payerPayee = payerPayee,
                description = description,
                issueDate = issueDate!!,
                paymentDate = paymentDate,
                monetaryValue = monetaryValue,
                transactionType = transactionType,
                isInstallment = false,
                category = category,
                bankAccount = bankAccount
            )
            saveAndExit(transaction)
        }
    }

    private fun validateFields(): Boolean {
        val fieldsValid = binding.editPayerPayee.isValid() &&
                binding.editDescription.isValid() &&
                binding.editIssueDate.isValid() &&
                binding.editPaymentDate.isValid() &&
                binding.editMoney.isValid()

        if (!fieldsValid) {
            Log.i("Validation", "Field validation failed.")
            return false
        }

        if (binding.groupRadioTransactionType.checkedRadioButtonId == -1) {
            Toast.makeText(context, getText(R.string.group_radio_error), Toast.LENGTH_SHORT).show()
            return false
        }

        val isExpense = binding.radioExpense.isChecked
        val noCategorySelected = binding.groupCategory.checkedRadioButtonId == -1

        if (isExpense && noCategorySelected) {
            Toast.makeText(context, "Selecione uma categoria para despesas", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun saveInstallments(
        payerPayee: String,
        description: String,
        issueDate: OffsetDateTime?,
        paymentDate: OffsetDateTime?,
        totalValue: Double,
        transactionType: Boolean,
        category: String?,
        bankAccount: String,
        current: Int,
        final: Int,
        saveAll: Boolean
    ) {
        if (saveAll) {
            for (i in current..final) {
                val currentMonthOffset = (i - current).toLong()
                val installmentDate = paymentDate?.plusMonths(currentMonthOffset)

                viewModel.insert(
                    TransactionEntity(
                        id = 0,
                        payerPayee = payerPayee,
                        description = description,
                        issueDate = issueDate!!,
                        paymentDate = installmentDate,
                        monetaryValue = totalValue,
                        transactionType = transactionType,
                        isInstallment = true,
                        installmentCurrent = i,
                        installmentTotal = final,
                        category = category,
                        bankAccount = bankAccount
                    )
                )
            }
            navigateBackAfterSave()
        } else {
            val transaction = TransactionEntity(
                id = 0,
                payerPayee = payerPayee,
                description = description,
                issueDate = issueDate!!,
                paymentDate = paymentDate,
                monetaryValue = totalValue,
                transactionType = transactionType,
                isInstallment = true,
                installmentCurrent = current,
                installmentTotal = final,
                category = category,
                bankAccount = bankAccount
            )
            saveAndExit(transaction)
        }
    }

    private fun saveAndExit(transaction: TransactionEntity) {
        viewModel.insert(transaction)
        navigateBackAfterSave()
    }

    private fun navigateBackAfterSave() {
        if (args.cloneTransactionId > 0) {
            findNavController().popBackStack(R.id.transactionFragment, false)
        } else {
            findNavController().navigateUp()
        }
    }
    // endregion
}