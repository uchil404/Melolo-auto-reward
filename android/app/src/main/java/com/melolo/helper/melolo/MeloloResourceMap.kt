package com.melolo.helper.melolo

/** C. ResourceMap Melolo 5.4.4 - identifier UI sebagai signal utama */
enum class MeloloTaskType {
    CHECK_IN, CHECK_IN_TOMORROW, CHEST_REWARD, AD_REWARD, CLAIM_REWARD,
    ALREADY_COMPLETED, NO_REWARD, LOGIN_REQUIRED, SECURITY_STOP, UNKNOWN
}

object MeloloResourceMap {
    // CHECK-IN
    const val CHECK_IN = "check_in"
    const val CHECK_IN_TITLE = "check_in_title"
    const val CHECK_IN_TITLE_NEW = "check_in_title_new"
    const val CHECK_IN_TASK_BUTTON_CHECK_IN = "check_in_task_button_check_in"
    const val CHECK_IN_TASK_BUTTON_TOMORROW = "check_in_task_button_tomorrow"
    const val CHECK_IN_POPUP_BUTTON_CLAIM_NOW = "check_in_popup_button_claim_now"
    const val CHECK_IN_POPUP_BUTTON_CLAIM_TOMORROW = "check_in_popup_button_claim_tomorrow"
    const val CHECK_IN_POPUP_TODAY = "check_in_popup_today"
    const val CHECK_IN_POPUP_TOMORROW = "check_in_popup_tomorrow"
    const val CHECK_IN_REWARD_ALL = "check_in_reward_all"
    const val CHECK_IN_TOMORROW_PAGE_BUTTON1 = "check_in_tomorrow_page_button1"
    // REWARD
    const val CLAIM_AD = "claim_ad"
    const val CLAIM_CHEST_REWARDS = "claim_chest_rewards"
    const val CHEST_AD_REWARDS = "chest_ad_rewards"
    const val CHEST_REWARDS = "chest_rewards"
    const val CASH_REWARD = "cash_reward"
    const val CASH_REWARDS = "cash_rewards"
    const val REWARD_AMOUNT = "rewardAmount"
    const val REWARD_AMOUNT_STR = "rewardAmountStr"
    const val COIN_REWARD_AMOUNT = "coinRewardAmount"
    const val REWARD_AMOUNT_TEXT = "rewardAmountText"
    // AD/VIDEO (observasi)
    const val AD_REWARD_FINISH = "AD_REWARD_FINISH"
    const val GROMORE = "GROMORE_SS_REWARD_VERIFY"

    fun toTaskType(resId: String): MeloloTaskType = when(resId){
        CHECK_IN_TASK_BUTTON_CHECK_IN -> MeloloTaskType.CHECK_IN
        CHECK_IN_TASK_BUTTON_TOMORROW, CHECK_IN_POPUP_BUTTON_CLAIM_TOMORROW -> MeloloTaskType.CHECK_IN_TOMORROW
        CHECK_IN_POPUP_BUTTON_CLAIM_NOW -> MeloloTaskType.CLAIM_REWARD
        CLAIM_AD, CHEST_AD_REWARDS -> MeloloTaskType.AD_REWARD
        CLAIM_CHEST_REWARDS, CHEST_REWARDS -> MeloloTaskType.CHEST_REWARD
        else -> MeloloTaskType.UNKNOWN
    }
}
