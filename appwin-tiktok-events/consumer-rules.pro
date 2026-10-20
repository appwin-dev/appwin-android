# The TikTok Business SDK references Play Billing for its optional
# auto-IAP tracking, which the Appwin adapter disables. Consumers that
# do not ship Play Billing would otherwise fail R8 with missing-class
# errors on com.android.billingclient.
-dontwarn com.android.billingclient.api.**
