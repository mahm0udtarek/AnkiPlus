/*
 * AnkiPlus is a modified fork of AnkiDroid, licensed under the GNU General Public License v3.0.
 *
 * Quiz mode: multiple choice questions built from the answers of other cards.
 */
package com.ichi2.anki

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {
    private data class Q(
        val question: String,
        val answer: String,
    )

    private val accent = Color.parseColor("#0A9AD8")
    private val good = Color.parseColor("#2E9E4F")
    private val bad = Color.parseColor("#D64545")
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var questionView: TextView
    private lateinit var questionCard: MaterialCardView
    private lateinit var optionsBox: LinearLayout
    private lateinit var resultView: TextView
    private var pool: List<Q> = emptyList()
    private var order: List<Q> = emptyList()
    private var index = 0
    private var score = 0
    private var query = ""

    private fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun themeColor(
        attr: Int,
        fallback: Int,
    ): Int = MaterialColors.getColor(this, attr, fallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        title = TextView(this).apply {
            textSize = 14f
            setTextColor(accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        questionView = TextView(this).apply {
            textSize = 22f
            setTextColor(onSurface)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }
        questionCard =
            MaterialCardView(this).apply {
                radius = dp(28).toFloat()
                cardElevation = dp(6).toFloat()
                setCardBackgroundColor(surface)
                addView(questionView)
                layoutParams =
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                        bottomMargin = dp(24)
                    }
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
            addView(questionCard)
            addView(optionsBox)
            addView(resultView)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        chooseDeck()
    }

    private fun chooseDeck() {
        title.text = "Quiz"
        questionView.text = "Choose a deck"
        resultView.text = ""
        optionsBox.removeAllViews()
        lifecycleScope.launch {
            val decks = withCol { decks.allNamesAndIds(skipEmptyDefault = true, includeFiltered = false) }
            optionsBox.removeAllViews()
            optionsBox.addView(button("All decks", true) { start("") })
            decks.forEach { d ->
                optionsBox.addView(button(d.name, false) { start("deck:\"${d.name}\"") })
            }
        }
    }

    private fun start(deckQuery: String) {
        query = deckQuery
        index = 0
        score = 0
        title.text = "Loading..."
        optionsBox.removeAllViews()
        lifecycleScope.launch {
            val cards =
                withCol {
                    val ids = findCards(query).shuffled().take(80)
                    ids.mapNotNull { id ->
                        try {
                            val f = getNote(getCard(id).nid).fields
                            val raw = f[0] + f[1]
                            if (Regex("(?i)<img|<object|<audio|<video|\\[sound|<embed|<br|<div|<p>|<ul|<ol|<table").containsMatchIn(raw)) {
                                null
                            } else {
                                val qq = plain(f[0])
                                val aa = plain(f[1])
                                if (qq.contains('\n') || aa.contains('\n') || qq.length > 120 || aa.length > 60 || aa.contains('\uFFFC')) null else Q(qq, aa)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            pool = cards.filter { it.question.isNotEmpty() && it.answer.isNotEmpty() }.distinctBy { it.answer }
            if (pool.size < 4) {
                title.text = "Quiz"
                questionView.text = "This deck needs at least 4 cards with different answers."
                optionsBox.removeAllViews()
                optionsBox.addView(button("Choose another deck", true) { chooseDeck() })
                return@launch
            }
            order = pool.shuffled().take(10)
            show()
        }
    }

    private fun show() {
        optionsBox.removeAllViews()
        if (index >= order.size) {
            title.text = "Finished"
            questionView.text = "Score: $score / ${order.size}"
            resultView.text = ""
            optionsBox.addView(button("Play again", true) { start(query) })
            optionsBox.addView(button("Choose deck", false) { chooseDeck() })
            optionsBox.addView(button("Close", false) { finish() })
            return
        }
        val q = order[index]
        title.text = "Question ${index + 1} of ${order.size}"
        questionView.text = q.question
        resultView.text = ""
        val options = (pool.filter { it.answer != q.answer }.shuffled().take(3).map { it.answer } + q.answer).shuffled()
        options.forEach { opt ->
            val b = button(opt, false) { }
            b.setOnClickListener {
                val ok = opt == q.answer
                if (ok) score++
                resultView.text = if (ok) "Correct!" else "Answer: ${q.answer}"
                resultView.setTextColor(if (ok) good else bad)
                for (i in 0 until optionsBox.childCount) {
                    val v = optionsBox.getChildAt(i) as MaterialButton
                    v.isEnabled = false
                    val isAnswer = v.text.toString() == q.answer
                    if (isAnswer || v === b) {
                        v.backgroundTintList = ColorStateList.valueOf(if (isAnswer) good else bad)
                        v.setTextColor(Color.WHITE)
                    }
                }
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
        filled: Boolean,
        onClick: (View) -> Unit,
    ): MaterialButton {
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        return MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            cornerRadius = dp(18)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(56)
            if (filled) {
                backgroundTintList = ColorStateList.valueOf(accent)
                setTextColor(Color.WHITE)
            } else {
                backgroundTintList = ColorStateList.valueOf(themeColor(com.google.android.material.R.attr.colorSurface, Color.WHITE))
                setTextColor(onSurface)
                strokeColor = ColorStateList.valueOf(accent)
                strokeWidth = dp(2)
            }
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            setOnClickListener { onClick(it) }
        }
    }
}
