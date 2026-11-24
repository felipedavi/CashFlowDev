package meimaonamassa.cashflow.util

import android.widget.EditText
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import meimaonamassa.cashflow.util.extension.isDateValid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DatePickerHelper {
    fun showDatePicker(
        fragmentManager: FragmentManager,
        editText: EditText,
        callback: ((result: String) -> Unit)? = null
    ) {
        val inDate = editText.text.toString()
        val builder = MaterialDatePicker.Builder.datePicker()
        builder.setTitleText("Selecione a data")

        if (inDate.isDateValid()) {
            try {
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = formatter.parse(inDate)
                if (parsed != null) {
                    builder.setSelection(parsed.time)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            if (selection != null) {
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
                formatter.timeZone = TimeZone.getTimeZone("UTC")
                val selectedDate = formatter.format(Date(selection))
                editText.setText(selectedDate)
                editText.error = null
                callback?.invoke(selectedDate)
            }
        }
        picker.show(fragmentManager, "materialDatePicker")
    }
}
