package com.melolo.helper

/** Batch3: static mapping class/call-site, request params, headers/device, Google flow
 *  Semua hanya observability/mapping - TIDAK mengambil/menyimpan credential.
 */
object StaticMapping {
    data class CallSite(val clazz: String, val method: String, val endpoint: String)
    // Isi manual dari analisis APK com.worldance.drama (jadx/apktool) - update saat APK berubah
    val sites = listOf(
        CallSite("RewardApi","claimReward","/api/reward/claim"),
        CallSite("CheckInApi","checkIn","/api/reward/checkin"),
        CallSite("AdApi","watchAd","/api/reward/ad")
    )
    val requestParams = mapOf(
        "claim" to listOf("taskId","rewardId"),
        "checkin" to listOf("date"),
        "ad" to listOf("adId","duration")
    )
    val headers = listOf("Authorization","X-Device-Id","X-App-Version","User-Agent")
    val deviceMeta = listOf("androidId","model","osVersion","appVersion")

    /** Google login flow mapping - hanya urutan langkah, tanpa token */
    object GoogleFlow {
        val steps = listOf("launch_login","select_account","consent","redirect","verify")
        fun observe(step: String) { Logger.info("GoogleFlow: $step") }
    }
}
