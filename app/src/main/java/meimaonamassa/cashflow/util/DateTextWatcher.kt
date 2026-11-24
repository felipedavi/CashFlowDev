package meimaonamassa.cashflow.util

import android.text.Editable
import android.text.TextWatcher

class DateTextWatcher : TextWatcher {
    private var isUpdating = false
    private var oldText = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        oldText = s?.toString() ?: ""
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isUpdating) return

        val str = s?.toString() ?: ""
        val clean = str.replace("[^\\d]".toRegex(), "")

        if (clean == oldText.replace("[^\\d]".toRegex(), "")) {
            return
        }

        isUpdating = true

        val sb = StringBuilder()
        val len = clean.length

        for (i in 0 until len.coerceAtMost(8)) {
            sb.append(clean[i])
            if ((i == 1 || i == 3) && i != len - 1) {
                sb.append("/")
            }
        }

        s?.replace(0, s.length, sb.toString())
        isUpdating = false
    }
}
