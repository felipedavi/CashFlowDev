package meimaonamassa.cashflow.feature.transaction.detail.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import meimaonamassa.cashflow.MainApplication
import meimaonamassa.cashflow.R
import meimaonamassa.cashflow.base.DateConverters
import meimaonamassa.cashflow.data.entity.TransactionEntity
import meimaonamassa.cashflow.databinding.FragmentTransactionDetailBinding
import meimaonamassa.cashflow.feature.transaction.detail.TransactionDetailViewModel
import meimaonamassa.cashflow.feature.transaction.detail.TransactionDetailViewModelFactory
import meimaonamassa.cashflow.util.CurrencyTextWatcher
import meimaonamassa.cashflow.util.DatePickerFragment
import meimaonamassa.cashflow.util.extension.fromCurrency
import meimaonamassa.cashflow.util.extension.fromFormattedDate
import meimaonamassa.cashflow.util.extension.hideKeyboard
import meimaonamassa.cashflow.util.extension.isValid
import meimaonamassa.cashflow.util.extension.toCurrency
import meimaonamassa.cashflow.util.extension.toFormattedDate

class TransactionDetailFragment : Fragment() {

    private lateinit var viewModel: TransactionDetailViewModel
    private var _binding: FragmentTransactionDetailBinding? = null
    private val binding get() = _binding!!
    private val args: TransactionDetailFragmentArgs by navArgs()
    private lateinit var selectedTransaction: TransactionEntity

    // region Lifecycle
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionDetailBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(
            this, TransactionDetailViewModelFactory(
                (requireActivity().application as MainApplication).repository
            )
        )[TransactionDetailViewModel::class.java]

        viewModel.start(args.transactionID)

        setupMenu()
        observe()
        setListeners()
        setupBankAutoComplete()

        return binding.root
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.detail_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_clone -> {
                        cloneTransaction()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion

    // region Observers & UI Setup
    private fun observe() {
        viewModel.transaction.observe(viewLifecycleOwner) { transaction ->
            selectedTransaction = transaction

            binding.editPayerPayee.setText(selectedTransaction.payerPayee)
            binding.editDescription.setText(selectedTransaction.description)
            binding.editIssueDate.setText(
                DateConverters.fromOffsetDateTime(selectedTransaction.issueDate)?.fromFormattedDate()
            )
            binding.editPaymentDate.setText(
                DateConverters.fromOffsetDateTime(selectedTransaction.paymentDate)?.fromFormattedDate()
            )
            binding.editMoney.setText(selectedTransaction.monetaryValue.toCurrency())
            binding.actvBankAccount.setText(selectedTransaction.bankAccount, false)

            val isIncome = selectedTransaction.transactionType
            binding.radioIncome.isChecked = isIncome
            binding.radioExpense.isChecked = !isIncome

            if (selectedTransaction.isInstallment) {
                binding.checkInstallment.isChecked = true
                binding.checkInstallment.isEnabled = false
                binding.layoutInstallments.visibility = View.VISIBLE

                binding.editInstallmentCurrent.setText(selectedTransaction.installmentCurrent?.toString())
                binding.editInstallmentFinal.setText(selectedTransaction.installmentTotal?.toString())

                binding.editInstallmentCurrent.isEnabled = false
                binding.editInstallmentFinal.isEnabled = false
            }

            when (selectedTransaction.category) {
                "Necessidades" -> binding.radioNeeds.isChecked = true
                "Desejos" -> binding.radioWants.isChecked = true
                "Investimentos" -> binding.radioInvestments.isChecked = true
            }
        }
    }

    private fun setListeners() {
        binding.editMoney.addTextChangedListener(CurrencyTextWatcher(binding.editMoney))

        setupDateField(binding.editIssueDate)
        setupDateField(binding.editPaymentDate)

        binding.groupRadioTransactionType.setOnCheckedChangeListener { _, checkedId ->
            updateTransactionTypeUI(checkedId == R.id.radio_income)
        }

        binding.buttonUpdate.setOnClickListener { handleUpdateClick() }
        binding.buttonClone.setOnClickListener { cloneTransaction() }
    }

    private fun cloneTransaction() {
        if (::selectedTransaction.isInitialized) {
            val action = TransactionDetailFragmentDirections
                .navigateToTransactionAddFragmentFromDetail(cloneTransactionId = selectedTransaction.id)
            findNavController().navigate(action)
        }
    }

    private fun setupDateField(editText: EditText) {
        editText.keyListener = null
        editText.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            hideKeyboard(view)
            if (hasFocus) {
                DatePickerFragment(editText) { selectedDate ->
                    editText.setText(selectedDate)
                }.show(requireActivity().supportFragmentManager, "datePicker")
                editText.clearFocus()
            }
        }
    }

    private fun setupBankAutoComplete() {
        val defaultBanks = listOf("Nubank", "Inter", "Mercado Pago", "Carteira")
        updateAutoCompleteAdapter(defaultBanks)

        viewModel.distinctBanks.observe(viewLifecycleOwner) { savedBanks ->
            val allBanks = (defaultBanks + savedBanks)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            updateAutoCompleteAdapter(allBanks)
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun updateAutoCompleteAdapter(banks: List<String>) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            banks
        )
        binding.actvBankAccount.setAdapter(adapter)
    }
    // endregion

    // region UI Helpers
    private fun updateTransactionTypeUI(isIncome: Boolean) {
        binding.textPayerPayee.setText(if (isIncome) R.string.text_payer else R.string.text_payee)
        binding.textPaymentDate.setText(if (isIncome) R.string.text_receipt_date else R.string.text_due_date)
        binding.textCategory.isVisible = !isIncome
        binding.groupCategory.isVisible = !isIncome
        if (isIncome) binding.groupCategory.clearCheck()
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

    // region Validation & Update Logic
    private fun handleUpdateClick() {
        if (!validateFields()) return

        val payerPayee = binding.editPayerPayee.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()
        val issueDate = DateConverters.toOffsetDateTime(binding.editIssueDate.text.toString().toFormattedDate())
        val paymentDate = DateConverters.toOffsetDateTime(binding.editPaymentDate.text.toString().toFormattedDate())
        val monetaryValue = binding.editMoney.text.toString().fromCurrency()
        val transactionType = binding.radioIncome.isChecked
        val category = getSelectedCategory()
        val bankAccount = binding.actvBankAccount.text.toString().trim().ifEmpty { "Carteira" }

        val updatedTransaction = selectedTransaction.copy(
            payerPayee = payerPayee,
            description = description,
            issueDate = issueDate!!,
            paymentDate = paymentDate,
            monetaryValue = monetaryValue,
            transactionType = transactionType,
            category = category,
            bankAccount = bankAccount
        )

        viewModel.update(updatedTransaction)
        findNavController().navigateUp()
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
            Toast.makeText(context, R.string.group_radio_error, Toast.LENGTH_SHORT).show()
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
    // endregion
}