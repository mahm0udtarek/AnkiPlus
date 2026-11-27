/*
 * AnkiPlus is a modified fork of AnkiDroid, licensed under the GNU General Public License v3.0.
 *
 * Quiz mode: multiple choice questions built from the answers of other cards.
 */
package com.ichi2.anki

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {
    private data class Q(
        val question: String,
        val answer: String,
    )

    private val green = Color.parseColor("#0A9AD8")
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var questionView: TextView
    private lateinit var optionsBox: LinearLayout
    private lateinit var resultView: TextView
    private var pool: List<Q> = emptyList()
    private var order: List<Q> = emptyList()
    private var index = 0
    private var score = 0

    private fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = TextView(this).apply {
            textSize = 14f
            setTextColor(green)
        }
        questionView = TextView(this).apply {
            textSize = 22f
            setPadding(0, dp(12), 0, dp(20))
        }
        optionsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
            addView(title)
            addView(questionView)
            addView(optionsBox)
            addView(resultView)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        lifecycleScope.launch {
            val cards =
                withCol {
                    val ids = findCards("").shuffled().take(80)
                    ids.mapNotNull { id ->
                        try {
                            val f = getNote(getCard(id).nid).fields
                            Q(plain(f[0]), plain(f[1]))
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            pool = cards.filter { it.question.isNotEmpty() && it.answer.isNotEmpty() }.distinctBy { it.answer }
            if (pool.size < 4) {
                questionView.text = "Quiz mode needs at least 4 cards with different answers. Add more cards first."
                return@launch
            }
            order = pool.shuffled().take(10)
            show()
        }
    }

    private fun show() {
        if (index >= order.size) {
            title.text = "Finished"
            questionView.text = "Score: $score / ${order.size}"
            optionsBox.removeAllViews()
            resultView.text = ""
            optionsBox.addView(button("Play again") {
                index = 0
                score = 0
                order = pool.shuffled().take(10)
                show()
            })
            optionsBox.addView(button("Close") { finish() })
            return
        }
        val q = order[index]
        title.text = "Question ${index + 1} of ${order.size}"
        questionView.text = q.question
        resultView.text = ""
        val options = (pool.filter { it.answer != q.answer }.shuffled().take(3).map { it.answer } + q.answer).shuffled()
        optionsBox.removeAllViews()
        options.forEach { opt ->
            val b = button(opt) { }
            b.setOnClickListener {
                val ok = opt == q.answer
                if (ok) score++
                resultView.text = if (ok) "Correct!" else "Answer: ${q.answer}"
                resultView.setTextColor(if (ok) Color.parseColor("#2E9E4F") else Color.parseColor("#D64545"))
                for (i in 0 until optionsBox.childCount) optionsBox.getChildAt(i).isEnabled = false
                b.postDelayed({
                    index++
                    show()
                }, 1100)
            }
            optionsBox.addView(b)
        }
    }

    private fun button(
        text: String,
        onClick: () -> Unit,
    ): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background =
                GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(green)
                }
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            setOnClickListener { onClick() }
        }
}
