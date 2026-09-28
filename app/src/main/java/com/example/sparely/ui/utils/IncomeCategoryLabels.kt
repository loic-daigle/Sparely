package com.example.sparely.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.sparely.R
import com.example.sparely.domain.model.IncomeCategory

@Composable
fun IncomeCategory.localizedName(): String = stringResource(
    when (this) {
        IncomeCategory.SALARY -> R.string.income_category_salary
        IncomeCategory.FREELANCE -> R.string.income_category_freelance
        IncomeCategory.GIFT -> R.string.income_category_gift
        IncomeCategory.INVESTMENT -> R.string.income_category_investment
        IncomeCategory.OTHER -> R.string.income_category_other
    }
)
